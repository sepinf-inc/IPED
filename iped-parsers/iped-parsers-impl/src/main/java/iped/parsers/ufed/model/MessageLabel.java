package iped.parsers.ufed.model;

import java.util.StringJoiner;

/**
 * Represents a <model type="MessageLabel"> element.
 */
public class MessageLabel extends BaseModel {

    private static final long serialVersionUID = 7409005813881439153L;

    public MessageLabel() {
        super("MessageLabel");
    }

    public String getLabel() { return (String) getField("Label"); }

    @Override
    public String toString() {
        return new StringJoiner(", ", MessageLabel.class.getSimpleName() + "[", "]")
                .add("id='" + getId() + "'")
                .add("Label='" + getLabel() + "'")
                .toString();
    }
}