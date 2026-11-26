package iped.parsers.standard;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.xml.sax.SAXException;

/**
 * Finds strings of text in data of unknown format, like unallocated space,
 * unknown or corrupted files.
 * <p>
 * Each buffer of data is read by several decoders:
 * <ul>
 * <li>a byte decoder for ASCII, UTF-8 (any script) and single byte legacy text
 * (windows-1252 letters, as before), which also skips the zeros of UTF-16 text
 * in the Latin-1 range;</li>
 * <li>UTF-16LE and UTF-16BE decoders, at even and odd offsets, for UTF-16 text
 * with characters out of the Latin-1 range (Cyrillic, Arabic, CJK...).</li>
 * </ul>
 * The byte decoder breaks strings where the script changes inside a word (one
 * Cyrillic letter glued to Han characters) and before combining marks without a
 * base letter. Valid multibyte UTF-8 is rare by chance, but random bytes read as
 * UTF-16 are mostly letters (CJK, Hangul, Yi...): each code unit is scored by
 * how much more likely it is in text than in other data (the letters of a
 * second script, uncommon Han or Hangul and symbols score negatively) and only
 * segments with enough evidence are kept. Where the strings of different
 * decoders overlap, the one with more letters and digits wins and the others are
 * trimmed: e.g. UTF-8 Cyrillic text also looks like Hangul in UTF-16, and UTF-16
 * text has short runs of ASCII. Letters decoded from multibyte UTF-8 count
 * twice.
 * <p>
 * Strings with combining marks are normalized to NFC, so decomposed text (NFD)
 * is searchable like precomposed text.
 */
final class RawStringExtractor {

    interface Sink {
        void write(char[] chars, int off, int len) throws SAXException;
    }

    // ---------------------------- character data ---------------------------

    static final int INVALID = 0, SPACE = 1, NEWLINE = 2, PUNCT = 3, DIGIT = 4, LETTER = 5, MARK = 6;

    // INFO bits: class, script, CJK script bit and flags of each BMP code point
    private static final int CLASS_MASK = 7;
    private static final int SCRIPT_SHIFT = 3;
    private static final int CJK_SHIFT = 11;
    /** Han or Hangul syllable */
    private static final int HAN_HANGUL = 1 << 16;
    /** Han or Hangul in the main set of a national standard */
    private static final int COMMON = 1 << 17;
    /** U+xx00 above Latin-1 */
    private static final int ZERO_LOW = 1 << 18;
    private static final int ABOVE_LATIN1 = 1 << 19;
    /** Letter or mark of a script other than Latin, Common or Inherited */
    private static final int SPECIFIC = 1 << 20;

    private static final int[] INFO = new int[0x10000];

    /**
     * Weight and flags of each code unit, for the UTF-16 decoders. The weight (low
     * byte) is how much more likely the unit is in UTF-16 text than in other data,
     * roughly log2(P(text) / P(random)), in bits.
     */
    private static final short[] U16 = new short[0x10000];
    private static final int U_INVALID = 0x100, U_SPECIFIC = 0x200, U_MARK = 0x400, U_WORD = 0x800, U_LATIN = 0x1000;
    /** Code units that can start a UTF-16 string: positive weight, not a combining mark */
    private static final boolean[] STARTS16 = new boolean[0x10000];

    /** Evidence a UTF-16 string needs: about one chance in a billion for random data. */
    private static final int MIN_SCORE16 = 30;
    /**
     * Evidence a UTF-16 string of Latin script needs: the byte decoder reads its
     * Latin-1 letters anyway, it only adds the others (accents, ligatures...).
     */
    private static final int MIN_SCORE16_LATIN = 20;
    /** A UTF-16 string ends where its score falls this much below its best. */
    private static final int MAX_DROP16 = 12;
    /** ASCII letter after a Latin letter, in UTF-16 */
    private static final int LATIN_WORD16 = 2;
    /**
     * A UTF-16 string must have this many letters more than the strings of the
     * byte decoder it overlaps: a length or other data glued to a Latin-1 string
     * must not win.
     */
    private static final int UTF16_HANDICAP = 2;
    /** A UTF-16 string ended by U+0000, or starting after it */
    private static final int END_BONUS16 = 6, START_BONUS16 = 3;
    /** Letters of a second non Latin script */
    private static final int SCRIPT_SWITCH16 = 12;
    /** Combining mark without a base letter */
    private static final int NO_BASE16 = 8;

    private static final int SC_COMMON = Character.UnicodeScript.COMMON.ordinal();
    private static final int SC_INHERITED = Character.UnicodeScript.INHERITED.ordinal();
    private static final int SC_LATIN = Character.UnicodeScript.LATIN.ordinal();

    // scripts written together in the same word
    private static final int CJK_HAN = 1, CJK_HIRAGANA = 2, CJK_KATAKANA = 4, CJK_HANGUL = 8, CJK_BOPOMOFO = 16;
    private static final int CJK_JAPANESE = CJK_HAN | CJK_HIRAGANA | CJK_KATAKANA;
    private static final int CJK_KOREAN = CJK_HAN | CJK_HANGUL;
    private static final int CJK_CHINESE = CJK_HAN | CJK_BOPOMOFO;
    private static final int[] CJK_BITS = new int[256];

    /** Single byte characters accepted by the byte decoder (windows-1252 letters). */
    private static final boolean[] IS_CHAR = new boolean[256];
    private static final char[] BYTE_TO_CHAR = new String(byteValues(), Charset.forName("windows-1252")).toCharArray();

    /** Single byte characters of the byte decoder: 1 = letter, 2 = digit, 3 = other, 0 = none. */
    private static final byte[] BYTE_TYPE = new byte[256];

    static {
        for (Character.UnicodeScript script : Character.UnicodeScript.values()) {
            CJK_BITS[script.ordinal()] = cjkBit(script);
        }
        for (int c = 0; c < 0x10000; c++) {
            INFO[c] = computeInfo(c);
        }
        for (int c = 0; c < 256; c++) {
            IS_CHAR[c] = (c >= 0x20 && c <= 0x7E) || (c >= 0xC0 && c <= 0xFC) || c == 0x0A || c == 0x0D || c == 0x09;
        }
        for (int c = 0; c < 256; c++) {
            if (IS_CHAR[c]) {
                char ch = BYTE_TO_CHAR[c];
                BYTE_TYPE[c] = (byte) (Character.isLetter(ch) ? 1 : Character.isDigit(ch) ? 2 : 3);
            }
        }

        // Han and Hangul in the main sets of the national standards: nearly all the
        // characters of real text, but a small part of all code points
        markCommon("GB2312", 0xB0, 0xD7); // GB 2312 level 1 hanzi
        markCommon("Big5", 0xA4, 0xC6); // Big5 frequently used hanzi
        markCommon("Shift_JIS", 0x88, 0x98); // JIS X 0208 level 1 kanji
        markCommon("EUC-KR", 0xB0, 0xC8); // KS X 1001 hangul syllables

        for (int c = 0; c < 0x10000; c++) {
            int weight = weight16(c, INFO[c]);
            int info = INFO[c], cls = info & CLASS_MASK;
            int u = weight & 0xFF;
            if (cls == INVALID) {
                u |= U_INVALID;
            }
            if ((info & SPECIFIC) != 0) {
                u |= U_SPECIFIC;
            }
            if (cls == MARK) {
                u |= U_MARK;
            }
            if (cls >= LETTER) {
                u |= U_WORD;
                int script = (info >>> SCRIPT_SHIFT) & 0xFF;
                if (script == SC_LATIN || (cls == MARK && script == SC_INHERITED)) {
                    u |= U_LATIN;
                }
            }
            U16[c] = (short) u;
            STARTS16[c] = cls != INVALID && cls != MARK && weight > 0;
        }
    }

    private static byte[] byteValues() {
        byte[] bytes = new byte[256];
        for (int i = 0; i < 256; i++) {
            bytes[i] = (byte) i;
        }
        return bytes;
    }

    private static int cjkBit(Character.UnicodeScript script) {
        switch (script) {
            case HAN:
                return CJK_HAN;
            case HIRAGANA:
                return CJK_HIRAGANA;
            case KATAKANA:
                return CJK_KATAKANA;
            case HANGUL:
                return CJK_HANGUL;
            case BOPOMOFO:
                return CJK_BOPOMOFO;
            default:
                return 0;
        }
    }

    private static int computeInfo(int cp) {
        int cls = computeClass(cp);
        Character.UnicodeScript us = Character.UnicodeScript.of(cp);
        int script = us.ordinal();
        int info = cls | (script << SCRIPT_SHIFT) | (cjkBit(us) << CJK_SHIFT);
        if (cp >= 0x100) {
            info |= ABOVE_LATIN1;
            if ((cp & 0xFF) == 0) {
                info |= ZERO_LOW;
            }
        }
        if (cls == LETTER && ((cp >= 0x4E00 && cp <= 0x9FFF) || (cp >= 0xAC00 && cp <= 0xD7A3)
                || (cp >= 0x3400 && cp <= 0x4DBF) || (cp >= 0xF900 && cp <= 0xFAFF) || cp >= 0x20000)) {
            info |= HAN_HANGUL;
        }
        if (cls >= LETTER && script != SC_COMMON && script != SC_INHERITED && script != SC_LATIN) {
            info |= SPECIFIC;
        }
        return info;
    }

    private static int infoOf(int cp) {
        return cp < 0x10000 ? INFO[cp] : computeInfo(cp);
    }

    private static int computeClass(int c) {
        if (c == '\t') {
            return SPACE;
        }
        if (c == '\n' || c == '\r') {
            return NEWLINE;
        }
        if (c == 0x200C || c == 0x200D) {
            // zero width (non) joiners, inside Persian and Indic words
            return MARK;
        }
        if ((c & 0xFFFE) == 0xFFFE || (c >= 0xFDD0 && c <= 0xFDEF)) {
            return INVALID;
        }
        switch (Character.getType(c)) {
            case Character.UPPERCASE_LETTER:
            case Character.LOWERCASE_LETTER:
            case Character.TITLECASE_LETTER:
            case Character.MODIFIER_LETTER:
            case Character.OTHER_LETTER:
                return LETTER;
            case Character.NON_SPACING_MARK:
            case Character.ENCLOSING_MARK:
            case Character.COMBINING_SPACING_MARK:
                return MARK;
            case Character.DECIMAL_DIGIT_NUMBER:
            case Character.LETTER_NUMBER:
            case Character.OTHER_NUMBER:
                return DIGIT;
            case Character.SPACE_SEPARATOR:
                return SPACE;
            case Character.LINE_SEPARATOR:
            case Character.PARAGRAPH_SEPARATOR:
                return NEWLINE;
            case Character.CONNECTOR_PUNCTUATION:
            case Character.DASH_PUNCTUATION:
            case Character.START_PUNCTUATION:
            case Character.END_PUNCTUATION:
            case Character.INITIAL_QUOTE_PUNCTUATION:
            case Character.FINAL_QUOTE_PUNCTUATION:
            case Character.OTHER_PUNCTUATION:
            case Character.MATH_SYMBOL:
            case Character.CURRENCY_SYMBOL:
            case Character.MODIFIER_SYMBOL:
            case Character.OTHER_SYMBOL:
                return PUNCT;
            default:
                // controls, format characters, private use, surrogates, unassigned
                return INVALID;
        }
    }

    private static void markCommon(String charset, int leadMin, int leadMax) {
        Charset cs;
        try {
            cs = Charset.forName(charset);
        } catch (Exception e) {
            return;
        }
        CharsetEncoder encoder = cs.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        CharBuffer in = CharBuffer.allocate(1);
        ByteBuffer out = ByteBuffer.allocate(8);
        for (int c = 0x3400; c < 0xFB00; c++) {
            if ((INFO[c] & HAN_HANGUL) == 0) {
                continue;
            }
            in.clear();
            in.put((char) c);
            in.flip();
            out.clear();
            encoder.reset();
            if (encoder.encode(in, out, true).isError()) {
                continue;
            }
            encoder.flush(out);
            out.flip();
            if (out.remaining() == 2) {
                int lead = out.get(0) & 0xFF;
                if (lead >= leadMin && lead <= leadMax) {
                    INFO[c] |= COMMON;
                }
            }
        }
    }

    private static int weight16(int cp, int info) {
        int cls = info & CLASS_MASK;
        if (cls == INVALID) {
            return 0;
        }
        // small numbers (U+00xx) are frequent in binary data
        if (cp < 0x80) {
            if (cp == ' ') {
                return 6;
            }
            if (cls == LETTER || cls == DIGIT || cls == NEWLINE || cp == '\t'
                    || ".,:;!?'\"()-/".indexOf(cp) >= 0) {
                return 2;
            }
            return 0;
        }
        if (cp < 0x100) {
            // U+00FF is 0xFF padding
            return (cls == LETTER && cp != 0xFF) || cp == 0xA0 ? 1 : 0;
        }
        if (cp >= 0xFF00) {
            // full and half width forms: 0xFF is frequent in binary data
            return cp >= 0xFF01 && cp <= 0xFF0F || cp == 0xFF1A || cp == 0xFF1B || cp == 0xFF1F ? 2 : 0;
        }
        Character.UnicodeScript script = Character.UnicodeScript.of(cp);
        if (cls == LETTER || cls == MARK) {
            boolean mark = cls == MARK;
            if ((info & ZERO_LOW) != 0 && !mark && (info & COMMON) == 0) {
                // Latin-1 UTF-16 read at the wrong offset (but U+4E00 is the common "one")
                return -6;
            }
            switch (script) {
                case LATIN:
                    if ((cp >= 0x250 && cp < 0x300) || (cp >= 0x2C60 && cp < 0x2C80) || (cp >= 0xA720 && cp < 0xA800)
                            || (cp >= 0xAB30 && cp < 0xAB70)) {
                        // IPA, modifier letters, Latin extended C, D and E are rare
                        return 0;
                    }
                    return 5;
                case INHERITED:
                    return 4;
                case GREEK:
                    return cp >= 0x1F00 && cp < 0x2000 ? 5 : 8;
                case CYRILLIC:
                    return cp < 0x530 ? 8 : 2;
                case ARMENIAN:
                case GEORGIAN:
                    return 7;
                case HEBREW:
                    return mark ? 3 : 9;
                case ARABIC:
                    return cp >= 0xFB50 ? 2 : mark ? 4 : 8;
                case SYRIAC:
                case THAANA:
                    return 5;
                case DEVANAGARI:
                case BENGALI:
                case GURMUKHI:
                case GUJARATI:
                case ORIYA:
                case TAMIL:
                case TELUGU:
                case KANNADA:
                case MALAYALAM:
                case SINHALA:
                case THAI:
                case LAO:
                    return mark ? 7 : 8;
                case TIBETAN:
                case MYANMAR:
                case KHMER:
                case ETHIOPIC:
                case MONGOLIAN:
                    return mark ? 5 : 6;
                case HIRAGANA:
                case KATAKANA:
                    return cp >= 0xFF66 && cp <= 0xFF9F ? 3 : 7;
                case HANGUL:
                    if (cp >= 0xAC00 && cp <= 0xD7A3) {
                        return (info & COMMON) != 0 ? 4 : -4;
                    }
                    return 2;
                case HAN:
                    return (info & COMMON) != 0 ? 3 : -4;
                case BOPOMOFO:
                    return 3;
                case COMMON:
                    return 0;
                default:
                    // scripts rarely found in digital evidence
                    return -3;
            }
        }
        if (cls == DIGIT) {
            if ((cp >= 0x660 && cp <= 0x669) || (cp >= 0x6F0 && cp <= 0x6F9)) {
                return 5;
            }
            return Character.getType(cp) == Character.DECIMAL_DIGIT_NUMBER ? 3 : -2;
        }
        if (cls == SPACE) {
            return cp == 0x3000 ? 7 : 0;
        }
        if (cls == NEWLINE) {
            return 0;
        }
        // punctuation and symbols
        if (cp >= 0x2010 && cp <= 0x2027) {
            return 5; // dashes, quotes, ellipsis
        }
        if (cp >= 0x3001 && cp <= 0x303F) {
            return 7; // CJK punctuation
        }
        if (cp >= 0xFF01 && cp <= 0xFF65) {
            return 4; // full width forms
        }
        if (cp == 0x060C || cp == 0x061B || cp == 0x061F || cp == 0x06D4 || cp == 0x0964 || cp == 0x0965) {
            return 6; // Arabic punctuation, danda
        }
        if (cp == 0x05BE || cp == 0x05F3 || cp == 0x05F4 || cp == 0x20AC || cp == 0x2122) {
            return 3;
        }
        return -3;
    }

    private static boolean isValidCjkMix(int mask) {
        return (mask & ~CJK_JAPANESE) == 0 || (mask & ~CJK_KOREAN) == 0 || (mask & ~CJK_CHINESE) == 0;
    }

    private static boolean isSeparator(char c) {
        int cls = INFO[c] & CLASS_MASK;
        return cls == SPACE || cls == NEWLINE;
    }

    // ---------------------------- state -------------------------------------

    private static final int MAX_RUN = 4096;

    /**
     * Strings read by the UTF-16 decoders are decoded again from these last bytes
     * when they are accepted. Must hold MAX_RUN units.
     */
    private static final int HISTORY = 16 * 1024;

    /**
     * Where the byte decoder reads a long string of ASCII or UTF-8 text, the
     * UTF-16 decoders only find garbage, which would lose to it anyway.
     */
    private static final int SUSPEND_UTF16_AFTER = 32;

    /** A string being read by the byte decoder. */
    private static final class ByteRun {
        final char[] chars = new char[MAX_RUN + 4];
        /** End offset of each char, relative to start */
        final int[] ends = new int[MAX_RUN + 4];
        long start;
        int len;
        int letters, digits, multibyte, zeroSkips;
        boolean nonLatin1, hasMarks;
        // current word piece
        boolean inPiece;
        int pieceScript = -1, pieceCjk;

        void reset() {
            len = letters = digits = multibyte = zeroSkips = 0;
            nonLatin1 = hasMarks = inPiece = false;
            pieceScript = -1;
            pieceCjk = 0;
        }
    }

    /**
     * A UTF-16 decoder. Its code units are only scored: the segments with enough
     * evidence of being text are decoded again from the bytes.
     */
    private static final class Utf16Lane {
        final int lane;
        final boolean bigEndian;
        final int parity;
        boolean prevZero, prevLetter;
        int lastScript = -1;
        // segment with positive score, and where its score was the highest
        int seg, segBest;
        long segStart, segBestEnd;
        /** Code units above Latin-1 in the segment: Latin-1 UTF-16 is read by the byte decoder */
        int segAbove;
        /** Letters of scripts other than Latin in the segment */
        int segForeign;
        /** segAbove and segForeign where the score was the highest */
        int bestAbove, bestForeign;
        boolean prevLatin;

        Utf16Lane(int lane, boolean bigEndian, int parity) {
            this.lane = lane;
            this.bigEndian = bigEndian;
            this.parity = parity;
        }
    }

    /** A string waiting for the overlap resolution. */
    private static final class Candidate {
        final int lane;
        long start, end;
        char[] chars;
        int[] ends; // relative to start + base
        int base;
        int len;
        int score;
        /** Evidence of UTF-16 strings, to choose between UTF-16 decoders */
        int score16;
        boolean continued;
        boolean hasMarks;
        /** Already written, overlapping strings are trimmed by it */
        boolean emitted;

        Candidate(int lane) {
            this.lane = lane;
        }

        long charStart(int i) {
            return i == 0 ? start : start + ends[i - 1] - base;
        }

        long charEnd(int i) {
            return start + ends[i] - base;
        }
    }

    private final int minSize;
    private final boolean utf16;
    private final Sink sink;

    private final ByteRun bytesRun = new ByteRun();
    // lanes: 1 = LE at even offsets, 2 = LE odd, 3 = BE even, 4 = BE odd
    private final Utf16Lane[] u16Lanes;

    // UTF-8 sequence being decoded by the byte decoder
    private final byte[] u8Bytes = new byte[4];
    private int u8Count, u8Need, u8Cp, u8Lo = 0x80, u8Hi = 0xBF;
    private long u8Start;

    /** Position of the current byte */
    private long pos;
    /** Start of the current buffer */
    private long bufStart;
    private byte[] cur;

    // ranges where the UTF-16 decoders are suspended, in the current buffer
    private long suspendStart = -1;
    private long[] suspended = new long[64];
    private int suspendedCount;

    private final byte[] hist = new byte[HISTORY];
    private int histLen;

    private final ArrayList<Candidate> pending = new ArrayList<>();

    /** Last strings written, to trim strings found later that overlap them. */
    private final Candidate[] emitted = new Candidate[64];
    private int emittedCount, emittedNext;
    private long emittedEnd = -1;

    private final char[] out = new char[32 * 1024];
    private int outLen;

    RawStringExtractor(int minSize, boolean utf16, Sink sink) {
        this.minSize = Math.max(1, minSize);
        this.utf16 = utf16;
        this.sink = sink;
        this.u16Lanes = utf16 ? new Utf16Lane[] { new Utf16Lane(1, false, 0), new Utf16Lane(2, false, 1),
                new Utf16Lane(3, true, 0), new Utf16Lane(4, true, 1) } : new Utf16Lane[0];
    }

    // ---------------------------- input -------------------------------------

    /** Reads the next n bytes of the data. */
    void process(byte[] in, int n) throws SAXException {
        if (n <= 0) {
            return;
        }
        cur = in;
        bufStart = pos;
        suspendedCount = 0;
        if (suspendStart >= 0) {
            // suspended since the previous buffer
            suspendStart = bufStart;
        }
        bytePass(in, n);
        if (utf16) {
            if (suspendStart >= 0) {
                addSuspended(suspendStart, bufStart + n);
            }
            for (Utf16Lane r : u16Lanes) {
                utf16Pass(r, in, n);
            }
        }
        drain(false);
        keepHistory(in, n);
    }

    private void keepHistory(byte[] in, int n) {
        if (n >= HISTORY) {
            System.arraycopy(in, n - HISTORY, hist, 0, HISTORY);
            histLen = HISTORY;
        } else {
            int keep = Math.min(histLen, HISTORY - n);
            System.arraycopy(hist, histLen - keep, hist, 0, keep);
            System.arraycopy(in, 0, hist, keep, n);
            histLen = keep + n;
        }
        cur = null;
        bufStart = pos;
    }

    /** A byte of the current buffer or of the history before it. */
    private int byteAt(long p) {
        if (cur != null && p >= bufStart) {
            return cur[(int) (p - bufStart)] & 0xFF;
        }
        return hist[(int) (p - (bufStart - histLen))] & 0xFF;
    }

    /**
     * Data not read, e.g. random data skipped by the caller: strings do not
     * continue across it.
     */
    void skip(long n) throws SAXException {
        endAll();
        drain(false);
        pos += n;
        bufStart = pos;
        histLen = 0;
    }

    /** Ends all strings and writes the pending ones. */
    void finish() throws SAXException {
        endAll();
        drain(true);
        flushOutput();
    }

    private void endAll() throws SAXException {
        if (u8Count > 0) {
            flushInvalidUtf8();
        }
        endBytes(false);
        suspendStart = -1;
        for (Utf16Lane r : u16Lanes) {
            endUtf16(r, false);
        }
    }

    // ---------------------------- byte decoder ------------------------------

    private void bytePass(byte[] in, int n) throws SAXException {
        for (int i = 0; i < n;) {
            if (u8Need == 0) {
                int next = singleBytes(in, i, n);
                if (next > i) {
                    i = next;
                    continue;
                }
            }
            int b = in[i] & 0xFF;
            if (u8Need == 0 && b >= 0xC2 && b <= 0xF4) {
                // UTF-8 sequence inside the buffer: checked in place
                int need = b < 0xE0 ? 1 : b < 0xF0 ? 2 : 3;
                if (i + need < n) {
                    int b1 = in[i + 1] & 0xFF;
                    int lo = b == 0xE0 ? 0xA0 : b == 0xF0 ? 0x90 : 0x80;
                    int hi = b == 0xED ? 0x9F : b == 0xF4 ? 0x8F : 0xBF;
                    boolean valid = b1 >= lo && b1 <= hi;
                    int cp = 0;
                    if (valid) {
                        if (need == 1) {
                            cp = ((b & 0x1F) << 6) | (b1 & 0x3F);
                        } else {
                            int b2 = in[i + 2] & 0xFF;
                            if ((b2 & 0xC0) != 0x80) {
                                valid = false;
                            } else if (need == 2) {
                                cp = ((b & 0x0F) << 12) | ((b1 & 0x3F) << 6) | (b2 & 0x3F);
                            } else {
                                int b3 = in[i + 3] & 0xFF;
                                if ((b3 & 0xC0) != 0x80) {
                                    valid = false;
                                } else {
                                    cp = ((b & 0x07) << 18) | ((b1 & 0x3F) << 12) | ((b2 & 0x3F) << 6) | (b3 & 0x3F);
                                }
                            }
                        }
                    }
                    if (valid) {
                        int info = infoOf(cp);
                        if ((info & CLASS_MASK) != INVALID) {
                            appendBytes(cp, info, pos, pos + need + 1, true);
                        } else {
                            endBytes(false);
                        }
                        i += need + 1;
                        pos += need + 1;
                    } else {
                        // not UTF-8: a single byte character
                        singleByte(b, pos);
                        i++;
                        pos++;
                    }
                    checkSuspend();
                    continue;
                }
            }
            stepBytes(in, i, n, b);
            i++;
            pos++;
            checkSuspend();
        }
    }

    /**
     * Whether the bytes from i on are a valid UTF-8 sequence, or may be one when
     * the buffer ends first.
     */
    private static boolean maybeUtf8(byte[] in, int i, int n) {
        int b = in[i] & 0xFF;
        int need = b < 0xE0 ? 1 : b < 0xF0 ? 2 : 3;
        if (i + need >= n) {
            return true;
        }
        int b1 = in[i + 1] & 0xFF;
        int lo = b == 0xE0 ? 0xA0 : b == 0xF0 ? 0x90 : 0x80;
        int hi = b == 0xED ? 0x9F : b == 0xF4 ? 0x8F : 0xBF;
        if (b1 < lo || b1 > hi) {
            return false;
        }
        return (need < 2 || (in[i + 2] & 0xC0) == 0x80) && (need < 3 || (in[i + 3] & 0xC0) == 0x80);
    }

    /**
     * Fast path for single byte characters: ASCII and windows-1252 letters are
     * appended to the string, the zeros of UTF-16 Latin-1 text are skipped and
     * other bytes end the string. Stops at the first multibyte UTF-8 sequence.
     * Returns where it stopped.
     */
    private int singleBytes(byte[] in, int i, int n) throws SAXException {
        ByteRun r = bytesRun;
        if (r.inPiece && (r.pieceCjk != 0 || (r.pieceScript >= 0 && r.pieceScript != SC_LATIN))) {
            // the current word is in another script
            return i;
        }
        final long base = pos - i;
        final char[] chars = r.chars;
        final int[] ends = r.ends;
        int len = r.len, letters = r.letters, digits = r.digits, zeroSkips = r.zeroSkips;
        long start = r.start;
        int lastType = r.inPiece ? 1 : 0;
        while (i < n) {
            int b = in[i] & 0xFF;
            if (b >= 0xC2 && b <= 0xF4 && maybeUtf8(in, i, n)) {
                // multibyte UTF-8: general path
                break;
            }
            int type = BYTE_TYPE[b];
            if (type != 0) {
                if (len == 0) {
                    start = base + i;
                }
                chars[len] = BYTE_TO_CHAR[b];
                ends[len++] = (int) (base + i + 1 - start);
                if (type == 1) {
                    letters++;
                } else if (type == 2) {
                    digits++;
                }
                lastType = type;
                i++;
                if (len == SUSPEND_UTF16_AFTER && zeroSkips == 0 && suspendStart < 0 && utf16) {
                    suspendStart = base + i;
                }
                if (len >= MAX_RUN) {
                    r.len = len;
                    r.start = start;
                    r.letters = letters;
                    r.digits = digits;
                    r.zeroSkips = zeroSkips;
                    r.inPiece = lastType == 1;
                    r.pieceScript = lastType == 1 ? SC_LATIN : -1;
                    r.pieceCjk = 0;
                    pos = base + i;
                    endBytes(true);
                    len = r.len;
                    start = r.start;
                    letters = r.letters;
                    digits = r.digits;
                    zeroSkips = r.zeroSkips;
                }
                continue;
            }
            if (b == 0 && !(i >= 2 && IS_CHAR[in[i - 2] & 0xFF]) && !(i + 2 < n && IS_CHAR[in[i + 2] & 0xFF])) {
                // a zero between UTF-16 Latin-1 characters
                if (len > 0) {
                    zeroSkips++;
                    if (suspendStart >= 0) {
                        addSuspended(suspendStart, base + i);
                        suspendStart = -1;
                    }
                }
                i++;
                continue;
            }
            // anything else ends the string
            if (len > 0) {
                r.len = len;
                r.start = start;
                r.letters = letters;
                r.digits = digits;
                r.zeroSkips = zeroSkips;
                r.inPiece = lastType == 1;
                r.pieceScript = lastType == 1 ? SC_LATIN : -1;
                r.pieceCjk = 0;
                pos = base + i;
                endBytes(false);
                len = letters = digits = zeroSkips = 0;
            } else if (suspendStart >= 0) {
                pos = base + i;
                endSuspend();
            }
            lastType = 0;
            i++;
        }
        r.len = len;
        r.start = start;
        r.letters = letters;
        r.digits = digits;
        r.zeroSkips = zeroSkips;
        if (len > 0 || r.inPiece) {
            r.inPiece = lastType == 1;
            r.pieceScript = lastType == 1 ? SC_LATIN : -1;
            r.pieceCjk = 0;
        }
        pos = base + i;
        return i;
    }

    private void checkSuspend() {
        if (suspendStart < 0 && bytesRun.len >= SUSPEND_UTF16_AFTER && bytesRun.zeroSkips == 0 && utf16) {
            suspendStart = pos;
        }
    }

    private void endSuspend() {
        if (suspendStart >= 0) {
            addSuspended(suspendStart, pos);
            suspendStart = -1;
        }
    }

    private void addSuspended(long start, long end) {
        if (end <= start) {
            return;
        }
        if (suspendedCount * 2 + 2 > suspended.length) {
            suspended = Arrays.copyOf(suspended, suspended.length * 2);
        }
        suspended[suspendedCount * 2] = start;
        suspended[suspendedCount * 2 + 1] = end;
        suspendedCount++;
    }

    private void stepBytes(byte[] in, int i, int n, int b) throws SAXException {
        if (u8Need > 0) {
            if (b >= u8Lo && b <= u8Hi) {
                u8Bytes[u8Count++] = (byte) b;
                u8Cp = (u8Cp << 6) | (b & 0x3F);
                u8Lo = 0x80;
                u8Hi = 0xBF;
                if (--u8Need == 0) {
                    u8Count = 0;
                    int info = infoOf(u8Cp);
                    if ((info & CLASS_MASK) != INVALID) {
                        appendBytes(u8Cp, info, u8Start, pos + 1, true);
                    } else {
                        endBytes(false);
                    }
                }
                return;
            }
            // not a valid sequence: its bytes are single byte characters
            flushInvalidUtf8();
        }
        if (b < 0x80) {
            if (IS_CHAR[b]) {
                appendBytes(b, INFO[b], pos, pos + 1, false);
            } else if (b != 0 || (i >= 2 && IS_CHAR[in[i - 2] & 0xFF]) || (i + 2 < n && IS_CHAR[in[i + 2] & 0xFF])) {
                // a zero between UTF-16 Latin-1 characters is skipped, anything else ends the string
                endBytes(false);
            } else if (bytesRun.len > 0) {
                bytesRun.zeroSkips++;
                endSuspend();
            }
            return;
        }
        if (b >= 0xC2 && b <= 0xF4) {
            u8Bytes[0] = (byte) b;
            u8Count = 1;
            u8Start = pos;
            if (b < 0xE0) {
                u8Need = 1;
                u8Cp = b & 0x1F;
            } else if (b < 0xF0) {
                u8Need = 2;
                u8Cp = b & 0x0F;
                if (b == 0xE0) {
                    u8Lo = 0xA0;
                } else if (b == 0xED) {
                    u8Hi = 0x9F;
                }
            } else {
                u8Need = 3;
                u8Cp = b & 0x07;
                if (b == 0xF0) {
                    u8Lo = 0x90;
                } else if (b == 0xF4) {
                    u8Hi = 0x8F;
                }
            }
            return;
        }
        singleByte(b, pos);
    }

    private void singleByte(int b, long at) throws SAXException {
        if (IS_CHAR[b]) {
            char c = BYTE_TO_CHAR[b];
            appendBytes(c, INFO[c], at, at + 1, false);
        } else {
            endBytes(false);
        }
    }

    /** The bytes of an incomplete UTF-8 sequence are decoded as single bytes. */
    private void flushInvalidUtf8() throws SAXException {
        int count = u8Count;
        u8Count = 0;
        u8Need = 0;
        u8Lo = 0x80;
        u8Hi = 0xBF;
        for (int k = 0; k < count; k++) {
            singleByte(u8Bytes[k] & 0xFF, u8Start + k);
        }
    }

    private void appendBytes(int cp, int info, long start, long end, boolean multibyte) throws SAXException {
        ByteRun r = bytesRun;
        int cls = info & CLASS_MASK;
        if (cp < 0x100 && r.pieceCjk == 0 && (r.pieceScript < 0 || r.pieceScript == SC_LATIN)) {
            // Latin-1: Latin letters, punctuation and digits
            if (cls == LETTER) {
                r.letters++;
                if (multibyte) {
                    r.multibyte++;
                }
                if (((info >>> SCRIPT_SHIFT) & 0xFF) == SC_LATIN) {
                    r.pieceScript = SC_LATIN;
                }
                r.inPiece = true;
            } else {
                if (r.inPiece) {
                    r.inPiece = false;
                    r.pieceScript = -1;
                }
                if (cls == DIGIT) {
                    r.digits++;
                }
            }
            if (r.len == 0) {
                r.start = start;
            }
            r.chars[r.len] = (char) cp;
            r.ends[r.len++] = (int) (end - r.start);
            if (r.len >= MAX_RUN) {
                endBytes(true);
            }
            return;
        }
        if (cls >= LETTER) {
            int script = (info >>> SCRIPT_SHIFT) & 0xFF;
            if (script != SC_COMMON && script != SC_INHERITED) {
                int cjk = (info >>> CJK_SHIFT) & 31;
                if (r.inPiece) {
                    boolean compatible = cjk != 0 ? r.pieceScript < 0 && isValidCjkMix(r.pieceCjk | cjk)
                            : r.pieceCjk == 0 && (r.pieceScript < 0 || r.pieceScript == script);
                    if (!compatible) {
                        // the script changes inside a word
                        endBytes(false);
                    }
                }
                if (cls == MARK && !r.inPiece) {
                    // combining mark without a base letter
                    endBytes(false);
                    return;
                }
                if (cjk != 0) {
                    r.pieceCjk |= cjk;
                } else {
                    r.pieceScript = script;
                }
            } else if (cls == MARK && !r.inPiece) {
                endBytes(false);
                return;
            }
            r.inPiece = true;
            if (cls == MARK) {
                r.hasMarks = true;
            } else {
                r.letters++;
                if (multibyte) {
                    r.multibyte++;
                }
            }
        } else {
            if (r.inPiece) {
                r.inPiece = false;
                r.pieceScript = -1;
                r.pieceCjk = 0;
            }
            if (cls == DIGIT) {
                r.digits++;
            }
        }
        if (r.len == 0) {
            r.start = start;
        }
        int rel = (int) (end - r.start);
        if (cp < 0x10000) {
            r.chars[r.len] = (char) cp;
            r.ends[r.len++] = rel;
            if (cp >= 0x100) {
                r.nonLatin1 = true;
            }
        } else {
            r.chars[r.len] = Character.highSurrogate(cp);
            r.ends[r.len++] = rel;
            r.chars[r.len] = Character.lowSurrogate(cp);
            r.ends[r.len++] = rel;
            r.nonLatin1 = true;
        }
        if (r.len >= MAX_RUN) {
            endBytes(true);
        }
    }

    /** Ends the string of the byte decoder (or a part of it, if continued). */
    private void endBytes(boolean continued) throws SAXException {
        ByteRun r = bytesRun;
        if (!continued) {
            endSuspend();
        }
        int n = r.len;
        if (n == 0) {
            if (r.inPiece) {
                r.reset();
            }
            return;
        }
        long runEnd = r.start + r.ends[n - 1];
        if (n >= minSize) {
            int from = 0, to = n;
            if (r.nonLatin1) {
                while (from < to && isSeparator(r.chars[from])) {
                    from++;
                }
                while (!continued && to > from && isSeparator(r.chars[to - 1])) {
                    to--;
                }
            }
            if (r.nonLatin1 && to - from >= minSize) {
                // a lone letter of another script between punctuation is garbage: valid
                // UTF-8 sequences of 2 bytes are frequent by chance
                for (int i = from; i < to; i++) {
                    if (isLoneForeignLetter(r.chars, from, to, i)) {
                        if (i - from >= minSize) {
                            addBytesCandidate(r, from, i, false);
                        }
                        from = i + 1;
                    }
                }
            }
            if (to - from >= minSize) {
                Candidate c = new Candidate(0);
                c.base = from == 0 ? 0 : r.ends[from - 1];
                c.start = r.start + c.base;
                c.end = r.start + r.ends[to - 1];
                c.len = to - from;
                c.chars = Arrays.copyOfRange(r.chars, from, to);
                c.ends = Arrays.copyOfRange(r.ends, from, to);
                c.score = r.letters + r.multibyte + r.digits;
                c.continued = continued;
                c.hasMarks = r.hasMarks;
                pending.add(c);
            }
        }
        if (continued) {
            // the string goes on
            boolean inPiece = r.inPiece;
            int pieceScript = r.pieceScript, pieceCjk = r.pieceCjk, zeroSkips = r.zeroSkips;
            r.reset();
            r.start = runEnd;
            r.inPiece = inPiece;
            r.pieceScript = pieceScript;
            r.pieceCjk = pieceCjk;
            r.zeroSkips = zeroSkips;
        } else {
            r.reset();
        }
    }

    private void addBytesCandidate(ByteRun r, int from, int to, boolean continued) {
        Candidate c = new Candidate(0);
        c.base = from == 0 ? 0 : r.ends[from - 1];
        c.start = r.start + c.base;
        c.end = r.start + r.ends[to - 1];
        c.len = to - from;
        c.chars = Arrays.copyOfRange(r.chars, from, to);
        c.ends = Arrays.copyOfRange(r.ends, from, to);
        c.score = r.letters + r.multibyte + r.digits;
        c.continued = continued;
        c.hasMarks = r.hasMarks;
        pending.add(c);
    }

    private static boolean isLoneForeignLetter(char[] chars, int from, int to, int i) {
        char c = chars[i];
        int group = letterGroup(c);
        if (c < 0x250 || group == -2 || group == -1 || group == SC_LATIN + 2) {
            return false;
        }
        if (group == 1 && (INFO[c] & (HAN_HANGUL | COMMON)) != HAN_HANGUL) {
            // a common CJK letter may stand alone: 3月, 第1章
            return false;
        }
        if (i > from && (isWordChar(chars[i - 1]) || isSeparator(chars[i - 1]))) {
            return false;
        }
        if (i + 1 < to && (isWordChar(chars[i + 1]) || isSeparator(chars[i + 1]))) {
            return false;
        }
        return i > from || i + 1 < to;
    }

    // ---------------------------- UTF-16 decoders ---------------------------

    private static int unitAt(byte[] in, int j, boolean bigEndian) {
        int b0 = in[j] & 0xFF, b1 = in[j + 1] & 0xFF;
        return bigEndian ? (b0 << 8) | b1 : b0 | (b1 << 8);
    }

    private void utf16Pass(Utf16Lane r, byte[] in, int n) {
        // units start at positions of the lane parity: the first one may begin with
        // the last byte of the previous buffer
        int j = (int) ((bufStart + r.parity) & 1);
        if (j == 1) {
            if (histLen > 0 && !isSuspended(bufStart - 1)) {
                int b0 = hist[histLen - 1] & 0xFF, b1 = in[0] & 0xFF;
                stepUtf16(r, r.bigEndian ? (b0 << 8) | b1 : b0 | (b1 << 8), bufStart - 1);
            } else {
                endUtf16(r, false);
            }
        }
        final boolean bigEndian = r.bigEndian;
        final long base = bufStart;
        final long[] susp = suspended;
        final int suspCount = suspendedCount;
        int k = 0;
        // units must end before the next suspended range
        int limit = suspCount > 0 ? (int) Math.min(n, susp[0] - base) : n;
        // the lane state is kept in local variables in the loop
        int seg = r.seg, segBest = r.segBest, lastScript = r.lastScript, segAbove = r.segAbove,
                segForeign = r.segForeign, bestAbove = r.bestAbove, bestForeign = r.bestForeign;
        long segStart = r.segStart, segBestEnd = r.segBestEnd;
        boolean prevLetter = r.prevLetter, prevZero = r.prevZero, prevLatin = r.prevLatin;
        while (true) {
            if (j + 1 >= limit) {
                if (limit >= n) {
                    break;
                }
                // skip the suspended range
                if (seg > 0) {
                    closeSegment(r, segStart, segBest, segBestEnd, bestAbove, bestForeign, 0);
                    seg = 0;
                }
                prevZero = prevLetter = prevLatin = false;
                int next = (int) (susp[2 * k + 1] - base);
                k++;
                limit = k < suspCount ? (int) Math.min(n, susp[2 * k] - base) : n;
                if (((next ^ j) & 1) != 0) {
                    next++;
                }
                if (next > j) {
                    j = next;
                }
                continue;
            }
            if (seg == 0) {
                // fast skip of the units that cannot start a segment: text has two units of
                // positive weight in a row
                while (j + 3 < limit) {
                    if (STARTS16[unitAt(in, j, bigEndian)]) {
                        if (STARTS16[unitAt(in, j + 2, bigEndian)]) {
                            break;
                        }
                        j += 4;
                    } else {
                        j += 2;
                    }
                }
                if (j + 1 >= limit) {
                    continue;
                }
                prevZero = j >= 2 ? in[j - 1] == 0 && in[j - 2] == 0 : prevZero;
                prevLetter = prevLatin = false;
            }
            long p = base + j;
            int unit = unitAt(in, j, bigEndian);
            int u = U16[unit];
            j += 2;
            if ((u & U_INVALID) != 0) {
                // end of a string
                if (seg > 0) {
                    closeSegment(r, segStart, segBest, segBestEnd, bestAbove, bestForeign,
                            unit == 0 && segBestEnd == p ? END_BONUS16 : 0);
                    seg = 0;
                }
                prevZero = unit == 0;
                prevLetter = prevLatin = false;
                continue;
            }
            int w = (byte) u;
            if (unit < 0x80 && prevLatin && (u & U_LATIN) != 0) {
                w += LATIN_WORD16;
            }
            if (seg == 0) {
                if ((u & U_MARK) != 0) {
                    // combining mark without a base letter
                    w -= NO_BASE16;
                }
                if (w > 0) {
                    // a segment starts
                    if (prevZero) {
                        w += START_BONUS16;
                    }
                    segStart = p;
                    seg = segBest = w;
                    segBestEnd = p + 2;
                    bestAbove = segAbove = unit >>> 8 != 0 ? 1 : 0;
                    bestForeign = segForeign = (u & U_SPECIFIC) != 0 ? 1 : 0;
                    lastScript = (u & U_SPECIFIC) != 0 ? (INFO[unit] >>> SCRIPT_SHIFT) & 0xFF : -1;
                }
            } else {
                if ((u & U_SPECIFIC) != 0) {
                    segForeign++;
                    int script = (INFO[unit] >>> SCRIPT_SHIFT) & 0xFF;
                    if (lastScript >= 0 && script != lastScript
                            && (CJK_BITS[lastScript] == 0 || CJK_BITS[script] == 0)) {
                        w -= SCRIPT_SWITCH16;
                    }
                    lastScript = script;
                }
                if ((u & U_MARK) != 0 && !prevLetter) {
                    w -= NO_BASE16;
                }
                seg += w;
                if (unit >>> 8 != 0) {
                    segAbove++;
                }
                if (seg <= 0 || seg < segBest - MAX_DROP16) {
                    // garbage after the string
                    closeSegment(r, segStart, segBest, segBestEnd, bestAbove, bestForeign, 0);
                    seg = 0;
                } else {
                    if (seg > segBest) {
                        segBest = seg;
                        segBestEnd = p + 2;
                        bestAbove = segAbove;
                        bestForeign = segForeign;
                    }
                    if (p + 2 - segStart >= 2L * MAX_RUN) {
                        // long string: written in parts
                        if (segBest >= MIN_SCORE16 && segAbove > 0) {
                            decodeUtf16(r, segStart, p + 2, true, segBest);
                        }
                        seg = 0;
                    }
                }
            }
            prevLetter = (u & U_WORD) != 0;
            prevLatin = (u & U_LATIN) != 0;
            prevZero = false;
        }
        r.seg = seg;
        r.segBest = segBest;
        r.segStart = segStart;
        r.segBestEnd = segBestEnd;
        r.segAbove = segAbove;
        r.segForeign = segForeign;
        r.bestAbove = bestAbove;
        r.bestForeign = bestForeign;
        r.lastScript = lastScript;
        r.prevLetter = prevLetter;
        r.prevLatin = prevLatin;
        r.prevZero = prevZero;
    }

    private boolean isSuspended(long p) {
        for (int k = 0; k < suspendedCount; k++) {
            if (p >= suspended[2 * k] && p < suspended[2 * k + 1]) {
                return true;
            }
        }
        return false;
    }

    /**
     * The unit made of the last byte of the previous buffer and the first of this
     * one: same logic as utf16Pass, on the fields of the lane.
     */
    private void stepUtf16(Utf16Lane r, int unit, long unitPos) {
        int u = U16[unit];
        if ((u & U_INVALID) != 0) {
            if (r.seg > 0) {
                closeSegment(r, r.segStart, r.segBest, r.segBestEnd, r.bestAbove, r.bestForeign,
                        unit == 0 && r.segBestEnd == unitPos ? END_BONUS16 : 0);
                r.seg = 0;
            }
            r.prevZero = unit == 0;
            r.prevLetter = r.prevLatin = false;
            return;
        }
        int w = (byte) u;
        if (unit < 0x80 && r.prevLatin && (u & U_LATIN) != 0) {
            w += LATIN_WORD16;
        }
        if (r.seg == 0) {
            if ((u & U_MARK) != 0) {
                w -= NO_BASE16;
            }
            if (w > 0) {
                if (r.prevZero) {
                    w += START_BONUS16;
                }
                r.segStart = unitPos;
                r.seg = r.segBest = w;
                r.segBestEnd = unitPos + 2;
                r.bestAbove = r.segAbove = unit >>> 8 != 0 ? 1 : 0;
                r.bestForeign = r.segForeign = (u & U_SPECIFIC) != 0 ? 1 : 0;
                r.lastScript = (u & U_SPECIFIC) != 0 ? (INFO[unit] >>> SCRIPT_SHIFT) & 0xFF : -1;
            }
        } else {
            if ((u & U_SPECIFIC) != 0) {
                r.segForeign++;
                int script = (INFO[unit] >>> SCRIPT_SHIFT) & 0xFF;
                if (r.lastScript >= 0 && script != r.lastScript
                        && (CJK_BITS[r.lastScript] == 0 || CJK_BITS[script] == 0)) {
                    w -= SCRIPT_SWITCH16;
                }
                r.lastScript = script;
            }
            if ((u & U_MARK) != 0 && !r.prevLetter) {
                w -= NO_BASE16;
            }
            r.seg += w;
            if (unit >>> 8 != 0) {
                r.segAbove++;
            }
            if (r.seg <= 0 || r.seg < r.segBest - MAX_DROP16) {
                closeSegment(r, r.segStart, r.segBest, r.segBestEnd, r.bestAbove, r.bestForeign, 0);
                r.seg = 0;
            } else if (r.seg > r.segBest) {
                r.segBest = r.seg;
                r.segBestEnd = unitPos + 2;
                r.bestAbove = r.segAbove;
                r.bestForeign = r.segForeign;
            }
        }
        r.prevLetter = (u & U_WORD) != 0;
        r.prevLatin = (u & U_LATIN) != 0;
        r.prevZero = false;
    }

    private void closeSegment(Utf16Lane r, long segStart, int segBest, long segBestEnd, int above, int foreign,
            int bonus) {
        if (above > 0 && segBest + bonus >= (foreign > 0 ? MIN_SCORE16 : MIN_SCORE16_LATIN)) {
            decodeUtf16(r, segStart, segBestEnd, false, segBest + bonus);
        }
    }

    /** Ends a UTF-16 string, not by U+0000. */
    private void endUtf16(Utf16Lane r, boolean byZero) {
        if (r.seg > 0) {
            closeSegment(r, r.segStart, r.segBest, r.segBestEnd, r.bestAbove, r.bestForeign, 0);
            r.seg = 0;
        }
        r.prevLetter = r.prevLatin = false;
        r.prevZero = byZero;
    }

    /**
     * Tables of numbers read as UTF-16 may look like letters too, but with a
     * structure text does not have: letters of different scripts glued together,
     * words of accented letters only, every other unit alike (fields of 32 bits),
     * the same letter many times, letters in increasing or decreasing order.
     */
    static boolean isStructured(char[] chars, int from, int to, int letters) {
        // letters glued to letters of another script: in text, scripts change
        // between words, except Latin glued to CJK in brands and loanwords
        int glued = 0, changes = 0, strictChanges = 0, prevGroup = -1;
        int latin = 0, accented = 0;
        for (int i = from; i < to; i++) {
            char c = chars[i];
            int info = INFO[c];
            int cls = info & CLASS_MASK;
            if (cls != LETTER) {
                if (cls != MARK) {
                    prevGroup = -1;
                }
                continue;
            }
            int script = (info >>> SCRIPT_SHIFT) & 0xFF;
            int group = ((info >>> CJK_SHIFT) & 31) != 0 ? 1 : script == SC_COMMON ? -1 : script + 2;
            if (script == SC_LATIN) {
                latin++;
                if (c >= 0x80) {
                    accented++;
                }
            }
            if (group >= 0) {
                if (prevGroup >= 0) {
                    glued++;
                    if (group != prevGroup) {
                        changes++;
                        if (group != 1 && prevGroup != 1) {
                            strictChanges++;
                        }
                    }
                }
                prevGroup = group;
            }
        }
        if (changes * (glued < 4 ? 2 : 4) > glued || (strictChanges > 0 && letters < 12)) {
            return true;
        }
        // Latin words have mostly ASCII letters
        if (latin >= 4 && accented * 10 > latin * 7) {
            return true;
        }
        // Latin-1 UTF-16 read at the wrong offset: U+5000 U+6100 U+6700...
        int zeroLow = 0;
        for (int i = from; i < to; i++) {
            if (chars[i] >= 0x100 && (chars[i] & 0xFF) == 0) {
                zeroLow++;
            }
        }
        if (zeroLow >= 3 && zeroLow * 2 >= letters) {
            return true;
        }
        int n = to - from;
        if (n >= 6) {
            // one parity of units with the same high or low byte, the other not
            int[] even = new int[512], odd = new int[512];
            for (int i = from; i < to; i++) {
                int[] counts = ((i - from) & 1) == 0 ? even : odd;
                counts[chars[i] >>> 8]++;
                counts[256 + (chars[i] & 0xFF)]++;
            }
            int ne = (n + 1) / 2, no = n / 2;
            for (int h = 0; h < 512; h++) {
                if ((even[h] * 4 >= ne * 3 && odd[h] * 4 < no) || (odd[h] * 4 >= no * 3 && even[h] * 4 < ne)) {
                    return true;
                }
            }
        }
        if (letters < 4) {
            return false;
        }
        // letters: in arithmetic progression, increasing or decreasing, repeated
        char[] sorted = new char[letters];
        int k = 0, sameStep = 0, up = 0, down = 0, steps = 0, prev = -1, prevStep = Integer.MIN_VALUE;
        for (int i = from; i < to; i++) {
            char c = chars[i];
            if ((INFO[c] & CLASS_MASK) != LETTER) {
                continue;
            }
            sorted[k++] = c;
            if (prev >= 0) {
                int step = c - prev;
                if (step != 0 && step == prevStep) {
                    sameStep++;
                }
                if (step > 0) {
                    up++;
                } else if (step < 0) {
                    down++;
                }
                prevStep = step;
                steps++;
            }
            prev = c;
        }
        if (sameStep >= 3 && sameStep * 2 >= steps) {
            return true;
        }
        if (steps >= 6 && (up * 10 >= steps * 9 || down * 10 >= steps * 9)) {
            return true;
        }
        Arrays.sort(sorted, 0, k);
        int max = 1, run = 1;
        for (int i = 1; i < k; i++) {
            run = sorted[i] == sorted[i - 1] ? run + 1 : 1;
            max = Math.max(max, run);
        }
        if (max * 100 > k * (k < 6 ? 60 : k < 12 ? 50 : 35)) {
            return true;
        }
        // every other unit the same
        if (n >= 8) {
            int sameEven = 0, sameOdd = 0;
            for (int i = from + 2; i < to; i++) {
                if (chars[i] == chars[i - 2] && !isSeparator(chars[i])) {
                    if (((i - from) & 1) == 0) {
                        sameEven++;
                    } else {
                        sameOdd++;
                    }
                }
            }
            int half = (n - 2) / 2;
            if (sameEven * 4 >= half * 3 || sameOdd * 4 >= half * 3) {
                return true;
            }
        }
        return false;
    }

    /** Script of a letter: 1 for CJK scripts, -1 for none, -2 for not a letter. */
    private static int letterGroup(char c) {
        int info = INFO[c];
        if ((info & CLASS_MASK) != LETTER) {
            return -2;
        }
        if (((info >>> CJK_SHIFT) & 31) != 0) {
            return 1;
        }
        int script = (info >>> SCRIPT_SHIFT) & 0xFF;
        return script == SC_COMMON || script == SC_INHERITED ? -1 : script + 2;
    }

    private static boolean isWordChar(char c) {
        return (INFO[c] & CLASS_MASK) >= LETTER;
    }

    /**
     * One or two letters glued to the start of the first word, in another script
     * than the rest of it.
     */
    static int trimGluedStart(char[] chars, int from, int to) {
        if (from >= to) {
            return from;
        }
        int end = from;
        while (end < to && isWordChar(chars[end])) {
            end++;
        }
        int g0 = letterGroup(chars[from]);
        if (g0 < 0) {
            return from;
        }
        int k = from + 1, n0 = 1;
        while (k < end && (letterGroup(chars[k]) == g0 || (INFO[chars[k]] & CLASS_MASK) == MARK)) {
            if ((INFO[chars[k]] & CLASS_MASK) == LETTER) {
                n0++;
            }
            k++;
        }
        if (k >= end || n0 > 2) {
            return from;
        }
        int g1 = letterGroup(chars[k]), n1 = 0;
        for (int i = k; i < end; i++) {
            if (letterGroup(chars[i]) == g1) {
                n1++;
            }
        }
        return g1 >= 0 && n1 >= 2 ? k : from;
    }

    /**
     * One or two letters glued to the end of the last word, in another script than
     * the rest of it.
     */
    static int trimGluedEnd(char[] chars, int from, int to) {
        if (from >= to) {
            return to;
        }
        int start = to;
        while (start > from && isWordChar(chars[start - 1])) {
            start--;
        }
        int last = to - 1;
        while (last > start && (INFO[chars[last]] & CLASS_MASK) == MARK) {
            last--;
        }
        int g0 = letterGroup(chars[last]);
        if (g0 < 0 || last <= start) {
            return to;
        }
        // the first letter of the glued piece: marks before it belong to the letter before
        int cut = last, n0 = 1, k = last - 1;
        while (k >= start) {
            if ((INFO[chars[k]] & CLASS_MASK) == MARK) {
                k--;
            } else if (letterGroup(chars[k]) == g0) {
                n0++;
                cut = k--;
            } else {
                break;
            }
        }
        if (k < start || n0 > 2) {
            return to;
        }
        int g1 = letterGroup(chars[k]), n1 = 0;
        for (int i = start; i <= k; i++) {
            if (letterGroup(chars[i]) == g1) {
                n1++;
            }
        }
        return g1 >= 0 && n1 >= 2 ? cut : to;
    }

    /** Decodes an accepted UTF-16 string again from the bytes. */
    private void decodeUtf16(Utf16Lane r, long start, long end, boolean continued, int score16) {
        int n = (int) ((end - start) >> 1);
        char[] chars = new char[n];
        int[] ends = new int[n];
        for (int k = 0; k < n; k++) {
            long p = start + 2L * k;
            int b0 = byteAt(p), b1 = byteAt(p + 1);
            chars[k] = (char) (r.bigEndian ? (b0 << 8) | b1 : b0 | (b1 << 8));
            ends[k] = 2 * (k + 1);
        }
        for (int k = 0; k + 1 < n; k++) {
            if (Character.isHighSurrogate(chars[k]) && Character.isLowSurrogate(chars[k + 1])) {
                ends[k] = ends[k + 1];
                k++;
            }
        }
        int from = 0, to = n;
        while (from < to && isSeparator(chars[from])) {
            from++;
        }
        while (!continued && to > from && isSeparator(chars[to - 1])) {
            to--;
        }
        // a length or other data glued to the string looks like letters of another script
        from = trimGluedStart(chars, from, to);
        if (!continued) {
            to = trimGluedEnd(chars, from, to);
        }
        if (to - from < minSize) {
            return;
        }
        int letters = 0, digits = 0, nonLatin1 = 0;
        boolean hasMarks = false;
        for (int k = from; k < to; k++) {
            int cls = INFO[chars[k]] & CLASS_MASK;
            if (cls == LETTER || Character.isHighSurrogate(chars[k])) {
                letters++;
            } else if (cls == DIGIT) {
                digits++;
            } else if (cls == MARK) {
                hasMarks = true;
            }
            if (chars[k] >= 0x100) {
                nonLatin1++;
            }
        }
        if (nonLatin1 == 0 || letters == 0) {
            // Latin-1 UTF-16 is read by the byte decoder
            return;
        }
        if (isStructured(chars, from, to, letters)) {
            return;
        }
        Candidate c = new Candidate(r.lane);
        c.base = from == 0 ? 0 : ends[from - 1];
        c.start = start + c.base;
        c.end = start + ends[to - 1];
        c.len = to - from;
        c.chars = from == 0 && to == n ? chars : Arrays.copyOfRange(chars, from, to);
        c.ends = from == 0 && to == n ? ends : Arrays.copyOfRange(ends, from, to);
        c.score = letters + digits - UTF16_HANDICAP;
        c.score16 = score16;
        c.continued = continued;
        c.hasMarks = hasMarks;
        addPending(c);
    }

    // ---------------------------- overlaps ----------------------------------

    private static final Comparator<Candidate> BY_START = (a, b) -> Long.compare(a.start, b.start);

    private void addPending(Candidate c) {
        int k = pending.size();
        while (k > 0 && pending.get(k - 1).start > c.start) {
            k--;
        }
        pending.add(k, c);
    }

    /** Start of the earliest string still being read that may overlap others. */
    private long horizon() {
        long h = pos;
        if (bytesRun.len > 0) {
            h = Math.min(h, bytesRun.start);
        }
        if (u8Count > 0) {
            h = Math.min(h, u8Start);
        }
        for (Utf16Lane r : u16Lanes) {
            if (r.seg >= MIN_SCORE16 / 2) {
                h = Math.min(h, r.segStart);
            }
        }
        return h;
    }

    private void drain(boolean all) throws SAXException {
        if (pending.isEmpty()) {
            return;
        }
        long horizon = all ? Long.MAX_VALUE : horizon();
        int i = 0;
        int size = pending.size();
        while (i < size) {
            long clusterEnd = pending.get(i).end;
            int j = i + 1;
            while (j < size && pending.get(j).start < clusterEnd) {
                clusterEnd = Math.max(clusterEnd, pending.get(j).end);
                j++;
            }
            if (clusterEnd > horizon) {
                break;
            }
            if (j == i + 1 && emittedEnd <= pending.get(i).start) {
                emit(pending.get(i));
            } else {
                resolve(pending.subList(i, j));
            }
            i = j;
        }
        if (i == size) {
            pending.clear();
        } else if (i > 0) {
            pending.subList(0, i).clear();
        }
    }

    private static final Comparator<Candidate> BY_SCORE = (a, b) -> {
        if (a.score != b.score) {
            return b.score - a.score;
        }
        return a.lane - b.lane;
    };

    /**
     * Overlapping strings: the best one wins, the others keep what is left. Strings
     * already written cannot be taken back, but trim the worse ones.
     */
    private void resolve(List<Candidate> cluster) throws SAXException {
        ArrayList<Candidate> byScore = new ArrayList<>(cluster.size());
        // UTF-16 text also decodes, one byte off, with the other byte order: keep the
        // reading with more evidence (the right one has its spaces)
        ArrayList<Candidate> utf16 = new ArrayList<>();
        for (Candidate c : cluster) {
            if (c.lane == 0) {
                byScore.add(c);
            } else {
                utf16.add(c);
            }
        }
        if (utf16.size() > 1) {
            utf16.sort(BY_SCORE16);
            ArrayList<Candidate> kept = new ArrayList<>();
            for (Candidate c : utf16) {
                Candidate t = largestFreePart(c, kept);
                if (t != null) {
                    kept.add(t);
                }
            }
            utf16 = kept;
        }
        byScore.addAll(utf16);
        long clusterStart = cluster.get(0).start;
        if (emittedEnd > clusterStart) {
            for (int k = 0; k < emittedCount; k++) {
                if (emitted[k].end > clusterStart) {
                    byScore.add(emitted[k]);
                }
            }
        }
        byScore.sort(BY_SCORE);
        ArrayList<Candidate> accepted = new ArrayList<>();
        ArrayList<Candidate> winners = new ArrayList<>();
        for (Candidate c : byScore) {
            if (c.emitted) {
                accepted.add(c);
                continue;
            }
            Candidate t = largestFreePart(c, accepted);
            if (t != null) {
                accepted.add(t);
                winners.add(t);
            }
        }
        winners.sort(BY_START);
        for (Candidate c : winners) {
            emit(c);
        }
    }

    /** The longest part of c not covered by accepted strings, if long enough. */
    private Candidate largestFreePart(Candidate c, List<Candidate> accepted) {
        int bestFrom = 0, bestTo = 0, from = -1;
        for (int k = 0; k <= c.len; k++) {
            boolean free = k < c.len && !overlaps(accepted, c.charStart(k), c.charEnd(k));
            if (free && from < 0) {
                from = k;
            } else if (!free && from >= 0) {
                if (k - from > bestTo - bestFrom) {
                    bestFrom = from;
                    bestTo = k;
                }
                from = -1;
            }
        }
        return bestTo - bestFrom >= minSize ? trim(c, bestFrom, bestTo) : null;
    }

    private static final Comparator<Candidate> BY_SCORE16 = (a, b) -> {
        if (a.score16 != b.score16) {
            return b.score16 - a.score16;
        }
        return a.lane - b.lane;
    };

    private static boolean overlaps(List<Candidate> accepted, long start, long end) {
        for (Candidate a : accepted) {
            if (start < a.end && end > a.start) {
                return true;
            }
        }
        return false;
    }

    private static Candidate trim(Candidate c, int from, int to) {
        if (from == 0 && to == c.len) {
            return c;
        }
        Candidate t = new Candidate(c.lane);
        t.start = c.charStart(from);
        t.end = c.charEnd(to - 1);
        t.len = to - from;
        t.chars = Arrays.copyOfRange(c.chars, from, to);
        t.ends = Arrays.copyOfRange(c.ends, from, to);
        t.base = (int) (t.start - c.start) + c.base;
        t.score = c.score;
        t.score16 = c.score16;
        t.continued = c.continued && to == c.len;
        t.hasMarks = c.hasMarks;
        return t;
    }

    private void recordEmitted(Candidate c) {
        Candidate e = emitted[emittedNext];
        if (e == null) {
            e = emitted[emittedNext] = new Candidate(-1);
            e.emitted = true;
            emittedCount++;
        }
        emittedNext = (emittedNext + 1) % emitted.length;
        e.start = c.start;
        e.end = c.end;
        e.score = c.score;
        emittedEnd = Math.max(emittedEnd, c.end);
    }

    // ---------------------------- output ------------------------------------

    private void emit(Candidate c) throws SAXException {
        recordEmitted(c);
        char[] chars = c.chars;
        int off = 0, len = c.len;
        if (c.hasMarks) {
            CharBuffer text = CharBuffer.wrap(chars, 0, len);
            if (!Normalizer.isNormalized(text, Normalizer.Form.NFC)) {
                chars = Normalizer.normalize(text, Normalizer.Form.NFC).toCharArray();
                len = chars.length;
            }
        }
        while (len > 0) {
            if (outLen == out.length) {
                flushOutput();
            }
            int n = Math.min(len, out.length - outLen);
            System.arraycopy(chars, off, out, outLen, n);
            outLen += n;
            off += n;
            len -= n;
        }
        if (!c.continued) {
            if (outLen == out.length) {
                flushOutput();
            }
            out[outLen++] = '\n';
        }
    }

    private void flushOutput() throws SAXException {
        if (outLen > 0) {
            sink.write(out, 0, outLen);
            outLen = 0;
        }
    }
}
