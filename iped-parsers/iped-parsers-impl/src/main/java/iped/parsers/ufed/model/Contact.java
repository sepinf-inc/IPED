package iped.parsers.ufed.model;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Represents a <model type="Contact"> element.
 */
public class Contact extends BaseModel {

    private static final long serialVersionUID = 9129744519486979882L;

    private final List<ContactEntry> entries = new ArrayList<>();

    public Contact() {
        super("Contact");
    }

    // Specific field getters
    public String getName() { return (String) getField("Name"); }
    public String getType() { return (String) getField("Type"); }
    public String getAccount() { return (String) getField("Account"); }

    public List<ContactEntry> getEntries() {
        return entries;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Contact.class.getSimpleName() + "[", "]")
            .add("id='" + getId() + "'")
            .add("Name='" + getName() + "'")
            .add("entries=" + entries)
            .toString();
    }
}