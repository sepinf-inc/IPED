package iped.parsers.ufed.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Represents a <model type="Chat"> element. This is the root object.
 */
public class Chat extends BaseModel {

    private static final long serialVersionUID = -568791121571161053L;

    private final List<Party> participants = new ArrayList<>();
    private final List<ContactPhoto> photos = new ArrayList<>();
    private final List<InstantMessage> messages = new ArrayList<>();
    private final Map<String, BaseModel> others = new HashMap<>();

    public Chat() {
        super("Chat");
    }

    // Specific field getters
    public String getSource() { return (String) getField("Source"); }
    public String getServiceIdentifier() { return (String) getField("ServiceIdentifier"); }
    public String getName() { return (String) getField("Name"); }
    public String getFieldId() { return (String) getField("Id"); }
    public Date getStartTime() { return (Date) getField("StartTime"); }
    public Date getLastActivity() { return (Date) getField("LastActivity"); }
    public String getAccount() { return (String) getField("Account"); }
    public String getChatType() { return (String) getField("ChatType"); }

    // Child model getters
    public List<Party> getParticipants() {
        return participants;
    }

    public Party getParticipantPhoneOwner() {
        return participants.stream().filter(p -> p.isPhoneOwner()).findFirst().orElse(null);
    }

    public List<ContactPhoto> getPhotos() {
        return photos;
    }

    public List<InstantMessage> getMessages() {
        return messages;
    }

    public Map<String, BaseModel> getOthers() {
        return others;
    }

    @Override
    public String toString() {
        return new StringJoiner("\n  ", Chat.class.getSimpleName() + "[\n", "\n]")
                .add("id='" + getId() + "'")
                .add("Source='" + getSource() + "'")
                .add("participants=" + participants)
                .add("photos=" + photos)
                .add("messages=" + messages)
                .toString();
    }
}

