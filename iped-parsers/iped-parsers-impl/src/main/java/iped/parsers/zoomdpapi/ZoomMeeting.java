package iped.parsers.zoomdpapi;

import java.util.ArrayList;
import java.util.List;

/**
 * A Zoom meeting with its metadata, messages, participants,
 * shared files, and recordings.
 *
 * @author Calil Khalil (Hakal)
 */
public class ZoomMeeting {

    private String meetingId;
    private String meetingNo;
    private String confId;
    private String topic;
    private String description;
    private String hostId;
    private String hostName;
    private long startTime;
    private long endTime;
    private long scheduleTime;
    private int duration;
    private int meetingType;
    private int participantsSize;
    private String cloudRecordingStatus;

    private List<ZoomMessage> messages = new ArrayList<>();
    private List<ZoomParticipant> participants = new ArrayList<>();
    private List<ZoomSharedFile> sharedFiles = new ArrayList<>();
    private List<ZoomRecording> recordings = new ArrayList<>();

    public String getMeetingId() { return meetingId; }
    public void setMeetingId(String meetingId) { this.meetingId = meetingId; }

    public String getMeetingNo() { return meetingNo; }
    public void setMeetingNo(String meetingNo) { this.meetingNo = meetingNo; }

    public String getConfId() { return confId; }
    public void setConfId(String confId) { this.confId = confId; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getHostId() { return hostId; }
    public void setHostId(String hostId) { this.hostId = hostId; }

    public String getHostName() { return hostName; }
    public void setHostName(String hostName) { this.hostName = hostName; }

    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }

    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }

    public long getScheduleTime() { return scheduleTime; }
    public void setScheduleTime(long scheduleTime) { this.scheduleTime = scheduleTime; }

    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }

    public int getMeetingType() { return meetingType; }
    public void setMeetingType(int meetingType) { this.meetingType = meetingType; }

    public int getParticipantsSize() { return participantsSize; }
    public void setParticipantsSize(int participantsSize) { this.participantsSize = participantsSize; }

    public String getCloudRecordingStatus() { return cloudRecordingStatus; }
    public void setCloudRecordingStatus(String cloudRecordingStatus) { this.cloudRecordingStatus = cloudRecordingStatus; }

    public List<ZoomMessage> getMessages() { return messages; }
    public List<ZoomParticipant> getParticipants() { return participants; }
    public List<ZoomSharedFile> getSharedFiles() { return sharedFiles; }
    public List<ZoomRecording> getRecordings() { return recordings; }

    public String getTitle() {
        StringBuilder sb = new StringBuilder("Zoom Meeting");
        if (topic != null && !topic.isBlank()) {
            sb.append(" - ").append(topic.strip());
        }
        if (meetingNo != null && !meetingNo.isBlank()) {
            sb.append(" (").append(meetingNo.strip()).append(")");
        }
        return sb.toString();
    }
}
