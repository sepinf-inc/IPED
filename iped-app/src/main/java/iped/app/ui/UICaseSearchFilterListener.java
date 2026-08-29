package iped.app.ui;

import java.awt.Dialog.ModalityType;
import java.io.IOException;
import java.util.Set;

import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.app.ui.columns.ColumnsManagerUI;
import iped.engine.search.QueryBuilder;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.utils.LocalizedFormat;
import iped.viewers.util.ProgressDialog;

public class UICaseSearchFilterListener implements CaseSearchFilterListener {

    private static Logger LOGGER = LoggerFactory.getLogger(UICaseSearchFilterListener.class);
    ProgressDialog progressDialog;
    CaseSearcherFilter caseSearcherFilter;

    public UICaseSearchFilterListener(CaseSearcherFilter caseSearcherFilter) {
        this.caseSearcherFilter = caseSearcherFilter;
    }

    @Override
    public void init() {

    }

    @Override
    public void onDone() {
        try {
            try {
                saveHighlightTerms();
            } catch (Exception e) {
                e.printStackTrace();
            }

            App.get().clearAllFilters.setNumberOfFilters(caseSearcherFilter.getNumFilters());

            if (!caseSearcherFilter.isCancelled())
                try {
                    App.get().ipedResult = caseSearcherFilter.get();

                    App.get().resultsTable.getColumnModel().getColumn(0).setHeaderValue(LocalizedFormat.format(caseSearcherFilter.get().getLength()));
                    App.get().resultsTable.getTableHeader().repaint();
                    if (App.get().ipedResult.getLength() < 1 << 24 && App.get().resultsTable.getRowSorter() != null) {
                        App.get().resultsTable.getRowSorter().allRowsChanged();
                        App.get().resultsTable.getRowSorter().setSortKeys(App.get().resultSortKeys);
                        App.get().galleryModel.fireTableDataChanged();
                    } else {
                        App.get().resultsModel.fireTableDataChanged();
                        App.get().galleryModel.fireTableStructureChanged();
                    }
                    App.get().resultsModel.fireTableDataChanged();
                    ColumnsManagerUI.getInstance().updateDinamicCols();
                    new ResultTotalSizeCounter().countVolume(App.get().ipedResult);

                } catch (Exception e) {
                    e.printStackTrace();
                }

            App.get().filtersPanel.updateUI();
        } finally {
            if (progressDialog != null)
                progressDialog.close();
        }
    }

    private void saveHighlightTerms() throws ParseException, QueryNodeException {
        Set<String> highlightTerms = new QueryBuilder(App.get().appCase).getQueryStrings(caseSearcherFilter.getQueryText());
        highlightTerms.addAll(App.get().metadataPanel.getHighlightTerms());

        // Add bookmark query terms to highlight
        String bookmarkQuery = (String) App.get().queryComboBox.getClientProperty(App.ACTIVE_BOOKMARK_QUERY_PROPERTY);
        if (bookmarkQuery != null && !bookmarkQuery.trim().isEmpty()) {
            Set<String> bookmarkTerms = new QueryBuilder(App.get().appCase).getQueryStrings(bookmarkQuery);
            highlightTerms.addAll(bookmarkTerms);
        }

        App.get().setHighlightTerms(highlightTerms);

        // Build the highlight query
        Query highlightQuery = App.get().metadataPanel.getHighlightQuery();
        Query searchQuery = caseSearcherFilter.getSearcher().getQuery();
        
        // Build the combined highlight query
        BooleanQuery.Builder boolQuery = new BooleanQuery.Builder();
        
        if (highlightQuery != null) {
            boolQuery.add(highlightQuery, Occur.SHOULD);
        }
        
        if (searchQuery != null) {
            boolQuery.add(searchQuery, Occur.SHOULD);
        }
        
        // Add bookmark query to highlight
        if (bookmarkQuery != null && !bookmarkQuery.trim().isEmpty()) {
            try {
                Query bmkQuery = new QueryBuilder(App.get().appCase).getQuery(bookmarkQuery);
                boolQuery.add(bmkQuery, Occur.SHOULD);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        highlightQuery = boolQuery.build();

        App.get().setQuery(highlightQuery);
    }

    @Override
    public void onCancel(boolean mayInterruptIfRunning) {
        LOGGER.error(Messages.getString("UISearcher.Canceled")); //$NON-NLS-1$
        caseSearcherFilter.searcher.cancel();
        try {
            App.get().appCase.reopen();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (progressDialog != null)
                progressDialog.close();
        }
    }

    @Override
    public void onStart() {
        progressDialog = new ProgressDialog(App.get(), caseSearcherFilter, true, 0, ModalityType.TOOLKIT_MODAL);
    }

}
