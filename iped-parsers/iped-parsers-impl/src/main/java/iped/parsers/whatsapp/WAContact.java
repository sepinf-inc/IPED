package iped.parsers.whatsapp;

public class WAContact {

    public static final String waSuffix = "@s.whatsapp.net";
    public static final String waStatusBroadcast = "status@broadcast";
    public static final String waGroupSuffix = "@g.us";
    public static final String waStatusSuffix = "@status";
    public static final String waNewsletterSuffix = "@newsletter";

    private String id;

    private String suffix;

    private String status;

    private String displayName;

    private String givenName;

    private String waName;

    private String sortName;

    private String nickName;

    private byte[] avatar;

    private String avatarPath;
    
    private boolean deleted = false;

    public WAContact(String id) {
        updateId(id);
    }

    public String getId() {
        return id;
    }

    public void updateId(String id) {
        if (id != null) {
            String[] idSplit = id.split("@", 2);
            this.id = idSplit[0].trim();
            this.suffix = idSplit.length > 1 ? idSplit[1].trim() : "";
        } else {
            this.id = this.suffix = "";
        }
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getGivenName() {
        return givenName;
    }

    public void setGivenName(String givenName) {
        this.givenName = givenName;
    }

    public String getWaName() {
        return waName;
    }

    public void setWaName(String waName) {
        this.waName = waName;
    }

    public String getSortName() {
        return sortName;
    }

    public void setSortName(String sortName) {
        this.sortName = sortName;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getName() {
        if (displayName != null && !displayName.isBlank())
            return displayName;
        else if (waName != null && !waName.isBlank())
            return waName;
        else if (givenName != null && !givenName.isBlank())
            return givenName;
        else if (nickName != null && !nickName.isBlank())
            return nickName;
        else
            return id;

    }

    public String getTitle() {
        return "WhatsApp Contact: " + getName(); //$NON-NLS-1$
    }

    public byte[] getAvatar() {
        return avatar;
    }

    public void setAvatar(byte[] avatar) {
        this.avatar = avatar;
    }

    public String getFullId() {
        if (id == null || id.isBlank()) {
            return "";
        }
        return id.strip() + (!suffix.isBlank() ? "@" + suffix.strip() : ""); //$NON-NLS-1$
    }

    public String getAvatarPath() {
        return avatarPath;
    }

    public void setAvatarPath(String avatarPath) {
        this.avatarPath = avatarPath;
    }
    
    public boolean isDeleted() {
        return deleted;
    }
    
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    @Override
    public boolean equals(Object o) {
        // If the object is compared with itself then return true 
        if (o == this) {
            return true;
        }
 
        if (o == null || !(o instanceof WAContact)) {
            return false;
        }
         
        WAContact c = (WAContact) o;
         

        return this.getFullId().equals(c.getFullId());

    }

    @Override
    public int hashCode() {
        return this.getFullId().hashCode();
    }

}
