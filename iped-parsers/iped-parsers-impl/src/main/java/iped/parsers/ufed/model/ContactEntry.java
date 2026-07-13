package iped.parsers.ufed.model;

import java.util.StringJoiner;

/**
 * Base class for entries within a Contact, like PhoneNumber, UserID or EmailAddress.
 */
public class ContactEntry extends BaseModel {

    private static final long serialVersionUID = 8086132367609753299L;

    public ContactEntry(String modelType) {
        super(modelType);
    }

    public String getCategory() { return (String) getField("Category"); }
    public String getValue() { return (String) getField("Value"); }
    public String getDomain() { return (String) getField("Domain"); }

    @Override
    public String toString() {
        return new StringJoiner(", ", getClass().getSimpleName() + "[", "]")
            .add("Category='" + getCategory() + "'")
            .add("Value='" + getValue() + "'")
            .toString();
    }
}