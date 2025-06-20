package iped.parsers.ufed.model;

import java.util.StringJoiner;

/**
 * Base class for entries within a Contact, like PhoneNumber or UserID.
 */
public class ContactEntry extends BaseModel {

    private static final long serialVersionUID = 8086132367609753299L;

    public ContactEntry(String modelType) {
        super(modelType);
    }

    public String getCategory() { return (String) getField("Category"); }
    public String getValue() { return (String) getField("Value"); }
    public String getDomain() { return (String) getField("Domain"); }

    /**
     * Represents a <model type="UserID"> element within a Contact.
     */
    public static class UserID extends ContactEntry {

        private static final long serialVersionUID = -7842611089385593284L;

        public UserID() {
            super("UserID");
        }
    }

    /**
     * Represents a <model type="PhoneNumber"> element within a Contact.
     */
    public static class PhoneNumber extends ContactEntry {

        private static final long serialVersionUID = -3903921936591276832L;

        public PhoneNumber() {
            super("PhoneNumber");
        }
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", getClass().getSimpleName() + "[", "]")
            .add("Category='" + getCategory() + "'")
            .add("Value='" + getValue() + "'")
            .toString();
    }
}