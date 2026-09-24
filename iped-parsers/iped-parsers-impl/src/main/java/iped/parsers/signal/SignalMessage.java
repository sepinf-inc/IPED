package iped.parsers.signal;

import java.util.Date;

public class SignalMessage {

    public enum MessageType {
        INCOMING, OUTGOING, SYSTEM, CALL_INCOMING, CALL_OUTGOING, CALL_MISSED, CALL_GROUP;

        public boolean isRegular() {
            return this == INCOMING || this == OUTGOING;
        }

        public boolean isCall() {
            return this == CALL_INCOMING || this == CALL_OUTGOING
                    || this == CALL_MISSED || this == CALL_GROUP;
        }
    }

    private long id;
    private long threadId;
    private long fromRecipientId;
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

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getThreadId() { return threadId; }
    public void setThreadId(long threadId) { this.threadId = threadId; }

    public SignalContact getSender() { return sender; }
    public void setSender(SignalContact sender) { this.sender = sender; }

    public long getFromRecipientId() { return fromRecipientId; }
    public void setFromRecipientId(long fromRecipientId) { this.fromRecipientId = fromRecipientId; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public Date getDateSent() { return dateSent; }
    public void setDateSent(Date dateSent) { this.dateSent = dateSent; }

    public Date getDateReceived() { return dateReceived; }
    public void setDateReceived(Date dateReceived) { this.dateReceived = dateReceived; }

    public boolean isFromMe() { return fromMe; }
    public void setFromMe(boolean fromMe) { this.fromMe = fromMe; }

    public String getCallDetail() { return callDetail; }
    public void setCallDetail(String callDetail) { this.callDetail = callDetail; }

    public MessageType getMessageType() { return messageType; }
    public void setMessageType(MessageType messageType) { this.messageType = messageType; }
}
