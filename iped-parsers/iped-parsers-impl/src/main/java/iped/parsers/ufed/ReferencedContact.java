package iped.parsers.ufed;

import static iped.parsers.ufed.UfedUtils.readUfedMetadata;

import iped.data.IItemReader;

public class ReferencedContact extends AbstractReferencedItem {

    public ReferencedContact(IItemReader item) {
        super(item);
    }

    public String getName() {
        return readUfedMetadata(item, "name");
    }

    public String getPhoneNumber() {
        return readUfedMetadata(item, "PhoneNumber");
    }

    public String getUsername() {
        return readUfedMetadata(item, "Username");
    }

    public String getUserID() {
        return readUfedMetadata(item, "UserID");
    }
}
