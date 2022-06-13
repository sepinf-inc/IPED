package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.io.File;
import java.io.IOException;

import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.util.BytesRef;

import com.zaxxer.sparsebits.SparseBitSet;

import dpf.sp.gpinf.indexer.desktop.App;
import dpf.sp.gpinf.indexer.desktop.BookmarksController;
import dpf.sp.gpinf.indexer.lucene.DocValuesUtil;
import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.MultiSearchResult;
import dpf.sp.gpinf.indexer.ui.fileViewer.util.AttachmentSearcher;
import iped3.IItem;
import iped3.IItemId;
import iped3.util.BasicProps;

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

}
