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

import org.apache.lucene.search.ScoreDoc;

public class SearchResult {

	public int length = 0;
	public int[] docs;
	public float[] scores;

	public SearchResult(int num) {
		this.length = num;
		docs = new int[num];
		scores = new float[num];
	}

	public SearchResult addResults(ScoreDoc[] scoreDocs) {

		SearchResult result = new SearchResult(length + scoreDocs.length);

		for (int i = 0; i < result.length; i++) {
			if (i < this.length) {
				result.docs[i] = this.docs[i];
				result.scores[i] = this.scores[i];
			} else {
				result.docs[i] = scoreDocs[i - this.length].doc;
				result.scores[i] = scoreDocs[i - this.length].score;
			}
		}

		return result;

	}

}
