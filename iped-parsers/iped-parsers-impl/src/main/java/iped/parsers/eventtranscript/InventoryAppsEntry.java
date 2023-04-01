package iped.parsers.eventtranscript;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class InventoryAppsEntry {
    private Date timestamp;
    private String timestampStr;
    private String localTime;
    private String timezone;
    private String tagName;
    private String eventName;
    private String type;
    private String name;
    private String packageFullName;
    private String version;
    private String publisher;
    private String rootDirPath;
    private String hidden;
    private Date installDate;
    private String source;
    private String installDateStr;
    private String OSVersionAtInstallTime;
    private String userSID;
    private String userID;
    private String JSONPayload;

    public Date getTimestamp() {
        return this.timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public void setTimestamp(String timestamp) throws ParseException {
        this.timestampStr = timestamp;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        setTimestamp(format.parse(timestamp));
    }

    public String getTimestampStr() {
        return this.timestampStr;
    }

    public String getLocalTime() {
        return this.localTime;
    }

    public void setLocalTime(String localTime) {
        this.localTime = localTime;
    }

    public String getTimezone() {
        return this.timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getTagName() {
        return this.tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public String getEventName() {
        return this.eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getType() {
        return this.type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPackageFullName() {
        return this.packageFullName;
    }

    public void setPackageFullName(String packageFullName) {
        this.packageFullName = packageFullName;
    }

    public String getVersion() {
        return this.version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getPublisher() {
        return this.publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public String getRootDirPath() {
        return this.rootDirPath;
    }

    public void setRootDirPath(String rootDirPath) {
        this.rootDirPath = rootDirPath;
    }

    public String getHidden() {
        return this.hidden;
    }

    public void setHidden(String hidden) {
        this.hidden = hidden;
    }

    public Date getInstallDate() {
        return this.installDate;
    }

    public void setInstallDate(Date installDate) {
        this.installDate = installDate;
    }

    public void setInstallDate(String installDate) throws ParseException {
        this.installDateStr = installDate != null ? installDate : "";
        if (!installDateStr.isEmpty()) {
            SimpleDateFormat format = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            setInstallDate(format.parse(installDate));
        }
    }

    public String getInstallDateStr() {
        return this.installDateStr;
    }

    public String getSource() {
        return this.source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getOSVersionAtInstallTime() {
        return this.OSVersionAtInstallTime;
    }

    public void setOSVersionAtInstallTime(String OSVersionAtInstallTime) {
        this.OSVersionAtInstallTime = OSVersionAtInstallTime;
    }

    public String getUserSID() {
        return this.userSID;
    }

    public void setUserSID(String userSID) {
        this.userSID = userSID;
    }

    public String getUserID() {
        return this.userID;
    }

    public void setUserID(String userID) {
        this.userID = userID;
    }

    public String getJSONPayload() {
        return this.JSONPayload;
    }

    public void setJSONPayload(String JSONPayload) {
        this.JSONPayload = JSONPayload;
    }
}
