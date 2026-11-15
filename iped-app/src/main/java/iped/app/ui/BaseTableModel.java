package iped.app.ui;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;

import org.apache.lucene.document.Document;
import org.apache.lucene.search.Query;

import iped.app.ui.bookmarks.BookmarkIcon;
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
    public void mousePressed(MouseEvent arg0) {
    }

    @Override
    public void mouseReleased(MouseEvent evt) {
        if (evt.getClickCount() == 2 && selectedIndex != -1) {
            int docId = results.getLuceneIds()[selectedIndex];
            ExternalFileOpen.open(docId);
        }
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
        valueChanged(lsm);
    }

    public abstract void valueChanged(ListSelectionModel lsm);

    public abstract Query createQuery(Document doc);

    protected abstract void onListItemsResultsComplete();

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
