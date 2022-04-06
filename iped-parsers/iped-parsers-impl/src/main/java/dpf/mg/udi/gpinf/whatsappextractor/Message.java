package dpf.mg.udi.gpinf.whatsappextractor;

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
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.codec.binary.Hex;

import dpf.sp.gpinf.indexer.parsers.util.ChildPornHashLookup;
import iped3.IItemBase;

/**
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@dpf.gov.br>
 */
public class Message {

    private static File thumbsfile;
    private static FileChannel fileChannel;
    private static AtomicLong fileOffset = new AtomicLong();

    private long id;
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
    private IItemBase mediaItem = null;
    private String mediaQuery = null;

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
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
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
        }
        return false;
    }

    public boolean isCall() {
        return messageType == MessageType.VIDEO_CALL || messageType == MessageType.VOICE_CALL
                || messageType == MessageType.MISSED_VIDEO_CALL || messageType == MessageType.MISSED_VOICE_CALL;
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
    
    public IItemBase getMediaItem() {
        return mediaItem;
    }

    public void setMediaItem(IItemBase mediaItem) {
        this.mediaItem = mediaItem;
    }

    public String getMediaQuery() {
        return mediaQuery;
    }

    public void setMediaQuery(String mediaQuery) {
        this.mediaQuery = mediaQuery;
    }

    public static enum MessageType {
        TEXT_MESSAGE, IMAGE_MESSAGE, AUDIO_MESSAGE, VIDEO_MESSAGE, CONTACT_MESSAGE, LOCATION_MESSAGE, SHARE_LOCATION_MESSAGE, VOICE_CALL, VIDEO_CALL, APP_MESSAGE, GIF_MESSAGE, MESSAGES_NOW_ENCRYPTED, ENCRIPTION_KEY_CHANGED, MISSED_VOICE_CALL, MISSED_VIDEO_CALL, DELETED_MESSAGE, DELETED_FROM_SENDER, GROUP_CREATED, USER_JOINED_GROUP, USER_JOINED_GROUP_FROM_LINK, USERS_JOINED_GROUP, USER_LEFT_GROUP, USER_REMOVED_FROM_GROUP, URL_MESSAGE, GROUP_ICON_CHANGED, GROUP_ICON_DELETED, GROUP_DESCRIPTION_CHANGED, SUBJECT_CHANGED, YOU_ADMIN, WAITING_MESSAGE,STICKER_MESSAGE, UNKNOWN_MESSAGE
    }

    public static enum MessageStatus {
        MESSAGE_UNSENT, MESSAGE_SENT, MESSAGE_DELIVERED, MESSAGE_VIEWED
    }
}
