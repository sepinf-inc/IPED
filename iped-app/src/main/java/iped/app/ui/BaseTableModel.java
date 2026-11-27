package iped.app.ui;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.search.Query;

import iped.app.ui.bookmarks.BookmarkIcon;
import iped.data.IItemId;
import iped.engine.search.IPEDSearcher;
import iped.engine.search.LuceneSearchResult;
import iped.engine.search.MultiSearchResult;
import iped.engine.task.index.IndexItem;
import iped.engine.util.Util;
import iped.properties.BasicProps;
import iped.search.IMultiSearchResult;

public abstract class BaseTableModel extends AbstractTableModel implements MouseListener, ListSelectionListener, SearchResultTableModel {

    private static final long serialVersionUID = 1L;

    protected String sortResultsBy = BasicProps.NAME;
    protected boolean cleanBeforeListItems;

    protected LuceneSearchResult results = new LuceneSearchResult(0);
    protected int selectedIndex = -1;
    protected Document refDoc;

    // true while the viewer shows the reference item instead of the highlighted one
    private boolean showingRefItem;

    public void clear() {
        results = new LuceneSearchResult(0);
        fireTableDataChanged();
    }

    @Override
    public int getColumnCount() {
        return 4;
    }

    @Override
    public int getRowCount() {
        return results.getLength();
    }

    @Override
    public String getColumnName(int col) {
        switch (col) {
            case 2:
                return BookmarkIcon.columnName;

            case 3:
                return IndexItem.NAME;
        }
        return "";
    }

    @Override
    public boolean isCellEditable(int row, int col) {
        return col == 1;
    }

    @Override
    public Class<?> getColumnClass(int c) {
        return c == 1 ? Boolean.class : String.class;
    }

    @Override
    public void setValueAt(Object value, int row, int col) {
        App.get().appCase.getMultiBookmarks().setChecked((Boolean) value, App.get().appCase.getItemId(results.getLuceneIds()[row]));
        BookmarksController.get().updateUISelection();
    }

    @Override
    public Object getValueAt(int row, int col) {
        switch (col) {
            case 0:
                // Row counter
                return row + 1;

            case 1:
                // Item Checkbox
                return App.get().appCase.getMultiBookmarks().isChecked(App.get().appCase.getItemId(results.getLuceneIds()[row]));

            case 2:
                // Item Bookmarks
                return Util.concatStrings(App.get().appCase.getMultiBookmarks().getBookmarkList(App.get().appCase.getItemId(results.getLuceneIds()[row])), true);

            case 3:
                // Item Name
                try {
                    Document doc = App.get().appCase.getSearcher().doc(results.getLuceneIds()[row]);
                    return doc.get(IndexItem.NAME);
                } catch (Exception e) {
                }
        }
        return "";
    }

    @Override
    public void mouseClicked(MouseEvent arg0) {
    }

    @Override
    public void mouseEntered(MouseEvent arg0) {
    }

    @Override
    public void mouseExited(MouseEvent arg0) {
    }

    @Override
    public void mousePressed(MouseEvent evt) {
        // needed for Linux
        showContextMenu(evt);
    }

    @Override
    public void mouseReleased(MouseEvent evt) {
        if (showContextMenu(evt)) {
            return;
        }
        if (showingRefItem && evt.getSource() instanceof JTable) {
            // the highlighted row did not change, so no selection event is fired
            JTable table = (JTable) evt.getSource();
            int row = table.rowAtPoint(evt.getPoint());
            if (row != -1 && selectedIndex != -1 && table.convertRowIndexToModel(row) == selectedIndex) {
                showingRefItem = false;
                valueChanged(table.getSelectionModel());
            }
        }
        if (evt.getClickCount() == 2 && selectedIndex != -1) {
            int docId = results.getLuceneIds()[selectedIndex];
            ExternalFileOpen.open(docId);
        }
    }

    private boolean showContextMenu(MouseEvent evt) {
        if (!evt.isPopupTrigger() || !(evt.getSource() instanceof JTable)) {
            return false;
        }
        JTable table = (JTable) evt.getSource();
        int row = table.rowAtPoint(evt.getPoint());
        if (row == -1) {
            return false;
        }
        // right-clicking a row outside the current selection selects just that row
        if (!table.isRowSelected(row)) {
            table.setRowSelectionInterval(row, row);
        }
        new RelatedItemsMenu(this, table).show(table, evt.getX(), evt.getY());
        return true;
    }

    public int getLuceneId(int modelRow) {
        return results.getLuceneIds()[modelRow];
    }

    /**
     * Returns the key of the menu label of the action that shows the position of
     * an item of this panel in the preview of the reference item (the one selected
     * in the results table), or null if this panel does not support it.
     */
    public String getGoToPositionLabelKey() {
        return null;
    }

    /**
     * Configures the viewer to scroll to the position of the given item of this
     * panel when the preview of the reference item is loaded.
     * 
     * @param apply
     *            if false, just checks if the position is known
     * @return false if the position is unknown
     */
    protected boolean setPositionToScroll(int luceneId, boolean apply) {
        return false;
    }

    public boolean canGoToPosition(int modelRow) {
        return refDoc != null && getRefLuceneId() != -1 && setPositionToScroll(getLuceneId(modelRow), false);
    }

    /**
     * Shows the reference item (the one selected in the results table) in the
     * viewer, scrolled to the position of the given item of this panel.
     */
    public void goToPosition(int modelRow) {
        int refLuceneId = getRefLuceneId();
        if (refDoc != null && refLuceneId != -1 && setPositionToScroll(getLuceneId(modelRow), true)) {
            showingRefItem = true;
            new FileProcessor(refLuceneId, false).execute();
        }
    }

    protected int getRefLuceneId() {
        int row = App.get().resultsTable.getSelectedRow();
        if (row == -1) {
            return -1;
        }
        return App.get().appCase.getLuceneId(App.get().ipedResult.getItem(App.get().resultsTable.convertRowIndexToModel(row)));
    }

    /**
     * Returns the items highlighted in the given table (which must use this model),
     * in index order.
     */
    public ArrayList<IItemId> getSelectedItemIds(JTable table) {
        int[] rows = table.getSelectedRows();
        int[] luceneIds = new int[rows.length];
        for (int i = 0; i < rows.length; i++) {
            luceneIds[i] = getLuceneId(table.convertRowIndexToModel(rows[i]));
        }
        Arrays.sort(luceneIds);
        ArrayList<IItemId> ids = new ArrayList<>(luceneIds.length);
        for (int luceneId : luceneIds) {
            ids.add(App.get().appCase.getItemId(luceneId));
        }
        return ids;
    }

    @Override
    public IMultiSearchResult getSearchResult() {
        return MultiSearchResult.get(App.get().appCase, results);
    }

    @Override
    public final void valueChanged(ListSelectionEvent evt) {
        ListSelectionModel lsm = (ListSelectionModel) evt.getSource();

        if (lsm.getMinSelectionIndex() == -1 || selectedIndex == lsm.getMinSelectionIndex()) {
            selectedIndex = lsm.getMinSelectionIndex();
            return;
        }

        selectedIndex = lsm.getMinSelectionIndex();
        showingRefItem = false;
        valueChanged(lsm);
    }

    public abstract void valueChanged(ListSelectionModel lsm);

    public abstract Query createQuery(Document doc);

    protected abstract void onListItemsResultsComplete();

    /**
     * Sorts the results of the search. By default they are sorted by the sort
     * field comparing numbers inside the values by their numeric value, so e.g.
     * "message_2" comes before "message_10".
     */
    protected void sortResults(LuceneSearchResult results) throws IOException {
        Map<Integer, String> values = readSortValues(results.getLuceneIds(), sortResultsBy);
        sortResults(results, (a, b) -> compareNaturally(values.get(a), values.get(b)));
    }

    /**
     * Sorts the results with the given comparator of lucene ids. The sort is
     * stable, so ties keep the order returned by the search.
     */
    protected void sortResults(LuceneSearchResult results, Comparator<Integer> comparator) {
        int[] docs = results.getLuceneIds();
        Integer[] sorted = Arrays.stream(docs).boxed().toArray(Integer[]::new);
        Arrays.sort(sorted, comparator);
        for (int i = 0; i < docs.length; i++) {
            docs[i] = sorted[i];
        }
    }

    /**
     * Reads the (first) value of a string field, as indexed for sorting, for each
     * given lucene id. Items without a value are mapped to an empty string.
     */
    protected Map<Integer, String> readSortValues(int[] docs, String field) throws IOException {
        Map<Integer, String> values = new HashMap<>();
        // doc values must be read in increasing doc id order
        int[] docsById = docs.clone();
        Arrays.sort(docsById);
        LeafReader reader = App.get().appCase.getLeafReader();
        SortedDocValues sdv = reader.getSortedDocValues(field);
        SortedSetDocValues ssdv = sdv == null ? reader.getSortedSetDocValues(field) : null;
        for (int doc : docsById) {
            String value = "";
            if (sdv != null && sdv.advanceExact(doc)) {
                value = sdv.lookupOrd(sdv.ordValue()).utf8ToString();
            } else if (ssdv != null && ssdv.advanceExact(doc)) {
                value = ssdv.lookupOrd(ssdv.nextOrd()).utf8ToString();
            }
            values.put(doc, value);
        }
        return values;
    }

    static int compareNaturally(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i), cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int endA = i, endB = j;
                while (endA < a.length() && Character.isDigit(a.charAt(endA))) {
                    endA++;
                }
                while (endB < b.length() && Character.isDigit(b.charAt(endB))) {
                    endB++;
                }
                // ignores leading zeros
                while (i < endA - 1 && a.charAt(i) == '0') {
                    i++;
                }
                while (j < endB - 1 && b.charAt(j) == '0') {
                    j++;
                }
                int result = (endA - i) - (endB - j);
                if (result == 0) {
                    result = a.substring(i, endA).compareTo(b.substring(j, endB));
                }
                if (result != 0) {
                    return result;
                }
                i = endA;
                j = endB;
            } else {
                if (ca != cb) {
                    return ca - cb;
                }
                i++;
                j++;
            }
        }
        return (a.length() - i) - (b.length() - j);
    }

    public final void listItems(Document doc) {

        if (cleanBeforeListItems) {
            results = new LuceneSearchResult(0);
            fireTableDataChanged();
        }

        Query query = createQuery(doc);

        if (query != null) {

            try {
                IPEDSearcher task = new IPEDSearcher(App.get().appCase, query, sortResultsBy);
                task.setRewritequery(false);
                results = MultiSearchResult.get(task.multiSearch(), App.get().appCase);
                sortResults(results);

                final int length = results.getLength();

                if (length > 0) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            onListItemsResultsComplete();
                        }
                    });
                    refDoc = doc;
                } else {
                    refDoc = null;
                }

            } catch (Exception e) {
                results = new LuceneSearchResult(0);
                refDoc = null;
                e.printStackTrace();
            }
            if (cleanBeforeListItems) {
                fireTableDataChanged();
            }
        }

        if (!cleanBeforeListItems) {
            fireTableDataChanged();
        }
    }
}
