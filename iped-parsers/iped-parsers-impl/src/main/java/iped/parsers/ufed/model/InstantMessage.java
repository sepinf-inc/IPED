package iped.parsers.ufed.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

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

    private transient Chat chat;
    private Party from;
    private final List<Party> to = new ArrayList<>();
    private final List<Attachment> attachments = new ArrayList<>();
    private final List<Contact> sharedContacts = new ArrayList<>();
    private InstantMessageExtraData messageExtraData = new InstantMessageExtraData();

    private InstantMessage embeddedMessage;
    private ChatActivity activityLog;
    private Coordinate position;

    public InstantMessage() {
        super("InstantMessage");
    }

    public InstantMessage(Chat chat) {
        this();
        this.chat = chat;
    }

    // Specific field getters
    public String getBody() { return (String) getField("Body"); }
    public String getType() { return (String) getField("Type"); }
    public String getPlatform() { return (String) getField("Platform"); }
    public String getIdentifier() { return (String) getField("Identifier"); }
    public Date getTimeStamp() { return (Date) getField("TimeStamp"); }
    public String getSource() { return (String) getField("Source"); }
    public String getLabel() { return (String) getField("Label"); }
    public String getSourceApplication() { return (String) getField("SourceApplication"); }
    public boolean isLocationSharing() { return BooleanUtils.toBoolean((Boolean) getField("IsLocationSharing")); }
    public String getSubject() { return (String) getField("Subject"); }

    public MessageStatus getStatus() {
        return MessageStatus.parse((String) getField("Status"));
    }

    public Chat getChat() {
        return chat;
    }

    // Getters and Setters for child models
    public Optional<Party> getFrom() {
        return Optional.ofNullable(from);
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

    public InstantMessageExtraData getExtraData() {
        return messageExtraData;
    }

    public Optional<InstantMessage> getEmbeddedMessage() {
        return Optional.ofNullable(embeddedMessage);
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
        return hasLabel("System") || getFrom().map(Party::isSystemMessage).orElse(false);
    }

    public boolean isFromPhoneOwner() {
        return getFrom().map(Party::isPhoneOwner).orElse(false);
    }

    public boolean hasLabel(String label) {
        return messageExtraData.getMessageLabels().stream().anyMatch(l -> label.equalsIgnoreCase(l.getLabel()));
    }

    public boolean isEdited() {
        return hasLabel("Edited");
    }

    public boolean isReplyMessage() {
        return messageExtraData.getReplyMessage().isPresent()
                || hasLabel("Reply")
                || messageExtraData.getQuotedMessage().filter(q -> "Reply".equalsIgnoreCase(q.getLabel())).isPresent();
    }

    public boolean isForwardedMessage() {
        return messageExtraData.getForwardedMessage().isPresent()
                || hasLabel("Forwarded")
                || messageExtraData.getQuotedMessage().filter(q -> "Forwarded".equalsIgnoreCase(q.getLabel())).isPresent();
    }

    public Party findForwardedMessageOriginalSender(Chat chat) {
        Optional<Party> originalSender = messageExtraData.getForwardedMessage().map(ForwardedMessageData::getOriginalSender);
        if (originalSender.isPresent()) {
            return originalSender.get();
        }

        InstantMessage quotedMessage = findQuotedMessage(chat);
        if (quotedMessage != null) {
            return quotedMessage.getFrom().orElse(null);
        }
        return null;
    }

    public InstantMessage findForwardedMessage(Chat chat) {
        return findQuotedMessage(chat);
    }

    public InstantMessage findReplyMessage(Chat chat) {

        // first search in replyMessage embedded InstantMessage
        Optional<InstantMessage> replyMessage = messageExtraData.getReplyMessage().map(ReplyMessageData::getInstantMessage);
        if (replyMessage.isPresent()) {
            return replyMessage.get();
        }

        // search using replyMessage.originalMessageID
        replyMessage = messageExtraData
                .getReplyMessage()
                .map(ReplyMessageData::getOriginalMessageID)
                .map(id -> findMessageUsingOriginalMessageId(id, chat));
        if (replyMessage.isPresent()) {
            return replyMessage.get();
        }

        // finally try in quoted message
        return findQuotedMessage(chat);
    }

    public InstantMessage findQuotedMessage(Chat chat) {

        // search using quotedMessage.originalMessageID
        Optional<InstantMessage> quotedMessage = messageExtraData
                .getQuotedMessage()
                .map(QuotedMessageData::getOriginalMessageID)
                .map(id -> findMessageUsingOriginalMessageId(id, chat));
        if (quotedMessage.isPresent()) {
            return quotedMessage.get();
        }

        // search using quotedMessage.referenceId
        quotedMessage = messageExtraData
                .getQuotedMessage()
                .map(QuotedMessageData::getReferenceId)
                .map(refId -> findMessageUsingReferenceId(refId, chat));
        if (quotedMessage.isPresent()) {
            return quotedMessage.get();
        }

        return null;
    }

    private InstantMessage findMessageUsingOriginalMessageId(String originalMessageID, Chat chat) {

        // first compare with embeddedMessage
        if (embeddedMessage != null && StringUtils.equals(embeddedMessage.getIdentifier(), originalMessageID)) {
            return embeddedMessage;
        }

        // ... so look up in chat messages
        if (chat != null) {
            InstantMessage chatMessage = chat.findMessageByIdentifier(originalMessageID);
            if (chatMessage != null) {
                return chatMessage;
            }
        }
        return null;
    }

    private InstantMessage findMessageUsingReferenceId(String referenceId, Chat chat) {

        // first compare with embeddedMessage
        if (embeddedMessage != null && StringUtils.equals(embeddedMessage.getId(), referenceId)) {
            return embeddedMessage;
        }

        // ... so look up in chat messages
        if (chat != null) {
            InstantMessage chatMessage = chat.findMessageByUfedId(referenceId);
            if (chatMessage != null) {
                return chatMessage;
            }
        }
        return null;
    }

    public String getAnchorId() {
        return StringUtils.firstNonBlank(getIdentifier(), getId(), Integer.toString(getSourceIndex()));
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

        return Comparator.comparing(InstantMessage::getTimeStamp, Comparator.nullsLast(Date::compareTo))
                         .thenComparing(InstantMessage::getSourceIndex)
                         .compare(this, o);

    }
}