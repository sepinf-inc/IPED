package iped.parsers.eventtranscript;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class BrowserHistoryEntry {
    private String userSID;
    private String correlationGuid;
    private Date timestamp;
    private String[] tagNames;
    private String[] eventNames;
    private String url;
    private String[] pageTitles;

    private String JSONPayload;

    public String getUserSID() {
        return this.userSID;
    }

    public void setUserSID(String userSID) {
        this.userSID = userSID;
    }

    public String getCorrelationGuid() {
        return this.correlationGuid;
    }

    public void setCorrelationGuid(String correlationGuid) {
        this.correlationGuid = correlationGuid;
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

    public void setEventNames(String[] fullEventNames) {
        this.eventNames = fullEventNames;
    }

    public String[] getEventNames() {
        return this.eventNames;
    }

    public void setTagNames(String[] tagNames) {
        this.tagNames = tagNames;
    }

    public String[] getTagNames() {
        return this.tagNames;
    }

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String[] getPageTitles() {
        return this.pageTitles;
    }

    public void setPageTitles(String[] pageTitles) {
        this.pageTitles = pageTitles;
    }

    public String getJSONPayload() {
        return this.JSONPayload;
    }

    public void setJSONPayload(String JSONPayload) {
        this.JSONPayload = JSONPayload;
        // JSONObject json = new JSONObject(JSONPayload);
        // JSONObject data = json.getJSONObject("data");
        // if (data.has("url") && !data.getString("url").isEmpty())
        //     url = data.getString("url");
        // correlationGuid = data.getString("CorrelationGuid");
    }

}
