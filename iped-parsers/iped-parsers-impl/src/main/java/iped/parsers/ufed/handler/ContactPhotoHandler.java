package iped.parsers.ufed.handler;

import java.util.List;

import iped.data.IItemReader;
import iped.parsers.ufed.model.ContactPhoto;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;

public class ContactPhotoHandler extends BaseModelHandler<ContactPhoto> {

    protected ContactPhotoHandler(ContactPhoto model) {
        super(model, null);
    }

    @Override
    public void loadReferences(IItemSearcher searcher) {

        if (model.getImageData() != null) {
            // already loaded
            return;
        }

        if (model.getPhotoNodeId() != null) {
            String query = searcher.escapeQuery(ExtraProperties.UFED_ID) + ":\"" + model.getPhotoNodeId() + "\"";
            List<IItemReader> result = searcher.search(query);
            if (!result.isEmpty()) {
                IItemReader contactPhoto = result.get(0);
                model.setImageData(contactPhoto.getThumb());
            }
        }
    }
}
