package iped.engine.task.yara;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

/**
 * Per-worker scanner wrapper. Each {@code YaraScanner} owns a
 * {@code YRX_SCANNER*} created from the shared {@link YaraEngine}.
 *
 * <p>The YARA-X scanner is <b>not thread-safe</b>: every thread/worker that
 * scans needs its own instance. The underlying {@code YRX_RULES} (shared
 * via {@link YaraEngine}) is safe for concurrent read-only access.</p>
 *
 * <p>The match callback is installed once at construction via
 * {@code yrx_scanner_on_matching_rule}; on each {@link #scan} call the
 * accumulated list is cleared before scanning and the scanner is reused.</p>
 *
 * <p>For each matching rule the collector iterates patterns ({@code yrx_rule_iter_patterns})
 * and within each pattern iterates matches ({@code yrx_pattern_iter_matches}),
 * slicing bytes from the current scan buffer and encoding them as lowercase hex
 * (cap configurable via {@code matchHexMaxBytes}). These bytes populate the per-rule
 * {@code yara:match:<namespace>/<name>} facet and the text-viewer highlight in the UI (FR-008a).</p>
 */
public final class YaraScanner implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(YaraScanner.class);

    private static final char[] HEX_LC = "0123456789abcdef".toCharArray();

    private final Pointer scannerPtr;
    private final int matchHexMaxBytes;
    private final MatchCollector collector;
    private boolean closed = false;

    YaraScanner(Pointer scannerPtr, int matchHexMaxBytes) {
        this.scannerPtr = scannerPtr;
        this.matchHexMaxBytes = Math.max(1, matchHexMaxBytes);
        this.collector = new MatchCollector(this.matchHexMaxBytes);
        // The callback is installed exactly once for the lifetime of the scanner — state
        // (match list + current buffer) is reset/set before each scan().
        YaraEngine.LibYaraX.INSTANCE.yrx_scanner_on_matching_rule(scannerPtr, collector, Pointer.NULL);
    }

    /**
     * Scans a buffer and returns the list of matches for the current call.
     *
     * @param buffer bytes to scan
     * @param length valid bytes in the buffer (≤ {@code buffer.length})
     * @param timeoutSeconds {@code 0} = no timeout; {@code > 0} = limit in seconds
     */
    public List<YaraMatch> scan(byte[] buffer, int length, int timeoutSeconds) {
        if (closed || scannerPtr == null || buffer == null || length <= 0) {
            return Collections.emptyList();
        }
        collector.reset(buffer, length);
        if (timeoutSeconds > 0) {
            YaraEngine.LibYaraX.INSTANCE.yrx_scanner_set_timeout(scannerPtr, (long) timeoutSeconds);
        }
        Memory native_buf = new Memory(length);
        native_buf.write(0, buffer, 0, length);
        try {
            int rc = YaraEngine.LibYaraX.INSTANCE.yrx_scanner_scan(scannerPtr, native_buf, (long) length);
            if (rc != YaraEngine.YRX_SUCCESS && rc != YaraEngine.YRX_SCAN_TIMEOUT) {
                logger.debug("yrx_scanner_scan returned {}", rc);
            }
            return collector.takeMatches();
        } finally {
            // Release the reference to the Java buffer; the native callback does NOT
            // retain pointers after yrx_scanner_scan returns.
            collector.clearBuffer();
        }
    }

    @Override
    public void close() {
        if (!closed && scannerPtr != null && YaraEngine.isAvailable()) {
            try {
                YaraEngine.LibYaraX.INSTANCE.yrx_scanner_destroy(scannerPtr);
            } catch (Throwable t) {
                logger.warn("yrx_scanner_destroy threw {}: {}", t.getClass().getSimpleName(), t.getMessage());
            }
        }
        closed = true;
    }

    /**
     * Reads the identifier or namespace of a rule via
     * {@code yrx_rule_identifier}/{@code yrx_rule_namespace}. The pointers
     * returned by libyara-x-capi are <b>not NUL-terminated</b> — the
     * length comes in {@code len}.
     */
    private static String readPointerSlice(Pointer rule, boolean identifierMode) {
        PointerByReference out = new PointerByReference();
        LongByReference len = new LongByReference();
        int rc = identifierMode
                ? YaraEngine.LibYaraX.INSTANCE.yrx_rule_identifier(rule, out, len)
                : YaraEngine.LibYaraX.INSTANCE.yrx_rule_namespace(rule, out, len);
        if (rc != YaraEngine.YRX_SUCCESS || out.getValue() == null || len.getValue() <= 0) {
            return "";
        }
        byte[] bytes = out.getValue().getByteArray(0, (int) len.getValue());
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** Same pattern as {@link #readPointerSlice} but for the pattern identifier ({@code $name}). */
    private static String readPatternIdentifier(Pointer pattern) {
        PointerByReference out = new PointerByReference();
        LongByReference len = new LongByReference();
        int rc = YaraEngine.LibYaraX.INSTANCE.yrx_pattern_identifier(pattern, out, len);
        if (rc != YaraEngine.YRX_SUCCESS || out.getValue() == null || len.getValue() <= 0) {
            return "";
        }
        byte[] bytes = out.getValue().getByteArray(0, (int) len.getValue());
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Collects {@link YaraMatch}es inside the YARA-X scan callback. The internal
     * list is cleared on each {@link YaraScanner#scan} call to avoid accumulating
     * matches from previous scans.
     *
     * <p>Holds a reference to the current scan's Java buffer so that
     * pattern/match callbacks can slice the matched bytes. The reference is
     * cleared after {@code yrx_scanner_scan} returns.</p>
     */
    private static final class MatchCollector implements YaraEngine.RuleCallback {
        private final List<YaraMatch> matches = new ArrayList<>();
        private final int matchHexMaxBytes;
        private byte[] currentBuffer;
        private int currentLength;

        MatchCollector(int matchHexMaxBytes) {
            this.matchHexMaxBytes = matchHexMaxBytes;
        }

        void reset(byte[] buffer, int length) {
            matches.clear();
            this.currentBuffer = buffer;
            this.currentLength = length;
        }

        void clearBuffer() {
            this.currentBuffer = null;
            this.currentLength = 0;
        }

        List<YaraMatch> takeMatches() {
            return new ArrayList<>(matches);
        }

        @Override
        public void invoke(Pointer rule, Pointer userData) {
            if (rule == null) {
                return;
            }
            try {
                String name = readPointerSlice(rule, true);
                String ns = readPointerSlice(rule, false);
                final List<String> tags = new ArrayList<>();
                YaraEngine.LibYaraX.INSTANCE.yrx_rule_iter_tags(rule, (tag, ud) -> {
                    if (tag != null && !tag.isEmpty()) {
                        tags.add(tag);
                    }
                }, Pointer.NULL);

                List<MatchedString> strings = collectMatchedStrings(rule);
                matches.add(new YaraMatch(ns, name, tags, java.util.Collections.emptyMap(), strings));
            } catch (Throwable t) {
                logger.debug("MatchCollector failed to read YRX_RULE: {}", t.toString());
            }
        }

        /**
         * Iterates the patterns of the matched rule (only patterns with at least
         * one match are delivered by {@code yrx_rule_iter_patterns}) and, for
         * each pattern, iterates matches slicing bytes from the current buffer.
         */
        private List<MatchedString> collectMatchedStrings(Pointer rule) {
            final List<MatchedString> out = new ArrayList<>();
            try {
                YaraEngine.LibYaraX.INSTANCE.yrx_rule_iter_patterns(rule, (pattern, ud) -> {
                    if (pattern == null) {
                        return;
                    }
                    try {
                        final String id = readPatternIdentifier(pattern);
                        if (id.isEmpty()) {
                            return;
                        }
                        YaraEngine.LibYaraX.INSTANCE.yrx_pattern_iter_matches(pattern, (match, ud2) -> {
                            if (match == null) {
                                return;
                            }
                            try {
                                long offset = match.offset;
                                long length = match.length;
                                if (offset < 0 || length <= 0) {
                                    return;
                                }
                                boolean truncated = length > matchHexMaxBytes;
                                String hex = extractHex(offset, length);
                                out.add(new MatchedString(id, offset, hex, truncated));
                            } catch (Throwable t) {
                                logger.debug("Match iteration failed for {}: {}", id, t.toString());
                            }
                        }, Pointer.NULL);
                    } catch (Throwable t) {
                        logger.debug("Pattern iteration failed: {}", t.toString());
                    }
                }, Pointer.NULL);
            } catch (Throwable t) {
                logger.debug("Pattern listing failed: {}", t.toString());
            }
            return out;
        }

        /**
         * Slices {@code length} bytes from the current buffer starting at {@code offset}
         * and encodes them as lowercase hex. Clamped to {@link #matchHexMaxBytes} to
         * guard against huge patterns; the caller sets
         * {@link MatchedString#isTruncated()} when truncation occurs.
         */
        private String extractHex(long offset, long length) {
            if (currentBuffer == null || currentLength <= 0) {
                return "";
            }
            if (offset >= currentLength) {
                return "";
            }
            long available = currentLength - offset;
            long take = Math.min(Math.min(length, available), (long) matchHexMaxBytes);
            if (take <= 0) {
                return "";
            }
            int o = (int) offset;
            int n = (int) take;
            char[] hex = new char[n * 2];
            for (int i = 0; i < n; i++) {
                int b = currentBuffer[o + i] & 0xFF;
                hex[i * 2] = HEX_LC[b >>> 4];
                hex[i * 2 + 1] = HEX_LC[b & 0xF];
            }
            return new String(hex);
        }
    }
}
