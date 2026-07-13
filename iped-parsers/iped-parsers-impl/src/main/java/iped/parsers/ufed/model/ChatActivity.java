package iped.parsers.ufed.model;

import java.util.Date;
import java.util.UUID;

/**
 * Represents a <model type="ChatActivity"> element.
 */
public class ChatActivity extends BaseModel {

    private static final long serialVersionUID = 1824242543657876491L;

    private Party participant;

    public ChatActivity() {
        super("ChatActivity");
    }

    public String getAction() { return (String) getField("Action"); }
    public UUID getSystemMessageId() { return (UUID) getField("SystemMessageId"); }
    public Date getSystemMessageTimeStamp() { return (Date) getField("SystemMessageTimeStamp"); }
    public String getSystemMessageBody() { return (String) getField("SystemMessageBody"); }

    public Party getParticipant() {
        return participant;
    }

    public void setParticipant(Party participant) {
        this.participant = participant;
    }
}