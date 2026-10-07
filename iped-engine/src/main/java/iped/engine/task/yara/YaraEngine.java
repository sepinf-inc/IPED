package iped.engine.task.yara;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

/**
 * Thin JNA bindings for the YARA-X engine via {@code libyara-x-capi}.
 *
 * <p>YARA-X is the official Rust rewrite of YARA by Victor M. Alvarez (same
 * author as classic YARA). IPED migrated from classic libyara 4.x to YARA-X
 * on 2026-05-19 (Rust rewrite, successor of the classic libyara C library).</p>
 *
 * <p>Intentionally minimal surface:</p>
 * <ul>
 *   <li>{@code yrx_compiler_create/destroy/ban_module/new_namespace/add_source_with_origin/errors_json/build}
 *       — catalog compilation;</li>
 *   <li>{@code yrx_rules_destroy} — ruleset cleanup;</li>
 *   <li>{@code yrx_scanner_create/destroy/set_timeout/on_matching_rule/scan} — per-item scan;</li>
 *   <li>{@code yrx_rule_identifier/namespace/iter_tags} — reading inside the callback;</li>
 *   <li>{@code yrx_buffer_destroy}, {@code yrx_last_error} — utilities.</li>
 * </ul>
 *
 * <p>The {@code cuckoo} module is banned at runtime via {@code yrx_compiler_ban_module}.
 * Rules with {@code import "cuckoo"} produce an isolated compile error and are
 * discarded; the remaining rules continue (FR-002 + FR-005 + R-09).</p>
 *
 * <p>Detailed matched-string extraction (pattern ID + offset + bytes) is
 * done via {@code yrx_rule_iter_patterns} → {@code yrx_pattern_iter_matches}
 * in {@link YaraScanner}. Bytes are sliced from the current scan buffer and
 * encoded as lowercase hex, truncated at {@code YaraConfig.matchHexMaxBytes}
 * — feeds the per-rule {@code yara:match:*} facet and the text-viewer highlight
 * (FR-008a).</p>
 */
public final class YaraEngine implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(YaraEngine.class);

    /** Flags do compilador (yara-x: {@code YRX_RELAXED_RE_SYNTAX}). */
    public static final int YRX_RELAXED_RE_SYNTAX = 2;

    /** YRX_RESULT codes in order from the upstream header. */
    public static final int YRX_SUCCESS = 0;
    public static final int YRX_SYNTAX_ERROR = 1;
    public static final int YRX_VARIABLE_ERROR = 2;
    public static final int YRX_SCAN_ERROR = 3;
    public static final int YRX_SCAN_TIMEOUT = 4;
    public static final int YRX_INVALID_ARGUMENT = 5;
    public static final int YRX_INVALID_UTF8 = 6;
    public static final int YRX_INVALID_STATE = 7;
    public static final int YRX_SERIALIZATION_ERROR = 8;
    public static final int YRX_NO_METADATA = 9;
    public static final int YRX_NOT_SUPPORTED = 10;

    /**
     * Engine version identifier, surfaced in the catalog-compiled log line.
     *
     * <p>The YARA-X C API does not expose the version programmatically, so it is
     * hardcoded here to match exactly the {@code libyara-x-capi} binary bundled
     * in {@code tools/yara-x/<os>/}. When updating the binary, update this
     * constant, the {@code YARAX_VERSION} in the CI workflow, and
     * {@code tools/yara-x/README.md}.</p>
     */
    public static final String ENGINE_VERSION = "yara-x-1.16.0";

    private static volatile boolean libraryAvailable = false;
    private static volatile String libraryLoadError = null;

    private final Pointer rulesPtr;

    private YaraEngine(Pointer rulesPtr) {
        this.rulesPtr = rulesPtr;
    }

    /**
     * Activates the {@code libyara-x-capi} native library, optionally using
     * {@code hint} as the first path to try. Idempotent.
     *
     * <p>Search order:</p>
     * <ol>
     *   <li>If {@code hint} points to an existing file, its parent directory is
     *       prepended to JNA's search path.</li>
     *   <li>The bundled {@code tools/yara-x/<os>/} folder inside the IPED release
     *       is auto-detected from this class's code source location.</li>
     *   <li>JNA falls back to its built-in paths ({@code jna.library.path},
     *       classpath {@code <platform>/yara_x_capi.dll}, system library path).</li>
     * </ol>
     *
     * @return {@code true} if the engine is available for use.
     */
    public static synchronized boolean ensureAvailable(String hint) {
        if (libraryAvailable) {
            return true;
        }
        if (libraryLoadError != null) {
            return false;
        }
        try {
            if (hint != null && !hint.isEmpty()) {
                File hintFile = new File(hint);
                if (hintFile.exists()) {
                    NativeLibrary.addSearchPath("yara_x_capi", hintFile.getParentFile().getAbsolutePath());
                }
            }
            String autoDetected = detectBundledLibraryDir();
            if (autoDetected != null) {
                NativeLibrary.addSearchPath("yara_x_capi", autoDetected);
                logger.debug("YARA-X: added native search path {}", autoDetected);
            }
            // Forces the link; any failure surfaces as UnsatisfiedLinkError here.
            LibYaraX lib = LibYaraX.INSTANCE;
            // Calls a harmless function to confirm the linker actually resolved the symbol.
            lib.yrx_last_error();
            libraryAvailable = true;
            logger.info("YARA-X engine loaded (platform: {})", Platform.RESOURCE_PREFIX);
            return true;
        } catch (UnsatisfiedLinkError e) {
            libraryLoadError = "libyara-x-capi not loadable: " + e.getMessage();
            logger.warn("YARA-X engine disabled — {}", libraryLoadError);
            return false;
        } catch (Throwable t) {
            libraryLoadError = "libyara-x-capi init error: " + t.getMessage();
            logger.warn("YARA-X engine disabled — {}", libraryLoadError);
            return false;
        }
    }

    /**
     * Locates the bundled {@code libyara-x-capi} directory inside the IPED release.
     * Delegates to {@link YaraInstallPaths#getBundledLibraryDir()}; returns
     * {@code null} when running outside a release.
     */
    private static String detectBundledLibraryDir() {
        File dir = YaraInstallPaths.getBundledLibraryDir();
        return (dir == null) ? null : dir.getAbsolutePath();
    }

    /** Idempotent shutdown (no-op for YARA-X — classic libyara required {@code yr_finalize}). */
    public static synchronized void shutdown() {
        libraryAvailable = false;
    }

    public static boolean isAvailable() {
        return libraryAvailable;
    }

    /**
     * Engine version in the format {@code yara-x-<MAJOR.MINOR.PATCH>}. Hardcoded in
     * {@link #ENGINE_VERSION} — see that constant for the update policy.
     */
    public static String getEngineVersion() {
        return libraryAvailable ? ENGINE_VERSION : "yara-x-unavailable";
    }

    /**
     * Compiles a catalog of source rule files. For each {@code .yar}/{@code .yara} file:
     * (1) sets a namespace = basename without extension; (2) calls
     * {@code yrx_compiler_add_source_with_origin}; (3) on failure, extracts
     * errors via {@code yrx_compiler_errors_json} and reports them to {@code errorSink}.
     * Finally calls {@code yrx_compiler_build}. The {@code cuckoo} module is banned
     * before compilation.
     *
     * @return compiled ruleset, or {@code null} if no rule compiled successfully.
     */
    public static YaraEngine compileSources(List<File> sourceFiles, CompileErrorSink errorSink) {
        if (!ensureAvailable(null) || sourceFiles == null || sourceFiles.isEmpty()) {
            return null;
        }
        PointerByReference compilerRef = new PointerByReference();
        int rc = LibYaraX.INSTANCE.yrx_compiler_create(YRX_RELAXED_RE_SYNTAX, compilerRef);
        if (rc != YRX_SUCCESS) {
            logger.warn("yrx_compiler_create returned {}: {}", rc, lastError());
            return null;
        }
        Pointer compiler = compilerRef.getValue();
        try {
            // Ban the cuckoo module so that rules importing it fail with a clear message.
            int banRc = LibYaraX.INSTANCE.yrx_compiler_ban_module(compiler,
                    "cuckoo",
                    "Cuckoo module unsupported",
                    "IPED does not bundle the cuckoo module; rules importing it are rejected.");
            if (banRc != YRX_SUCCESS) {
                logger.debug("yrx_compiler_ban_module(cuckoo) returned {} — continuing", banRc);
            }

            int sourcesAdded = 0;
            for (File f : sourceFiles) {
                if (!f.isFile()) {
                    if (errorSink != null) {
                        errorSink.report(f, -1, "not a regular file");
                    }
                    continue;
                }
                String ns = stripExt(f.getName());
                int nsRc = LibYaraX.INSTANCE.yrx_compiler_new_namespace(compiler, ns);
                if (nsRc != YRX_SUCCESS) {
                    if (errorSink != null) {
                        errorSink.report(f, -1, "yrx_compiler_new_namespace returned " + nsRc);
                    }
                    continue;
                }
                String src;
                try {
                    src = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                } catch (IOException ioe) {
                    if (errorSink != null) {
                        errorSink.report(f, -1, "read error: " + ioe.getMessage());
                    }
                    continue;
                }
                int addRc = LibYaraX.INSTANCE.yrx_compiler_add_source_with_origin(compiler, src, f.getAbsolutePath());
                if (addRc != YRX_SUCCESS) {
                    reportCompilerErrors(compiler, f, errorSink);
                    // Continue even on error — other rules in the catalog may have compiled.
                    continue;
                }
                sourcesAdded++;
            }
            if (sourcesAdded == 0) {
                return null;
            }
            Pointer rules = LibYaraX.INSTANCE.yrx_compiler_build(compiler);
            if (rules == null) {
                logger.warn("yrx_compiler_build returned NULL: {}", lastError());
                return null;
            }
            return new YaraEngine(rules);
        } finally {
            LibYaraX.INSTANCE.yrx_compiler_destroy(compiler);
        }
    }

    /** Default cap used by {@link #createScanner()} and {@link #scan} (in sync with {@code YaraConfig} default). */
    public static final int DEFAULT_MATCH_HEX_MAX_BYTES = 256;

    /**
     * Creates a {@link YaraScanner} (one instance per worker) over this ruleset
     * using the default hex-per-match cap.
     *
     * <p>The scanner is not thread-safe; {@code YRX_RULES} (shared read-only
     * across scanners) is. See {@code research.md} §R-04.</p>
     */
    public YaraScanner createScanner() {
        return createScanner(DEFAULT_MATCH_HEX_MAX_BYTES);
    }

    /**
     * Creates a {@link YaraScanner} with an explicit bytes-per-matched-string cap —
     * used by {@code YaraScanTask}, which passes {@code YaraConfig.matchHexMaxBytes}.
     *
     * @param matchHexMaxBytes maximum number of bytes to slice from the scan buffer
     *                         per individual match (above this the hex is truncated and
     *                         {@link MatchedString#isTruncated()} returns {@code true})
     */
    public YaraScanner createScanner(int matchHexMaxBytes) {
        if (rulesPtr == null || !libraryAvailable) {
            return null;
        }
        PointerByReference scannerRef = new PointerByReference();
        int rc = LibYaraX.INSTANCE.yrx_scanner_create(rulesPtr, scannerRef);
        if (rc != YRX_SUCCESS) {
            logger.warn("yrx_scanner_create returned {}: {}", rc, lastError());
            return null;
        }
        return new YaraScanner(scannerRef.getValue(), matchHexMaxBytes);
    }

    /**
     * Convenience method for one-shot scans (used in tests). For the real pipeline,
     * call {@link #createScanner(int)} once per worker and reuse it.
     *
     * @param buffer bytes to scan
     * @param length valid bytes in the buffer (≤ {@code buffer.length})
     * @param timeoutSeconds {@code 0} = no timeout; {@code > 0} = limit in seconds
     */
    public List<YaraMatch> scan(byte[] buffer, int length, int timeoutSeconds) {
        if (rulesPtr == null || buffer == null || length <= 0 || !libraryAvailable) {
            return Collections.emptyList();
        }
        try (YaraScanner sc = createScanner()) {
            return (sc == null) ? Collections.emptyList() : sc.scan(buffer, length, timeoutSeconds);
        }
    }

    @Override
    public void close() {
        if (rulesPtr != null && libraryAvailable) {
            try {
                LibYaraX.INSTANCE.yrx_rules_destroy(rulesPtr);
            } catch (Throwable t) {
                logger.warn("yrx_rules_destroy threw {}: {}", t.getClass().getSimpleName(), t.getMessage());
            }
        }
    }

    /* ---------------------------------------------------------------------- */
    /*                      Error helpers / introspection                      */
    /* ---------------------------------------------------------------------- */

    private static String lastError() {
        try {
            String e = LibYaraX.INSTANCE.yrx_last_error();
            return (e == null || e.isEmpty()) ? "(no detail)" : e;
        } catch (Throwable t) {
            return "(yrx_last_error unavailable)";
        }
    }

    /**
     * Extracts accumulated compiler errors via {@code yrx_compiler_errors_json}
     * and reports them to the sink one by one.
     */
    private static void reportCompilerErrors(Pointer compiler, File source, CompileErrorSink sink) {
        if (sink == null) {
            return;
        }
        PointerByReference bufRef = new PointerByReference();
        int rc = LibYaraX.INSTANCE.yrx_compiler_errors_json(compiler, bufRef);
        if (rc != YRX_SUCCESS || bufRef.getValue() == null) {
            sink.report(source, -1, "compile error (yrx_compiler_errors_json failed, last_error=" + lastError() + ")");
            return;
        }
        Pointer bufPtr = bufRef.getValue();
        try {
            YRX_BUFFER buf = Structure.newInstance(YRX_BUFFER.class, bufPtr);
            buf.read();
            if (buf.data == null || buf.length <= 0) {
                sink.report(source, -1, "compile error (empty errors_json buffer)");
                return;
            }
            byte[] jsonBytes = buf.data.getByteArray(0, (int) buf.length);
            String json = new String(jsonBytes, StandardCharsets.UTF_8);
            parseErrorsJson(json, source, sink);
        } catch (Throwable t) {
            sink.report(source, -1, "compile error (failed to parse errors_json: " + t.getMessage() + ")");
        } finally {
            LibYaraX.INSTANCE.yrx_buffer_destroy(bufPtr);
        }
    }

    private static void parseErrorsJson(String json, File source, CompileErrorSink sink) {
        try {
            JsonNode root = new ObjectMapper().readTree(json);
            // The yara-x JSON is a list of objects with fields such as
            // {"code","title","origin":{"file","line",...},"text",...}.
            // Accept both a root array and an object with an "errors":[...] key.
            JsonNode errors = root.isArray() ? root : root.path("errors");
            if (errors == null || !errors.isArray() || errors.size() == 0) {
                // No structured errors — fall back to reporting the raw JSON.
                sink.report(source, -1, json.length() > 500 ? json.substring(0, 500) : json);
                return;
            }
            for (JsonNode err : errors) {
                int line = -1;
                JsonNode lineNode = err.path("origin").path("line");
                if (lineNode.isInt()) {
                    line = lineNode.asInt();
                } else if (err.path("line").isInt()) {
                    line = err.path("line").asInt();
                }
                String title = err.path("title").asText("");
                String text = err.path("text").asText("");
                String message = title.isEmpty() ? text : (text.isEmpty() ? title : title + " — " + text);
                if (message.isEmpty()) {
                    message = err.toString();
                }
                sink.report(source, line, message);
            }
        } catch (Throwable t) {
            sink.report(source, -1, "compile error (parse failed: " + t.getMessage() + "): " + json);
        }
    }

    private static String stripExt(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot > 0) ? fileName.substring(0, dot) : fileName;
    }

    /* ---------------------------------------------------------------------- */
    /*                          Internal — JNA binding                         */
    /* ---------------------------------------------------------------------- */

    interface LibYaraX extends Library {
        LibYaraX INSTANCE = Native.load("yara_x_capi", LibYaraX.class);

        // Compiler lifecycle
        int yrx_compiler_create(int flags, PointerByReference compiler);

        void yrx_compiler_destroy(Pointer compiler);

        int yrx_compiler_ban_module(Pointer compiler, String moduleName, String errorTitle, String errorMsg);

        int yrx_compiler_new_namespace(Pointer compiler, String namespaceName);

        int yrx_compiler_add_source_with_origin(Pointer compiler, String src, String origin);

        int yrx_compiler_errors_json(Pointer compiler, PointerByReference buf);

        Pointer yrx_compiler_build(Pointer compiler);

        // Rules
        void yrx_rules_destroy(Pointer rules);

        // Scanner
        int yrx_scanner_create(Pointer rules, PointerByReference scanner);

        void yrx_scanner_destroy(Pointer scanner);

        int yrx_scanner_set_timeout(Pointer scanner, long timeoutSeconds);

        int yrx_scanner_on_matching_rule(Pointer scanner, RuleCallback callback, Pointer userData);

        int yrx_scanner_scan(Pointer scanner, Pointer data, long len);

        // Rule introspection (callback context)
        int yrx_rule_identifier(Pointer rule, PointerByReference ident, LongByReference len);

        int yrx_rule_namespace(Pointer rule, PointerByReference ns, LongByReference len);

        int yrx_rule_iter_tags(Pointer rule, TagCallback callback, Pointer userData);

        int yrx_rule_iter_patterns(Pointer rule, PatternCallback callback, Pointer userData);

        // Pattern introspection (inside the pattern callback)
        int yrx_pattern_identifier(Pointer pattern, PointerByReference ident, LongByReference len);

        int yrx_pattern_iter_matches(Pointer pattern, MatchCallback callback, Pointer userData);

        // Buffer
        void yrx_buffer_destroy(Pointer buf);

        // Misc
        String yrx_last_error();
    }

    /** Layout of {@code YRX_BUFFER} ({@code uint8_t *data; size_t length;}). */
    public static class YRX_BUFFER extends Structure {
        public Pointer data;
        public long length;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("data", "length");
        }
    }

    /**
     * Layout of {@code YRX_MATCH} ({@code size_t offset; size_t length;}).
     * The {@code size_t} fields are modelled as {@code long} — valid on Win64
     * and Linux64 (both have an 8-byte {@code size_t}), the OSes IPED supports
     * today (see {@code root CLAUDE.md} §3).
     */
    public static class YRX_MATCH extends Structure {
        public long offset;
        public long length;

        public YRX_MATCH() {
        }

        public YRX_MATCH(Pointer p) {
            super(p);
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("offset", "length");
        }

        public static class ByReference extends YRX_MATCH implements Structure.ByReference {
            public ByReference() {
            }

            public ByReference(Pointer p) {
                super(p);
            }
        }
    }

    /** Native callback for rule matches: {@code void (*)(const YRX_RULE *rule, void *user_data)}. */
    public interface RuleCallback extends Callback {
        void invoke(Pointer rule, Pointer userData);
    }

    /** Native callback for tags: {@code void (*)(const char *tag, void *user_data)}. */
    public interface TagCallback extends Callback {
        void invoke(String tag, Pointer userData);
    }

    /** Native callback for patterns: {@code void (*)(const YRX_PATTERN *pattern, void *user_data)}. */
    public interface PatternCallback extends Callback {
        void invoke(Pointer pattern, Pointer userData);
    }

    /**
     * Native callback for matches inside a pattern:
     * {@code void (*)(const YRX_MATCH *match, void *user_data)}.
     *
     * <p>The pointer is only valid during callback execution (libyara-x-capi
     * frees the object on return) — read {@code offset}/{@code length} BEFORE
     * returning.</p>
     */
    public interface MatchCallback extends Callback {
        void invoke(YRX_MATCH.ByReference match, Pointer userData);
    }

    /** Simple sink for reporting individual compile errors (FR-002 + FR-005). */
    public interface CompileErrorSink {
        void report(File source, int lineNumber, String message);
    }
}
