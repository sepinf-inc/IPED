package iped.app.ui.auxview;

import javax.swing.ListSelectionModel;

import org.apache.lucene.document.Document;
import org.apache.lucene.search.Query;

import iped.app.ui.App;
import iped.app.ui.BaseTableModel;
import iped.data.IItem;
import iped.engine.search.IPEDSearcher;
import iped.engine.search.LuceneSearchResult;
import iped.engine.search.MultiSearchResult;
import iped.properties.BasicProps;

public class AuxViewTableModel extends BaseTableModel {

    private static final long serialVersionUID = 835174004014306139L;

    @Override
    public void valueChanged(ListSelectionModel lsm) {
        int id = results.getLuceneIds()[selectedIndex];
        IItem item = App.get().appCase.getItemByLuceneID(id);
        App.get().auxViewPanel.setItem(item);
    }

    public void listItems(String luceneQuery) {
        if (luceneQuery == null || luceneQuery.isBlank()) {
            results = new LuceneSearchResult(0);
        } else {
            try {
                IPEDSearcher task = new IPEDSearcher(App.get().appCase, luceneQuery, BasicProps.NAME);
                results = MultiSearchResult.get(task.multiSearch(), App.get().appCase);
            } catch (Exception e) {
                results = new LuceneSearchResult(0);
                e.printStackTrace();
            }
        }
        fireTableDataChanged();
    }

    @Override
    public Query createQuery(Document doc) {
        return null;
    }

    @Override
    protected void onListItemsResultsComplete() {
    }
}