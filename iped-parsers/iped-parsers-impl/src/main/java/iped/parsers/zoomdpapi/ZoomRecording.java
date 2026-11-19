package iped.parsers.zoomdpapi;

/**
 * A cloud or local recording associated with a Zoom meeting.
 *
 * @author Calil Khalil (Hakal)
 */
public class ZoomRecording {

    private String meetingId;
    private String meetingNo;
    private String topic;
    private String shareLink;
    private String recordingUrl;
    private String previewUrl;
    private String passcode;
    private String location;
    private long startTime;
    private int duration;
    private boolean local;

    public String getMeetingId() { return meetingId; }
    public void setMeetingId(String meetingId) { this.meetingId = meetingId; }

    public String getMeetingNo() { return meetingNo; }
    public void setMeetingNo(String meetingNo) { this.meetingNo = meetingNo; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getShareLink() { return shareLink; }
    public void setShareLink(String shareLink) { this.shareLink = shareLink; }

    public String getRecordingUrl() { return recordingUrl; }
    public void setRecordingUrl(String recordingUrl) { this.recordingUrl = recordingUrl; }

    public String getPreviewUrl() { return previewUrl; }
    public void setPreviewUrl(String previewUrl) { this.previewUrl = previewUrl; }

    public String getPasscode() { return passcode; }
    public void setPasscode(String passcode) { this.passcode = passcode; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }

    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }

    public boolean isLocal() { return local; }
    public void setLocal(boolean local) { this.local = local; }
}
