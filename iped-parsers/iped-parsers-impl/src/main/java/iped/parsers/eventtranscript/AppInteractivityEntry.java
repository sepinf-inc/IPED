package iped.parsers.eventtranscript;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class AppInteractivityEntry {
    private Date timestamp;
    private String timestampStr;
    private String tagName;
    private String eventName;
    private Date aggregationStartTime;
    private String aggregationStartTimeStr;
    private String AggregationDurationMS;
    private String appId;
    private String appVersionDate;
    private String PEHeaderChecksum;
    private String type;
    private String windowSize;
    private String mouseInputSec;
    private String inFocusDurationMS;
    private String userActivityDurationMS;
    private String sinceFirstInteractivityMS;
    private String userOrDisplayActiveDuration;
    private String focusLostCount;
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
        this.aggregationStartTimeStr = aggregationStartTime;
        if (!aggregationStartTime.isEmpty()) {
            SimpleDateFormat format = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
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

    public String getAggregationDurationMS() {
        return this.AggregationDurationMS;
    }


    public String getAppId() {
        return this.appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
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

    public String getInFocusDurationMS() {
        return this.inFocusDurationMS;
    }

    public void setInFocusDurationMS(String inFocusDurationMS) {
        this.inFocusDurationMS = inFocusDurationMS;
    }

    public String getUserActivityDurationMS() {
        return this.userActivityDurationMS;
    }

    public void setUserActivityDurationMS(String userActivityDurationMS) {
        this.userActivityDurationMS = userActivityDurationMS;
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

    public String getJSONPayload() {
        return this.JSONPayload;
    }

    public void setJSONPayload(String JSONPayload) {
        this.JSONPayload = JSONPayload;
    }

}
