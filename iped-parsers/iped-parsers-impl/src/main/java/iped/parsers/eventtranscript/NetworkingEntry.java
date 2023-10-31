package iped.parsers.eventtranscript;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class NetworkingEntry {
    private Date UTCTimestamp;
    private String UTCTimestampStr;
    private String localTime;
    private String timezone;
    private String eventName;
    private String seq;
    private String eventSource;
    private String eventReason;
    private String dataJSON;
    private String JSONPayload;

    public Date getUTCTimestamp() {
        return this.UTCTimestamp;
    }

    public String getUTCTimestampStr() {
        return this.UTCTimestampStr;
    }

    public void setUTCTimestamp(Date UTCTimestamp) {
        this.UTCTimestamp = UTCTimestamp;
    }

    public void setUTCTimestamp(String UTCTimestamp) throws ParseException {
        this.UTCTimestampStr = UTCTimestamp;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        setUTCTimestamp(format.parse(UTCTimestamp));
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

    public String getSeq() {
        return this.seq;
    }

    public void setSeq(String seq) {
        this.seq = seq;
    }

    public String getEventSource() {
        return this.eventSource;
    }

    public void setEventSource(String eventSource) {
        this.eventSource = eventSource;
    }

    public String getEventReason() {
        return this.eventReason;
    }

    public void setEventReason(String eventReason) {
        this.eventReason = eventReason;
    }

    public String getDataJSON() {
        return this.dataJSON;
    }

    public void setDataJSON(String dataJSON) {
        this.dataJSON = dataJSON;
    }

    public String getJSONPayload() {
        return this.JSONPayload;
    }

    public void setJSONPayload(String JSONPayload) {
        this.JSONPayload = JSONPayload;
    }

}
