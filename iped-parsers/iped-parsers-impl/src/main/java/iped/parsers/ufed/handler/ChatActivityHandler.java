package iped.parsers.ufed.handler;

import static iped.properties.ExtraProperties.UFED_META_PREFIX;

import org.apache.tika.metadata.Metadata;

import iped.data.IItemReader;
import iped.parsers.ufed.model.ChatActivity;

public class ChatActivityHandler extends BaseModelHandler<ChatActivity> {

    private String source;

    protected ChatActivityHandler(ChatActivity model, String source, IItemReader parentItem) {
        super(model, parentItem);
        this.source = source;
    }

    public ChatActivityHandler(ChatActivity model, String source) {
        super(model);
        this.source = source;
    }

    @Override
    protected void fillMetadata(String prefix, Metadata metadata) {
        
        super.fillMetadata(prefix, metadata);

        // ChatActivity Party
        if (model.getParticipant() != null) {
            new PartyHandler(model.getParticipant(), source).fillMetadata(UFED_META_PREFIX + "Party", metadata);
            metadata.add(UFED_META_PREFIX + "Party" + ":isPhoneOwner", Boolean.toString(model.getParticipant().isPhoneOwner()));
        }

    }

    @Override
    public String getTitle() {

        String actionStr = model.getAction() != null ? "-" + model.getAction() : "";

        return new StringBuilder()
                .append(model.getModelType())
                .append(actionStr)
                .append("-[")
                .append(model.getId())
                .append("]")
                .toString();
    }
}
