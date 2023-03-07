package iped.parsers.eventtranscript;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class DevicesEntry {
    private Date timestamp;
    private String timestampStr;
    private String localTime;
    private String timezone;
    private String model;
    private String tagName;
    private String eventName;
    private String provider;
    private String manufacturer;
    private String enumerator;
    private String instanceId;
    private Date installDate;
    private String installDateStr;
    private Date firstInstallDate;
    private String firstInstallDateStr;
    private String userSID;
    private String userId;
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

    public String getModel() {
        return this.model;
    }

    public void setModel(String model) {
        this.model = model;
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

    public String getProvider() {
        return this.provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getManufacturer() {
        return this.manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getEnumerator() {
        return this.enumerator;
    }

    public void setEnumerator(String enumerator) {
        this.enumerator = enumerator;
    }

    public String getInstanceId() {
        return this.instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
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
            SimpleDateFormat format = new SimpleDateFormat("MM-dd-yyy");
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            setInstallDate(format.parse(installDate));
        }
    }

    public String getInstallDateStr() {
        return this.installDateStr;
    }

    public Date getFirstInstallDate() {
        return this.firstInstallDate;
    }

    public String getFirstInstallDateStr() {
        return this.firstInstallDateStr;
    }

    public void setFirstInstallDate(Date firstInstallDate) {
        this.firstInstallDate = firstInstallDate;
    }

    public void setFirstInstallDate(String firstInstallDate) throws ParseException {
        this.firstInstallDateStr = firstInstallDate != null ? firstInstallDate : "";
        if (!firstInstallDateStr.isEmpty()) {
            SimpleDateFormat format = new SimpleDateFormat("MM-dd-yyy");
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            setFirstInstallDate(format.parse(firstInstallDate));
        }
    }

    public String getUserSID() {
        return this.userSID;
    }

    public void setUserSID(String userSID) {
        this.userSID = userSID;
    }

    public String getUserId() {
        return this.userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getJSONPayload() {
        return this.JSONPayload;
    }

    public void setJSONPayload(String JSONPayload) {
        this.JSONPayload = JSONPayload;
    }

}
