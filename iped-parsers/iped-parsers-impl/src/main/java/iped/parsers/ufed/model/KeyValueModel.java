package iped.parsers.ufed.model;

import java.util.StringJoiner;

/**
 * Represents a <model type="KeyValueModel">, used for additional information.
 */
public class KeyValueModel extends BaseModel {
    private static final long serialVersionUID = 7457842257760567678L;

    public KeyValueModel() {
        super("KeyValueModel");
    }

    public String getKey() { return (String) getField("Key"); }
    public String getValue() { return (String) getField("Value"); }

    @Override
    public String toString() {
        return new StringJoiner(", ", KeyValueModel.class.getSimpleName() + "[", "]")
                .add("Key='" + getKey() + "'")
                .add("Value='" + getValue() + "'")
                .toString();
    }
}