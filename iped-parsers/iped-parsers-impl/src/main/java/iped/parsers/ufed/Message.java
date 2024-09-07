package iped.parsers.ufed;

import static iped.parsers.ufed.UfedUtils.readUfedMetadata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import iped.data.IItemReader;
import iped.parsers.util.ConversationUtils;
import iped.properties.ExtraProperties;
import iped.utils.DateUtil;

public class Message implements Comparable<Message> {

    private IItemReader item;
    private List<Attachment> attachments = new ArrayList<>();

    private boolean systemMessage;
    private boolean forwarded;
    private boolean edited;
    private boolean quoted;

    private Message messageQuote;

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

    public Message(IItemReader messageItem) {
        this.item = messageItem;

        List<String> labels = Arrays.asList(messageItem.getMetadata().getValues(ExtraProperties.UFED_META_PREFIX + "Label"));
        forwarded = labels.contains("Forwarded");
        quoted = labels.contains("Reply");
        edited = labels.contains("Edited");
        systemMessage = labels.contains("System");
    }

    public IItemReader getItem() {
        return item;
    }

    public long getId() {
        return item.getId();
    }

    public String getFrom() {
        return item.getMetadata().get(ExtraProperties.COMMUNICATION_FROM);
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

    public boolean isDeleted() {
        return item.isDeleted();
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

    public void setForwarded(boolean forwarded) {
        this.forwarded = forwarded;
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

    public List<Attachment> getAttachments() {
        return attachments;
    }

    public Attachment addAttachment(IItemReader attachItem) {
        if (attachItem.getParentId() != item.getId()) {
            throw new IllegalArgumentException("Attachment parentId don't match: " + attachItem.getParentId() + " x " + item.getId());
        }
        Attachment attach = new Attachment(attachItem);
        attachments.add(attach);
        return attach;
    }

    public String getIdentifier() {
        return readUfedMetadata(item, "Identifier");
    }

    public String getOriginalMessageID() {
        return readUfedMetadata(item, "OriginalMessageID");
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
            return (int) (this.getId() - o.getId());
        }
    }
}
