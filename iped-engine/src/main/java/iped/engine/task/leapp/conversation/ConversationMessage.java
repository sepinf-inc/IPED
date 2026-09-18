package iped.engine.task.leapp.conversation;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import iped.data.IItemReader;
import iped.utils.DateUtil;

/**
 * One message of a LEAPP conversation view: the values of the view-mapped columns of a single data_list row, plus the
 * case items of the row's 'media' typed columns.
 */
public class ConversationMessage {

    /** Index of the row in the plugin's data_list, used as a stable per-plugin anchor id. */
    private final int rowIndex;

    private final String sender;
    private final String body;

    /** True/false when the view declares a directionColumn; null when the direction is unknown. */
    private final Boolean outgoing;

    private final String rawTime;
    private final Date timestamp;

    private final List<IItemReader> mediaItems = new ArrayList<>();

    private String latitude;
    private String longitude;

    public ConversationMessage(int rowIndex, String sender, String body, Boolean outgoing, String rawTime) {
        this.rowIndex = rowIndex;
        this.sender = sender;
        this.body = body;
        this.outgoing = outgoing;
        this.rawTime = rawTime;
        this.timestamp = parseTime(rawTime);
    }

    /**
     * Time cells arrive stringified from Python (str(datetime) produces "2023-05-12 14:33:12.123456+00:00"-like values,
     * other plugins emit ISO strings); DateUtil handles both the 'T' and space separated ISO-8601 variants, with or
     * without fraction of second. Parsing failures are not fatal: the raw string is still shown in the HTML.
     */
    private static Date parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return DateUtil.tryToParseDate(value.trim());
    }

    public int getRowIndex() {
        return rowIndex;
    }

    public String getAnchorId() {
        return "leapp-msg-" + rowIndex;
    }

    public String getSender() {
        return sender;
    }

    public String getBody() {
        return body;
    }

    public boolean isOutgoing() {
        return Boolean.TRUE.equals(outgoing);
    }

    public Boolean getOutgoing() {
        return outgoing;
    }

    public String getRawTime() {
        return rawTime;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public List<IItemReader> getMediaItems() {
        return mediaItems;
    }

    public void setLocation(String latitude, String longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getLatitude() {
        return latitude;
    }

    public String getLongitude() {
        return longitude;
    }
}
