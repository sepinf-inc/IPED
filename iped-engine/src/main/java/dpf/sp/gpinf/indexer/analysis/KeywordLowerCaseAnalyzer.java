package dpf.sp.gpinf.indexer.analysis;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;

public class KeywordLowerCaseAnalyzer extends Analyzer {

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        KeywordTokenizer tokenizer = new KeywordTokenizer();
        return new TokenStreamComponents(tokenizer, new LowerCaseFilter(tokenizer));
    }

}
