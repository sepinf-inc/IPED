package iped.parsers.eventtranscript;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import com.github.openjson.JSONObject;

public class CensusEntry {
    private Date timestamp;
    private String timestampStr;
    private String localTime;
    private String timezone;
    private String eventName;
    private String tagName;
    private String dataJSON;
    private String userID;
    private String userSID;
    private String JSONPayload;

    public Date getTimestamp() {
        return this.timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public String getTimestampStr() {
        return this.timestampStr;
    }

    public void setTimestamp(String timestamp) throws ParseException {
        this.timestampStr = timestamp;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        setTimestamp(format.parse(timestamp));
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

    public String getEventName() {
        return this.eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getTagName() {
        return this.tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public String getDataJSON() {
        return this.dataJSON;
    }

    public void setDataJSON(String dataJSON) {
        JSONObject prettyDataJSON = new JSONObject(dataJSON);
        this.dataJSON = prettyDataJSON.toString(2);
    }

    public String getUserID() {
        return this.userID;
    }

    public void setUserID(String userID) {
        this.userID = userID;
    }

    public String getUserSID() {
        return this.userSID;
    }

    public void setUserSID(String userSID) {
        this.userSID = userSID;
    }

    public String getJSONPayload() {
        return this.JSONPayload;
    }

    public void setJSONPayload(String JSONPayload) {
        this.JSONPayload = JSONPayload;
    }

}
