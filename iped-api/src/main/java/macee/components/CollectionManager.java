package macee.components;

import java.io.IOException;
import java.util.Collection;
import java.util.UUID;
import macee.CaseItem;
import macee.collection.CaseItemCollection;
import macee.descriptor.DescriptorType;

public interface CollectionManager {

    // GERAL
    CaseItemCollection newCollection(String name, DescriptorType type);

    CaseItemCollection getCollection(UUID guid);

    CaseItemCollection getCollection(String name, DescriptorType type);

    void deleteCollection(String name);

    void deleteCollection(UUID guid);

    // BOOKMARKS
    void bookmark(CaseItemCollection collection, String bookmarkName);

    void bookmarkItem(CaseItem item, String bookmarkName);

    Collection<CaseItemCollection> getBookmarks();

    CaseItemCollection getBookmarkedItems(String bookmarkName);

    // TAGS
    void tag(CaseItemCollection collection, String tag);

    void tagItem(CaseItem item, String tag);

    Collection<CaseItemCollection> getTags();

    CaseItemCollection getTaggedItems(String tag);

    // IGNORAR
    void ignore(CaseItemCollection collection, String ignoreTag);

    void ignoreItem(CaseItem item, String ignoreTag);

    void ignoreItem(CaseItem item);

    Collection<CaseItemCollection> getIgnoreTags();

    CaseItemCollection getIgnoredItems();

    // PARA CADA ITEM
    Collection<CaseItemCollection> getCollectionsForItem(CaseItem item);

    Collection<CaseItemCollection> getCollectionsForItem(CaseItem item, DescriptorType type);

    Collection<CaseItemCollection> getCollectionsForItem(String sourceId, int itemId);

    Collection<CaseItemCollection> getCollectionsForItem(String sourceId, int itemId,
        DescriptorType type);

    // EXPORTAR
    void export(CaseItemCollection collection, String outputPath, boolean useHashAsName)
        throws IOException;
}
