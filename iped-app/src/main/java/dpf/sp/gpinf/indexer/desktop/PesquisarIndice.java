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
package dpf.sp.gpinf.indexer.desktop;

import java.awt.Dialog.ModalityType;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JOptionPane;

import org.apache.commons.lang.ArrayUtils;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.gov.pf.labld.graph.desktop.FilterSelectedEdges;
import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.search.ImageSimilarityLowScoreFilter;
import dpf.sp.gpinf.indexer.search.ItemId;
import dpf.sp.gpinf.indexer.search.MultiSearchResult;
import dpf.sp.gpinf.indexer.search.QueryBuilder;
import dpf.sp.gpinf.indexer.search.SimilarFacesSearch;
import dpf.sp.gpinf.indexer.search.SimilarImagesSearch;
import iped3.IItemId;
import iped3.desktop.CancelableWorker;
import iped3.desktop.ProgressDialog;
import iped3.exception.ParseException;
import iped3.exception.QueryNodeException;
import iped3.search.LuceneSearchResult;

public class PesquisarIndice extends CancelableWorker<MultiSearchResult, Object> {

    private static Logger LOGGER = LoggerFactory.getLogger(PesquisarIndice.class);

    private static SoftReference<MultiSearchResult> allItemsCache;
    private static IPEDSource ipedCase;

    volatile int numFilters = 0;
    ProgressDialog progressDialog;

    String queryText;
    Query query;
    IPEDSearcher searcher;

    public PesquisarIndice(String queryText) {
        this.queryText = queryText;
        searcher = new IPEDSearcher(App.get().appCase, queryText);
    }

    public PesquisarIndice(Query query) {
        this.query = query;
        searcher = new IPEDSearcher(App.get().appCase, query);
    }

    public void applyUIQueryFilters() {
        try {
            searcher.setQuery(getQueryWithUIFilter());

        } catch (ParseException | QueryNodeException e) {
            JOptionPane.showMessageDialog(App.get(), Messages.getString("UISearcher.Error.Msg") + e.getMessage(), //$NON-NLS-1$
                    Messages.getString("UISearcher.Error.Title"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
            // e.printStackTrace();
        }
    }

    public LuceneSearchResult pesquisar() throws Exception {
        return searcher.luceneSearch();
    }

    private Query getQueryWithUIFilter() throws ParseException, QueryNodeException {

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

        if (App.get().filtro.getSelectedIndex() > 1) {
            String filter = App.get().filtro.getSelectedItem().toString();
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

        if (!App.get().appCase.isFTKReport()) {
            Query treeQuery = App.get().treeListener.getQuery();
            if (treeQuery != null) {
                BooleanQuery.Builder boolQuery = new BooleanQuery.Builder();
                boolQuery.add(treeQuery, Occur.MUST);
                boolQuery.add(result, Occur.MUST);
                result = boolQuery.build();
                numFilters++;
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

    @Override
    public MultiSearchResult doInBackground() {

        synchronized (this.getClass()) {

            if (this.isCancelled())
                return null;

            MultiSearchResult result = null;
            try {
                progressDialog = new ProgressDialog(App.get(), this, true, 0, ModalityType.TOOLKIT_MODAL);

                if (ipedCase == null || ipedCase != App.get().appCase) {
                    allItemsCache = null;
                    ipedCase = App.get().appCase;
                }

                Query q = searcher.getQuery();
                LOGGER.info("Searching for query " + (q != null ? q.toString() : queryText)); //$NON-NLS-1$

                if (q instanceof MatchAllDocsQuery && allItemsCache != null)
                    result = allItemsCache.get();

                if (result == null) {
                    result = searcher.multiSearch();
                    if (q instanceof MatchAllDocsQuery && (allItemsCache == null || allItemsCache.get() == null))
                        allItemsCache = new SoftReference(result.clone());
                }

                String filtro = ""; //$NON-NLS-1$
                if (App.get().filtro.getSelectedItem() != null)
                    filtro = App.get().filtro.getSelectedItem().toString();

                if (filtro.equals(App.FILTRO_SELECTED)) {
                    result = (MultiSearchResult) App.get().appCase.getMultiMarcadores().filtrarSelecionados(result);
                    numFilters++;
                    LOGGER.info("Filtering for selected items."); //$NON-NLS-1$
                }

                HashSet<String> bookmarkSelection = (HashSet<String>) App.get().bookmarksListener.selection.clone();
                if (!bookmarkSelection.isEmpty() && !bookmarkSelection.contains(BookmarksTreeModel.ROOT)) {
                    numFilters++;
                    StringBuilder bookmarks = new StringBuilder();
                    for (String bookmark : bookmarkSelection)
                        bookmarks.append("\"" + bookmark + "\" "); //$NON-NLS-1$ //$NON-NLS-2$
                    LOGGER.info("Filtering for bookmarks " + bookmarks.toString()); //$NON-NLS-1$

                    if (bookmarkSelection.contains(BookmarksTreeModel.NO_BOOKMARKS)) {
                        if (bookmarkSelection.size() == 1)
                            result = (MultiSearchResult) App.get().appCase.getMultiMarcadores()
                                    .filtrarSemMarcadores(result);
                        else {
                            bookmarkSelection.remove(BookmarksTreeModel.NO_BOOKMARKS);
                            result = (MultiSearchResult) App.get().appCase.getMultiMarcadores()
                                    .filtrarSemEComMarcadores(result, bookmarkSelection);
                        }
                    } else
                        result = (MultiSearchResult) App.get().appCase.getMultiMarcadores().filtrarMarcadores(result,
                                bookmarkSelection);

                }

                Set<IItemId> itemsWithValuesSelected = App.get().metadataPanel.getFilteredItemIds();
                if (itemsWithValuesSelected != null) {
                    numFilters++;
                    ArrayList<IItemId> filteredItems = new ArrayList<IItemId>();
                    ArrayList<Float> scores = new ArrayList<Float>();
                    int i = 0;
                    for (IItemId item : result.getIterator()) {
                        if (itemsWithValuesSelected.contains(item)) {
                            filteredItems.add(item);
                            scores.add(result.getScore(i));
                        }
                        i++;
                    }
                    result = new MultiSearchResult(filteredItems.toArray(new ItemId[0]),
                            ArrayUtils.toPrimitive(scores.toArray(new Float[0])));
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

                if (App.get().similarImagesQueryRefItem != null) {
                    new ImageSimilarityScorer(result, App.get().similarImagesQueryRefItem).score();
                    result = ImageSimilarityLowScoreFilter.filter(result);
                }

                if (App.get().filterDuplicates.isSelected()) {
                    DynamicDuplicateFilter duplicateFilter = new DynamicDuplicateFilter(App.get().appCase);
                    result = duplicateFilter.filter(result);
                    numFilters++;
                }

                if (App.get().similarFacesRefItem != null) {
                    SimilarFacesSearch sfs = new SimilarFacesSearch(App.get().appCase, App.get().similarFacesRefItem);
                    result = sfs.filter(result);
                }
                
                if(App.get().timelineTableView) {
                    long t = System.currentTimeMillis();
                    result = new TimelineResults().expandTimestamps(result);
                    LOGGER.info("Toggle table timeline took {}ms" + (System.currentTimeMillis() - t));
                }

                saveHighlightTerms();

            } catch (Throwable e) {
                e.printStackTrace();
                return new MultiSearchResult(new ItemId[0], new float[0]);

            }

            return result;
        }

    }

    private void saveHighlightTerms() throws ParseException, QueryNodeException {
        Set<String> highlightTerms = new QueryBuilder(App.get().appCase).getQueryStrings(queryText);
        highlightTerms.addAll(App.get().metadataPanel.getHighlightTerms());
        // for(String str : highlightTerms)
        // System.out.println("highlightTerm: " + str);
        App.get().getSearchParams().highlightTerms = highlightTerms;

        Query highlightQuery = App.get().metadataPanel.getHighlightQuery();
        if (highlightQuery != null) {
            BooleanQuery.Builder boolQuery = new BooleanQuery.Builder();
            boolQuery.add(highlightQuery, Occur.SHOULD);
            boolQuery.add(searcher.getQuery(), Occur.SHOULD);
            highlightQuery = boolQuery.build();
        } else
            highlightQuery = searcher.getQuery();

        App.get().setQuery(highlightQuery);
    }

    @Override
    public void done() {

        App.get().clearAllFilters.setNumberOfFilters(numFilters);

        if (!this.isCancelled())
            try {
                App.get().ipedResult = this.get();

                App.get().resultsTable.getColumnModel().getColumn(0).setHeaderValue(this.get().getLength());
                App.get().resultsTable.getTableHeader().repaint();
                if (App.get().ipedResult.getLength() < 1 << 24 && App.get().resultsTable.getRowSorter() != null) {
                    App.get().resultsTable.getRowSorter().allRowsChanged();
                    App.get().resultsTable.getRowSorter().setSortKeys(App.get().resultSortKeys);
                } else {
                    App.get().resultsModel.fireTableDataChanged();
                    App.get().galleryModel.fireTableStructureChanged();
                }
                ColumnsManager.getInstance().updateDinamicCols();
                new ResultTotalSizeCounter().countVolume(App.get().ipedResult);

            } catch (Exception e) {
                e.printStackTrace();
            }
        if (progressDialog != null)
            progressDialog.close();

    }

    @Override
    public boolean doCancel(boolean mayInterruptIfRunning) {

        LOGGER.error(Messages.getString("UISearcher.Canceled")); //$NON-NLS-1$
        searcher.cancel();
        try {
            App.get().appCase.reopen();

        } catch (IOException e) {
            e.printStackTrace();
        }

        return cancel(mayInterruptIfRunning);
    }

}
