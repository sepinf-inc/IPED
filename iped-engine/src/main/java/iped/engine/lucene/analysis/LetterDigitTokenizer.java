package iped.engine.lucene.analysis;

import org.apache.lucene.analysis.util.CharTokenizer;

/**
 * Tokenizer that returns alphanumeric and optional custom configured chars as
 * tokens. Other chars are considered text separators.
 * 
 */
public class LetterDigitTokenizer extends CharTokenizer {

    private final int[] extraCodePoints;

    /**
     * Construct a new LetterTokenizer.
     */
    public LetterDigitTokenizer(int[] extraCodePoints) {
        super();
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

}
