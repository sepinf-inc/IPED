package dpf.mt.gpinf.skype.parser;

import java.util.Date;

public class SkypeAccount implements SkypeUser{

    String id;
    String skypeName;
    String about;
    String mood;
    Date moodTimestamp;
    Date birthday;
    Date profileTimestamp;
    String fullname;
    private byte[] avatar;
    Date avatarTimestamp;
    String email;
    String phoneHome;
    String phoneOffice;
    String phoneMobile;
    String city;
    String country;
    String province;
    Date registrationTimestamp;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSkypeName() {
        return skypeName;
    }

    public void setSkypeName(String skypeName) {
        this.skypeName = skypeName;
    }

    public String getAbout() {
        return about;
    }

    public void setAbout(String about) {
        this.about = about;
    }

    public String getMood() {
        return mood;
    }

    public void setMood(String mood) {
        this.mood = mood;
    }

    public Date getMoodTimestamp() {
        return moodTimestamp;
    }

    public void setMoodTimestamp(Date moodTimestamp) {
        this.moodTimestamp = moodTimestamp;
    }

    public Date getBirthday() {
        return birthday;
    }

    public void setBirthday(Date birthday) {
        this.birthday = birthday;
    }

    public Date getProfileTimestamp() {
        return profileTimestamp;
    }

    public void setProfileTimestamp(Date profileTimestamp) {
        this.profileTimestamp = profileTimestamp;
    }

    public String getFullname() {
        return fullname;
    }

    public void setFullname(String fullname) {
        this.fullname = fullname;
    }

    public byte[] getAvatar() {
        return avatar;
    }

    public void setAvatar(byte[] avatar) {
        this.avatar = avatar;
    }

    public Date getAvatarTimestamp() {
        return avatarTimestamp;
    }

    public void setAvatarTimestamp(Date avatarTimestamp) {
        this.avatarTimestamp = avatarTimestamp;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneHome() {
        return phoneHome;
    }

    public void setPhoneHome(String phoneHome) {
        this.phoneHome = phoneHome;
    }

    public String getPhoneOffice() {
        return phoneOffice;
    }

    public void setPhoneOffice(String phoneOffice) {
        this.phoneOffice = phoneOffice;
    }

    public String getPhoneMobile() {
        return phoneMobile;
    }

    public void setPhoneMobile(String phoneMobile) {
        this.phoneMobile = phoneMobile;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public Date getRegistrationTimestamp() {
        return registrationTimestamp;
    }

    public void setRegistrationTimestamp(Date registrationTimestamp) {
        this.registrationTimestamp = registrationTimestamp;
    }
    
    public String getBestName() {
        String name = getFullname();
        if (name == null || name.trim().isEmpty())
            name = getSkypeName();
        return name;
    }

}
