package iped.parsers.signal;

public class SignalContact {

    private static final String UNKNOWN = "Unknown";

    private final long id;
    private final String phone;
    private final String profileGivenName;
    private final String profileFamilyName;
    private final String profileJoinedName;
    private final String systemDisplayName;
    private final String groupId;
    // Signal account identifier and username. Since phone number privacy (2024) a contact
    // may have no phone number at all, and these are then the only stable identifiers.
    private String aci;
    private String username;

    public SignalContact(long id, String phone, String profileGivenName, String profileFamilyName,
            String profileJoinedName, String systemDisplayName, String groupId) {
        this.id = id;
        this.phone = phone;
        this.profileGivenName = profileGivenName;
        this.profileFamilyName = profileFamilyName;
        this.profileJoinedName = profileJoinedName;
        this.systemDisplayName = systemDisplayName;
        this.groupId = groupId;
    }

    public long getId() {
        return id;
    }

    public void setAci(String aci) {
        this.aci = aci;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPhone() {
        return phone;
    }

    public boolean isGroup() {
        return groupId != null && !groupId.isBlank();
    }

    public String getGroupId() {
        return groupId;
    }

    /** Best available display name for this contact. */
    /** True when the recipient has any name or phone number of its own. */
    public boolean isIdentified() {
        return !getDisplayName().equals(UNKNOWN);
    }

    public String getDisplayName() {
        if (systemDisplayName != null && !systemDisplayName.isBlank())
            return systemDisplayName;
        if (profileJoinedName != null && !profileJoinedName.isBlank())
            return profileJoinedName;
        if (profileGivenName != null && !profileGivenName.isBlank()) {
            String full = profileGivenName;
            if (profileFamilyName != null && !profileFamilyName.isBlank())
                full += " " + profileFamilyName;
            return full;
        }
        if (phone != null && !phone.isBlank())
            return phone;
        if (username != null && !username.isBlank())
            return username;
        return UNKNOWN;
    }

    /**
     * Full identifier used in metadata: display name + phone (if available). Recipients
     * with neither name nor phone are qualified with their recipient id, so that
     * unrelated unidentified people do not collapse into a single node in link analysis.
     */
    public String getFullId() {
        String name = getDisplayName();
        if (phone != null && !phone.isBlank() && !name.equals(phone))
            return name + " (" + phone + ")";
        // Without a phone number the account id keeps people apart, and keeps the same
        // person together across devices, which a database-local recipient id cannot do
        if (aci != null && !aci.isBlank())
            return name + " (" + aci + ")";
        // Signal usernames are reserved, so one identifies the account across devices,
        // which a database-local recipient id cannot do
        if (username != null && !username.isBlank())
            return name.equals(username) ? name : name + " (" + username + ")";
        // Nothing global identifies this contact: the recipient id at least keeps two
        // people with the same name apart within the case
        if (!isIdentified())
            return name + " (rid:" + id + ")";
        return name;
    }
}
