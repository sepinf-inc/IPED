package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.io.File;

import dpf.sp.gpinf.indexer.desktop.App;
import dpf.sp.gpinf.indexer.search.IPEDSearcherImpl;
import dpf.sp.gpinf.indexer.search.ItemIdImpl;
import dpf.sp.gpinf.indexer.search.MultiSearchResultImpl;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.HtmlLinkViewer.AttachmentSearcher;
import iped3.ItemId;

public class AttachmentSearcherImpl implements AttachmentSearcher{

	@Override
	public File getTmpFile(String luceneQuery) {
		
		IPEDSearcherImpl searcher = new IPEDSearcherImpl(App.get().appCase, luceneQuery);
		try {
			MultiSearchResultImpl result = searcher.multiSearch();
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
