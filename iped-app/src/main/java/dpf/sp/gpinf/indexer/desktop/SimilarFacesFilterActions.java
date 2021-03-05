package dpf.sp.gpinf.indexer.desktop;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.RowSorter;
import javax.swing.RowSorter.SortKey;
import javax.swing.SortOrder;

import dpf.sp.gpinf.indexer.search.SimilarFacesSearch;
import iped3.IItemId;

public class SimilarFacesFilterActions {

    public static void clear() {
        clear(true);
    }

    public static void clear(boolean updateResults) {
        App app = App.get();
        if (app.similarFacesRefItem != null) {
            app.similarFacesRefItem = null;
            app.similarFacesFilterPanel.setVisible(false);
            List<? extends SortKey> sortKeys = app.resultsTable.getRowSorter().getSortKeys();
            if (sortKeys != null && !sortKeys.isEmpty() && sortKeys.get(0).getColumn() == 2
                    && app.similarFacesPrevSortKeys != null)
                ((ResultTableRowSorter) app.resultsTable.getRowSorter())
                        .setSortKeysSuper(app.similarFacesPrevSortKeys);
            app.similarFacesPrevSortKeys = null;
            if (updateResults)
                app.appletListener.updateFileListing();
        }
    }

    public static void searchSimilarImages(boolean external) {
        App app = App.get();
        
        int minScore = 0;
        while (minScore == 0) {
            try {
                String input = JOptionPane.showInputDialog(app, "Define minimum similarity (1-100):",
                        SimilarFacesSearch.getMinScore());
                minScore = Integer.parseInt(input.trim());
                if (minScore < 1 || minScore > 100) {
                    minScore = 0;
                    continue;
                }
                SimilarFacesSearch.setMinScore(minScore);

            } catch (NumberFormatException e) {
            }
        }

        if (external) {
            // TODO
        } else {
            app.similarFacesRefItem = null;
            int selIdx = app.resultsTable.getSelectedRow();
            if (selIdx != -1) {
                IItemId itemId = app.ipedResult.getItem(app.resultsTable.convertRowIndexToModel(selIdx));
                if (itemId != null) {
                    app.similarFacesRefItem = app.appCase.getItemByItemId(itemId);
                    if (app.similarFacesRefItem.getExtraAttribute(SimilarFacesSearch.FACE_FEATURES) == null) {
                        app.similarFacesRefItem = null;
                    }
                }
            }
        }

        if (app.similarFacesRefItem != null) {
            List<? extends SortKey> sortKeys = app.resultsTable.getRowSorter().getSortKeys();
            if (sortKeys == null || sortKeys.isEmpty() || sortKeys.get(0).getColumn() != 2) {
                app.similarFacesPrevSortKeys = sortKeys;
                ArrayList<RowSorter.SortKey> sortScore = new ArrayList<RowSorter.SortKey>();
                sortScore.add(new RowSorter.SortKey(2, SortOrder.DESCENDING));
                ((ResultTableRowSorter) app.resultsTable.getRowSorter()).setSortKeysSuper(sortScore);
            }
            app.appletListener.updateFileListing();
        }
        app.similarFacesFilterPanel.setCurrentItem(app.similarFacesRefItem, external);
        app.similarFacesFilterPanel.setVisible(app.similarFacesRefItem != null);
    }

    public static boolean isFeatureEnabled() {
        return true;
    }

}
