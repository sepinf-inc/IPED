package iped.engine.rag;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes technical noise from text already extracted by the parsers for RAG.
 *
 * <p>The same sanitized text is used for embeddings and {@code content_stored}.
 * Bracketed identifiers, meaningful whitespace and Unicode text are preserved.
 * The original Lucene {@code content} field is never changed.</p>
 */
public final class RAGTextSanitizer {

    // Keep investigative metadata; exclude only the existing technical prefixes.
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

    private static final Pattern AD_SCRIPT = Pattern.compile(
            "document\\.write\\(decodeURIComponent\\(\"[^\"]+\"\\)\\);?",
            Pattern.CASE_INSENSITIVE);

    private RAGTextSanitizer() {}

    /**
     * Cleans RAG text without removing literal angle-bracket content or inline
     * separators. Does not change fragmentation or embedding eligibility rules.
     */
    public static String sanitize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        if (hasAdvertisingScript(text)) {
            // Script matches may span lines. Normalize and remove RGB lines first,
            // preserving the existing order before matching scripts and metadata.
            String cleaned = normalizeForScripts(text);
            if (cleaned.contains("red=") && cleaned.contains("blue=")) {
                cleaned = stripRGBPalettes(cleaned);
            }
            Matcher matcher = hasAdvertisingScript(cleaned) ? AD_SCRIPT.matcher(cleaned) : null;
            boolean match = matcher != null && matcher.find();
            LineCleaner out = new LineCleaner(cleaned.length(), false, false);
            for (int i = 0; i < cleaned.length();) {
                if (match && i == matcher.start()) {
                    i = matcher.end();
                    match = matcher.find();
                    continue;
                }
                char c = cleaned.charAt(i++);
                if (c == '\n') {
                    out.finishLine(true);
                } else {
                    out.text.append(c);
                }
            }
            out.finishLine(false);
            return out.text.toString().trim();
        }

        LineCleaner out = new LineCleaner(text.length(), true,
                text.contains("red=") && text.contains("blue="));
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r') {
                if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                c = '\n';
            } else {
                c = normalizeCharacter(c);
            }
            if (c == '\n') {
                out.finishLine(true);
            } else {
                out.text.append(c);
            }
        }
        out.finishLine(false);
        return out.text.toString().trim();
    }

    private static boolean hasAdvertisingScript(String text) {
        return text.contains("decodeURIComponent(") && (text.contains("OpenX")
                || text.contains("comscore") || text.contains("quantserve") || text.contains("criteo"));
    }

    private static char normalizeCharacter(char c) {
        if (c == '\u00a0' || c == '\u202f') {
            return ' ';
        }
        if (c == '\f' || c == '\u000b') {
            return '\n';
        }
        if ((c < 32 && c != '\n' && c != '\r' && c != '\t') || c == 127) {
            return ' ';
        }
        return c;
    }

    private static String normalizeForScripts(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r') {
                if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                c = '\n';
            } else {
                c = normalizeCharacter(c);
            }
            if (c == '\n') {
                trimLineEnd(out, 0);
            }
            out.append(c);
        }
        trimLineEnd(out, 0);
        return out.toString();
    }

    private static void trimLineEnd(StringBuilder text, int start) {
        int end = text.length();
        while (end > start && isHorizontalSpace(text.charAt(end - 1))) {
            end--;
        }
        text.setLength(end);
    }

    private static boolean isHorizontalSpace(char c) {
        return c == ' ' || c == '\t';
    }

    private static boolean startsWith(StringBuilder text, int start, int end, String value) {
        if (end - start < value.length()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (text.charAt(start + i) != value.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static boolean contains(StringBuilder text, int start, int end, String value) {
        for (int i = start; i + value.length() <= end; i++) {
            if (startsWith(text, i, end, value)) {
                return true;
            }
        }
        return false;
    }

    /** Keeps line processing in one output buffer, without splitting the fragment. */
    private static class LineCleaner {
        private final StringBuilder text;
        private final boolean trimLineEnds;
        private final boolean removeRgb;
        private int lineStart;
        private int newlines;

        private LineCleaner(int capacity, boolean trimLineEnds, boolean removeRgb) {
            text = new StringBuilder(capacity);
            this.trimLineEnds = trimLineEnds;
            this.removeRgb = removeRgb;
        }

        private void finishLine(boolean newline) {
            if (trimLineEnds) {
                trimLineEnd(text, lineStart);
            }
            int end = text.length();
            int content = lineStart;
            while (content < end && Character.isWhitespace(text.charAt(content))) {
                content++;
            }
            for (String prefix : NOISE_PREFIXES) {
                if (startsWith(text, content, end, prefix)) {
                    text.setLength(lineStart);
                    return;
                }
            }
            if (removeRgb && contains(text, lineStart, end, "index=")
                    && contains(text, lineStart, end, "red=") && contains(text, lineStart, end, "blue=")) {
                text.setLength(lineStart);
                return;
            }

            int write = lineStart;
            int segment = lineStart;
            while (segment < end) {
                int stop = segment;
                while (stop < end && !isUnicodeLineSeparator(text.charAt(stop))) {
                    stop++;
                }
                int left = segment;
                int right = stop;
                while (left < right && isHorizontalSpace(text.charAt(left))) {
                    left++;
                }
                while (right > left && isHorizontalSpace(text.charAt(right - 1))) {
                    right--;
                }
                boolean decorative = right - left >= 5;
                for (int i = left; decorative && i < right; i++) {
                    if (text.charAt(i) != '_' && text.charAt(i) != '-') {
                        decorative = false;
                    }
                }
                if (!decorative) {
                    for (int i = segment; i < stop; i++) {
                        char c = text.charAt(i);
                        if (!isHorizontalSpace(c)) {
                            newlines = 0;
                        }
                        text.setCharAt(write++, c);
                    }
                }
                if (stop < end) {
                    char separator = text.charAt(stop);
                    text.setCharAt(write++, separator);
                    newlines = 0;
                }
                segment = stop + 1;
            }
            text.setLength(write);
            if (newline) {
                if (newlines < 2) {
                    text.append('\n');
                }
                newlines = Math.min(2, newlines + 1);
            }
            lineStart = text.length();
        }
    }

    private static boolean isUnicodeLineSeparator(char c) {
        // Preserve Java MULTILINE separator semantics without deleting delimiters.
        return c == '\u0085' || c == '\u2028' || c == '\u2029';
    }

    /**
     * Strips HTML tags for callers explicitly requesting that operation. RAG
     * sanitation does not use it on text already extracted by the parsers.
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

    private static String stripRGBPalettes(String text) {
        if (text == null) return "";
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder(text.length());
        for (String line : lines) {
            if (line.contains("index=") && line.contains("red=") && line.contains("blue=")) {
                continue;
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /** Determines whether text meets the existing RAG embedding eligibility gate. */
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
}
