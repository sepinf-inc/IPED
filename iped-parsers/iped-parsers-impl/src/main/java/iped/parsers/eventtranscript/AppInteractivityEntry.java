package iped.parsers.eventtranscript;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class AppInteractivityEntry {
    private String app;
    private Date timestamp;
    private String timestampStr;
    private String localTime;
    private String timezone;
    private String tagName;
    private String eventName;
    private Date aggregationStartTime;
    private String aggregationStartTimeStr;
    private String aggregationDuration;
    private String appVersionDate;
    private String PEHeaderChecksum;
    private String type;
    private String windowSize;
    private String mouseInputSec;
    private String inFocusDuration;
    private String userActiveDuration;
    private String sinceFirstInteractivityMS;
    private String userOrDisplayActiveDuration;
    private String focusLostCount;
    private String programID;
    private String userID;
    private String userSID;
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

    public String getTimestampStr() {
        return this.timestampStr;
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

    public Date getAggregationStartTime() {
        return this.aggregationStartTime;
    }

    public void setAggregationStartTime(String aggregationStartTime) throws ParseException {
        this.aggregationStartTimeStr = aggregationStartTime != null ? aggregationStartTime : "";
        if (!aggregationStartTime.isEmpty()) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            setAggregationStartTime(format.parse(aggregationStartTime));
        }
    }

    public void setAggregationStartTime(Date aggregationStartTime) {
        this.aggregationStartTime = aggregationStartTime;
    }

    public String getAggregationStartTimeStr() {
        return this.aggregationStartTimeStr;
    }

    public String getAggregationDuration() {
        return this.aggregationDuration;
    }

    public void setAggregationDuration(String aggregationDuration) {
        this.aggregationDuration = aggregationDuration;
    }

    public String getApp() {
        return this.app;
    }

    public void setApp(String appId) {
        this.app = appId;
    }

    public String getAppVersionDate() {
        return this.appVersionDate;
    }

    public void setAppVersionDate(String appVersionDate) {
        this.appVersionDate = appVersionDate;
    }

    public String getPEHeaderChecksum() {
        return this.PEHeaderChecksum;
    }

    public void setPEHeaderChecksum(String PEHeaderChecksum) {
        this.PEHeaderChecksum = PEHeaderChecksum;
    }

    public String getType() {
        return this.type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getWindowSize() {
        return this.windowSize;
    }

    public void setWindowSize(String windowSize) {
        this.windowSize = windowSize;
    }

    public String getMouseInputSec() {
        return this.mouseInputSec;
    }

    public void setMouseInputSec(String mouseInputSec) {
        this.mouseInputSec = mouseInputSec;
    }

    public String getInFocusDuration() {
        return this.inFocusDuration;
    }

    public void setInFocusDuration(String inFocusDurationMS) {
        this.inFocusDuration = inFocusDurationMS;
    }

    public String getUserActiveDuration() {
        return this.userActiveDuration;
    }

    public void setUserActiveDuration(String userActiveDuration) {
        this.userActiveDuration = userActiveDuration;
    }

    public String getSinceFirstInteractivityMS() {
        return this.sinceFirstInteractivityMS;
    }

    public void setSinceFirstInteractivityMS(String sinceFirstInteractivityMS) {
        this.sinceFirstInteractivityMS = sinceFirstInteractivityMS;
    }

    public String getUserOrDisplayActiveDuration() {
        return this.userOrDisplayActiveDuration;
    }

    public void setUserOrDisplayActiveDuration(String userOrDisplayActiveDuration) {
        this.userOrDisplayActiveDuration = userOrDisplayActiveDuration;
    }

    public String getFocusLostCount() {
        return this.focusLostCount;
    }

    public void setFocusLostCount(String focusLostCount) {
        this.focusLostCount = focusLostCount;
    }

    public String getProgramID() {
        return this.programID;
    }

    public void setProgramID(String programID) {
        this.programID = programID;
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
