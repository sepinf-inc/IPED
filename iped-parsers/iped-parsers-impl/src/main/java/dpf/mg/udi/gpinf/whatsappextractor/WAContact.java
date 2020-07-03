package dpf.mg.udi.gpinf.whatsappextractor;

public class WAContact {

    private final String id;

    private final String suffix;

    private String status;

    private String displayName;

    private String givenName;

    private String waName;

    private String sortName;

    private String nickName;

    private byte[] avatar;

    private String avatarPath;

    public WAContact(String id) {
        if (id != null && id.contains("@")) { //$NON-NLS-1$
            String[] id_split = id.split("@"); //$NON-NLS-1$
            this.id = id_split[0];
            this.suffix = id_split[1];
        } else {
            this.id = id;
            this.suffix = ""; //$NON-NLS-1$
        }
    }

    public String getId() {
        return id;
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
        if (waName != null)
            return waName;
        else if (displayName != null)
            return displayName;
        else if (givenName != null)
            return givenName;
        else if (nickName != null)
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
        return id + "@" + suffix; //$NON-NLS-1$
    }

    public String getAvatarPath() {
        return avatarPath;
    }

    public void setAvatarPath(String avatarPath) {
        this.avatarPath = avatarPath;
    }
}
