package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.io.File;

import dpf.sp.gpinf.indexer.desktop.App;
import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.ItemId;
import dpf.sp.gpinf.indexer.search.MultiSearchResult;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.WhatsAppViewer.WhatsAppAttachSearcher;

public class WhatsAppAttachSearcherImpl implements WhatsAppAttachSearcher{

	@Override
	public File getTmpFile(String sha256) {
		
		String queryStr = "sha-256:" + sha256;
		
		IPEDSearcher searcher = new IPEDSearcher(App.get().appCase, queryStr);
		try {
			MultiSearchResult result = searcher.multiSearch();
			if(result.getLength() == 0)
				return null;
			ItemId item = result.getItem(0);
			File file = App.get().appCase.getItemByItemId(item).getTempFile();
			return file;
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return null;
	}

}
