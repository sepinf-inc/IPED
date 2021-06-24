package dpf.sp.gpinf.indexer.analysis;

import org.apache.lucene.analysis.util.CharTokenizer;

/*
 * Tokenizador que divide o texto em caracteres diferentes de letras e números.
 * Também converte os caracteres para minúsculas.
 * Assim a indexação tem comportamento similar ao FTK
 */
public class LetterDigitTokenizer extends CharTokenizer {

    private final boolean convertCharsToLowerCase;

    private final int[] extraCodePoints;

    /**
     * Construct a new LetterTokenizer.
     */
    public LetterDigitTokenizer(boolean convertCharsToLowerCase, int[] extraCodePoints) {
        super();
        this.convertCharsToLowerCase = convertCharsToLowerCase;
        this.extraCodePoints = extraCodePoints;
    }

    private static final boolean[] isChar = getCharMap();

    private static boolean[] getCharMap() {

        boolean[] isChar = new boolean[1114112];
        for (int c = 0; c < isChar.length; c++) {
            if (Character.isLetterOrDigit(c)) {
                isChar[c] = true;
            }
        }

        return isChar;

    }

    /**
     * Collects only characters which satisfy {@link Character#isLetter(char)}.
     */
    @Override
    protected boolean isTokenChar(int c) {
        // return Character.isLetterOrDigit(c) || (extraCodePoints != null &&
        // isExtraCodePoints(c));
        return isChar[c] || (extraCodePoints != null && isExtraCodePoints(c));
    }

    private final boolean isExtraCodePoints(int c) {

        for (int i = 0; i < extraCodePoints.length; i++) {
            if (c == extraCodePoints[i]) {
                return true;
            }
        }

        return false;
    }

    /**
     * Converts char to lower case {@link Character#toLowerCase(char)}.
     */
    @Override
    protected int normalize(int c) {
        if (convertCharsToLowerCase) {
            return Character.toLowerCase(c);
        } else {
            return c;
        }
    }

}
