package iped.parsers.eventtranscript;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class BrowserHistoryEntry {
    private String userSID;
    private Date timestamp;
    private String locale;
    private String producer;
    private String tag;
    private String fullEventName;
    private String loggingBinaryName;
    private String fullEventNameHash;
    private String keywords;
    private String groupGUID;
    private String isCore;
    private String compressedPayloadSize;
    private String JSONPayload;

    public String getUserSID() {
        return this.userSID;
    }

    public void setUserSID(String userSID) {
        this.userSID = userSID;
    }

    public Date getTimestamp() {
        return this.timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public void setTimestamp(String timestamp) throws ParseException {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        setTimestamp(format.parse(timestamp));
    }

    public String getLocale() {
        return this.locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getProducer() {
        return this.producer;
    }

    public void setProducer(String producer) {
        this.producer = producer;
    }

    public String getTag() {
        return this.tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getFullEventName() {
        return this.fullEventName;
    }

    public void setFullEventName(String fullEventName) {
        this.fullEventName = fullEventName;
    }

    public String getLoggingBinaryName() {
        return this.loggingBinaryName;
    }

    public void setLoggingBinaryName(String loggingBinaryName) {
        this.loggingBinaryName = loggingBinaryName;
    }

    public String getFullEventNameHash() {
        return this.fullEventNameHash;
    }

    public void setFullEventNameHash(String fullEventNameHash) {
        this.fullEventNameHash = fullEventNameHash;
    }

    public String getKeywords() {
        return this.keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public String getGroupGUID() {
        return this.groupGUID;
    }

    public void setGroupGUID(String groupGUID) {
        this.groupGUID = groupGUID;
    }

    public String getIsCore() {
        return this.isCore;
    }

    public void setIsCore(String isCore) {
        this.isCore = isCore;
    }

    public String getCompressedPayloadSize() {
        return this.compressedPayloadSize;
    }

    public void setCompressedPayloadSize(String compressedPayloadSize) {
        this.compressedPayloadSize = compressedPayloadSize;
    }

    public String getJSONPayload() {
        return this.JSONPayload;
    }

    public void setJSONPayload(String JSONPayload) {
        this.JSONPayload = JSONPayload;
    }
}
