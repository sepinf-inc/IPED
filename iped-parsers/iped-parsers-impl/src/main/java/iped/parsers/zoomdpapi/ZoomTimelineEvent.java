package iped.parsers.zoomdpapi;

/**
 * A chronological activity event (waiting room access, avatar load,
 * message, file transfer) used to reconstruct a forensic timeline.
 *
 * @author Calil Khalil (Hakal)
 */
public class ZoomTimelineEvent implements Comparable<ZoomTimelineEvent> {

    private long timestamp;
    private String type;
    private String description;
    private String actor;
    private String actorGuid;
    private String meetingId;
    private String resourceUrl;
    private String localPath;
    private long fileSize;
    private String content;
    private String avatarColor;
    private String avatarHash;
    private String avatarUuid;

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public String getActorGuid() { return actorGuid; }
    public void setActorGuid(String actorGuid) { this.actorGuid = actorGuid; }

    public String getMeetingId() { return meetingId; }
    public void setMeetingId(String meetingId) { this.meetingId = meetingId; }

    public String getResourceUrl() { return resourceUrl; }
    public void setResourceUrl(String resourceUrl) { this.resourceUrl = resourceUrl; }

    public String getLocalPath() { return localPath; }
    public void setLocalPath(String localPath) { this.localPath = localPath; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getAvatarColor() { return avatarColor; }
    public void setAvatarColor(String avatarColor) { this.avatarColor = avatarColor; }

    public String getAvatarHash() { return avatarHash; }
    public void setAvatarHash(String avatarHash) { this.avatarHash = avatarHash; }

    public String getAvatarUuid() { return avatarUuid; }
    public void setAvatarUuid(String avatarUuid) { this.avatarUuid = avatarUuid; }

    @Override
    public int compareTo(ZoomTimelineEvent o) {
        return Long.compare(this.timestamp, o.timestamp);
    }
}
