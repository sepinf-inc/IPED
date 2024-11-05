package iped.parsers.ufed;

import static iped.parsers.ufed.UfedUtils.readUfedMetadata;
import static iped.parsers.ufed.UfedUtils.readUfedMetadataArray;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import iped.data.IItemReader;
import iped.parsers.util.ConversationUtils;
import iped.properties.ExtraProperties;
import iped.utils.DateUtil;

public class Message implements Comparable<Message> {

    private IItemReader item;

    private List<MessageChatActivity> activityLog = new ArrayList<>();
    private List<MessageAttachment> attachments = new ArrayList<>();
    private ReferencedLocation referencedLocation;
    private List<MessageContact> sharedContacts = new ArrayList<>();

    private boolean systemMessage;
    private boolean forwarded;
    private boolean edited;
    private boolean quoted;

    private Message messageQuote;

    private String chatAccount;

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

    public Message(IItemReader messageItem, Chat chat) {
        this.item = messageItem;

        List<String> labels = readUfedMetadataArray(messageItem, "Label");
        forwarded = labels.contains("Forwarded");
        quoted = labels.contains("Reply");
        edited = labels.contains("Edited");
        systemMessage = labels.contains("System");

        chatAccount = chat.getItem().getMetadata().get(ExtraProperties.CONVERSATION_ACCOUNT);
    }

    public IItemReader getItem() {
        return item;
    }

    public long getId() {
        return item.getId();
    }

    public String getUfedId() {
        return item.getMetadata().get(ExtraProperties.UFED_ID);
    }

    public String getSource() {
        return readUfedMetadata(item, "Source");
    }

    public String getSourceIndex() {
        return readUfedMetadata(item, "source_index");
    }

    public String getCoordinateId() {
        return item.getMetadata().get(ExtraProperties.UFED_COORDINATE_ID);
    }

    public String getLatitude() {
        return readUfedMetadata(item, "Latitude");
    }

    public String getLongitude() {
        return readUfedMetadata(item, "Longitude");
    }

    public boolean isLocationSharing() {
        return Boolean.parseBoolean(readUfedMetadata(item, "IsLocationSharing"));
    }

    public String getFrom() {
        String from = item.getMetadata().get(ExtraProperties.COMMUNICATION_FROM);
        if (from == null && isFromMe()) {
            from = chatAccount;
        }
        return from;
    }

    public String getTo() {
        return item.getMetadata().get(ExtraProperties.COMMUNICATION_TO);
    }

    public String getDirection() {
        return item.getMetadata().get(ExtraProperties.COMMUNICATION_DIRECTION);
    }

    public boolean isFromMe() {
        return ConversationUtils.DIRECTION_OUTGOING.equals(getDirection());
    }

    public Date getTimeStamp() {
        String str = item.getMetadata().get(ExtraProperties.MESSAGE_DATE);
        if (str != null) {
            return DateUtil.tryToParseDate(str);
        }
        return null;
    }

    public String getBody() {
        for (String body : item.getMetadata().getValues(ExtraProperties.MESSAGE_BODY)) {
            if (!body.startsWith(UFEDChatParser.ATTACHED_MEDIA_MSG))
                return body;
        }
        return null;
    }

    public MessageStatus getStatus() {
        return MessageStatus.parse(readUfedMetadata(item, "Status"));
    }

    public boolean isSystemMessage() {
        return systemMessage;
    }

    public boolean isForwarded() {
        return forwarded;
    }

    public boolean isEdited() {
        return edited;
    }

    public boolean isQuoted() {
        return quoted;
    }

    public Message getMessageQuote() {
        return this.messageQuote;
    }

    public void setMessageQuote(Message messageQuote) {
        this.messageQuote = messageQuote;
    }

    public boolean isTrash() {
        return Boolean.parseBoolean(readUfedMetadata(item, "deleted_trash"));
    }

    public String getIdentifier() {
        return readUfedMetadata(item, "Identifier");
    }

    public String getOriginalMessageID() {
        return readUfedMetadata(item, "OriginalMessageID");
    }

    public String getOriginalSender() {
        return readUfedMetadata(item, "OriginalSender");
    }

    public List<MessageChatActivity> getActivityLog() {
        return activityLog;
    }

    public MessageChatActivity addActivityLog(IItemReader activityLogItem) {
        if (activityLogItem.getParentId() != item.getId()) {
            throw new IllegalArgumentException(
                    "ActivityLog parentId doesn't match: " + activityLogItem.getParentId() + " x " + item.getId());
        }
        MessageChatActivity activity = new MessageChatActivity(activityLogItem);
        activityLog.add(activity);
        return activity;
    }

    public List<MessageAttachment> getAttachments() {
        return attachments;
    }

    public MessageAttachment addAttachment(IItemReader attachItem) {
        if (attachItem.getParentId() != item.getId()) {
            throw new IllegalArgumentException(
                    "Attachment parentId doesn't match: " + attachItem.getParentId() + " x " + item.getId());
        }
        MessageAttachment attach = new MessageAttachment(attachItem);
        attachments.add(attach);
        return attach;
    }

    public ReferencedLocation getReferencedLocation() {
        return referencedLocation;
    }

    public ReferencedLocation setReferencedLocation(IItemReader localizationItem) {
        referencedLocation = new ReferencedLocation(localizationItem);
        return referencedLocation;
    }

    public List<MessageContact> getSharedContacts() {
        return sharedContacts;
    }

    public MessageContact addSharedContact(IItemReader contactItem) {
        MessageContact contact = new MessageContact(contactItem);
        sharedContacts.add(contact);
        return contact;
    }

    @Override
    public int compareTo(Message o) {
        Date thisTime = this.getTimeStamp();
        Date otherTime = o.getTimeStamp();
        if (thisTime == null) {
            if (otherTime == null)
                return 0;
            else
                return -1;
        } else if (otherTime == null)
            return 1;
        else {
            int ret = thisTime.compareTo(otherTime);
            if (ret != 0) {
                return ret;
            }
            // compare Identifier (example "1257970071:7180")
            if (StringUtils.isNoneBlank(this.getIdentifier(), o.getIdentifier())) {
                String[] id1 = this.getIdentifier().split(":");
                String[] id2 = o.getIdentifier().split(":");
                if (id1.length == 2 && id2.length == 2) {
                    if (id1[0].equals(id2[0]) && StringUtils.isNumeric(id1[1]) && StringUtils.isNumeric(id2[1])) {
                        return Integer.parseInt(id1[1]) - Integer.parseInt(id2[1]);
                    }
                }
            }
            return (int) (this.getItem().getId() - o.getItem().getId());
        }
    }
}
