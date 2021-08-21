package dpf.sp.gpinf.indexer.analysis;

import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
/* [Triage] The following libraries are used to process tokens in a non-standard way */
import org.apache.lucene.analysis.miscellaneous.LengthFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.util.FilteringTokenFilter;

/*
 * Analisador de texto que utiliza o tokenizador LowerCaseLetterDigitTokenizer e
 * o filtro ASCIIFoldingFilter, o qual converte caracteres para seus equivalentes ascii,
 * removando acentos, cedilhas, etc.
 */
public class StandardASCIIAnalyzer extends Analyzer {

    private Set<?> stopSet;

    /**
     * Specifies whether deprecated acronyms should be replaced with HOST type. See
     * {@linkplain https://issues.apache.org/jira/browse/LUCENE-1068}
     */
    // private final boolean replaceInvalidAcronym,enableStopPositionIncrements;

    private final boolean pipeTokenizer;

    /**
     * Default maximum allowed token length
     */
    public static final int DEFAULT_MAX_TOKEN_LENGTH = 255;

    private int maxTokenLength = DEFAULT_MAX_TOKEN_LENGTH;

    private boolean filterNonLatinChars = false;

    private boolean convertCharsToAscii = true;

    private boolean convertCharsToLowerCase = true;

    private int[] extraChars;

    /**
     * Builds an analyzer with the default stop words.
     *
     * @param matchVersion
     *            Lucene version to match
     */
    public StandardASCIIAnalyzer(boolean pipeTokenizer) {
        this.pipeTokenizer = pipeTokenizer;
    }

    public void setFilterNonLatinChars(boolean filterNonLatinChars) {
        this.filterNonLatinChars = filterNonLatinChars;
    }

    public void setConvertCharsToLower(boolean convertToLower) {
        this.convertCharsToLowerCase = convertToLower;
    }

    public void setConvertCharsToAscii(boolean convertCharsToAscii) {
        this.convertCharsToAscii = convertCharsToAscii;
    }

    public void setExtraCharsToIndex(int[] extraChars) {
        this.extraChars = extraChars;
    }

    /**
     * Set maximum allowed token length. If a token is seen that exceeds this length
     * then it is discarded. This setting only takes effect the next time
     * tokenStream or reusableTokenStream is called.
     */
    public void setMaxTokenLength(int length) {
        maxTokenLength = length;
    }

    /**
     * @see #setMaxTokenLength
     */
    public int getMaxTokenLength() {
        return maxTokenLength;
    }

    @Override
    protected TokenStreamComponents createComponents(final String fieldName) {

        Tokenizer tokenizer;
        if (pipeTokenizer) {
            tokenizer = new CategoryTokenizer();
        } else {
            tokenizer = new LetterDigitTokenizer(convertCharsToLowerCase, extraChars);
        }

        // src.setMaxTokenLength(maxTokenLength);
        TokenStream tok = tokenizer;
        if (convertCharsToAscii)
            tok = new FastASCIIFoldingFilter(tokenizer);

        // tok = new StopFilter(matchVersion, tok, stopwords);

        /*
         * The following code removes tokens that exceed the maximum size or that
         * contain non-latin characters (after being converted by the
         * FastASCIIFoldingFilter). Nonetheless, the filters are not applied to the
         * Category's description, which is checked by the following "if"
         */
        if (!(pipeTokenizer)) {
            tok = new LengthFilter(tok, 1, maxTokenLength);
            if (filterNonLatinChars)
                tok = new Latin1CharacterFilter(tok);
        }

        return new TokenStreamComponents(tokenizer, tok);
    }

    /*
     * [Triage] Filters that identifies tokens that contain non-latin Unicode
     * characters
     */
    public class Latin1CharacterFilter extends FilteringTokenFilter {

        private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);

        public Latin1CharacterFilter(TokenStream tokenStream) {
            super(tokenStream);
        }

        @Override
        public boolean accept() {

            final char[] buffer = termAtt.buffer();
            final int length = termAtt.length();

            // Runs through each character of the token, checking for invalid ones
            // If the token is clear, it returns true
            for (int i = 0; i < length; i++) {
                final char c = buffer[i];
                if (c > '\u00FF') {
                    return false;
                }
            }
            return true;
        }
    }
}
