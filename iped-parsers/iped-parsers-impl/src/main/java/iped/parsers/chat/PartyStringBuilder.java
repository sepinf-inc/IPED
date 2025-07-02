package iped.parsers.chat;

import org.apache.commons.lang3.StringUtils;

import iped.parsers.ufed.model.Party;

/**
 * Abstract base class for building party strings for various chat applications.
 * This class follows the Builder pattern to allow for flexible construction of the party string.
 */
public abstract class PartyStringBuilder {

    protected String userId;
    protected String name;
    protected String phoneNumber;
    protected String username;

    public PartyStringBuilder withParty(Party party) {
        party.getReferencedContact().ifPresentOrElse(ref -> {
            this.userId = StringUtils.firstNonBlank(party.getIdentifier(), ref.getUserID());
            this.name = StringUtils.firstNonBlank(party.getName(), ref.getName());
            this.phoneNumber = ref.getPhoneNumber();
            this.username = ref.getUsername();
        }, () -> {
            this.userId = party.getIdentifier();
            this.name = party.getName();
        });
        return this;
    }

    public PartyStringBuilder withUserId(String userId) {
        this.userId = userId;
        return this;
    }

    public PartyStringBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public PartyStringBuilder withPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
        return this;
    }

    public PartyStringBuilder withUsername(String username) {
        this.username = username;
        return this;
    }

    /**
     * Builds the final, formatted party string based on the provided details
     * and the specific rules of the concrete builder implementation.
     *
     * @return The formatted party string.
     */
    public abstract String build();
}
