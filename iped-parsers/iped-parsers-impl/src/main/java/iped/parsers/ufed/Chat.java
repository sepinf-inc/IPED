package iped.parsers.ufed;

import static iped.parsers.ufed.UfedUtils.readUfedMetadata;

import iped.data.IItemReader;
import iped.parsers.util.ConversationUtils;
import iped.properties.ExtraProperties;

public class Chat {

    private IItemReader item;

    private boolean isGroup;
    private byte[] contactPhotoThumb;

    public Chat(IItemReader item) {
        this.item = item;

        isGroup = ConversationUtils.TYPE_GROUP
                .equalsIgnoreCase(item.getMetadata().get(ExtraProperties.CONVERSATION_TYPE))
                || item.getMetadata().getValues(ExtraProperties.CONVERSATION_PARTICIPANTS).length > 2;
    }

    public IItemReader getItem() {
        return item;
    }

    public String getId() {
        return readUfedMetadata(item, "id");
    }

    public boolean isGroup() {
        return isGroup;
    }

    public byte[] getContactPhotoThumb() {
        return contactPhotoThumb;
    }

    public void setContactPhotoThumb(byte[] contactPhotoThumb) {
        this.contactPhotoThumb = contactPhotoThumb;
    }
}
