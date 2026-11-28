package iped.engine.lucene.analysis;

import java.io.IOException;
import java.nio.CharBuffer;
import java.text.Normalizer;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 * Normalizes tokens of the {@link AnalyzerVersion#UNICODE} analysis:
 * <ul>
 * <li>removes default ignorable characters kept inside words by
 * {@link UnicodeTokenizer} (joiners, soft hyphen, variation selectors...), so
 * words match whether or not they were typed with them;</li>
 * <li>converts the token to Unicode normalization form NFC, so composed and
 * decomposed (e.g. macOS file names) texts match. When folding, NFKC is used
 * instead, which also maps compatibility characters (full width and half width
 * forms, Arabic presentation forms, ligatures...) except the ordinal indicators
 * º and ª, and emoji skin tone modifiers are removed.</li>
 * </ul>
 */
public final class UnicodeNormalizationFilter extends TokenFilter {

    private static final byte KEEP = 0;
    private static final byte REMOVE = 1;
    private static final byte CHECK = 2;

    private static final byte[] NFC_ACTIONS = buildActions(false);
    private static final byte[] NFKC_ACTIONS = buildActions(true);

    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final boolean folding;
    private final Normalizer.Form form;
    private final byte[] actions;

    /**
     * @param folding
     *            use NFKC and remove emoji skin tones instead of using NFC
     */
    public UnicodeNormalizationFilter(TokenStream input, boolean folding) {
        super(input);
        this.folding = folding;
        this.form = folding ? Normalizer.Form.NFKC : Normalizer.Form.NFC;
        this.actions = folding ? NFKC_ACTIONS : NFC_ACTIONS;
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (!input.incrementToken()) {
            return false;
        }
        char[] buf = termAtt.buffer();
        int len = termAtt.length();
        boolean remove = false, check = false;
        for (int i = 0; i < len; i++) {
            byte action = actions[buf[i]];
            if (action == REMOVE) {
                remove = true;
            } else if (action == CHECK) {
                check = true;
            }
        }
        if (!remove && !check) {
            return true;
        }

        int newLen = 0;
        for (int i = 0; i < len;) {
            int cp = Character.codePointAt(buf, i, len);
            int n = Character.charCount(cp);
            if (!isIgnorable(cp)) {
                if (newLen != i) {
                    System.arraycopy(buf, i, buf, newLen, n);
                }
                newLen += n;
            }
            i += n;
        }
        termAtt.setLength(newLen);

        if (check) {
            CharBuffer chars = CharBuffer.wrap(buf, 0, newLen);
            if (!Normalizer.isNormalized(chars, form)) {
                termAtt.setEmpty().append(normalize(chars));
            }
        }
        return true;
    }

    private String normalize(CharBuffer chars) {
        if (!folding) {
            return Normalizer.normalize(chars, form);
        }
        // normalize the text around ordinal indicators, which are kept
        StringBuilder sb = new StringBuilder(chars.length());
        int start = 0;
        for (int i = 0; i < chars.length(); i++) {
            if (isOrdinalIndicator(chars.charAt(i))) {
                sb.append(Normalizer.normalize(chars.subSequence(start, i), form)).append(chars.charAt(i));
                start = i + 1;
            }
        }
        return sb.append(Normalizer.normalize(chars.subSequence(start, chars.length()), form)).toString();
    }

    /**
     * NFKC turns "º" and "ª" into "o" and "a", so "nº" would match the very common
     * Portuguese and Spanish word "no", and "2ª" would be "2a". Keep them, as the
     * legacy analysis does.
     */
    private static boolean isOrdinalIndicator(int c) {
        return c == 0x00AA || c == 0x00BA;
    }

    private boolean isIgnorable(int cp) {
        if (cp <= Character.MAX_VALUE) {
            return actions[cp] == REMOVE;
        }
        // variation selectors supplement (ideographic variation sequences)
        if (cp >= 0xE0100 && cp <= 0xE01EF) {
            return true;
        }
        // emoji skin tone modifiers
        return folding && cp >= 0x1F3FB && cp <= 0x1F3FF;
    }

    private static boolean isBmpIgnorable(int c) {
        return c == 0x00AD // soft hyphen
                || c == 0x034F // combining grapheme joiner
                || (c >= 0x180B && c <= 0x180E) // mongolian variation selectors and vowel separator
                || (c >= 0x200B && c <= 0x200D) // zero width space, non-joiner, joiner
                || (c >= 0x2060 && c <= 0x2064) // word joiner and invisible operators
                || (c >= 0xFE00 && c <= 0xFE0F) // variation selectors
                || c == 0xFEFF; // zero width no-break space (BOM)
    }

    /**
     * A token can only change under normalization if it has a character that
     * changes alone, a combining mark (composition and reordering), a Hangul
     * conjoining jamo (composition) or a supplementary character. Tokens made only
     * of other characters (most text, CJK included) skip the normalizer.
     */
    private static byte[] buildActions(boolean nfkc) {
        Normalizer.Form form = nfkc ? Normalizer.Form.NFKC : Normalizer.Form.NFC;
        byte[] actions = new byte[Character.MAX_VALUE + 1];
        for (int c = 0; c <= Character.MAX_VALUE; c++) {
            if (isBmpIgnorable(c)) {
                actions[c] = REMOVE;
            } else if (nfkc && isOrdinalIndicator(c)) {
                actions[c] = KEEP;
            } else if (Character.isSurrogate((char) c) || isMark(c) || isConjoiningJamo(c)
                    || !Normalizer.isNormalized(String.valueOf((char) c), form)) {
                actions[c] = CHECK;
            } else {
                actions[c] = KEEP;
            }
        }
        return actions;
    }

    private static boolean isMark(int c) {
        int type = Character.getType(c);
        return type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }

    private static boolean isConjoiningJamo(int c) {
        return (c >= 0x1100 && c <= 0x11FF) || (c >= 0xA960 && c <= 0xA97F) || (c >= 0xD7B0 && c <= 0xD7FF);
    }
}
