package iped.engine.task.yara;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.YaraConfig;
import iped.engine.task.AbstractTask;
import iped.properties.ExtraProperties;

/**
 * Pipeline task that applies YARA-X rules to each eligible item's binary
 * content and persists matches as multi-valued Lucene fields modeled after the
 * {@code RegexTask} {@code Regex:<category>} pattern:
 *
 * <ul>
 *   <li>{@link ExtraProperties#YARA_MATCH_PREFIX}{@code <namespace>/<name>} —
 *       one multi-valued field per matched rule, each value being a distinct
 *       matched string (decoded to printable ASCII when possible, lowercase
 *       hex otherwise). Drives the per-rule facet in {@code MetadataPanel} and
 *       feeds {@code getHighlightTerms()} through the literal-value branch.</li>
 *   <li>{@link ExtraProperties#YARA_TAGS} — multi-valued: union of tags
 *       declared on matched rules. Useful for cross-rule grouping (e.g.
 *       "show all items tagged {@code apt}").</li>
 * </ul>
 *
 * <p>Lifecycle (mirrors {@code HashDBLookupTask}):</p>
 * <ul>
 *   <li>{@link #init} — synchronized on a static lock: the first worker loads
 *       the config, compiles the catalog once and populates
 *       {@link #sharedEngine}. Every worker (including the first) then creates
 *       its own {@link YaraScanner} (not thread-safe).</li>
 *   <li>{@link #process} — scans the item if eligible and respects the
 *       size/timeout caps in {@link YaraConfig}.</li>
 *   <li>{@link #finish} — each worker destroys its scanner; the last worker
 *       destroys the shared engine and prints a metrics summary.</li>
 * </ul>
 *
 * <p>When the feature is disabled ({@code enableYara=false}, empty catalog, or
 * the native engine is unavailable), {@link #isEnabled} returns {@code false}
 * and {@link #process} is a no-op (FR-013 / FR-014).</p>
 */
public class YaraScanTask extends AbstractTask {

    private static final Logger logger = LoggerFactory.getLogger(YaraScanTask.class);

    /** Static lock used to guard the one-shot init/finish across all workers. */
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final AtomicBoolean finished = new AtomicBoolean(false);

    /** Shared across all workers (read-only after {@code yrx_compiler_build}). */
    private static volatile YaraEngine sharedEngine = null;
    /** {@code true} if the feature is globally enabled for this case. */
    private static volatile boolean taskEnabled = false;

    /** Global metrics aggregated across all workers. */
    private static final AtomicLong itemsScanned = new AtomicLong();
    private static final AtomicLong itemsSkippedSize = new AtomicLong();
    private static final AtomicLong itemsSkippedNoStream = new AtomicLong();
    private static final AtomicLong itemsSkippedError = new AtomicLong();
    private static final AtomicLong itemsWithMatches = new AtomicLong();
    private static final AtomicLong totalMatches = new AtomicLong();

    /* Per-worker state (one instance per worker). */
    private YaraConfig config;
    private YaraScanner scanner;
    private int timeoutSeconds;

    @Override
    public List<Configurable<?>> getConfigurables() {
        // IMPORTANT: this is called during Configuration.loadConfigurables BEFORE
        // configurables are registered, so ConfigurationManager.findObject(...) would
        // return null here. Build a fresh instance instead (same pattern as
        // HashDBLookupTask, HashTask, etc.). The Manager then registers it and
        // populates its fields; init(cm) below retrieves it via cm.findObject(...).
        return Arrays.asList(new YaraConfig());
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        initWithConfig(configurationManager.findObject(YaraConfig.class));
    }

    /**
     * Testable init path that bypasses {@code ConfigurationManager} —
     * package-private for use in {@code YaraScanTaskIntegrationTest}. In
     * production this is always entered via {@link #init(ConfigurationManager)}.
     */
    void initWithConfig(YaraConfig config) {
        this.config = config;
        synchronized (initialized) {
            if (!initialized.get()) {
                taskEnabled = doSharedInit(config);
                initialized.set(true);
            }
        }
        // Per-worker setup runs only when the shared init succeeded.
        if (taskEnabled && sharedEngine != null) {
            scanner = sharedEngine.createScanner(config.getMatchHexMaxBytes());
            if (scanner == null) {
                // Engine reported available at shared init but scanner creation failed
                // for this worker — degrade locally without taking the whole task down.
                logger.warn("YaraScanTask: failed to create per-worker scanner — task disabled for this worker");
                return;
            }
            // Ceil ms->s: the config accepts perItemTimeoutMs >= 100, but the YARA-X
            // C API only supports whole-second granularity. Plain integer division
            // would truncate any sub-second value to 0 (= no timeout), so round up to
            // keep at least a 1 s limit.
            int timeoutMs = config.getPerItemTimeoutMs();
            timeoutSeconds = (timeoutMs <= 0) ? 0 : Math.max(1, (timeoutMs + 999) / 1000);
        }
    }

    /**
     * Runs exactly once (synchronized). Loads the catalog, bans the cuckoo module
     * (via {@link YaraEngine#compileSources}), and populates {@link #sharedEngine}.
     */
    private static boolean doSharedInit(YaraConfig config) {
        if (config == null || !config.isEnabled()) {
            logger.info("YaraScanTask disabled (enableYara=false or YaraConfig missing).");
            return false;
        }
        if (config.getRuleDirectories().isEmpty()) {
            logger.warn("YaraScanTask: ruleDirectories is empty — task disabled for this case.");
            return false;
        }
        if (!YaraEngine.ensureAvailable(config.getEngineLibraryHint())) {
            logger.warn("YaraScanTask: libyara-x-capi not loadable — task disabled for this case.");
            return false;
        }
        List<File> sources = YaraRulesetLoader.discover(config.getRuleDirectories());
        if (sources.isEmpty()) {
            logger.warn("YaraScanTask: no .yar/.yara files found under {} — task disabled.",
                    config.getRuleDirectories());
            return false;
        }
        long t0 = System.nanoTime();
        sharedEngine = YaraEngine.compileSources(sources, (src, line, msg) -> {
            String where = (src == null) ? "?" : src.getName();
            if (line > 0) {
                logger.warn("YaraScanTask: rule compile error at {}:{} — {}", where, line, msg);
            } else {
                logger.warn("YaraScanTask: rule compile error at {} — {}", where, msg);
            }
        });
        long compileMs = (System.nanoTime() - t0) / 1_000_000L;
        if (sharedEngine == null) {
            logger.warn("YaraScanTask: no YARA-X rules compiled successfully — task disabled.");
            return false;
        }
        logger.info("YaraScanTask: catalog compiled in {} ms from {} source files (engine {}).",
                compileMs, sources.size(), YaraEngine.getEngineVersion());
        return true;
    }

    @Override
    public boolean isEnabled() {
        return taskEnabled && scanner != null;
    }

    @Override
    public void process(IItem evidence) throws Exception {
        if (!isEnabled() || evidence == null || evidence.isDir()) {
            return;
        }
        if (!isItemEligible(evidence)) {
            return;
        }
        long maxBytes = config.getMaxFileSizeBytes();
        Long lengthBoxed = evidence.getLength();
        long length = (lengthBoxed == null) ? 0L : lengthBoxed;
        if (length > maxBytes) {
            itemsSkippedSize.incrementAndGet();
            return;
        }
        // Read with a hard cap regardless of the declared length: getLength() can be
        // unknown (null/0) or wrong for some streams -- and is not consulted at all
        // when scanAllItems=true -- so relying on it alone would let the read pull an
        // arbitrarily large item into memory and silently bypass maxFileSizeBytes.
        byte[] buffer = readItemContent(evidence, maxBytes);
        if (buffer == null) {
            itemsSkippedNoStream.incrementAndGet();
            return;
        }
        if (buffer.length > maxBytes) {
            // Stream turned out larger than maxFileSizeBytes (declared length was
            // unknown/too small) -- skip as oversize instead of scanning a truncation.
            itemsSkippedSize.incrementAndGet();
            return;
        }

        List<YaraMatch> matches;
        try {
            matches = scanner.scan(buffer, buffer.length, timeoutSeconds);
        } catch (Throwable t) {
            // Native failure on a single item is caught and counted as skipped/error;
            // it does not propagate to avoid aborting the case (FR-005).
            itemsSkippedError.incrementAndGet();
            logger.debug("YaraScanTask: scan threw {} on item {}: {}",
                    t.getClass().getSimpleName(), evidence.getId(), t.getMessage());
            return;
        }
        itemsScanned.incrementAndGet();
        if (matches.isEmpty()) {
            return;
        }

        persistMatches(evidence, matches, buffer.length);
        itemsWithMatches.incrementAndGet();
        totalMatches.addAndGet(matches.size());
    }

    /**
     * Selective default (R-06): the item must have accessible binary content.
     * Setting {@code scanAllItems=true} bypasses the eligibility check.
     */
    private boolean isItemEligible(IItem evidence) {
        if (config.isScanAllItems()) {
            return true;
        }
        Long len = evidence.getLength();
        if (len == null || len <= 0) {
            return false;
        }
        // There is no cheap way to test "has stream" without opening it — the open
        // happens in readItemContent() and items without a stream will fall into
        // itemsSkippedNoStream there.
        return evidence.getMediaType() != null;
    }

    /** Largest byte[] the JVM can reliably allocate (mirrors JDK internal headroom). */
    private static final int MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;

    /**
     * Reads item content up to {@code maxBytes} plus one sentinel byte, so the caller
     * can detect that the stream exceeded the cap without ever buffering more than
     * {@code maxBytes + 1} in memory. Returns {@code null} if the stream could not be
     * opened or an I/O failure occurred.
     */
    private byte[] readItemContent(IItem evidence, long maxBytes) {
        try (InputStream in = evidence.getBufferedInputStream()) {
            if (in == null) {
                return null;
            }
            long cap = Math.min(maxBytes + 1, (long) MAX_ARRAY_LENGTH);
            return in.readNBytes((int) cap);
        } catch (IOException e) {
            logger.debug("YaraScanTask: I/O error reading item {}: {}", evidence.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * Populates the YARA fields on the item. Deterministic ordering: tags in a
     * {@link LinkedHashSet} that
     * preserves union insertion order; per-rule match values collected in a
     * {@link LinkedHashSet} (dedup while preserving encounter order) then
     * written sorted lexicographically so the {@link iped.engine.task.index.IndexItem}
     * {@code SortedSetDocValues} facet is stable across runs.
     *
     * <p>Field layout (rev-5):</p>
     * <ul>
     *   <li>{@code yara:tag} — multi-valued: union of tags declared on matched
     *       rules. Cross-rule grouping facet (e.g. "all items tagged {@code apt}").</li>
     *   <li>{@code yara:match:<namespace>/<name>} — multi-valued, one field per
     *       matched rule; each value is a distinct matched string (decoded to
     *       printable ASCII when possible, else lowercase hex). Mirrors the
     *       {@code Regex:<category>} pattern from {@code RegexTask} — drilling
     *       a value filters the gallery and the same selection becomes a
     *       text-viewer highlight term via {@code MetadataPanel.getHighlightTerms()}.</li>
     * </ul>
     *
     * <p>The aggregated {@code yara:rule} list and the {@code yara:matches} JSON
     * audit blob were removed in rev-5 as redundant with the per-rule fields.
     * The {@code scannedBytes} parameter is kept for future per-item metrics but
     * is currently unused.</p>
     */
    private void persistMatches(IItem evidence, List<YaraMatch> matches, int scannedBytes) {
        Set<String> tagSet = new LinkedHashSet<>();
        // Merge values across multiple YaraMatch instances that share the same
        // namespace/name (rare but possible — defensive).
        Map<String, Set<String>> perRuleValues = new LinkedHashMap<>();
        for (YaraMatch m : matches) {
            tagSet.addAll(m.getTags());
            Set<String> values = perRuleValues.computeIfAbsent(m.getIdentifier(),
                    k -> new LinkedHashSet<>());
            for (MatchedString s : m.getStrings()) {
                String value = YaraHighlightSupport.decodeHexForFacet(s.getHex());
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
        }
        if (!tagSet.isEmpty()) {
            evidence.setExtraAttribute(ExtraProperties.YARA_TAGS, new ArrayList<>(tagSet));
        }
        for (Map.Entry<String, Set<String>> entry : perRuleValues.entrySet()) {
            Set<String> values = entry.getValue();
            if (values.isEmpty()) {
                continue;
            }
            List<String> ordered = new ArrayList<>(values);
            Collections.sort(ordered);
            evidence.setExtraAttribute(ExtraProperties.YARA_MATCH_PREFIX + entry.getKey(), ordered);
        }
    }

    @Override
    public void finish() throws Exception {
        if (scanner != null) {
            scanner.close();
            scanner = null;
        }
        synchronized (finished) {
            if (!finished.get()) {
                if (sharedEngine != null) {
                    sharedEngine.close();
                    sharedEngine = null;
                }
                if (taskEnabled) {
                    logger.info("YaraScanTask scan summary:");
                    logger.info("  itemsScanned   : {}", itemsScanned.get());
                    logger.info("  itemsWithMatches: {}", itemsWithMatches.get());
                    logger.info("  matchesTotal   : {}", totalMatches.get());
                    long skippedSize = itemsSkippedSize.get();
                    long skippedStream = itemsSkippedNoStream.get();
                    long skippedError = itemsSkippedError.get();
                    if (skippedSize + skippedStream + skippedError > 0) {
                        logger.info("  itemsSkipped   : {} (size={}, no-stream={}, error={})",
                                skippedSize + skippedStream + skippedError,
                                skippedSize, skippedStream, skippedError);
                    }
                }
                finished.set(true);
            }
        }
    }

    /* Visible for tests — reset static state between integration test runs. */
    static void resetForTests() {
        synchronized (initialized) {
            synchronized (finished) {
                if (sharedEngine != null) {
                    try {
                        sharedEngine.close();
                    } catch (Throwable ignore) {
                    }
                    sharedEngine = null;
                }
                initialized.set(false);
                finished.set(false);
                taskEnabled = false;
                itemsScanned.set(0);
                itemsSkippedSize.set(0);
                itemsSkippedNoStream.set(0);
                itemsSkippedError.set(0);
                itemsWithMatches.set(0);
                totalMatches.set(0);
            }
        }
    }
}
