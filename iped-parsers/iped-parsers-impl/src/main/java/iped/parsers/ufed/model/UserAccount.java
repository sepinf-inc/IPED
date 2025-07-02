package iped.parsers.ufed.model;

import java.util.Date;

/**
 * Represents a <model type="UserAccount"> element.
 */
public class UserAccount extends Accountable {

    private static final long serialVersionUID = -86919640944906671L;
    public UserAccount() {
        super("UserAccount");
    }

    // Specific field getters
    public String getUsername() { return (String) getField("Username"); }
    public String getPassword() { return (String) getField("Password"); }
    public String getServerAddress() { return (String) getField("ServerAddress"); }
    public Date getTimeCreated() { return (Date) getField("TimeCreated"); }

}