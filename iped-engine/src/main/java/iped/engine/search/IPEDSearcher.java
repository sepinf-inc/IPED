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
package iped.engine.search;

import java.io.IOException;
import java.io.InterruptedIOException;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;

import iped.engine.data.IPEDMultiSource;
import iped.engine.data.IPEDSource;
import iped.engine.lucene.NoScoringCollector;
import iped.engine.task.index.IndexItem;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.search.IIPEDSearcher;
import iped.search.SearchResult;

public class IPEDSearcher implements IIPEDSearcher {

    public static final int MAX_SIZE_TO_SCORE = 1000000;

    IPEDSource ipedCase;
    Query query;
    boolean treeQuery, noScore, rewriteQuery = true;
    NoScoringCollector collector;
    Sort sort;

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

    public IPEDSearcher(IPEDSource ipedCase, Query query, String... sort) {
        this.ipedCase = ipedCase;
        this.query = query;
        setSorting(sort);
    }

    public IPEDSearcher(IPEDSource ipedCase, String query, String... sort) {
        this.ipedCase = ipedCase;
        setQuery(query);
        setSorting(sort);
    }

    // TODO improve this to handle other field types
    private void setSorting(String... sort) {
        SortField[] fields = new SortField[sort.length];
        for (int i = 0; i < fields.length; i++) {
            fields[i] = new SortField(sort[i], SortField.Type.STRING);
        }
        this.sort = new Sort(fields);
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

    public void setRewritequery(boolean rewriteQuery) {
        this.rewriteQuery = rewriteQuery;
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

        return luceneSearch().getSearchResult(ipedCase);
    }

    public MultiSearchResult multiSearch() throws IOException {
        if (!(ipedCase instanceof IPEDMultiSource))
            throw new UnsupportedOperationException("Use search() method for only one IPEDSource!"); //$NON-NLS-1$

        return MultiSearchResult.get((IPEDMultiSource) ipedCase, luceneSearch());
    }

    public LuceneSearchResult luceneSearch() throws IOException {
        return searchAll();
    }

    private LuceneSearchResult searchAll() throws IOException {

        // System.out.println("searching");

        Query query = this.query;
        if (query instanceof MatchAllDocsQuery) {
            query = QueryBuilder.getMatchAllItemsQuery();
        } else if (rewriteQuery) {
            query = new QueryBuilder(ipedCase, true).rewriteQuery(query);
        }
        if (!treeQuery) {
            query = getNonTreeQuery(query);
        }

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
        LuceneSearchResult searchResult = new LuceneSearchResult(0);

        // sort by index doc order: needed by features using docValues that iterate over results
        Sort sort = null;
        if (this.sort != null) {
            sort = this.sort;
        } else {
            sort = new Sort(SortField.FIELD_DOC);
        }
        
        int maxResults = MAX_SIZE_TO_SCORE;
        ScoreDoc[] scoreDocs = null;
        do {
            ScoreDoc lastScoreDoc = null;
            if (scoreDocs != null)
                lastScoreDoc = scoreDocs[scoreDocs.length - 1];

            scoreDocs = ipedCase.getSearcher().searchAfter(lastScoreDoc, query, maxResults, sort, true).scoreDocs;

            searchResult = searchResult.addResults(scoreDocs);

        } while (scoreDocs.length > 0 && !canceled);

        return searchResult;
    }
    
    public boolean hasDocId(int docId) {
        if (collector != null) {
            return collector.bits.get(docId);
        }
        return true;
    }

    private Query getNonTreeQuery(Query query) {
        BooleanQuery.Builder result = new BooleanQuery.Builder();
        result.add(query, Occur.MUST);
        result.add(new TermQuery(new Term(IndexItem.TREENODE, "true")), Occur.MUST_NOT); //$NON-NLS-1$
        return result.build();
    }

}
