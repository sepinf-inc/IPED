package iped.parsers.mail.win10.entries;

public class RecipientEntry extends AbstractEntry {
    private long messageId;
    private String displayName;
    private RecipientType type;

    public RecipientEntry(int rowId, long messageId, String displayName, RecipientType type) {
        super(rowId);
        this.messageId = messageId;
        this.displayName = displayName;
        this.type = type;
    }

    public long getMessageId() {
        return this.messageId;
    }

    public void setMessageId(int messageId) {
        this.messageId = messageId;
    }


    public String getDisplayName() {
        return this.displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public RecipientType getType() {
        return this.type;
    }

    public void setType(RecipientType type) {
        this.type = type;
    }

    public enum RecipientType {
        FROM, TO, CC, BCC;
    }
}
