package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.io.File;
import java.io.IOException;

import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.util.BytesRef;

import com.zaxxer.sparsebits.SparseBitSet;

import dpf.sp.gpinf.indexer.desktop.App;
import dpf.sp.gpinf.indexer.desktop.StateController;
import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.search.ItemId;
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
                App.get().appCase.getMultiMarcadores().setSelected(checked, item);
            }
            StateController.get().updateGUI();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public boolean isChecked(String hash) {
        if (sdv == null) {
            return false;
        }
        int ord = sdv.lookupTerm(new BytesRef(hash));
        return selectedHashOrds.get(ord);
    }

    @Override
    public String getHash(IItemId itemId) {
        if (sdv == null) {
            return null;
        }
        int luceneId = App.get().appCase.getLuceneId(itemId);
        return sdv.get(luceneId).utf8ToString();
    }

    @Override
    public void updateSelectionCache() {
        try {
            sdv = App.get().appCase.getAtomicReader().getSortedDocValues(BasicProps.HASH);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (sdv == null) {
            return;
        }
        selectedHashOrds.clear();
        for (IPEDSource source : App.get().appCase.getAtomicSources()) {
            for (int id = 0; id <= source.getLastId(); id++) {
                if (source.getMarcadores().isSelected(id)) {
                    ItemId itemId = new ItemId(source.getSourceId(), id);
                    int luceneId = App.get().appCase.getLuceneId(itemId);
                    int ord = sdv.getOrd(luceneId);
                    if (ord > -1)
                        selectedHashOrds.set(ord);
                }
            }
        }
    }

}
