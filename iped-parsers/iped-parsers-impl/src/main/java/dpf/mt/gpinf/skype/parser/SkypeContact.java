package dpf.mt.gpinf.skype.parser;

import java.util.Date;

/**
 * Classe que representa um contato Skype registrada no arquivo main.db.
 *
 * @author Patrick Dalla Bernardina patrick.pdb@dpf.gov.br
 */

public class SkypeContact implements SkypeUser{

    String Id;
    String skypeName;
    String pstnNumber;
    String fullName;
    String city;
    String about;
    String displayName;
    String assignedPhone;
    private byte[] avatar;
    Date birthday;
    String email;
    Date profileDate;
    Date avatarDate;
    Date lastOnlineDate;
    Date lastUsed;

    public String getId() {
        return Id;
    }

    public void setId(String id) {
        Id = id;
    }

    public String getSkypeName() {
        return skypeName;
    }

    public void setSkypeName(String skypeName) {
        this.skypeName = skypeName;
    }

    public String getPstnNumber() {
        return pstnNumber;
    }

    public void setPstnNumber(String pstnNumber) {
        this.pstnNumber = pstnNumber;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
    
    public String getBestName() {
        String name = getFullName();
        if (name == null || name.trim().isEmpty())
            name = getDisplayName();
        if (name == null || name.trim().isEmpty())
            name = getSkypeName();
        return name;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getAbout() {
        return about;
    }

    public void setAbout(String sobre) {
        this.about = sobre;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAssignedPhone() {
        return assignedPhone;
    }

    public void setAssignedPhone(String assignedPhone) {
        this.assignedPhone = assignedPhone;
    }

    public byte[] getAvatar() {
        return avatar;
    }

    public void setAvatar(byte[] avatar) {
        this.avatar = avatar;
    }

    public Date getBirthday() {
        return birthday;
    }

    public void setBirthday(Date birthday) {
        this.birthday = birthday;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Date getProfileDate() {
        return profileDate;
    }

    public void setProfileDate(Date profileDate) {
        this.profileDate = profileDate;
    }

    public Date getAvatarDate() {
        return avatarDate;
    }

    public void setAvatarDate(Date avatarDate) {
        this.avatarDate = avatarDate;
    }

    public Date getLastOnlineDate() {
        return lastOnlineDate;
    }

    public void setLastOnlineDate(Date lastOnlineDate) {
        this.lastOnlineDate = lastOnlineDate;
    }

    public Date getLastUsed() {
        return lastUsed;
    }

    public void setLastUsed(Date lastUsed) {
        this.lastUsed = lastUsed;
    }

}
