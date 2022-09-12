/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package iped.app.ui;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.highlight.Encoder;
import org.apache.lucene.search.highlight.Fragmenter;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.NullFragmenter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.highlight.TextFragment;
import org.apache.lucene.search.highlight.TokenSources;

import iped.viewers.ATextViewer;
import iped.viewers.util.LuceneSimpleHTMLEncoder;

public class TextHighlighter {

    // Highlight dos fragmentos
    public static TextFragment[] getHighlightedFrags(boolean breakOnNewLine, String text, String fieldName,
            int minFragmentSize) throws Exception {

        Query query = App.get().getQuery();
        if (text == null || query == null) {
            return new TextFragment[0];
        }
        TokenStream stream = TokenSources.getTokenStream(fieldName, text, App.get().appCase.getAnalyzer());
        QueryScorer scorer = new QueryScorer(query, fieldName);
        Fragmenter fragmenter;
        SimpleHTMLFormatter formatter = new SimpleHTMLFormatter(ATextViewer.HIGHLIGHT_START_TAG,
                ATextViewer.HIGHLIGHT_END_TAG);
        int maxFragments = 1;
        if (minFragmentSize != 0) {
            fragmenter = new TextFragmenter(minFragmentSize);
            // fragmenter = new SimpleSpanFragmenter(scorer, fragmentSize);
            maxFragments += text.length() / minFragmentSize;
        } else {
            fragmenter = new NullFragmenter();
        }
        Encoder encoder = new LuceneSimpleHTMLEncoder();
        // Encoder encoder = new DefaultEncoder();
        Highlighter highlighter = new Highlighter(formatter, encoder, scorer);
        highlighter.setTextFragmenter(fragmenter);
        highlighter.setMaxDocCharsToAnalyze(Integer.MAX_VALUE);
        return highlighter.getBestTextFragments(stream, text, false, maxFragments);
    }

    private static class TextFragmenter implements Fragmenter {

        private int minFragmentSize;
        private OffsetAttribute offsetAtt;
        private int lastFragEnd = 0;
        private int prevTokenEnd = 0;

        public TextFragmenter(int minFragmentSize) {
            this.minFragmentSize = minFragmentSize;
        }

        @Override
        public void start(String originalText, TokenStream stream) {
            offsetAtt = stream.addAttribute(OffsetAttribute.class);
        }

        @Override
        public boolean isNewFragment() {
            int currTokenEnd = offsetAtt.endOffset();
            boolean isNewFrag = prevTokenEnd - lastFragEnd >= minFragmentSize;
            if (isNewFrag) {
                lastFragEnd = prevTokenEnd;
            }
            prevTokenEnd = currTokenEnd;
            return isNewFrag;
        }

    }

}
