package iped.parsers.chat;

/**
 * Abstract base class for building party strings for various chat applications.
 * This class follows the Builder pattern to allow for flexible construction of the party string.
 */
public abstract class PartyStringBuilder {

    protected String name;
    protected String phoneNumber;
    protected String userId;
    protected String username;

    public PartyStringBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public PartyStringBuilder withPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
        return this;
    }

    public PartyStringBuilder withUserId(String userId) {
        this.userId = userId;
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
