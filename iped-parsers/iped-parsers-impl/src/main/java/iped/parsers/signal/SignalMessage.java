package iped.parsers.signal;

import java.util.Date;

public class SignalMessage {

    public enum MessageType {
        INCOMING, OUTGOING, SYSTEM, CALL_INCOMING, CALL_OUTGOING, CALL_MISSED, CALL_GROUP

    }

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

    public boolean isScheduled() { return scheduled; }
    public void setScheduled(boolean scheduled) { this.scheduled = scheduled; }

    public String getCallDetail() { return callDetail; }
    public void setCallDetail(String callDetail) { this.callDetail = callDetail; }

    public MessageType getMessageType() { return messageType; }
    public void setMessageType(MessageType messageType) { this.messageType = messageType; }
}
