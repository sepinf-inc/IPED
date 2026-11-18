package iped.parsers.threema;

import java.io.File;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import iped.data.IItemReader;

/**
 *
 * @author André Rodrigues Costa <andre.arc@pf.gov.br>
 */
public class Message {

    private long id;
    private String remoteResource;
    private int status;
    private String text;
    private boolean fromMe;
    private Date timeStamp;
    private String mediaMime;
    private long mediaSize;
    private String mediaName;
    private String mediaDescription;
    private MessageType messageType;
    private double latitude;
    private double longitude;
    private String url;
    private byte[] thumbnail;
    private File data;
    private String dataName;
    private String mediaDuration;
    private MessageStatus messageStatus;

    private Set<String> childPornSets = new HashSet<>();
    private IItemReader mediaItem = null;
    private String mediaQuery = null;

    public Message() {
        messageType = MessageType.TEXT_MESSAGE;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getMediaDescription() {
        return mediaDescription;
    }

    public void setMediaDescription(String mediaDescription) {
        this.mediaDescription = mediaDescription;
    }

    public String getRemoteResource() {
        return remoteResource;
    }

    public void setRemoteResource(String remoteResource) {
        this.remoteResource = remoteResource;
    }

    public File getData() {
        return data;
    }

    public void setData(File data) {
        this.data = data;
    }

    public String getDataName() {
        return dataName;
    }

    public void setDataName(String dataName) {
        this.dataName = dataName;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isFromMe() {
        return fromMe;
    }

    public void setFromMe(boolean fromMe) {
        this.fromMe = fromMe;
    }

    public Date getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(Date timeStamp) {
        this.timeStamp = timeStamp;
    }

    public String getMediaMime() {
        return mediaMime;
    }

    public void setMediaMime(String mediaMime) {
        if (mediaMime != null && mediaMime.contains(";")) { //$NON-NLS-1$
            mediaMime = mediaMime.split(";")[0]; //$NON-NLS-1$
        }
        this.mediaMime = mediaMime;
    }

    public long getMediaSize() {
        return mediaSize;
    }

    public void setMediaSize(long mediaSize) {
        this.mediaSize = mediaSize;
    }

    public String getMediaName() {
        return mediaName;
    }

    public void setMediaName(String mediaName) {
        this.mediaName = mediaName;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        if (latitude < -90.0 || latitude > 90.0) {
            this.latitude = 0.0;
        } else {
            this.latitude = latitude;
        }
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        if (longitude < -180.0 || longitude > 180.0) {
            this.longitude = 0.0;
        } else {
            this.longitude = longitude;
        }
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public byte[] getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(byte[] thumbnail) {
        this.thumbnail = thumbnail;
    }

    public String getMediaDuration() {
        return mediaDuration;
    }

    public void setMediaDuration(String mediaDuration) {
        this.mediaDuration = mediaDuration;
    }

    public MessageStatus getMessageStatus() {
        return messageStatus;
    }

    public void setMessageStatus(MessageStatus messageStatus) {
        this.messageStatus = messageStatus;
    }

    public Set<String> getChildPornSets() {
        return childPornSets;
    }

    public void addChildPornSets(Collection<String> sets) {
        this.childPornSets.addAll(sets);
    }

    public IItemReader getMediaItem() {
        return mediaItem;
    }

    public void setMediaItem(IItemReader mediaItem) {
        this.mediaItem = mediaItem;
    }

    public String getMediaQuery() {
        return mediaQuery;
    }

    public void setMediaQuery(String mediaQuery) {
        this.mediaQuery = mediaQuery;
    }

    public enum MessageType {
        TEXT_MESSAGE, IMAGE_MESSAGE, AUDIO_MESSAGE, VIDEO_MESSAGE, UNKNOWN_MEDIA_MESSAGE, LOCATION_MESSAGE, THREEMA_CALL, APP_MESSAGE, GIF_MESSAGE, SELF_ADDED_TO_GROUP, SELF_REMOVED_FROM_GROUP, SELF_LEFT_GROUP, GROUP_NOTE_STARTED, GROUP_NOTE_ENDED, GROUP_CREATOR_LEFT, GROUP_CALL_STARTED, GROUP_CALL_ENDED, REJECTED_CALL_BUSY, REJECTED_CALL_DISABLED, REJECTED_CALL_OFF_HOURS, WORK_CONSUMER_INFO, MISSED_CALL, USER_JOINED_GROUP, USER_LEFT_GROUP, USER_REMOVED_FROM_GROUP, URL_MESSAGE, GROUP_ICON_CHANGED, GROUP_ICON_DELETED, GROUP_RENAMED, WAITING_MESSAGE, REJECTED_CALL, REJECTED_CALL_TIMEOUT, UNKNOWN_CALL_RESPONSE, UNKNOWN_MESSAGE
    }

    public enum MessageStatus {
        MESSAGE_UNSENT, MESSAGE_SENT, MESSAGE_DELIVERED, MESSAGE_VIEWED
    }
}
