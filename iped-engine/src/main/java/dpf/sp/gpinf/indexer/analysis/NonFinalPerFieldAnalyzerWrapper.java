package dpf.sp.gpinf.indexer.analysis;

import java.util.Collections;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.DelegatingAnalyzerWrapper;

public class NonFinalPerFieldAnalyzerWrapper extends DelegatingAnalyzerWrapper {

    private final Analyzer defaultAnalyzer;
    private final Map<String, Analyzer> fieldAnalyzers;

    /**
     * Constructs with default analyzer.
     *
     * @param defaultAnalyzer Any fields not specifically
     * defined to use a different analyzer will use the one provided here.
     */
    public NonFinalPerFieldAnalyzerWrapper(Analyzer defaultAnalyzer) {
      this(defaultAnalyzer, null);
    }

    /**
     * Constructs with default analyzer and a map of analyzers to use for 
     * specific fields.
     *
     * @param defaultAnalyzer Any fields not specifically
     * defined to use a different analyzer will use the one provided here.
     * @param fieldAnalyzers a Map (String field name to the Analyzer) to be 
     * used for those fields 
     */
    public NonFinalPerFieldAnalyzerWrapper(Analyzer defaultAnalyzer, Map<String, Analyzer> fieldAnalyzers) {
      super(PER_FIELD_REUSE_STRATEGY);
      this.defaultAnalyzer = defaultAnalyzer;
      this.fieldAnalyzers = (fieldAnalyzers != null) ? fieldAnalyzers : Collections.<String, Analyzer>emptyMap();
    }

    @Override
    protected Analyzer getWrappedAnalyzer(String fieldName) {
        Analyzer analyzer = fieldAnalyzers.get(fieldName);
        return (analyzer != null) ? analyzer : defaultAnalyzer;
    }

    @Override
    public String toString() {
        return "PerFieldAnalyzerWrapper(" + fieldAnalyzers + ", default=" + defaultAnalyzer + ")";
    }
}
