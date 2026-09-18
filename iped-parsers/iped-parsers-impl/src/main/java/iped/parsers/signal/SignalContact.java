package iped.parsers.signal;

public class SignalContact {

    private final long id;
    private final String phone;
    private final String profileGivenName;
    private final String profileFamilyName;
    private final String profileJoinedName;
    private final String systemDisplayName;
    private final String groupId;

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
        return "Unknown";
    }

    /** Full identifier used in metadata: display name + phone (if available). */
    public String getFullId() {
        String name = getDisplayName();
        if (phone != null && !phone.isBlank() && !name.equals(phone))
            return name + " (" + phone + ")";
        return name;
    }
}
