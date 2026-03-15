package iped.parsers.zoomdpapi;

import java.util.Date;

/**
 * A chat message extracted from Zoom encrypted databases.
 * Comparable by timestamp for timeline ordering.
 *
 * @author Calil Khalil (Hakal)
 */
public class ZoomMessage implements Comparable<ZoomMessage> {

    private String id;
    private String meetingId;
    private String body;
    private long timestamp;
    private String senderName;
    private String senderGuid;
    private int msgType;
    private boolean fileTransfer;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getMeetingId() { return meetingId; }
    public void setMeetingId(String meetingId) { this.meetingId = meetingId; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public Date getDate() {
        return timestamp > 0 ? new Date(timestamp) : null;
    }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getSenderGuid() { return senderGuid; }
    public void setSenderGuid(String senderGuid) { this.senderGuid = senderGuid; }

    public int getMsgType() { return msgType; }
    public void setMsgType(int msgType) { this.msgType = msgType; }

    public boolean isFileTransfer() { return fileTransfer; }
    public void setFileTransfer(boolean fileTransfer) { this.fileTransfer = fileTransfer; }

    @Override
    public int compareTo(ZoomMessage o) {
        return Long.compare(this.timestamp, o.timestamp);
    }
}
