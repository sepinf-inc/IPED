/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iped3.search;

import org.apache.lucene.search.ScoreDoc;

/**
 *
 * @author WERNECK
 */
public interface ILuceneResult {

    LuceneSearchResult add(LuceneSearchResult items);

    LuceneSearchResult addResults(ScoreDoc[] scoreDocs);

    void clearResults();

    LuceneSearchResult clone();

    int getLength();

    int[] getLuceneIds();

    float[] getScores();

    LuceneSearchResult intersect(LuceneSearchResult items);

    /**
     * Interseção mais eficiente
     *
     * @param items
     * @return
     */
    LuceneSearchResult intersect2(LuceneSearchResult items);

}
