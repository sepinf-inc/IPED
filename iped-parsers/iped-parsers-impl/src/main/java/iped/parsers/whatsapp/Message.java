package iped.parsers.whatsapp;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.codec.binary.Hex;

import iped.data.IItemReader;
import iped.parsers.util.ChildPornHashLookup;

/**
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@pf.gov.br>
 */
public class Message {

    private static File thumbsfile;
    private static FileChannel fileChannel;
    private static AtomicLong fileOffset = new AtomicLong();
    private static AtomicInteger deletedCounter = new AtomicInteger();

    private long id;
    private int deletedId = -1;
    private String callId = null;
    private String remoteId;
    private String remoteResource;
    private String localResource;
    private int status;
    private String data;
    private boolean fromMe;
    private boolean deleted;
    private Date timeStamp;
    private String mediaUrl;
    private String mediaMime;
    private long mediaSize;
    private String mediaName;
    private String mediaHash;
    private long thumbOffset = -1;
    private int thumbSize = -1;
    private String mediaFile;
    private String mediaCaption;
    private String mediaThumbFile;
    private MessageType messageType;
    private double latitude;
    private double longitude;
    private List<String> vcards;
    private String url;
    private String thumbpath;
    private int mediaDuration;
    private MessageStatus messageStatus;
    private String recoveredFrom = null;
    private Set<String> childPornSets = new HashSet<>();
    private IItemReader mediaItem = null;
    private String mediaQuery = null;
    private List<MessageAddOn> addOns = new ArrayList<>();

    static {
        try {
            thumbsfile = File.createTempFile("whatsapp", ".thumbs");
            thumbsfile.deleteOnExit();
            fileChannel = FileChannel.open(thumbsfile.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void closeStaticResources() throws IOException {
        if (fileChannel.isOpen()) {
            fileChannel.truncate(0);
            fileChannel.close();
        }
        thumbsfile.delete();
    }

    public Message() {
        messageType = MessageType.TEXT_MESSAGE;
        vcards = new ArrayList<>();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    /**
     * Deleted recovered messages may have the same id as an allocated message. This
     * returns a global unique id for a decoded database. Calls can also have the
     * same id, so a new info is used as id
     * 
     * @return a unique string id
     */
    public String getUniqueId() {
        String new_id = Long.toString(id);
        if (callId != null) {
            new_id += "_" + callId;
        }
        if (deletedId == -1) {
            deletedId = deletedCounter.getAndIncrement();
        }
        if (deleted) {
            new_id += "_" + deletedId;
        }
        return new_id;
    }

    public String getRemoteId() {
        return remoteId;
    }

    public void setRemoteId(String remoteId) {
        this.remoteId = remoteId;
    }

    public String getRemoteResource() {
        return remoteResource;
    }

    public void setRemoteResource(String remoteResource) {
        this.remoteResource = remoteResource;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
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

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
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

    public String getMediaHash() {
        return mediaHash;
    }

    public void setMediaHash(String mediaHash, boolean encoded) {
        if (mediaHash == null || mediaHash.isEmpty())
            return;
        if (encoded) {
            byte[] hash = Base64.getDecoder().decode(mediaHash);
            this.mediaHash = new String(Hex.encodeHex(hash, false));
        } else {
            this.mediaHash = mediaHash;
        }
        childPornSets.addAll(ChildPornHashLookup.lookupHash(this.mediaHash));
    }

    public byte[] getThumbData() {
        if (thumbSize == -1) {
            return null;
        }
        try {
            ByteBuffer bb = ByteBuffer.allocate(thumbSize);
            fileChannel.read(bb, thumbOffset);
            return bb.array();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setThumbData(byte[] rawData) {
        if (rawData == null) {
            thumbSize = -1;
            return;
        }
        try {
            thumbSize = rawData.length;
            thumbOffset = fileOffset.getAndAdd(thumbSize);
            fileChannel.write(ByteBuffer.wrap(rawData), thumbOffset);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getMediaName() {
        return mediaName;
    }

    public void setMediaName(String mediaName) {
        this.mediaName = mediaName;
    }

    public String getMediaFile() {
        return mediaFile;
    }

    public void setMediaFile(String mediaFile) {
        this.mediaFile = mediaFile;
    }

    public String getMediaCaption() {
        return mediaCaption;
    }

    public void setMediaCaption(String mediaCaption) {
        this.mediaCaption = mediaCaption;
    }

    public String getMediaThumbFile() {
        return mediaThumbFile;
    }

    public void setMediaThumbFile(String mediaThumbFile) {
        this.mediaThumbFile = mediaThumbFile;
    }

    public String getLocalResource() {
        return localResource;
    }

    public void setLocalResource(String localResource) {
        this.localResource = localResource;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    public List<String> getVcards() {
        return vcards;
    }

    public void setVcards(List<String> vcards) {
        this.vcards = vcards;
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

    public String getThumbpath() {
        return thumbpath;
    }

    public void setThumbpath(String thumbpath) {
        this.thumbpath = thumbpath;
    }

    public int getMediaDuration() {
        return mediaDuration;
    }

    public void setMediaDuration(int mediaDuration) {
        this.mediaDuration = mediaDuration;
    }

    public MessageStatus getMessageStatus() {
        return messageStatus;
    }

    public void setMessageStatus(MessageStatus messageStatus) {
        this.messageStatus = messageStatus;
    }

    public boolean isSystemMessage() {
        switch (messageType) {
            case MESSAGES_NOW_ENCRYPTED:
            case ENCRIPTION_KEY_CHANGED:
            case GROUP_CREATED:
            case USER_JOINED_GROUP:
            case USER_JOINED_GROUP_FROM_LINK:
            case USERS_JOINED_GROUP:
            case USER_LEFT_GROUP:
            case USER_REMOVED_FROM_GROUP:
            case GROUP_ICON_CHANGED:
            case GROUP_ICON_DELETED:
            case GROUP_DESCRIPTION_CHANGED:
            case SUBJECT_CHANGED:
            case YOU_ADMIN:
            case UNKNOWN_MESSAGE:
                return true;
            default:
        }
        return false;
    }

    public boolean isCall() {
        return messageType == MessageType.VIDEO_CALL || messageType == MessageType.VOICE_CALL
                || messageType == MessageType.MISSED_VIDEO_CALL || messageType == MessageType.MISSED_VOICE_CALL
                || messageType == MessageType.REFUSED_VIDEO_CALL || messageType == MessageType.REFUSED_VOICE_CALL
                || messageType == MessageType.UNKNOWN_VOICE_CALL || messageType == MessageType.UNKNOWN_VIDEO_CALL;
    }

    public String getRecoveredFrom() {
        return recoveredFrom;
    }

    public void setRecoveredFrom(String recoveredFrom) {
        this.recoveredFrom = recoveredFrom;
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

    public boolean addMessageAddOn(MessageAddOn m) {
        return addOns.add(m);
    }

    public List<MessageAddOn> getAddOns() {
        return addOns;
    }

    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    public static enum MessageType {
        TEXT_MESSAGE, IMAGE_MESSAGE, AUDIO_MESSAGE, VIDEO_MESSAGE, UNKNOWN_MEDIA_MESSAGE, CONTACT_MESSAGE, LOCATION_MESSAGE, SHARE_LOCATION_MESSAGE, VOICE_CALL, VIDEO_CALL, APP_MESSAGE, GIF_MESSAGE, MESSAGES_NOW_ENCRYPTED, ENCRIPTION_KEY_CHANGED, MISSED_VOICE_CALL, MISSED_VIDEO_CALL, DELETED_MESSAGE, DELETED_FROM_SENDER, GROUP_CREATED, USER_JOINED_GROUP, USER_JOINED_GROUP_FROM_LINK, USERS_JOINED_GROUP, USER_LEFT_GROUP, USER_REMOVED_FROM_GROUP, URL_MESSAGE, GROUP_ICON_CHANGED, GROUP_ICON_DELETED, GROUP_DESCRIPTION_CHANGED, SUBJECT_CHANGED, YOU_ADMIN, WAITING_MESSAGE, STICKER_MESSAGE, REFUSED_VIDEO_CALL, REFUSED_VOICE_CALL, UNKNOWN_VOICE_CALL, UNKNOWN_VIDEO_CALL, UNKNOWN_MESSAGE
    }

    public static enum MessageStatus {
        MESSAGE_UNSENT, MESSAGE_SENT, MESSAGE_DELIVERED, MESSAGE_VIEWED
    }
}
