package iped.viewers.api;

import java.io.File;
import java.util.List;

import iped.data.IItem;
import iped.data.IItemId;

public interface AttachmentSearcher {

    File getTmpFile(String luceneQuery);

    IItem getItem(String luceneQuery);

    List<IItem> getItems(String luceneQuery);

    void checkItem(String luceneQuery, boolean checked);

    boolean isChecked(String hash);

    String getHash(IItemId itemId);

    void updateSelectionCache();

    String escapeQuery(String query);

    void view(String luceneQuery);
}
