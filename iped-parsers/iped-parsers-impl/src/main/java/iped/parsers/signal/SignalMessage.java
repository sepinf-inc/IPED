package iped.parsers.signal;

import java.util.Date;

public class SignalMessage {

    public enum MessageType {
        INCOMING, OUTGOING, SYSTEM, CALL_INCOMING, CALL_OUTGOING, CALL_MISSED;

        public boolean isRegular() {
            return this == INCOMING || this == OUTGOING;
        }
    }

    private long id;
    private long threadId;
    private long fromRecipientId;
    private String body;
    private Date dateSent;
    private Date dateReceived;
    private boolean fromMe;
    private MessageType messageType = MessageType.OUTGOING;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getThreadId() { return threadId; }
    public void setThreadId(long threadId) { this.threadId = threadId; }

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

    public MessageType getMessageType() { return messageType; }
    public void setMessageType(MessageType messageType) { this.messageType = messageType; }
}
