package iped.engine.lucene.analysis;

import java.io.IOException;
import java.lang.Character.UnicodeScript;

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;

/**
 * Tokenizer of the {@link AnalyzerVersion#UNICODE} analysis.
 * <ul>
 * <li>Words are runs of letters, digits, combining marks and joiners, so
 * scripts that write vowels or diacritics as combining marks (Hebrew, Arabic,
 * Indic scripts, Thai, decomposed Latin...) are not broken inside words.</li>
 * <li>Scripts written without spaces between words (Chinese, Japanese, Korean,
 * Thai, Lao, Khmer, Myanmar...) produce one token per grapheme cluster. Queries
 * for them are turned into phrase queries, so any substring can be found
 * without word segmentation dictionaries.</li>
 * <li>Emoji sequences (with modifiers, ZWJ sequences and flags) are single
 * tokens.</li>
 * </ul>
 * Unicode normalization, lower casing and diacritic folding are done by the
 * token filters that follow.
 */
public final class UnicodeTokenizer extends Tokenizer {

    /** Same default as Lucene's CharTokenizer: longer words are split. */
    public static final int DEFAULT_MAX_TOKEN_LENGTH = 255;

    static final byte SEPARATOR = 0;
    /** Letters and digits of scripts written with spaces between words. */
    static final byte WORD = 1;
    /** Combining marks: continue the current word or cluster. */
    static final byte EXTEND = 2;
    /** Joiners and soft hyphen: continue the current token, never start one. */
    static final byte JOINER = 3;
    /** Letters of scripts written without spaces: one token per grapheme cluster. */
    static final byte CLUSTER = 4;
    static final byte EMOJI = 5;
    /** Skin tone modifiers and tag characters, continue an emoji. */
    static final byte EMOJI_MODIFIER = 6;
    /** Regional indicators, a pair of them is a flag. */
    static final byte REGIONAL_INDICATOR = 7;

    private static final int ZWJ = 0x200D;

    /** Minimum chars kept buffered ahead, enough for two code points. */
    private static final int LOOKAHEAD = 4;

    private static final byte[] BMP_CLASSES = new byte[Character.MAX_VALUE + 1];

    static {
        for (int c = 0; c <= Character.MAX_VALUE; c++) {
            BMP_CLASSES[c] = computeClass(c);
        }
    }

    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);

    private final int[] extraCodePoints;
    private final int maxTokenLength;

    private final char[] buffer = new char[4096];
    /** Offset in the input of buffer[0]. */
    private int bufferOffset;
    /** Next char of the buffer to be consumed. */
    private int pos;
    /** Number of valid chars in the buffer. */
    private int limit;
    private boolean eof;
    private int finalOffset;

    public UnicodeTokenizer() {
        this(null, DEFAULT_MAX_TOKEN_LENGTH);
    }

    /**
     * @param extraCodePoints
     *            code points to be indexed as word characters (they are
     *            separators by default), may be null
     * @param maxTokenLength
     *            longer tokens are split, must be greater than 1
     */
    public UnicodeTokenizer(int[] extraCodePoints, int maxTokenLength) {
        if (maxTokenLength < 2) {
            throw new IllegalArgumentException("maxTokenLength must be greater than 1: " + maxTokenLength);
        }
        this.extraCodePoints = extraCodePoints;
        this.maxTokenLength = maxTokenLength;
    }

    @Override
    public boolean incrementToken() throws IOException {
        clearAttributes();
        int cp;
        byte cls;
        while (true) {
            if (!fill()) {
                finalOffset = correctOffset(bufferOffset + pos);
                return false;
            }
            cp = codePointAt(pos);
            cls = classOf(cp);
            if (cls != SEPARATOR && cls != JOINER) {
                break;
            }
            pos += Character.charCount(cp);
        }

        int start = bufferOffset + pos;
        consume(cp);
        switch (cls) {
            case CLUSTER:
                readCluster(cp);
                break;
            case EMOJI:
            case EMOJI_MODIFIER:
                readEmoji();
                break;
            case REGIONAL_INDICATOR:
                readFlag();
                break;
            default:
                readWord();
                break;
        }
        offsetAtt.setOffset(correctOffset(start), correctOffset(bufferOffset + pos));
        return true;
    }

    private void readWord() throws IOException {
        while (true) {
            int cp = peek();
            if (cp < 0) {
                return;
            }
            byte cls = classOf(cp);
            if ((cls != WORD && cls != EXTEND && cls != JOINER) || !fits(Character.charCount(cp))) {
                return;
            }
            consume(cp);
        }
    }

    private void readCluster(int base) throws IOException {
        int prev = base;
        while (true) {
            int cp = peek();
            if (cp < 0) {
                return;
            }
            byte cls = classOf(cp);
            boolean take;
            if (cls == EXTEND || cls == JOINER) {
                take = true;
            } else if (cls == CLUSTER) {
                take = isStackingVirama(prev) || continuesHangulSyllable(prev, cp);
            } else {
                take = false;
            }
            if (!take || !fits(Character.charCount(cp))) {
                return;
            }
            consume(cp);
            prev = cp;
        }
    }

    private void readEmoji() throws IOException {
        while (true) {
            int cp = peek();
            if (cp < 0) {
                return;
            }
            byte cls = classOf(cp);
            if (cls == EMOJI_MODIFIER || cls == EXTEND) {
                // skin tones, tags, variation selectors, keycap
                if (!fits(Character.charCount(cp))) {
                    return;
                }
                consume(cp);
            } else if (cp == ZWJ) {
                int next = peekSecond();
                if (next < 0 || classOf(next) != EMOJI || !fits(1 + Character.charCount(next))) {
                    return;
                }
                consume(cp);
                consume(next);
            } else {
                return;
            }
        }
    }

    private void readFlag() throws IOException {
        int cp = peek();
        if (cp >= 0 && classOf(cp) == REGIONAL_INDICATOR) {
            consume(cp);
        }
    }

    private boolean fits(int chars) {
        return termAtt.length() + chars <= maxTokenLength;
    }

    private void consume(int cp) {
        int len = termAtt.length();
        char[] termBuffer = termAtt.resizeBuffer(len + 2);
        len += Character.toChars(cp, termBuffer, len);
        termAtt.setLength(len);
        pos += Character.charCount(cp);
    }

    private int peek() throws IOException {
        return fill() ? codePointAt(pos) : -1;
    }

    /** The code point after the next one, or -1. */
    private int peekSecond() throws IOException {
        if (!fill()) {
            return -1;
        }
        int i = pos + Character.charCount(codePointAt(pos));
        return i < limit ? codePointAt(i) : -1;
    }

    /**
     * Keeps at least {@link #LOOKAHEAD} chars buffered after pos (unless the input
     * ends before). Returns false if there is nothing left to read.
     */
    private boolean fill() throws IOException {
        if (limit - pos >= LOOKAHEAD || eof) {
            return pos < limit;
        }
        if (pos > 0) {
            System.arraycopy(buffer, pos, buffer, 0, limit - pos);
            bufferOffset += pos;
            limit -= pos;
            pos = 0;
        }
        while (limit < LOOKAHEAD && !eof) {
            int read = input.read(buffer, limit, buffer.length - limit);
            if (read < 0) {
                eof = true;
            } else {
                limit += read;
            }
        }
        return pos < limit;
    }

    private int codePointAt(int i) {
        char c = buffer[i];
        if (Character.isHighSurrogate(c) && i + 1 < limit && Character.isLowSurrogate(buffer[i + 1])) {
            return Character.toCodePoint(c, buffer[i + 1]);
        }
        return c;
    }

    private byte classOf(int cp) {
        byte cls = cp <= Character.MAX_VALUE ? BMP_CLASSES[cp] : computeClass(cp);
        if (extraCodePoints != null && (cls == SEPARATOR || cls == JOINER)) {
            for (int extra : extraCodePoints) {
                if (cp == extra) {
                    return WORD;
                }
            }
        }
        return cls;
    }

    @Override
    public void end() throws IOException {
        super.end();
        offsetAtt.setOffset(finalOffset, finalOffset);
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        bufferOffset = 0;
        pos = 0;
        limit = 0;
        eof = false;
        finalOffset = 0;
    }

    /**
     * Returns true if the text contains characters of scripts written without
     * spaces, which are indexed one grapheme cluster per token.
     */
    public static boolean containsClusterScript(CharSequence text) {
        for (int i = 0; i < text.length();) {
            int cp = Character.codePointAt(text, i);
            if (isClusterScriptChar(cp)) {
                return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }

    public static boolean isClusterScriptChar(int cp) {
        return (cp <= Character.MAX_VALUE ? BMP_CLASSES[cp] : computeClass(cp)) == CLUSTER;
    }

    static byte computeClass(int cp) {
        if (cp == ZWJ || cp == 0x200C || cp == 0x00AD || cp == 0x2060 || cp == 0xFEFF) {
            return JOINER;
        }
        if ((cp >= 0x1F3FB && cp <= 0x1F3FF) || (cp >= 0xE0020 && cp <= 0xE007F)) {
            return EMOJI_MODIFIER;
        }
        if (cp >= 0x1F1E6 && cp <= 0x1F1FF) {
            return REGIONAL_INDICATOR;
        }
        if (cp == 0xFF9E || cp == 0xFF9F) {
            // halfwidth katakana voiced sound marks: compose with the previous kana under NFKC
            return EXTEND;
        }
        if (cp == 0x30FC || cp == 0xFF70) {
            // katakana prolonged sound marks belong to the COMMON script
            return CLUSTER;
        }
        switch (Character.getType(cp)) {
            case Character.NON_SPACING_MARK:
            case Character.COMBINING_SPACING_MARK:
            case Character.ENCLOSING_MARK:
                return EXTEND;
            case Character.OTHER_SYMBOL:
                return isEmojiBlock(cp) ? EMOJI : SEPARATOR;
            default:
                break;
        }
        if (Character.isLetterOrDigit(cp)) {
            return isClusterScript(cp) ? CLUSTER : WORD;
        }
        return SEPARATOR;
    }

    private static boolean isEmojiBlock(int cp) {
        return (cp >= 0x1F000 && cp <= 0x1FAFF) || (cp >= 0x2600 && cp <= 0x27BF) || (cp >= 0x2B00 && cp <= 0x2BFF)
                || cp == 0x231A || cp == 0x231B || cp == 0x2328 || cp == 0x23CF || (cp >= 0x23E9 && cp <= 0x23F3)
                || (cp >= 0x23F8 && cp <= 0x23FA);
    }

    private static boolean isClusterScript(int cp) {
        switch (UnicodeScript.of(cp)) {
            case HAN:
            case HIRAGANA:
            case KATAKANA:
            case HANGUL:
            case BOPOMOFO:
            case YI:
            case THAI:
            case LAO:
            case KHMER:
            case MYANMAR:
            case TAI_LE:
            case NEW_TAI_LUE:
            case TAI_THAM:
            case TAI_VIET:
            case BALINESE:
            case JAVANESE:
                return true;
            default:
                return false;
        }
    }

    /** Viramas that stack the next consonant into the same cluster. */
    private static boolean isStackingVirama(int cp) {
        return cp == 0x17D2 || cp == 0x1039 || cp == 0x1A60;
    }

    private static boolean isHangulL(int cp) {
        return (cp >= 0x1100 && cp <= 0x115F) || (cp >= 0xA960 && cp <= 0xA97F);
    }

    private static boolean isHangulV(int cp) {
        return (cp >= 0x1160 && cp <= 0x11A7) || (cp >= 0xD7B0 && cp <= 0xD7C6);
    }

    private static boolean isHangulT(int cp) {
        return (cp >= 0x11A8 && cp <= 0x11FF) || (cp >= 0xD7CB && cp <= 0xD7FB);
    }

    private static boolean isHangulSyllable(int cp) {
        return cp >= 0xAC00 && cp <= 0xD7A3;
    }

    /**
     * Hangul syllable sequences of decomposed (conjoining jamo) text, UAX #29
     * rules GB6 to GB8.
     */
    private static boolean continuesHangulSyllable(int prev, int next) {
        if (isHangulL(prev)) {
            return isHangulL(next) || isHangulV(next) || isHangulSyllable(next);
        }
        boolean prevIsLV = isHangulSyllable(prev) && (prev - 0xAC00) % 28 == 0;
        if (isHangulV(prev) || prevIsLV) {
            return isHangulV(next) || isHangulT(next);
        }
        if (isHangulT(prev) || isHangulSyllable(prev)) {
            return isHangulT(next);
        }
        return false;
    }
}
