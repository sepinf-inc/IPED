package iped.parsers.ufed.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Represents a <model type="Email"> element.
 */
public class Email extends BaseModel {
    private static final long serialVersionUID = 8185611534854025504L;
    private Optional<Party> from = Optional.empty();
    private final List<Party> to = new ArrayList<>();
    private final List<Party> cc = new ArrayList<>();
    private final List<Party> bcc = new ArrayList<>();
    private final List<Attachment> attachments = new ArrayList<>();

    public Email() {
        super("Email");
    }

    // Specific field getters
    public String getFolder() { return getFieldAsString("Folder"); }
    public String getSubject() { return getFieldAsString("Subject"); }
    public String getBody() { return getFieldAsString("Body"); }
    public String getSnippet() { return getFieldAsString("Snippet"); }
    public Date getTimeStamp() { return (Date) getField("TimeStamp"); }
    public String getEmailHeader() { return getFieldAsString("EmailHeader"); }
    public String getAccount() { return getFieldAsString("Account"); }
    public String getStatus() { return getFieldAsString("Status"); }
    public String getSource() { return getFieldAsString("Source"); }

    public List<String> getLabels() {
        return getFieldAsList("Labels");
    }

    // Child model getters/setters
    public Optional<Party> getFrom() { return from; }
    public void setFrom(Party from) { this.from = Optional.of(from); }
    public List<Party> getTo() { return to; }
    public List<Party> getCc() { return cc; }
    public List<Party> getBcc() { return bcc; }
    public List<Attachment> getAttachments() { return attachments; }

    public boolean isFromPhoneOwner() {
        return getFrom().map(Party::isPhoneOwner).orElse(false);
    }
}