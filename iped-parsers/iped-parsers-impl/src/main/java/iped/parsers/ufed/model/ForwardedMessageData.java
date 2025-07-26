package iped.parsers.ufed.model;

import java.util.Date;

/**
 * Represents a <model type="ForwardedMessageData"> element.
 */
public class ForwardedMessageData extends BaseModel {

    private static final long serialVersionUID = -8207503266072934828L;

    private Party originalSender;

    public ForwardedMessageData() {
        super("ForwardedMessageData");
    }

    public String getLabel() { return (String) getField("Label"); }
    public Date getTimeCreated() { return (Date) getField("TimeCreated"); }

    public Party getOriginalSender() {
        return originalSender;
    }
    public void setOriginalSender(Party originalSender) {
        this.originalSender = originalSender;
    }
}