package iped.parsers.ufed.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Represents a <model type="InstantMessage"> element.
 */
public class InstantMessage extends BaseModel implements Comparable<InstantMessage> {

    private static final long serialVersionUID = -6119178123925362471L;

    public static enum MessageStatus {
        Unknown, Default, Unsent, Sent, Delivered, Read, Unread;

        public static MessageStatus parse(String value) {
            if (value == null) {
                return null;
            }
            try {
                return valueOf(value);
            } catch (IllegalArgumentException e) {
                return Unknown;
            }
        }
    }

    public static enum CallStatus {
        Unknown, NotEstablishedUnknownReason, Established, Missed, Rejected,
    }

    private Party from;
    private final List<Party> to = new ArrayList<>();
    private final List<Attachment> attachments = new ArrayList<>();
    private final List<Contact> sharedContacts = new ArrayList<>();
    private InstantMessageExtraData messageExtraData = new InstantMessageExtraData();
    private final Map<String, BaseModel> others = new HashMap<>();

    private InstantMessage embeddedMessage;
    private ChatActivity activityLog;
    private Coordinate position;

    public InstantMessage() {
        super("InstantMessage");
    }

    // Specific field getters
    public String getBody() { return (String) getField("Body"); }
    public String getType() { return (String) getField("Type"); }
    public String getPlatform() { return (String) getField("Platform"); }
    public String getIdentifier() { return (String) getField("Identifier"); }
    public Date getTimeStamp() { return (Date) getField("TimeStamp"); }
    public String getSourceApplication() { return (String) getField("SourceApplication"); }
    public boolean isLocationSharing() { return Boolean.TRUE.equals(getField("IsLocationSharing")); }

    public MessageStatus getStatus() {
        return MessageStatus.parse((String) getField("Status"));
    }

    // Getters and Setters for child models
    public Party getFrom() {
        return from;
    }

    public void setFrom(Party from) {
        this.from = from;
    }

    public List<Party> getTo() {
        return to;
    }

    public List<Attachment> getAttachments() {
        return attachments;
    }

    public List<Contact> getSharedContacts() {
        return sharedContacts;
    }

    public InstantMessageExtraData getMessageExtraData() {
        return messageExtraData;
    }

    public Map<String, BaseModel> getOthers() {
        return others;
    }

    public InstantMessage getEmbeddedMessage() {
        return embeddedMessage;
    }

    public void setEmbeddedMessage(InstantMessage embeddedMessage) {
        this.embeddedMessage = embeddedMessage;
    }

    public Coordinate getPosition() {
        return position;
    }

    public void setPosition(Coordinate position) {
        this.position = position;
    }

    public ChatActivity getActivityLog() {
        return activityLog;
    }

    public void setActivityLog(ChatActivity activityLog) {
        this.activityLog = activityLog;
    }

    // for convenience (the information about system message is in From
    public boolean isSystemMessage() {
        return from != null && from.isSystemMessage();
    }

    @Override
    public String toString() {
        return new StringJoiner("\n    ", InstantMessage.class.getSimpleName() + "[\n    ", "\n  ]")
                .add("id='" + getId() + "'")
                .add("Body='" + getBody() + "'")
                .add("TimeStamp='" + getTimeStamp() + "'")
                .add("from=" + (from != null ? from.getName() : "null"))
                .add("attachments=" + attachments)
                .add("sharedContacts=" + sharedContacts)
                .add("messageExtraData=" + messageExtraData)
                .toString();
    }

    @Override
    public int compareTo(InstantMessage o) {
        int thisIndex = getSourceIndex();
        int otherIndex = o.getSourceIndex();
        int ret = 0;
        if (thisIndex >= 0 && otherIndex >= 0) {
            ret = Integer.compare(thisIndex, otherIndex);
        }

        if (ret == 0) {
            Date thisTime = getTimeStamp();
            Date otherTime = getTimeStamp();
            if (thisTime != null && otherTime != null) {
                ret = thisTime.compareTo(otherTime);
            }
        }

        return ret;
    }
}