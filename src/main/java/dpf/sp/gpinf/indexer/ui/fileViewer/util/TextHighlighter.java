package dpf.sp.gpinf.indexer.ui.fileViewer.util;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.highlight.Encoder;
import org.apache.lucene.search.highlight.Fragmenter;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.NullFragmenter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleFragmenter;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.highlight.TextFragment;
import org.apache.lucene.search.highlight.TokenSources;
import org.apache.lucene.search.Query;

import dpf.sp.gpinf.indexer.util.LuceneSimpleHTMLEncoder;

public class TextHighlighter {

  public static TextFragment[] getHighlightedFrags(AppSearchParams params,
      boolean breakOnNewLine,
      String text,
      String fieldName) throws Exception {

    if (text == null) {
      return new TextFragment[0];
    }
    TokenStream stream = TokenSources.getTokenStream(fieldName, text, params.analyzer);
    QueryScorer scorer = new QueryScorer(params.query, fieldName);
    Fragmenter fragmenter;
    SimpleHTMLFormatter formatter = new SimpleHTMLFormatter(params.HIGHLIGHT_START_TAG, params.HIGHLIGHT_END_TAG);
    int fragmentNumber = 1;
    if (params.FRAG_SIZE != 0) {
      fragmenter = new SimpleFragmenter(params.FRAG_SIZE);
      fragmentNumber += text.length() / params.FRAG_SIZE;
    } else {
      fragmenter = new NullFragmenter();
    }
    Encoder encoder = new LuceneSimpleHTMLEncoder();
    Highlighter highlighter = new Highlighter(formatter, encoder, scorer);
    highlighter.setTextFragmenter(fragmenter);
    highlighter.setMaxDocCharsToAnalyze(Integer.MAX_VALUE);
    return highlighter.getBestTextFragments(stream, text, false, fragmentNumber);
  }

}
