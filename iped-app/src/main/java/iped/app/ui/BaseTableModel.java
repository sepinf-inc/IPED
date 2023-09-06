package iped.app.ui;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;

import org.apache.lucene.document.Document;

import iped.engine.search.LuceneSearchResult;
import iped.engine.search.MultiSearchResult;
import iped.engine.task.index.IndexItem;
import iped.search.IMultiSearchResult;

public abstract class BaseTableModel extends AbstractTableModel
        implements MouseListener, ListSelectionListener, SearchResultTableModel {

    private static final long serialVersionUID = 1L;

    protected LuceneSearchResult results = new LuceneSearchResult(0);
    protected int selectedIndex = -1;
    protected Document refDoc;

    public void clear() {
        results = new LuceneSearchResult(0);
        fireTableDataChanged();
    }

    @Override
    public int getColumnCount() {
        return 3;
    }

    @Override
    public int getRowCount() {
        return results.getLength();
    }

    @Override
    public String getColumnName(int col) {
        if (col == 2)
            return IndexItem.NAME;

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
        App.get().appCase.getMultiBookmarks().setChecked((Boolean) value,
                App.get().appCase.getItemId(results.getLuceneIds()[row]));
        BookmarksController.get().updateUISelection();
    }

    @Override
    public Object getValueAt(int row, int col) {
        switch (col) {
            case 0:
                return row + 1;

            case 1:
                return App.get().appCase.getMultiBookmarks()
                        .isChecked(App.get().appCase.getItemId(results.getLuceneIds()[row]));

            case 2:
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
}
