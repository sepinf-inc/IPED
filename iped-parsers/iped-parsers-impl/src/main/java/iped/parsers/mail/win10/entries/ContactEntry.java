package iped.parsers.mail.win10.entries;

public class ContactEntry extends AbstractEntry {
    private int storeId;
    private String displayName;
    private String firstName;
    private String lastName;
    private String email;
    private String emailWork;
    private String emailOther;
    private String phone;
    private String workPhone;
    private String address;
    private boolean hasName;
    private int parentFolderId;

    public ContactEntry(int rowId) {
        super(rowId);
    }

    public int getStoreId() {
        return this.storeId;
    }

    public void setStoreId(int storeId) {
        this.storeId = storeId;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getFirstName() {
        return this.firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return this.lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return this.email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getEmailWork() {
        return this.emailWork;
    }

    public void setEmailWork(String emailWork) {
        this.emailWork = emailWork;
    }

    public String getEmailOther() {
        return this.emailOther;
    }

    public void setEmailOther(String emailOther) {
        this.emailOther = emailOther;
    }

    public String getPhone() {
        return this.phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getWorkPhone() {
        return this.workPhone;
    }

    public void setWorkPhone(String workPhone) {
        this.workPhone = workPhone;
    }

    public String getAddress() {
        return this.address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public boolean isHasName() {
        return this.hasName;
    }

    public boolean getHasName() {
        return this.hasName;
    }

    public void setHasName(boolean hasName) {
        this.hasName = hasName;
    }

    public int getParentFolderId() {
        return this.parentFolderId;
    }

    public void setParentFolderId(int parentFolderId) {
        this.parentFolderId = parentFolderId;
    }

    
}
