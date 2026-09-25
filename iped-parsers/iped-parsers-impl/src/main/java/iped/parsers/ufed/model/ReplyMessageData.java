package iped.parsers.ufed.model;
/**
 * Represents a <model type="ReplyMessageData"> element.
 */
public class ReplyMessageData extends BaseModel {

    private static final long serialVersionUID = 5592672053101949285L;

    private InstantMessage instantMessage;

    public ReplyMessageData() {
        super("ReplyMessageData");
    }

    public String getLabel() { return getFieldAsString("Label"); }
    public String getOriginalMessageID() { return getFieldAsString("OriginalMessageID"); }

    public InstantMessage getInstantMessage() {
        return instantMessage;
    }
    public void setInstantMessage(InstantMessage instantMessage) {
        this.instantMessage = instantMessage;
    }
}