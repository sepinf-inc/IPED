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

    public String getCategory() { return getFieldAsString("Category"); }
    public String getValue() { return getFieldAsString("Value"); }
    public String getDomain() { return getFieldAsString("Domain"); }

    @Override
    public String toString() {
        return new StringJoiner(", ", getClass().getSimpleName() + "[", "]")
            .add("Category='" + getCategory() + "'")
            .add("Value='" + getValue() + "'")
            .toString();
    }
}