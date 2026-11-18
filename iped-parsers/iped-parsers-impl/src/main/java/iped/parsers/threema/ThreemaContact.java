package iped.parsers.threema;

public class ThreemaContact {

    private final String suffix = "@threema.ch";

    private final String deviceID;

    private final String firstName;

    private final String lastName;

    private final String nickName;

    private String identity;

    private byte[] avatar;

    private boolean deleted = false;

    public ThreemaContact(String firstName, String lastName, String nickName, String identity, String deviceID) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.nickName = nickName;
        this.identity = identity;
        this.deviceID = deviceID;
    }

    public String getId() {
        if (identity != null && !identity.isBlank())
            return identity;
        else if (deviceID != null && !deviceID.isBlank())
            return deviceID;
        else
            return "";
    }

    public String getFirstName() {
        return (firstName == null) ? "" : firstName;
    }

    public String getLastName() {
        return (lastName == null) ? "" : " " + lastName;
    }

    public String getNickName() {
        return (nickName == null ? "" : " \"" + nickName + "\"");
    }

    public String getTitle() {
        return "Threema Contact: " + nickName; //$NON-NLS-1$
    }

    public byte[] getAvatar() {
        return avatar;
    }

    public void setAvatar(byte[] avatar) {
        this.avatar = avatar;
    }

    public String getFullId() {

        if (identity != null && !identity.isBlank())
            return getFirstName() + getLastName() + getNickName() + " (" + identity + suffix + ")";
        else if (deviceID != null && !deviceID.isBlank())
            return nickName + " (" + deviceID + ")";

        return ""; //$NON-NLS-1$
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    @Override
    public boolean equals(Object o) {
        // If the object is compared with itself then return true
        if (o == this) {
            return true;
        }

        if (o == null || !(o instanceof ThreemaContact)) {
            return false;
        }

        ThreemaContact c = (ThreemaContact) o;

        return this.getFullId().equals(c.getFullId());

    }

    @Override
    public int hashCode() {
        return this.getFullId().hashCode();
    }

}
