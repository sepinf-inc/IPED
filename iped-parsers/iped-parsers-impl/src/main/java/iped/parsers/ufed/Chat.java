package iped.parsers.ufed;

import iped.data.IItemReader;
import iped.parsers.util.ConversationUtils;
import iped.properties.ExtraProperties;

public class Chat {

    private IItemReader item;

    private String title;
    private boolean isGroup;
    private byte[] contactPhotoThumb;

    public Chat(IItemReader item) {
        this.item = item;

        title = UFEDChatParser.getChatName(item.getMetadata());

        isGroup = ConversationUtils.TYPE_GROUP.equalsIgnoreCase(item.getMetadata().get(ExtraProperties.CONVERSATION_TYPE))
                || item.getMetadata().getValues(ExtraProperties.CONVERSATION_PARTICIPANTS).length > 2;
    }

    public IItemReader getItem() {
        return item;
    }

    public String getTitle() {
        return title;
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
