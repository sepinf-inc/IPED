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

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.process.IndexItem;
import iped3.IItemId;
import iped3.exception.ParseException;
import iped3.exception.QueryNodeException;
import iped3.search.IIPEDSearcher;
import iped3.search.LuceneSearchResult;
import iped3.search.SearchResult;

public class IPEDSearcher implements IIPEDSearcher {

    private static Logger LOGGER = LoggerFactory.getLogger(IPEDSearcher.class);

    public static final int MAX_SIZE_TO_SCORE = 1000000;

    IPEDSource ipedCase;
    Query query;
    boolean treeQuery, noScore;
    NoScoringCollector collector;

    private volatile boolean canceled;

    public IPEDSearcher(IPEDSource ipedCase) {
        this.ipedCase = ipedCase;
    }

    public IPEDSearcher(IPEDSource ipedCase, Query query) {
        this.ipedCase = ipedCase;
        this.query = query;
    }

    public IPEDSearcher(IPEDSource ipedCase, String query) {
        this.ipedCase = ipedCase;
        setQuery(query);
    }

    public void setTreeQuery(boolean treeQuery) {
        this.treeQuery = treeQuery;
    }

    public void setNoScoring(boolean noScore) {
        this.noScore = noScore;
    }

    public void setQuery(Query query) {
        this.query = query;
    }

    public void setQuery(String queryText) {
        try {
            query = new QueryBuilder(ipedCase).getQuery(queryText);

        } catch (ParseException | QueryNodeException e) {
            throw new RuntimeException(e);
        }
    }

    public Query getQuery() {
        return query;
    }

    public void cancel() {
        canceled = true;
        if (collector != null)
            collector.cancel();
    }

    public SearchResult search() throws IOException {
        if (ipedCase instanceof IPEDMultiSource)
            throw new UnsupportedOperationException("Use multiSearch() method for IPEDMultiSource!"); //$NON-NLS-1$

        return SearchResult.get(ipedCase, luceneSearch());
    }

    public MultiSearchResult multiSearch() throws IOException {
        if (!(ipedCase instanceof IPEDMultiSource))
            throw new UnsupportedOperationException("Use search() method for only one IPEDSource!"); //$NON-NLS-1$

        return MultiSearchResult.get((IPEDMultiSource) ipedCase, luceneSearch());
    }

    public LuceneSearchResult luceneSearch() throws IOException {
        return filterFragmentedResults(searchAll());
    }

    private LuceneSearchResult searchAll() throws IOException {

        // System.out.println("searching");

        if (!treeQuery)
            query = getNonTreeQuery();

        collector = new NoScoringCollector(ipedCase.getReader().maxDoc());
        try {
            ipedCase.getSearcher().search(query, collector);

        } catch (InterruptedIOException e) {
            // e.printStackTrace();
        }
        // do not compute scores (slow) when result set is large
        if (noScore || collector.getTotalHits() > MAX_SIZE_TO_SCORE || canceled)
            return collector.getSearchResults();

        // otherwise get results computing score

        // sort by index doc order: needed by features using docValues that iterate over results
        Sort sort = new Sort(SortField.FIELD_DOC);
        int maxResults = MAX_SIZE_TO_SCORE;
        ScoreDoc[] scoreDocs = null;
        ScoreDoc[] totalScoreDocs = null;
        do {
            ScoreDoc lastScoreDoc = null;
            if (scoreDocs != null)
                lastScoreDoc = scoreDocs[scoreDocs.length - 1];

            scoreDocs = ipedCase.getSearcher().searchAfter(lastScoreDoc, query, maxResults, sort, true).scoreDocs;

            if (totalScoreDocs == null) {
                totalScoreDocs = scoreDocs;
            } else {
                int prevLen = totalScoreDocs.length;
                totalScoreDocs = Arrays.copyOf(totalScoreDocs, prevLen + scoreDocs.length);
                System.arraycopy(scoreDocs, 0, totalScoreDocs, prevLen, scoreDocs.length);
            }

        } while (scoreDocs.length > 0 && !canceled);

        // see #925 why this sorting is needed, this can be optimized
        sortResultsByFinalLuceneIds(totalScoreDocs);

        LuceneSearchResult searchResult = new LuceneSearchResult(0);
        searchResult = searchResult.addResults(totalScoreDocs);
        return searchResult;
    }

    private void sortResultsByFinalLuceneIds(ScoreDoc[] totalScoreDocs) {
        if (ipedCase instanceof IPEDMultiSource) {
            Arrays.parallelSort(totalScoreDocs, new Comparator<ScoreDoc>() {
                public int compare(ScoreDoc doc1, ScoreDoc doc2) {
                    return ipedCase.getLuceneId(((IPEDMultiSource) ipedCase).getItemId(doc1.doc))
                            - ipedCase.getLuceneId(((IPEDMultiSource) ipedCase).getItemId(doc2.doc));
                }
            });
        } else {
            Arrays.parallelSort(totalScoreDocs, new Comparator<ScoreDoc>() {
                public int compare(ScoreDoc doc1, ScoreDoc doc2) {
                    return ipedCase.getLuceneId(ipedCase.getId(doc1.doc))
                            - ipedCase.getLuceneId(ipedCase.getId(doc2.doc));
                }
            });
        }
    }

    private Query getNonTreeQuery() {
        BooleanQuery.Builder result = new BooleanQuery.Builder();
        result.add(query, Occur.MUST);
        result.add(new TermQuery(new Term(IndexItem.TREENODE, "true")), Occur.MUST_NOT); //$NON-NLS-1$
        return result.build();
    }

    private LuceneSearchResult filterFragmentedResults(LuceneSearchResult prevResult) {

        // System.out.println("fragments");

        if (ipedCase instanceof IPEDMultiSource)
            return filterFragmentedResultsMulti((IPEDMultiSource) ipedCase, prevResult);

        HashSet<Integer> duplicates = new HashSet<Integer>();
        int[] docs = prevResult.getLuceneIds();
        for (int i = 0; i < prevResult.getLength(); i++) {
            int id = ipedCase.getId(docs[i]);
            if (ipedCase.isSplited(id)) {
                if (!duplicates.contains(id)) {
                    duplicates.add(id);
                } else {
                    docs[i] = -1;
                }
            }
        }

        prevResult.clearResults();
        return prevResult;

    }

    private LuceneSearchResult filterFragmentedResultsMulti(IPEDMultiSource ipedCase, LuceneSearchResult prevResult) {
        HashMap<Integer, HashSet<Integer>> duplicates = new HashMap<Integer, HashSet<Integer>>();
        int[] docs = prevResult.getLuceneIds();
        if (prevResult.getLength() <= MAX_SIZE_TO_SCORE) {

            for (int i = 0; i < prevResult.getLength(); i++) {
                IItemId item = ipedCase.getItemId(docs[i]);
                IPEDSource atomicSource = (IPEDSource) ipedCase.getAtomicSourceBySourceId(item.getSourceId());
                int id = item.getId();
                if (atomicSource.isSplited(id)) {
                    HashSet<Integer> dups = duplicates.get(atomicSource.getSourceId());
                    if (dups == null) {
                        dups = new HashSet<Integer>();
                        duplicates.put(atomicSource.getSourceId(), dups);
                    }
                    if (!dups.contains(id)) {
                        dups.add(id);
                    } else {
                        docs[i] = -1;
                    }
                }
            }

            // Otimização: considera que itens estão em ordem crescente do LuceneId (qdo não
            // usa scores)
        } else {
            IPEDSource atomicSource = null;
            int baseDoc = 0;
            int maxdoc = 0;
            for (int i = 0; i < docs.length; i++) {
                if (atomicSource == null || docs[i] >= baseDoc + maxdoc) {
                    atomicSource = (IPEDSource) ipedCase.getAtomicSource(docs[i]);
                    baseDoc = ipedCase.getBaseLuceneId(atomicSource);
                    maxdoc = atomicSource.getReader().maxDoc();
                }
                int id = atomicSource.getId(docs[i] - baseDoc);
                if (atomicSource.isSplited(id)) {
                    HashSet<Integer> dups = duplicates.get(atomicSource.getSourceId());
                    if (dups == null) {
                        dups = new HashSet<Integer>();
                        duplicates.put(atomicSource.getSourceId(), dups);
                    }
                    if (!dups.contains(id)) {
                        dups.add(id);
                    } else {
                        docs[i] = -1;
                    }
                }
            }
        }

        prevResult.clearResults();
        return prevResult;

    }

}
