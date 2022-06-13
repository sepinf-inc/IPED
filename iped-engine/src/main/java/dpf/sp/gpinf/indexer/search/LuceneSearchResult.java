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

import java.util.Arrays;
import java.util.Comparator;

import org.apache.lucene.search.ScoreDoc;

import iped3.IIPEDSource;
import iped3.search.SearchResult;

/**
 * Classe que armazena os resultados de uma pesquisa. Decompõe ScoreDoc[] para
 * descartar atributo desnecessário e header do objeto, ocupando 1/3 da memória
 * original.
 * 
 * @author Nassif
 *
 */
public class LuceneSearchResult {

    int length = 0;
    int[] docs;
    float[] scores;

    public LuceneSearchResult(int num) {
        this.length = num;
        docs = new int[num];
        scores = new float[num];
    }

    public int[] getLuceneIds() {
        return docs;
    }

    public int getLength() {
        return length;
    }

    public float[] getScores() {
        return scores;
    }

    public static LuceneSearchResult buildSearchResult(int[] docs, float[] scores) {
        LuceneSearchResult s = new LuceneSearchResult(0);
        s.docs = docs;
        s.scores = scores;
        s.length = docs.length;
        return s;
    }

    public static LuceneSearchResult get(IIPEDSource iSource, SearchResult ipedResult) {
        LuceneSearchResult lResult = new LuceneSearchResult(ipedResult.getLength());
        int i = 0;
        for (int id : ipedResult.getIds()) {
            lResult.docs[i] = iSource.getLuceneId(id);
            lResult.scores[i] = ipedResult.getScore(i);
            i++;
        }
        return lResult;
    }

    public SearchResult getSearchResult(IIPEDSource iSource) {
        float[] scores = this.scores;
        int[] ids = new int[this.getLength()];

        int i = 0;
        for (int luceneId : this.docs) {
            ids[i++] = iSource.getId(luceneId);
        }
        return new SearchResult(ids, scores);
    }

    public LuceneSearchResult addResults(ScoreDoc[] scoreDocs) {

        LuceneSearchResult result = new LuceneSearchResult(length + scoreDocs.length);

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

    public void clearResults() {

        int blanks = 0;
        for (int i = 0; i < docs.length; i++)
            if (docs[i] != -1) {
                docs[i - blanks] = docs[i];
                scores[i - blanks] = scores[i];
            } else
                blanks++;

        int[] _docs = new int[docs.length - blanks];
        float[] _scores = new float[scores.length - blanks];

        System.arraycopy(docs, 0, _docs, 0, _docs.length);
        System.arraycopy(scores, 0, _scores, 0, _scores.length);

        docs = _docs;
        scores = _scores;
        length -= blanks;
    }

    public LuceneSearchResult intersect(LuceneSearchResult items) {

        LuceneSearchResult result = this.clone();

        int[] docs2 = items.docs.clone();
        Arrays.sort(docs2);

        for (int i = 0; i < result.length; i++)
            if (Arrays.binarySearch(docs2, result.docs[i]) < 0)
                result.docs[i] = -1;

        result.clearResults();

        return result;
    }

    /**
     * Interseção mais eficiente
     * 
     * @param items
     * @return
     */
    private LuceneSearchResult intersect2(LuceneSearchResult items) {

        LuceneSearchResult result = this.clone();

        int[] docs2 = items.docs.clone();
        Arrays.sort(docs2);

        Integer[] index = new Integer[result.length];
        for (int i = 0; i < index.length; i++)
            index[i] = i;
        Arrays.sort(index, new IndexComparator(result.docs));

        int i = 0, j = 0;
        while (i < result.length) {
            if (j == docs2.length || result.docs[index[i]] < docs2[j])
                result.docs[index[i++]] = -1;
            else if (result.docs[index[i]] > docs2[j])
                j++;
            else {
                i++;
                j++;
            }
        }
        result.clearResults();

        return result;
    }

    public LuceneSearchResult add(LuceneSearchResult items) {
        LuceneSearchResult result = new LuceneSearchResult(this.length + items.length);
        System.arraycopy(docs, 0, result.docs, 0, docs.length);
        System.arraycopy(items.docs, 0, result.docs, docs.length, items.docs.length);
        System.arraycopy(scores, 0, result.scores, 0, scores.length);
        System.arraycopy(items.scores, 0, result.scores, scores.length, items.scores.length);
        return result;
    }

    @Override
    public LuceneSearchResult clone() {
        LuceneSearchResult result = new LuceneSearchResult(0);
        result.docs = this.docs.clone();
        result.scores = this.scores.clone();
        result.length = this.length;
        return result;
    }

    class IndexComparator implements Comparator<Integer> {

        int[] values;

        IndexComparator(int[] values) {
            this.values = values;
        }

        @Override
        public int compare(Integer o1, Integer o2) {
            return values[o1] - values[o2];
        }

    }

}
