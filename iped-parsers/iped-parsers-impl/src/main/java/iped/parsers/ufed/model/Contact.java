package iped.parsers.ufed.model;

import static iped.parsers.ufed.util.UfedUtils.readUfedMetadata;

import java.util.Optional;

import iped.data.IItemReader;
import iped.parsers.ufed.reference.ReferencedAccountable;

/**
 * Represents a <model type="Contact"> element.
 */
public class Contact extends Accountable {

    private static final long serialVersionUID = 9129744519486979882L;

    // used for shared contacts
    private transient Optional<ReferencedAccountable> referencedContact = Optional.empty();

    public Contact() {
        super("Contact");
    }

    // Specific field getters
    public String getType() { return (String) getField("Type"); }
    public String getGroup() { return (String) getField("Group"); }
    public String getInteractionStatuses() { return (String) getField("InteractionStatuses"); }

    public Optional<ReferencedAccountable> getReferencedContact() {
        return referencedContact;
    }

    public void setReferencedContact(IItemReader contactItem) {
        String referencedId = readUfedMetadata(contactItem, "id");
        if (!referencedId.equals(getId())) {
            throw new IllegalArgumentException("Ufed id doesn't match: " + getId() + " x " + referencedId);
        }
        this.referencedContact = Optional.of(new ReferencedAccountable(contactItem));
    }
}