package iped.app.ui.viewers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.queryparser.flexible.standard.QueryParserUtil;
import org.apache.lucene.util.BytesRef;

import com.zaxxer.sparsebits.SparseBitSet;

import iped.app.ui.App;
import iped.app.ui.BookmarksController;
import iped.data.IItem;
import iped.data.IItemId;
import iped.engine.lucene.DocValuesUtil;
import iped.engine.search.IPEDSearcher;
import iped.engine.search.MultiSearchResult;
import iped.properties.BasicProps;
import iped.viewers.api.AttachmentSearcher;

public class AttachmentSearcherImpl implements AttachmentSearcher {

    private SparseBitSet selectedHashOrds = new SparseBitSet();
    private SortedDocValues sdv = null;

    @Override
    public File getTmpFile(String luceneQuery) {

        IItem item = getItem(luceneQuery);
        try {
            return item.getTempFile();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public IItem getItem(String luceneQuery) {
        IPEDSearcher searcher = new IPEDSearcher(App.get().appCase, luceneQuery);
        try {
            MultiSearchResult result = searcher.multiSearch();
            if (result.getLength() == 0)
                return null;
            IItemId item = result.getItem(0);
            return App.get().appCase.getItemByItemId(item);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public List<IItem> getItems(String luceneQuery) {
        IPEDSearcher searcher = new IPEDSearcher(App.get().appCase, luceneQuery);
        try {
            MultiSearchResult result = searcher.multiSearch();
            List<IItem> items = new ArrayList<>();
            for (int i = 0; i < result.getLength(); i++) {
                IItemId item = result.getItem(i);
                items.add(App.get().appCase.getItemByItemId(item));
            }
            return items;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return Collections.emptyList();
    }

    @Override
    public void checkItem(String luceneQuery, boolean checked) {
        IPEDSearcher searcher = new IPEDSearcher(App.get().appCase, luceneQuery);
        try {
            MultiSearchResult result = searcher.multiSearch();
            if (result.getLength() == 0)
                return;
            for (IItemId item : result.getIterator()) {
                App.get().appCase.getMultiBookmarks().setChecked(checked, item);
            }
            BookmarksController.get().updateUI();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public boolean isChecked(String hash) {
        if (sdv == null) {
            return false;
        }
        try {
            int ord = sdv.lookupTerm(new BytesRef(hash));
            return selectedHashOrds.get(ord);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * TODO test if this could be faster accessing the stored index doc instead of
     */
    @Override
    public String getHash(IItemId itemId) {
        SortedDocValues sdv = null;
        try {
            sdv = App.get().appCase.getAtomicReader().getSortedDocValues(BasicProps.HASH);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        int luceneId = App.get().appCase.getLuceneId(itemId);
        return DocValuesUtil.getVal(sdv, luceneId);
    }

    @Override
    public void updateSelectionCache() {
        try {
            sdv = App.get().appCase.getAtomicReader().getSortedDocValues(BasicProps.HASH);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        selectedHashOrds.clear();
        App.get().appCase.getLuceneIdStream().forEach(luceneId -> {
            IItemId itemId = App.get().appCase.getItemId(luceneId);
            if (App.get().appCase.getMultiBookmarks().isChecked(itemId)) {
                int ord = DocValuesUtil.getOrd(sdv, luceneId);
                if (ord > -1) {
                    selectedHashOrds.set(ord);
                }
            }
        });
    }

    @Override
    public String escapeQuery(String string) {
        string = string.replace('“', '"').replace('”', '"');
        return QueryParserUtil.escape(string);
    }

}
