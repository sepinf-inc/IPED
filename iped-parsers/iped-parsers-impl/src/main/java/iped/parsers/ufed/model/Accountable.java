package iped.parsers.ufed.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Represents a <model type="Contact"> or <model type="UserAccount">  element.
 */
public abstract class  Accountable extends BaseModel {

    private static final long serialVersionUID = -1873500968452011349L;

    private final List<ContactPhoto> photos = new ArrayList<>();

    private final Map<String, ContactEntry> contactEntries = new LinkedHashMap<>();
    private final Map<String, BaseModel> otherModelFields = new LinkedHashMap<>();


    protected Accountable(String modelType) {
        super(modelType);
    }

    // Specific field getters
    public String getName() { return (String) getField("Name"); }
    public String getSource() { return (String) getField("Source"); }
    public String getAccount() { return (String) getField("Account"); }

    public Optional<ContactEntry> getUserID() {
        return Optional.ofNullable(contactEntries.get("UserID"));
    }

    public Optional<ContactEntry> getPhoneNumber() {
        return Optional.ofNullable(contactEntries.get("PhoneNumber"));
    }

    public Optional<ContactEntry> getEmailAddress() {
        return Optional.ofNullable(contactEntries.get("EmailAddress"));
    }

    public Map<String, ContactEntry> getContactEntries() {
        return contactEntries;
    }

    public List<ContactPhoto> getPhotos() {
        return photos;
    }

    public Map<String, BaseModel> getOtherModelFields() {
        return otherModelFields;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", getClass().getSimpleName() + "[", "]")
            .add("id='" + getId() + "'")
            .add("Name='" + getName() + "'")
            .add("entries=" + contactEntries)
            .toString();
    }
}