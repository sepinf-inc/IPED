package iped.parsers.zoomdpapi;

/**
 * Zoom user account information extracted from zoomus.enc.db.
 *
 * @author Calil Khalil (Hakal)
 */
public class ZoomUserAccount {

    private String email;
    private String zoomEmail;
    private String firstName;
    private String lastName;
    private String zoomJid;
    private String zoomUserId;
    private String clientVersion;
    private long lastLoginTime;
    private int userType;
    private int accountType;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getZoomEmail() { return zoomEmail; }
    public void setZoomEmail(String zoomEmail) { this.zoomEmail = zoomEmail; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getZoomJid() { return zoomJid; }
    public void setZoomJid(String zoomJid) { this.zoomJid = zoomJid; }

    public String getZoomUserId() { return zoomUserId; }
    public void setZoomUserId(String zoomUserId) { this.zoomUserId = zoomUserId; }

    public String getClientVersion() { return clientVersion; }
    public void setClientVersion(String clientVersion) { this.clientVersion = clientVersion; }

    public long getLastLoginTime() { return lastLoginTime; }
    public void setLastLoginTime(long lastLoginTime) { this.lastLoginTime = lastLoginTime; }

    public int getUserType() { return userType; }
    public void setUserType(int userType) { this.userType = userType; }

    public int getAccountType() { return accountType; }
    public void setAccountType(int accountType) { this.accountType = accountType; }

    public String getName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }
        if (firstName != null) return firstName;
        if (lastName != null) return lastName;
        if (email != null) return email;
        return zoomJid;
    }
}
