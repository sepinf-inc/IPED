package dpf.sp.gpinf.indexer.ui.fileViewer.util;

import java.io.File;

import iped3.IItem;

public interface AttachmentSearcher {
    
    abstract File getTmpFile(String luceneQuery);

    abstract IItem getItem(String luceneQuery);

}
