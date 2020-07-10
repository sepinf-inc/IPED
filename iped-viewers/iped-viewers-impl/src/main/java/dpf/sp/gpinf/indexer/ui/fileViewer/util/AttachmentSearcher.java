package dpf.sp.gpinf.indexer.ui.fileViewer.util;

import java.io.File;

import iped3.IItem;
import iped3.IItemId;

public interface AttachmentSearcher {

    File getTmpFile(String luceneQuery);

    IItem getItem(String luceneQuery);

    void checkItem(String luceneQuery, boolean checked);

    boolean isChecked(String hash);

    String getHash(IItemId itemId);

    void updateSelectionCache();

}
