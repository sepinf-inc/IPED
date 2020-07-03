package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.io.File;
import java.io.IOException;

import dpf.sp.gpinf.indexer.desktop.App;
import dpf.sp.gpinf.indexer.desktop.MarcadoresController;
import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.MultiSearchResult;
import dpf.sp.gpinf.indexer.ui.fileViewer.util.AttachmentSearcher;
import iped3.IItem;
import iped3.IItemId;

public class AttachmentSearcherImpl implements AttachmentSearcher {

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
            for(IItemId item : result.getIterator()) {
                App.get().appCase.getMultiMarcadores().setSelected(checked, item, App.get().appCase);
            }
            MarcadoresController.get().atualizarGUI();

        } catch (Exception e) {
            e.printStackTrace();
        }
        
        
    }

}
