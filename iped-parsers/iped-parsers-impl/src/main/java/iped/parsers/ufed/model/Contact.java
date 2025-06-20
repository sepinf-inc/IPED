package iped.parsers.ufed.model;

import static iped.parsers.ufed.util.UfedUtils.readUfedMetadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

import iped.data.IItemReader;
import iped.parsers.ufed.model.ContactEntry.PhoneNumber;
import iped.parsers.ufed.model.ContactEntry.UserID;
import iped.parsers.ufed.reference.ReferencedContact;

/**
 * Represents a <model type="Contact"> element.
 */
public class Contact extends BaseModel {

    private static final long serialVersionUID = 9129744519486979882L;

    private final List<ContactPhoto> photos = new ArrayList<>();

    private UserID userID;
    private PhoneNumber phoneNumber;
    private final Map<String, ContactEntry> otherEntries = new HashMap<>();
    private final Map<String, BaseModel> otherModelFields = new HashMap<>();


    private transient ReferencedContact referencedContact;

    public Contact() {
        super("Contact");
    }

    // Specific field getters
    public String getName() { return (String) getField("Name"); }
    public String getType() { return (String) getField("Type"); }
    public String getAccount() { return (String) getField("Account"); }

    public Optional<UserID> getUserID() {
        return Optional.ofNullable(userID);
    }

    public void setUserID(UserID userID) {
        this.userID = userID;
    }

    public Optional<PhoneNumber> getPhoneNumber() {
        return Optional.ofNullable(phoneNumber);
    }

    public void setPhoneNumber(PhoneNumber phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public Map<String, ContactEntry> getOtherEntries() {
        return otherEntries;
    }

    public List<ContactPhoto> getPhotos() {
        return photos;
    }

    public Map<String, BaseModel> getOtherModelFields() {
        return otherModelFields;
    }

    public ReferencedContact getReferencedContact() {
        return referencedContact;
    }

    public void setReferencedContact(IItemReader contactItem) {
        String referencedId = readUfedMetadata(contactItem, "id");
        if (!referencedId.equals(getId())) {
            throw new IllegalArgumentException("Ufed id doesn't match: " + getId() + " x " + referencedId);
        }
        this.referencedContact = new ReferencedContact(contactItem);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Contact.class.getSimpleName() + "[", "]")
            .add("id='" + getId() + "'")
            .add("Name='" + getName() + "'")
            .add("entries=" + otherEntries)
            .toString();
    }
}