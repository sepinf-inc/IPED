package iped.engine.task.yara;

import java.nio.charset.StandardCharsets;

/**
 * Helpers that convert the raw bytes captured by a {@link MatchedString} (stored
 * as lowercase hex) into the human-friendly form
 * that feeds two surfaces in the IPED UI:
 *
 * <ul>
 *   <li>The per-rule facet field {@code yara:match:<namespace>/<name>} written by
 *       {@code YaraScanTask} — one value per distinct matched string. Mirrors the
 *       {@code Regex:<category>} pattern from {@code RegexTask}.</li>
 *   <li>The text-viewer highlight set assembled by
 *       {@code MetadataPanel.getHighlightTerms()} — selected facet values become
 *       highlight terms automatically via the existing literal-value branch
 *       (same path used by {@code Regex:*} / {@code NamedEntityTask.NER_PREFIX}).</li>
 * </ul>
 */
public final class YaraHighlightSupport {

    private YaraHighlightSupport() {
    }

    /**
     * Convert a matched-string hex payload into the value that lands in the
     * {@code yara:match:<rule_id>} facet field.
     *
     * <p>If every byte is printable ASCII (or a common whitespace control —
     * tab/LF/CR), the bytes are decoded as UTF-8 text and trimmed; otherwise the
     * original lowercase hex is returned unchanged so binary patterns remain
     * round-trippable in the index. Empty/malformed inputs return an empty
     * string so the caller can decide whether to skip.</p>
     *
     * @param hex hex-encoded bytes from {@link MatchedString#getHex()};
     *            may be {@code null} or empty
     * @return printable text when decodable, otherwise the lowercase hex,
     *         or an empty string for {@code null}/empty/odd-length/malformed input
     */
    public static String decodeHexForFacet(String hex) {
        if (hex == null || hex.isEmpty() || (hex.length() & 1) != 0) {
            return "";
        }
        String printable = decodePrintable(hex);
        if (printable != null) {
            return printable;
        }
        return hex.toLowerCase();
    }

    /**
     * Strict printable-decode used by {@link #decodeHexForFacet}. Returns
     * {@code null} when any decoded byte falls outside printable ASCII
     * ({@code 0x20..0x7E} + {@code \t}/{@code \n}/{@code \r}) — which signals
     * to the caller that the input is binary and should fall back to hex.
     *
     * <p>Visible for {@link YaraHighlightSupportTest}.</p>
     */
    static String decodePrintable(String hex) {
        if (hex == null || hex.isEmpty() || (hex.length() & 1) != 0) {
            return null;
        }
        int n = hex.length() / 2;
        byte[] bytes = new byte[n];
        for (int i = 0; i < n; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                return null;
            }
            bytes[i] = (byte) ((hi << 4) | lo);
        }
        for (byte b : bytes) {
            int c = b & 0xFF;
            boolean printable = (c >= 0x20 && c <= 0x7E) || c == '\t' || c == '\n' || c == '\r';
            if (!printable) {
                return null;
            }
        }
        String text = new String(bytes, StandardCharsets.UTF_8).trim();
        return text.isEmpty() ? null : text;
    }
}
