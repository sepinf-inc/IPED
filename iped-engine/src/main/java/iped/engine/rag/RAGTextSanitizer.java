package iped.engine.rag;

import java.util.regex.Pattern;

/**
 * Lightweight sanitizer for RAG fragment text.
 *
 * <p>Strips technical metadata noise, HTML layout tags, and long separator
 * lines from RAG text fragments before they are (a) stored in the Lucene {@code
 * CONTENT_STORED} field and (b) sent to the embedding provider.</p>

 * <p><strong>The original Lucene {@code content} field used for BM25 keyword
 * search is never touched by this class.</strong> Only the RAG-specific stored
 * field and embedding pipeline are affected.</p>
 */
public final class RAGTextSanitizer {

    // -----------------------------------------------------------------------
    // Technical metadata line prefixes to discard entirely from RAG fragments
    // (Preserves all investigative metadata like SMS, Calls, Locations, OCR, Transcriptions)
    // -----------------------------------------------------------------------
    private static final String[] NOISE_PREFIXES = {
            // Tika & Parser metadata
            "image:",
            "X-TIKA:",
            "Indexer-Content-Type:",

            // PDF internal technical metadata / permissions / page counts
            "pdf:access_permission:",
            "pdf:has",
            "pdf:unmapped",
            "pdf:encrypted:",
            "pdf:charsPerPage:",

            // HTML & HTTP technical rendering metadata & redundant IDs
            "html:viewport",
            "html:format-detection",
            "html:Content-Encoding",
            "html:Content-Type-Hint:",
            "Content-Encoding:",
            "Content-Length:",
            "embeddedRelationshipId:",

            // UFED internal structural offsets, confidence flags & UUIDs
            "ufed:decoding_confidence:",
            "ufed:File size:",
            "ufed:MD5:",
            "ufed:fs:",
            "ufed:Chunks:",
            "ufed:Data offset:",
            "ufed:Inode Number:",
            "ufed:Owner GID:",
            "ufed:Owner UID:",
            "ufed:embedded:",
            "ufed:extractionId:",
            "ufed:extractionName:",
            "ufed:fsid:",
            "ufed:id:",
            "ufed:isrelated:",
            "ufed:jumpTargets:",

            // Technical audio codecs/sample rates
            "audio:xmpDM:audioChannelType",
            "audio:xmpDM:audioCompressor",
            "audio:xmpDM:audioSampleRate",
    };

    /**
     * Matches URL-encoded JavaScript ad scripts such as document.write(decodeURIComponent("..."))
     */
    private static final Pattern AD_SCRIPT = Pattern.compile(
            "document\\.write\\(decodeURIComponent\\(\"[^\"]+\"\\)\\);?",
            Pattern.CASE_INSENSITIVE);

    /**
     * Matches 5 or more consecutive underscores or hyphens used as visual separators
     * (e.g. ________________________________________).
     */
    private static final Pattern LONG_SEPARATORS = Pattern.compile("[_\\-]{5,}");

    /**
     * Pre-compiled fast gatekeeper pattern dynamically built from {@link #NOISE_PREFIXES}.
     */
    private static final Pattern NOISE_FAST_PATTERN;

    static {
        StringBuilder sb = new StringBuilder(NOISE_PREFIXES.length * 15);
        for (int i = 0; i < NOISE_PREFIXES.length; i++) {
            if (i > 0) sb.append('|');
            sb.append(Pattern.quote(NOISE_PREFIXES[i]));
        }
        NOISE_FAST_PATTERN = Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
    }

    /**
     * Matches 3 or more consecutive newlines (with optional whitespace on blank lines)
     * to collapse them into a maximum of 2 newlines (\n\n).
     */
    private static final Pattern MULTI_NEWLINES = Pattern.compile("(?:[ \\t]*\\r?\\n){3,}");

    // Utility class — no instances.
    private RAGTextSanitizer() {}

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Returns a sanitized copy of {@code text} suitable for RAG storage and
     * embedding. Returns an empty string when {@code text} is {@code null} or
     * contains only whitespace after sanitization.
     *
     * @param text the raw fragment text produced by the IPED/Tika parser
     * @return sanitized text, or {@code ""} when the sanitized result is blank
     */
    public static String sanitize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String cleaned = text;

        // 1. Remove inline RGB palette entries using ultra-fast non-recursive iterative loop
        if (cleaned.contains("red=") && cleaned.contains("blue=")) {
            cleaned = stripRGBPalettes(cleaned);
        }

        // 2. Remove URL-encoded ad scripts (document.write(decodeURIComponent("%3C...")))
        if (cleaned.contains("decodeURIComponent(") && (cleaned.contains("OpenX") || cleaned.contains("comscore") || cleaned.contains("quantserve") || cleaned.contains("criteo"))) {
            cleaned = AD_SCRIPT.matcher(cleaned).replaceAll("");
        }

        // 3. Strip HTML tags using fast stack-safe linear char loop (O(N) time, O(1) stack space)
        if (cleaned.contains("<") && cleaned.contains(">")) {
            cleaned = stripHtmlTags(cleaned);
        }

        // 4. Remove whole lines that are pure technical metadata noise
        if (NOISE_FAST_PATTERN.matcher(cleaned).find()) {
            String[] lines = cleaned.split("\n", -1);
            StringBuilder sb = new StringBuilder(cleaned.length());
            for (String line : lines) {
                if (!isNoiseMetadataLine(line)) {
                    sb.append(line).append('\n');
                }
            }
            cleaned = sb.toString();
        }

        // 5. Collapse long separator lines (_______ or -------)
        cleaned = LONG_SEPARATORS.matcher(cleaned).replaceAll("");

        // 6. Collapse 3+ consecutive blank lines into at most 2 newlines (\n\n) using fast stack-safe char loop
        cleaned = collapseNewlines(cleaned).trim();

        return cleaned;
    }

    /**
     * Fast stack-safe O(N) linear character loop to collapse 3+ consecutive newlines into at most 2 (\n\n).
     * Prevents JVM StackOverflowError on huge unformatted text files.
     */
    private static String collapseNewlines(String text) {
        if (text == null || text.length() < 3) {
            return text;
        }
        int len = text.length();
        StringBuilder sb = new StringBuilder(len);
        int newlineCount = 0;

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '\r') {
                continue; // Normalize CRLF to LF
            }
            if (c == '\n') {
                newlineCount++;
                if (newlineCount <= 2) {
                    sb.append('\n');
                }
            } else {
                if (c != ' ' && c != '\t') {
                    newlineCount = 0;
                }
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Fast stack-safe O(N) linear character loop to strip HTML tags from text.
     * Prevents JVM StackOverflowError on unclosed HTML tags or huge documents.
     */
    public static String stripHtmlTags(String html) {
        if (html == null || !html.contains("<")) {
            return html;
        }
        int len = html.length();
        StringBuilder sb = new StringBuilder(len);
        boolean inTag = false;
        for (int i = 0; i < len; i++) {
            char c = html.charAt(i);
            if (c == '<') {
                inTag = true;
                sb.append(' ');
            } else if (c == '>') {
                inTag = false;
            } else if (!inTag) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Fast stack-safe O(N) iterative cleaner for RGB palette dumps.
     */
    private static String stripRGBPalettes(String text) {
        if (text == null) return "";
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder(text.length());
        for (String line : lines) {
            if (line.contains("index=") && line.contains("red=") && line.contains("blue=")) {
                continue; // Skip lines containing inline RGB palette dumps
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /**
     * Determines whether a sanitized text fragment contains meaningful human text or
     * investigative metadata (SMS, Calls, Locations, OCR, Transcriptions).
     *
     * @param text the sanitized fragment text
     * @return {@code true} if the fragment is useful for RAG embedding
     */
    public static boolean hasUsefulContent(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        int len = text.length();
        if (len < 10) {
            return false;
        }

        // Evaluate alphanumeric density directly on the sanitized text
        int alphaNumericCount = 0;
        for (int i = 0; i < len; i++) {
            if (Character.isLetterOrDigit(text.charAt(i))) {
                alphaNumericCount++;
            }
        }

        return ((double) alphaNumericCount / len) >= 0.25;
    }

    /**
     * Returns {@code true} when the line is pure technical metadata noise.
     */
    private static boolean isNoiseMetadataLine(String line) {
        String trimmed = line.stripLeading();
        for (String prefix : NOISE_PREFIXES) {
            if (trimmed.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
