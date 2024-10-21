package iped.parsers.ufed;

import static iped.parsers.ufed.UfedUtils.readUfedMetadata;

import iped.data.IItemReader;

public class MessageContact extends AbstractMessageSubitem {

    private ReferencedContact referencedContact;

    public MessageContact(IItemReader item) {
        super(item);
    }

    public String getName() {
        return readUfedMetadata(item, "Name");
    }
    
    public String getType() {
        return readUfedMetadata(item, "Type");
    }

    public ReferencedContact getReferencedContact() {
        return referencedContact;
    }

    public void setReferencedContact(IItemReader referencedItem) {
        String referencedId = readUfedMetadata(referencedItem, "id");
        String id = readUfedMetadata(item, "id");
        if (!id.equals(referencedId)) {
            throw new IllegalArgumentException("Ufed id doesn't match: " + id + " x " + referencedId);
        }
        this.referencedContact = new ReferencedContact(referencedItem);
    }
}
