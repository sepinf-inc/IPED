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

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import javax.swing.JOptionPane;

import org.apache.commons.lang.ArrayUtils;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.app.graph.FilterSelectedEdges;
import iped.data.IItemId;
import iped.engine.data.IPEDSource;
import iped.engine.data.ItemId;
import iped.engine.search.IPEDSearcher;
import iped.engine.search.ImageSimilarityLowScoreFilter;
import iped.engine.search.ImageSimilarityScorer;
import iped.engine.search.MultiSearchResult;
import iped.engine.search.QueryBuilder;
import iped.engine.search.SimilarFacesSearch;
import iped.engine.search.SimilarImagesSearch;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.viewers.api.CancelableWorker;
import iped.viewers.api.IQueryFilterer;
import iped.viewers.api.ResultSetViewer;

public class CaseSearcherFilter extends CancelableWorker<MultiSearchResult, Object> {
    private static Logger LOGGER = LoggerFactory.getLogger(CaseSearcherFilter.class);
    ArrayList<CaseSearchFilterListener> listeners = new ArrayList<CaseSearchFilterListener>();

    private static SoftReference<MultiSearchResult> allItemsCache;
    private static IPEDSource ipedCase;

    private ExecutorService threadPool;

    volatile int numFilters = 0;

    String queryText;
    Query query;
    IPEDSearcher searcher;

    public CaseSearcherFilter(String queryText) {
        this.queryText = queryText;
        searcher = new IPEDSearcher(App.get().appCase, queryText);
    }

    public CaseSearcherFilter(Query query) {
        this.query = query;
        searcher = new IPEDSearcher(App.get().appCase, query);
    }

    public void applyUIQueryFilters() {
        applyUIQueryFilters(null);// no exceptions
    }

    public void applyUIQueryFilters(Set<IQueryFilterer> exceptions) {
        try {
            searcher.setQuery(getQueryWithUIFilter(exceptions));

        } catch (ParseException | QueryNodeException e) {
            JOptionPane.showMessageDialog(App.get(), Messages.getString("UISearcher.Error.Msg") + e.getMessage(), //$NON-NLS-1$
                    Messages.getString("UISearcher.Error.Title"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
            // e.printStackTrace();
        }
    }

    public Query getQueryWithUIFilter(Set<IQueryFilterer> exceptions) throws ParseException, QueryNodeException {
        Query result;
        numFilters = 0;
        if (queryText != null) {
            result = new QueryBuilder(App.get().appCase).getQuery(queryText);
            if (!queryText.trim().isEmpty())
                numFilters++;
        } else {
            result = query;
            if (!(query instanceof MatchAllDocsQuery))
                numFilters++;
        }

        if(App.get().queryComboBox.getSelectedItem()!=null) {
            String searchText = App.get().queryComboBox.getSelectedItem().toString();
            if(searchText!=null) {
                if (!(searchText.equals(BookmarksController.HISTORY_DIV) || searchText.equals(App.SEARCH_TOOL_TIP))) {
                    BooleanQuery.Builder boolQuery = new BooleanQuery.Builder();
                    boolQuery.add(new QueryBuilder(App.get().appCase).getQuery(searchText), Occur.MUST);
                    boolQuery.add(result, Occur.MUST);
                    result = boolQuery.build();
                    numFilters++;
                }
            }
        }

        if (App.get().filterComboBox.getSelectedIndex() > 1) {
            String filter = App.get().filterComboBox.getSelectedItem().toString();
            filter = App.get().filterManager.getFilterExpression(filter);
            BooleanQuery.Builder boolQuery = new BooleanQuery.Builder();
            boolQuery.add(new QueryBuilder(App.get().appCase).getQuery(filter), Occur.MUST);
            boolQuery.add(result, Occur.MUST);
            result = boolQuery.build();
            numFilters++;
        }

        if (App.get().categoryListener.getQuery() != null) {
            BooleanQuery.Builder boolQuery = new BooleanQuery.Builder();
            boolQuery.add(App.get().categoryListener.getQuery(), Occur.MUST);
            boolQuery.add(result, Occur.MUST);
            result = boolQuery.build();
            numFilters++;
        }

        Query treeQuery = App.get().treeListener.getQuery();
        if (treeQuery != null) {
            BooleanQuery.Builder boolQuery = new BooleanQuery.Builder();
            boolQuery.add(treeQuery, Occur.MUST);
            boolQuery.add(result, Occur.MUST);
            result = boolQuery.build();
            numFilters++;
        }

        /* loop through all registered result set viewer to get configured query filters */ 
        List<ResultSetViewer> list = App.get().getResultSetViewers();
        for (Iterator<ResultSetViewer> iterator = list.iterator(); iterator.hasNext();) {
            ResultSetViewer resultSetViewer = iterator.next();
            if (resultSetViewer instanceof IQueryFilterer) {
                if (exceptions == null || !exceptions.contains(resultSetViewer)) {
                    Query resultSetViewerQuery = ((IQueryFilterer) resultSetViewer).getQuery();
                    if (resultSetViewerQuery != null) {
                        BooleanQuery.Builder boolQuery = new BooleanQuery.Builder();
                        boolQuery.add(resultSetViewerQuery, Occur.MUST);
                        boolQuery.add(result, Occur.MUST);
                        result = boolQuery.build();
                        numFilters++;
                    }
                }
            }
        }

        if (App.get().similarImagesQueryRefItem != null) {
            Query similarImagesQuery = new SimilarImagesSearch()
                    .getQueryForSimilarImages(App.get().similarImagesQueryRefItem);
            if (similarImagesQuery != null) {
                BooleanQuery.Builder boolQuery = new BooleanQuery.Builder();
                boolQuery.add(result, Occur.MUST);
                boolQuery.add(similarImagesQuery, Occur.MUST);
                result = boolQuery.build();
                searcher.setNoScoring(true);
                numFilters++;
            }
        }

        return result;
    }

    MultiSearchResult result = null;

    public MultiSearchResult getDoneResult() {
        return this.result;
    }

    @Override
    public MultiSearchResult doInBackground() {

        synchronized (this.getClass()) {

            if (this.isCancelled())
                return null;

            try {
                for (CaseSearchFilterListener caseSearchFilterListener : listeners) {
                    caseSearchFilterListener.onStart();
                }

                if (ipedCase == null || ipedCase != App.get().appCase) {
                    allItemsCache = null;
                    ipedCase = App.get().appCase;
                }

                Query q = searcher.getQuery();
                //LOGGER.info("Searching for query " + (q != null ? q.toString() : queryText)); //$NON-NLS-1$

                if (q instanceof MatchAllDocsQuery && allItemsCache != null)
                    result = allItemsCache.get();

                if (result == null) {
                    result = searcher.multiSearch();
                    if (q instanceof MatchAllDocsQuery && (allItemsCache == null || allItemsCache.get() == null))
                        allItemsCache = new SoftReference(result.clone());
                }

                String filtro = ""; //$NON-NLS-1$
                if (App.get().filterComboBox.getSelectedItem() != null)
                    filtro = App.get().filterComboBox.getSelectedItem().toString();

                if (filtro.equals(App.FILTRO_SELECTED)) {
                    result = (MultiSearchResult) App.get().appCase.getMultiBookmarks().filterChecked(result);
                    numFilters++;
                    //LOGGER.info("Filtering for selected items."); //$NON-NLS-1$
                }

                Set<String> bookmarkSelection = App.get().bookmarksListener.getSelectedBookmarkNames();
                if ((!bookmarkSelection.isEmpty() || App.get().bookmarksListener.isNoBookmarksSelected())
                        && !App.get().bookmarksListener.isRootSelected()) {
                    numFilters++;
                    StringBuilder bookmarks = new StringBuilder();
                    for (String bookmark : bookmarkSelection)
                        bookmarks.append("\"" + bookmark + "\" "); //$NON-NLS-1$ //$NON-NLS-2$
                    //LOGGER.info("Filtering for bookmarks " + bookmarks.toString()); //$NON-NLS-1$

                    if (App.get().bookmarksListener.isNoBookmarksSelected()) {
                        if (bookmarkSelection.isEmpty()) {
                            result = (MultiSearchResult) App.get().appCase.getMultiBookmarks()
                                    .filterNoBookmarks(result);
                        } else {
                            result = (MultiSearchResult) App.get().appCase.getMultiBookmarks()
                                    .filterBookmarksOrNoBookmarks(result, bookmarkSelection);
                        }
                    } else
                        result = (MultiSearchResult) App.get().appCase.getMultiBookmarks().filterBookmarks(result,
                                bookmarkSelection);

                }

                Set<IItemId> selectedEdges = FilterSelectedEdges.getInstance().getItemIdsOfSelectedEdges();
                if (selectedEdges != null && !selectedEdges.isEmpty()) {
                    numFilters++;
                    ArrayList<IItemId> filteredItems = new ArrayList<IItemId>();
                    ArrayList<Float> scores = new ArrayList<Float>();
                    int i = 0;
                    for (IItemId item : result.getIterator()) {
                        if (selectedEdges.contains(item)) {
                            filteredItems.add(item);
                            scores.add(result.getScore(i));
                        }
                        i++;
                    }
                    result = new MultiSearchResult(filteredItems.toArray(new ItemId[0]),
                            ArrayUtils.toPrimitive(scores.toArray(new Float[0])));
                }

                if (App.get().filterDuplicates.isSelected()) {
                    DynamicDuplicateFilter duplicateFilter = new DynamicDuplicateFilter(App.get().appCase);
                    result = duplicateFilter.filter(result);
                    numFilters++;
                }

                if (App.get().similarImagesQueryRefItem != null) {
                    //LOGGER.info("Starting similar image search...");
                    long t = System.currentTimeMillis();
                    new ImageSimilarityScorer(App.get().appCase, result, App.get().similarImagesQueryRefItem).score();
                    result = ImageSimilarityLowScoreFilter.filter(result);
                    t = System.currentTimeMillis() - t;
                    //LOGGER.info("Similar image search took {}ms to find {} images", t, result.getLength());
                }

                if (App.get().similarFacesRefItem != null) {
                    //LOGGER.info("Starting similar face search...");
                    long t = System.currentTimeMillis();
                    SimilarFacesSearch sfs = new SimilarFacesSearch(App.get().appCase, App.get().similarFacesRefItem);
                    result = sfs.filter(result);
                    numFilters++;
                    t = System.currentTimeMillis() - t;
                    //LOGGER.info("Similar face search took {}ms to find {} faces", t, result.getLength());
                }

                if (App.get().metadataPanel.isFiltering()) {
                    long t = System.currentTimeMillis();
                    result = App.get().metadataPanel.getFilteredItemIds(result);
                    numFilters++;
                    //LOGGER.info("Metadata panel filtering took {}ms", (System.currentTimeMillis() - t));
                }


            } catch (Throwable e) {
                e.printStackTrace();
                return new MultiSearchResult(new ItemId[0], new float[0]);

            }

            for (CaseSearchFilterListener caseSearchFilterListener : listeners) {
                if (isCancelled()) {
                    break;
                }
                caseSearchFilterListener.onDone();
            }
            result.setIpedSearcher(searcher);
            result.setIPEDSource(ipedCase);

            return result;
        }

    }

    @Override
    public void done() {
    }

    @Override
    public boolean doCancel(boolean mayInterruptIfRunning) {
        searcher.cancel();

        for (CaseSearchFilterListener caseSearchFilterListener : listeners) {
            caseSearchFilterListener.onCancel(mayInterruptIfRunning);
        }

        return cancel(mayInterruptIfRunning);
    }

    public int getNumFilters() {
        return numFilters;
    }

    public void setNumFilters(int numFilters) {
        this.numFilters = numFilters;
    }

    public IPEDSearcher getSearcher() {
        return searcher;
    }

    public void setSearcher(IPEDSearcher searcher) {
        this.searcher = searcher;
    }

    public String getQueryText() {
        return queryText;
    }

    public void setQueryText(String queryText) {
        this.queryText = queryText;
    }

    public void addCaseSearchFilterListener(CaseSearchFilterListener csfl) {
        listeners.add(csfl);
    }

    public ExecutorService getThreadPool() {
        return threadPool;
    }

    public void setThreadPool(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }
}
