package iped.engine.lucene.analysis;

import java.io.IOException;
import java.text.Normalizer;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 * Removes optional diacritics not handled by {@link FastASCIIFoldingFilter},
 * so searches without them find texts written with them (and vice versa):
 * <ul>
 * <li>combining marks of decomposed Latin, Greek and Cyrillic text (e.g.
 * Russian stress marks);</li>
 * <li>Greek accents and breathings, final sigma is mapped to sigma;</li>
 * <li>Cyrillic yo is mapped to ye;</li>
 * <li>Hebrew points (niqqud) and cantillation marks;</li>
 * <li>Arabic and Syriac vowel marks (harakat) and tatweel, plus the usual
 * Arabic/Persian orthographic normalization (alef variants, yeh, keheh, teh
 * marbuta, heh variants), as done by Lucene's Arabic and Persian
 * normalizers.</li>
 * </ul>
 * Marks that are part of the spelling in other scripts (Indic, Thai, ...) are
 * kept. Part of the {@link AnalyzerVersion#UNICODE} analysis when characters
 * are converted to ASCII.
 */
public final class DiacriticFoldingFilter extends TokenFilter {

    /** Marker of characters to delete (U+FFFF is a noncharacter). */
    private static final char DELETE = '￿';

    private static final char[] MAP = buildMap();

    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);

    public DiacriticFoldingFilter(TokenStream input) {
        super(input);
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (!input.incrementToken()) {
            return false;
        }
        char[] buf = termAtt.buffer();
        int len = termAtt.length();
        int i = 0;
        while (i < len && buf[i] < 0x0300) {
            i++;
        }
        if (i == len) {
            return true;
        }
        int newLen = i;
        for (; i < len; i++) {
            char mapped = MAP[buf[i]];
            if (mapped != DELETE) {
                buf[newLen++] = mapped;
            }
        }
        termAtt.setLength(newLen);
        return true;
    }

    private static char[] buildMap() {
        char[] map = new char[Character.MAX_VALUE + 1];
        for (int c = 0; c <= Character.MAX_VALUE; c++) {
            map[c] = (char) c;
        }

        // combining diacritical marks used by decomposed Latin, Greek and Cyrillic text
        deleteRange(map, 0x0300, 0x036F);
        deleteRange(map, 0x1AB0, 0x1AFF);
        deleteRange(map, 0x1DC0, 0x1DFF);
        deleteRange(map, 0x20D0, 0x20FF);
        deleteRange(map, 0xFE20, 0xFE2F);

        // Hebrew points and cantillation marks
        deleteNonSpacingMarks(map, 0x0591, 0x05C7);

        // Arabic and Syriac vowel marks and other optional marks
        deleteNonSpacingMarks(map, 0x0610, 0x061A);
        deleteNonSpacingMarks(map, 0x064B, 0x065F);
        deleteNonSpacingMarks(map, 0x0670, 0x0670);
        deleteNonSpacingMarks(map, 0x06D6, 0x06ED);
        deleteNonSpacingMarks(map, 0x0711, 0x0711);
        deleteNonSpacingMarks(map, 0x0730, 0x074A);
        deleteNonSpacingMarks(map, 0x08D3, 0x08FF);
        map[0x0640] = DELETE; // tatweel

        // Arabic and Persian letter variants
        map[0x0622] = map[0x0623] = map[0x0625] = map[0x0671] = 'ا'; // alef with madda/hamza, wasla -> alef
        map[0x0649] = map[0x06CC] = 'ي'; // alef maksura, farsi yeh -> yeh
        map[0x06A9] = 'ك'; // keheh -> kaf
        map[0x0629] = map[0x06C0] = map[0x06C1] = map[0x06C2] = map[0x06C3] = 'ه'; // teh marbuta, heh variants

        // Greek accented letters -> base letter
        for (int c = 0x0370; c <= 0x03FF; c++) {
            mapToGreekBase(map, c);
        }
        for (int c = 0x1F00; c <= 0x1FFF; c++) {
            mapToGreekBase(map, c);
        }
        map[0x03C2] = 'σ'; // final sigma -> sigma

        // Cyrillic yo -> ye
        map[0x0451] = 'е';
        map[0x0401] = 'Е';

        return map;
    }

    private static void deleteRange(char[] map, int from, int to) {
        for (int c = from; c <= to; c++) {
            map[c] = DELETE;
        }
    }

    private static void deleteNonSpacingMarks(char[] map, int from, int to) {
        for (int c = from; c <= to; c++) {
            if (Character.getType(c) == Character.NON_SPACING_MARK) {
                map[c] = DELETE;
            }
        }
    }

    private static void mapToGreekBase(char[] map, int c) {
        if (!Character.isLetter(c)) {
            return;
        }
        String decomposed = Normalizer.normalize(String.valueOf((char) c), Normalizer.Form.NFD);
        char base = decomposed.charAt(0);
        if (base != c && base >= 0x0370 && base <= 0x03FF && Character.isLetter(base)) {
            map[c] = base;
        }
    }
}
