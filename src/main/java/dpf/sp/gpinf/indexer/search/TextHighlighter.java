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
package dpf.sp.gpinf.indexer.search;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.search.highlight.Encoder;
import org.apache.lucene.search.highlight.Fragmenter;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.NullFragmenter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleFragmenter;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.highlight.TextFragment;
import org.apache.lucene.search.highlight.TokenSources;

import dpf.sp.gpinf.indexer.util.SimpleHTMLEncoder;

public class TextHighlighter {

	// Highlight dos fragmentos
	public static TextFragment[] getHighlightedFrags(boolean breakOnNewLine, String text, String fieldName, int fragmentSize) throws Exception {

		if (text == null)
			return new TextFragment[0];
		// App.get().analyzer = new StandardASCIIAnalyzer(Versao.current);
		TokenStream stream = TokenSources.getTokenStream(fieldName, text, App.get().analyzer);
		QueryScorer scorer = new QueryScorer(App.get().query, fieldName);
		Fragmenter fragmenter;
		SimpleHTMLFormatter formatter = new SimpleHTMLFormatter(App.HIGHLIGHT_START_TAG, App.HIGHLIGHT_END_TAG);
		int fragmentNumber = 1;
		if (fragmentSize != 0) {
			fragmenter = new SimpleFragmenter(fragmentSize);
			fragmentNumber += text.length() / fragmentSize;
		} else
			fragmenter = new NullFragmenter();
		Encoder encoder = new SimpleHTMLEncoder();
		// Encoder encoder = new DefaultEncoder();
		Highlighter highlighter = new Highlighter(formatter, encoder, scorer);
		highlighter.setTextFragmenter(fragmenter);
		highlighter.setMaxDocCharsToAnalyze(Integer.MAX_VALUE);
		return highlighter.getBestTextFragments(stream, text, false, fragmentNumber);
	}

}
