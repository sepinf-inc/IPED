package iped.parsers.android;

public class Contacto {
    private String id;
    private String displayName;
    private String phoneNumbers;
    private String accounts;
    private String emails;
    private String notes;
    private String deleted;
    public Contacto(String id, String displayName, String phoneNumbers, String accounts, String emails, String notes, String deleted) {
        this.id = id;
        this.displayName = displayName;
        this.phoneNumbers = phoneNumbers;
        this.accounts = accounts;
        this.emails = emails;
        this.notes = notes;
        this.deleted = deleted;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this. displayName = displayName;
    }

    public String getPhoneNumbers() {
        return phoneNumbers;
    }

    public void setPhoneNumbers(String phoneNumbers) {
        this. phoneNumbers = phoneNumbers;
    }

    public String getAccounts() {
        return accounts;
    }

    public void setAccounts(String accounts) {
        this. accounts = accounts;
    }

    public String getEmails() {
        return emails;
    }

    public void setEmails(String emails) {
        this. emails = emails;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this. notes = notes;
    }

    public String getDeleted() {
        return deleted;
    }

    public void setDeleted(String deleted) {
        this. deleted = deleted;
    }




}