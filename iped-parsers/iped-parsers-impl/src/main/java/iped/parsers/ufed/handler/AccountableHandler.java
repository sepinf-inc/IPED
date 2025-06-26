package iped.parsers.ufed.handler;
import static iped.properties.ExtraProperties.UFED_META_PREFIX;

import org.apache.tika.metadata.Metadata;

import iped.parsers.ufed.model.Accountable;
import iped.search.IItemSearcher;

/**
 * Handles all processing logic for a Accountable model.
 */
public class AccountableHandler<T extends Accountable> extends BaseModelHandler<T> {

    public AccountableHandler(T model) {
        super(model, null);
    }

    @Override
    public void loadReferences(IItemSearcher searcher) {
        model.getPhotos().forEach(photo -> {
            new ContactPhotoHandler(photo).loadReferences(searcher);
        });
    }

    @Override
    protected void fillMetadata(String prefix, Metadata metadata) {

        super.fillMetadata(prefix, metadata);

        model.getPhotos().forEach(photo -> {
            metadata.add(UFED_META_PREFIX + "Photo:id", photo.getPhotoNodeId());
            metadata.add(UFED_META_PREFIX + "Photo:name", photo.getName());
        });

        model.getContactEntries().forEach((key, value) -> {
            metadata.add(UFED_META_PREFIX + key, value.getValue());
            metadata.add(UFED_META_PREFIX + key + ":category", value.getCategory());
            metadata.add(UFED_META_PREFIX + key + ":domain", value.getDomain());
        });
    }

}