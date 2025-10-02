package iped.parsers.ufed.model;

import java.util.Date;
import java.util.Optional;
import java.util.StringJoiner;

import org.apache.commons.lang3.builder.EqualsBuilder;

import iped.data.IItemReader;
import iped.parsers.ufed.reference.ReferencedAccountable;

/**
 * Represents a <model type="Party"> element.
 */
public class Party extends BaseModel {

    private static final long serialVersionUID = -8316756774909953715L;

    public static final String SYSTEM_MESSAGE = "System Message";

    private transient Optional<ReferencedAccountable> referencedContact = Optional.empty();

    public Party() {
        super("Party");
    }

    // Specific field getters
    public String getIdentifier() { return (String) getField("Identifier"); }
    public String getName() { return (String) getField("Name"); }
    public Date getDateDelivered() { return (Date) getField("DateDelivered"); }
    public Date getDateRead() { return (Date) getField("DateRead"); }
    public Date getDatePlayed() { return (Date) getField("DatePlayed"); }

    public boolean isPhoneOwner() {
        Object value = getField("IsPhoneOwner");
        return value instanceof Boolean && (Boolean) value;
    }

    public boolean isGroupAdmin() {
        Object value = getField("IsGroupAdmin");
        return value instanceof Boolean && (Boolean) value;
    }

    public boolean isSystemMessage() {
        return SYSTEM_MESSAGE.equalsIgnoreCase(getIdentifier());
    }

    public Optional<ReferencedAccountable> getReferencedContact() {
        return referencedContact;
    }

    public void setReferencedContact(IItemReader contactItem) {
        this.referencedContact = Optional.of(new ReferencedAccountable(contactItem));
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Party.class.getSimpleName() + "[", "]")
                .add("id='" + getId() + "'")
                .add("Identifier='" + getIdentifier() + "'")
                .add("Name='" + getName() + "'")
                .add("isPhoneOwner=" + isPhoneOwner())
                .toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        Party other = (Party) obj;

        return new EqualsBuilder()
                .append(getIdentifier(), other.getIdentifier())
                .isEquals();
    }
}