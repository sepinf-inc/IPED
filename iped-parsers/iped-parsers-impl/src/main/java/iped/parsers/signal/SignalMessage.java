package iped.parsers.signal;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class SignalMessage {

    public enum MessageType {
        INCOMING, OUTGOING, SYSTEM, CALL_INCOMING, CALL_OUTGOING, CALL_MISSED, CALL_GROUP

    }

    // _id in the message table; 0 for calls read from the call table only
    private long id;
    // Resolved from the full recipient table, so senders who are no longer group
    // members are still identified.
    private SignalContact sender;
    private String body;
    private Date dateSent;
    private Date dateReceived;
    private boolean fromMe;
    private MessageType messageType = MessageType.OUTGOING;
    // Description of a call event taken from the call table (type, direction and
    // outcome), null for ordinary messages.
    private String callDetail;
    // Superseded by a later edit of the same message
    private boolean earlierRevision;
    // Deleted for everyone by whoever sent it
    private boolean remoteDeleted;
    // Composed to be sent later and never actually sent
    private boolean scheduled;
    // Who deleted the message for everyone, when the database records it
    private SignalContact deletedBy;
    // Media meant to be opened once; its content is gone after that
    private boolean viewOnce;
    // What a group update or other event row records, null for plain messages
    private String systemDetail;
    private List<SignalAttachment> attachments = new ArrayList<>();
    private List<SignalReaction> reactions = new ArrayList<>();
    // The message this one replies to: its date_sent, author and a copy of its text,
    // kept by Signal even when the original is gone (quoteMissing)
    private long quoteId;
    private long quoteAuthorId;
    private SignalContact quoteAuthor;
    private String quoteBody;
    private boolean quoteMissing;
    // The quoted message in this conversation, when the database still has it
    private SignalMessage quotedMessage;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public List<SignalAttachment> getAttachments() { return attachments; }

    public List<SignalReaction> getReactions() { return reactions; }

    public long getQuoteId() { return quoteId; }
    public void setQuoteId(long quoteId) { this.quoteId = quoteId; }

    public long getQuoteAuthorId() { return quoteAuthorId; }
    public void setQuoteAuthorId(long quoteAuthorId) { this.quoteAuthorId = quoteAuthorId; }

    public SignalContact getQuoteAuthor() { return quoteAuthor; }
    public void setQuoteAuthor(SignalContact quoteAuthor) { this.quoteAuthor = quoteAuthor; }

    public String getQuoteBody() { return quoteBody; }
    public void setQuoteBody(String quoteBody) { this.quoteBody = quoteBody; }

    public SignalMessage getQuotedMessage() { return quotedMessage; }
    public void setQuotedMessage(SignalMessage quotedMessage) { this.quotedMessage = quotedMessage; }

    public boolean isQuoteMissing() { return quoteMissing; }
    public void setQuoteMissing(boolean quoteMissing) { this.quoteMissing = quoteMissing; }

    /**
     * Whether the message is a reply, by the rule of Signal's MessageTable: a quote id,
     * an author, and quoted text or a quoted attachment.
     */
    public boolean isReply() {
        return quoteId != 0 && quoteAuthorId > 0
                && ((quoteBody != null && !quoteBody.isEmpty()) || attachments.stream().anyMatch(SignalAttachment::isQuote));
    }

    public SignalContact getSender() { return sender; }
    public void setSender(SignalContact sender) { this.sender = sender; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public Date getDateSent() { return dateSent; }
    public void setDateSent(Date dateSent) { this.dateSent = dateSent; }

    public Date getDateReceived() { return dateReceived; }
    public void setDateReceived(Date dateReceived) { this.dateReceived = dateReceived; }

    public boolean isFromMe() { return fromMe; }
    public void setFromMe(boolean fromMe) { this.fromMe = fromMe; }

    public boolean isEarlierRevision() { return earlierRevision; }
    public void setEarlierRevision(boolean earlierRevision) { this.earlierRevision = earlierRevision; }

    public boolean isRemoteDeleted() { return remoteDeleted; }
    public void setRemoteDeleted(boolean remoteDeleted) { this.remoteDeleted = remoteDeleted; }

    public SignalContact getDeletedBy() { return deletedBy; }
    public void setDeletedBy(SignalContact deletedBy) { this.deletedBy = deletedBy; }

    public String getSystemDetail() { return systemDetail; }
    public void setSystemDetail(String systemDetail) { this.systemDetail = systemDetail; }

    public boolean isViewOnce() { return viewOnce; }
    public void setViewOnce(boolean viewOnce) { this.viewOnce = viewOnce; }

    public boolean isScheduled() { return scheduled; }
    public void setScheduled(boolean scheduled) { this.scheduled = scheduled; }

    public String getCallDetail() { return callDetail; }
    public void setCallDetail(String callDetail) { this.callDetail = callDetail; }

    public MessageType getMessageType() { return messageType; }
    public void setMessageType(MessageType messageType) { this.messageType = messageType; }
}
