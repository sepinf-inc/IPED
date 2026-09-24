package iped.parsers.zoomdpapi;

/**
 * A meeting participant extracted from zoom_meet_participants.
 *
 * @author Calil Khalil (Hakal)
 */
public class ZoomParticipant {

    private String odId;
    private String name;
    private String odEmail;
    private String odAvatar;
    private int roleType;

    public String getOdId() { return odId; }
    public void setOdId(String odId) { this.odId = odId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getOdEmail() { return odEmail; }
    public void setOdEmail(String odEmail) { this.odEmail = odEmail; }

    public String getOdAvatar() { return odAvatar; }
    public void setOdAvatar(String odAvatar) { this.odAvatar = odAvatar; }

    public int getRoleType() { return roleType; }
    public void setRoleType(int roleType) { this.roleType = roleType; }

    public String getRoleName() {
        switch (roleType) {
            case 1: return "Host";
            case 2: return "Co-Host";
            default: return "Participant";
        }
    }
}
