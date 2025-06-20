package iped.parsers.ufed.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import iped.data.IItemReader;

/**
 * Represents a <model type="Chat"> element. This is the root object.
 */
public class Chat extends BaseModel {

    private static final long serialVersionUID = -568791121571161053L;

    public static final String TYPE_ONEONONE = "OneOnOne";
    public static final String TYPE_GROUP = "Group";
    public static final String TYPE_BROADCAST = "Broadcast";
    public static final String TYPE_UNKNOWN = "Unknown";

    public static final String SOURCE_WHATSAPP = "WhatsApp";
    public static final String SOURCE_WHATSAPP_BUSINESS = "WhatsApp Business";
    public static final String SOURCE_TELEGRAM = "Telegram";

    private final List<Party> participants = new ArrayList<>();
    private final List<ContactPhoto> photos = new ArrayList<>();
    private final List<InstantMessage> messages = new ArrayList<>();
    private final Map<String, BaseModel> others = new HashMap<>();

    private IItemReader referencedAccount;

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

    public Optional<Party> getPhoneOwnerParticipant() {
        return participants.stream().filter(p -> p.isPhoneOwner()).findFirst();
    }

    public List<Party> getOtherParticipants() {
        return participants.stream().filter(p -> !p.isPhoneOwner()).collect(Collectors.toList());
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

    public IItemReader getReferencedAccount() {
        return referencedAccount;
    }

    public void setReferencedAccount(IItemReader referencedAccount) {
        this.referencedAccount = referencedAccount;
    }

    public boolean isGroup() {
        return StringUtils.equalsAnyIgnoreCase(getChatType(), "Channel", "Group");
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

