package iped.parsers.mail.win10.entries;

public class StoreEntry extends AbstractEntry {
    String displayName;
    String address;
    String protocol;
    long synchOptionFlags;
    long downloadNewEmailMins;
    long downloadEmailFromDays;
    String incomingEmailServer;
    String outgoingEmailServer;
    String outgoingEmailServerUsername;
    String contactsServer;
    String calendarServer;

    public StoreEntry(int rowId) {
        super(rowId);
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAddress() {
        return this.address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getProtocol() {
        return this.protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public long getSynchOptionFlags() {
        return this.synchOptionFlags;
    }

    public void setSynchOptionFlags(long synchOptionFlags) {
        this.synchOptionFlags = synchOptionFlags;
    }

    public long getDownloadNewEmailMins() {
        return this.downloadNewEmailMins;
    }

    public void setDownloadNewEmailMins(long downloadNewEmailMins) {
        this.downloadNewEmailMins = downloadNewEmailMins;
    }

    public long getDownloadEmailFromDays() {
        return this.downloadEmailFromDays;
    }

    public void setDownloadEmailFromDays(long downloadEmailFromDays) {
        this.downloadEmailFromDays = downloadEmailFromDays;
    }

    public String getIncomingEmailServer() {
        return this.incomingEmailServer;
    }

    public void setIncomingEmailServer(String incomingEmailServer) {
        this.incomingEmailServer = incomingEmailServer;
    }

    public String getOutgoingEmailServer() {
        return this.outgoingEmailServer;
    }

    public void setOutgoingEmailServer(String outgoingEmailServer) {
        this.outgoingEmailServer = outgoingEmailServer;
    }

    public String getOutgoingEmailServerUsername() {
        return this.outgoingEmailServerUsername;
    }

    public void setOutgoingEmailServerUsername(String outgoingEmailServerUsername) {
        this.outgoingEmailServerUsername = outgoingEmailServerUsername;
    }

    public String getContactsServer() {
        return this.contactsServer;
    }

    public void setContactsServer(String contactsServer) {
        this.contactsServer = contactsServer;
    }

    public String getCalendarServer() {
        return this.calendarServer;
    }

    public void setCalendarServer(String calendarServer) {
        this.calendarServer = calendarServer;
    }
}
