package iped.parsers.ufed.model;

/**
 * Represents a <model type="QuotedMessageData"> element.
 */
public class QuotedMessageData extends BaseModel {

    private static final long serialVersionUID = 7261352646322121901L;

    public QuotedMessageData() {
        super("QuotedMessageData");
    }

    // Specific field getters
    public String getOriginalMessageID() { return (String) getField("OriginalMessageID"); }
    public String getReferenceId() { return (String) getField("ReferenceId"); }
    public String getLabel() { return (String) getField("Label"); }
}