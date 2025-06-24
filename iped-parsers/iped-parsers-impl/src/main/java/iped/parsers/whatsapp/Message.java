package iped.parsers.whatsapp;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.codec.binary.Hex;

import iped.data.IItemReader;
import iped.parsers.util.ChildPornHashLookup;

/**
 * @author Fabio Melo Pfeifer <pfeifer.fmp@pf.gov.br>
 */
public class Message implements Comparable<Message> {

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
    private boolean forwarded;
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
    private int duration;
    private MessageStatus messageStatus;
    private String recoveredFrom = null;
    private List<String> childPornSets;
    private IItemReader mediaItem = null;
    private String mediaQuery = null;
    private List<MessageAddOn> addOns;
    private long idQuote;
    private Message messageQuote = null;
    private boolean quoted = false;
    private MessageQuotedType messageQuotedType = MessageQuotedType.QUOTE_NOT_FOUND;
    private String uuid = null;
    private long editId = -1;
    private long quoteChatId = -1;
    private String quotePrivateGroupName;
    private byte[] metaData;
    private String groupInviteName;
    private MessageTemplate messageTemplate;
    private long sortId;
    private List<PollOption> pollOptions;
    private List<String> usersAction;
    private String uiElements;
    private MessageProduct product;
    private Date editTimeStamp;
    private String address;

    static {
        try {
            thumbsfile = File.createTempFile("whatsapp", ".thumbs");
            thumbsfile.deleteOnExit();
            fileChannel = FileChannel.open(thumbsfile.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static synchronized void reOpenChannel() {
        if (fileChannel.isOpen()) {
            return;
        }
        try {
            fileChannel = FileChannel.open(thumbsfile.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE);
        } catch (NoSuchFileException e) {
            // fix for https://github.com/sepinf-inc/IPED/issues/2051
            throw new RuntimeException(e);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static synchronized void closeStaticResources() throws IOException {
        if (fileChannel.isOpen()) {
            fileChannel.truncate(0);
            fileChannel.close();
        }
        thumbsfile.delete();
    }

    public Message() {
        messageType = MessageType.TEXT_MESSAGE;
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
        if (mediaMime != null && mediaMime.contains(";")) {
            mediaMime = mediaMime.split(";")[0];
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
        childPornSets = ChildPornHashLookup.lookupHashAndMerge(this.mediaHash, childPornSets);
    }

    public byte[] getThumbData() {
        if (thumbSize == -1) {
            return null;
        }
        try {
            ByteBuffer bb = ByteBuffer.allocate(thumbSize);
            fileChannel.read(bb, thumbOffset);
            return bb.array();
        } catch (ClosedChannelException e) {
            reOpenChannel();
            return getThumbData();
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
        } catch (ClosedChannelException e) {
            reOpenChannel();
            setThumbData(rawData);
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

    public boolean isForwarded() {
        return forwarded;
    }

    public void setForwarded(boolean forwarded) {
        this.forwarded = forwarded;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    public List<String> getVcards() {
        return vcards == null ? Collections.emptyList() : vcards;
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

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public MessageStatus getMessageStatus() {
        return messageStatus;
    }

    public void setMessageStatus(MessageStatus messageStatus) {
        this.messageStatus = messageStatus;
    }

    public boolean isSystemMessage() {
        switch (messageType) {
            case BLOCKED_CONTACT:
            case BUSINESS_CHANGED_NAME:
            case BUSINESS_CHAT:
            case BUSINESS_META_SECURE_SERVICE:
            case BUSINESS_OFFICIAL:
            case BUSINESS_TO_STANDARD:
            case CHANGED_DEVICE:
            case CHANGED_NUMBER_CHATTING_WITH_NEW:
            case CHANGED_NUMBER_CHATTING_WITH_OLD:
            case CHANGED_NUMBER_TO:
            case CHANNEL_ADDED_PRIVACY:
            case CHANNEL_CREATED:
            case CHAT_STARTED_FROM_AD:
            case COMMUNITY_MANAGEMENT_ACTION:
            case DELETED_BY_ADMIN:
            case DELETED_BY_SENDER:
            case DELETED_MESSAGE:
            case ENCRYPTION_KEY_CHANGED:
            case EPHEMERAL_CHANGED:
            case EPHEMERAL_DEFAULT:
            case EPHEMERAL_DURATION_CHANGED:
            case EPHEMERAL_SAVE:
            case GROUP_ADDED_TO_COMMUNITY:
            case COMMUNITY_CHANGED_ONLY_ADMINS_CAN_ADD:
            case COMMUNITY_CHANGED_ALL_MEMBERS_CAN_ADD:
            case GROUP_CHANGED_ALL_MEMBERS_CAN_ADD:
            case GROUP_CHANGED_ALL_MEMBERS_CAN_EDIT:
            case GROUP_CHANGED_ALL_MEMBERS_CAN_SEND:
            case GROUP_CHANGED_ONLY_ADMINS_CAN_ADD:
            case GROUP_CHANGED_ONLY_ADMINS_CAN_EDIT:
            case GROUP_CHANGED_ONLY_ADMINS_CAN_SEND:
            case GROUP_CHANGED_ADMIN_APPROVAL_OFF:
            case GROUP_CREATED:
            case COMMUNITY_DESCRIPTION_CHANGED:
            case GROUP_DESCRIPTION_CHANGED:
            case GROUP_DESCRIPTION_DELETED:
            case GROUP_ICON_CHANGED:
            case GROUP_ICON_DELETED:
            case GROUP_INVITE:
            case GROUP_NAME_CHANGED:
            case GROUP_ONLY_ADMINS_CAN_SEND:
            case MESSAGES_ENCRYPTED:
            case MESSAGES_NOW_ENCRYPTED:
            case NEW_PARTICIPANTS_NEED_ADMIN_APPROVAL:
            case ORDER_MESSAGE:
            case PINNED_MESSAGE:
            case SENDER_ADDED_TO_CONTACTS:
            case SENDER_IN_CONTACTS:
            case STANDARD_CHAT:
            case SUBJECT_CHANGED:
            case UNBLOCKED_CONTACT:
            case USER_ADDED_TO_GROUP:
            case USER_JOINED_GROUP_FROM_INVITATION:
            case USER_JOINED_GROUP_FROM_LINK:
            case USER_JOINED_WHATSAPP:
            case USER_LEFT_GROUP:
            case USER_REMOVED_FROM_GROUP:
            case USER_REQUEST_TO_ADD_TO_GROUP:
            case YOU_ADMIN:
            case YOU_NOT_ADMIN:
            case OVER_256_MEMBERS_ONLY_ADMINS_CAN_EDIT:
            case SECURITY_NOTIFICATIONS_NO_LONGER_AVAILABLE:
            case CONTACTED_FIND_BUSINESSES:
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

    public List<String> getChildPornSets() {
        return childPornSets == null ? Collections.emptyList() : childPornSets;
    }

    public void lookupAndAddChildPornSets(String hash) {
        childPornSets = ChildPornHashLookup.lookupHashAndMerge(hash, childPornSets);
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
        if (addOns == null) {
            addOns = new ArrayList<MessageAddOn>(1);
        }
        return addOns.add(m);
    }

    public List<MessageAddOn> getAddOns() {
        return addOns == null ? Collections.emptyList() : addOns;
    }

    public boolean addPollOption(PollOption opt) {
        if (pollOptions == null) {
            pollOptions = new ArrayList<PollOption>(1);
        }
        return pollOptions.add(opt);
    }

    public List<PollOption> getPollOptions() {
        return pollOptions == null ? Collections.emptyList() : pollOptions;
    }
    
    public boolean addUserAction(String user) {
        if (usersAction == null) {
            usersAction = new ArrayList<String>(1);
        }
        return usersAction.add(user);
    }

    public List<String> getUsersAction() {
        return usersAction == null ? Collections.emptyList() : usersAction;
    }

    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    public long getIdQuote() {
        return idQuote;
    }

    public void setIdQuote(long idQuote) {
        this.idQuote = idQuote;
    }

    public boolean isQuoted() {
        return this.quoted;
    }

    public void setQuoted(boolean quoted) {
        this.quoted = quoted;
    }

    public MessageQuotedType getMessageQuotedType() {
        return messageQuotedType;
    }

    public void setMessageQuotedType(MessageQuotedType messageQuotedType) {
        this.messageQuotedType = messageQuotedType;
    }

    public Message getMessageQuote(){
        return this.messageQuote;
    }

    public void setMessageQuote(Message messageQuote){
        this.messageQuote = messageQuote;
    }

    public String getUuid() {
        return this.uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public long getEditId() {
        return this.editId;
    }

    public void setEditId(long editId) {
        this.editId = editId;
    }

    public long getQuoteChatId() {
        return this.quoteChatId;
    }

    public void setQuoteChatId(long quoteChatId) {
        this.quoteChatId = quoteChatId;
    }

    public byte[] getMetaData() {
        return metaData;
    }

    public void setMetaData(byte[] metaData) {
        this.metaData = metaData;
    }

    public String getGroupInviteName() {
        return groupInviteName;
    }

    public void setGroupInviteName(String groupInviteName) {
        this.groupInviteName = groupInviteName;
    }

    public String getQuotePrivateGroupName() {
        return quotePrivateGroupName;
    }

    public void setQuotePrivateGroupName(String quotePrivateGroupName) {
        this.quotePrivateGroupName = quotePrivateGroupName;
    }

    public MessageTemplate getMessageTemplate() {
        return messageTemplate;
    }

    public void setMessageTemplate(MessageTemplate messageTemplate) {
        this.messageTemplate = messageTemplate;
    }

    public long getSortId() {
        return sortId != 0 ? sortId : timeStamp != null ? timeStamp.getTime() : id;
    }

    public void setSortId(long sortId) {
        this.sortId = sortId;
    }

    public String getUiElements() {
        return uiElements;
    }

    public void setUiElements(String uiElements) {
        this.uiElements = uiElements;
    }

    public MessageProduct getProduct() {
        return product;
    }

    public void setProduct(MessageProduct product) {
        this.product = product;
    }

    public Date getEditTimeStamp() {
        return editTimeStamp;
    }

    public void setEditTimeStamp(Date editTimeStamp) {
        this.editTimeStamp = editTimeStamp;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public static enum MessageType {
        TEXT_MESSAGE, IMAGE_MESSAGE, AUDIO_MESSAGE, VIDEO_MESSAGE, UNKNOWN_MEDIA_MESSAGE, CONTACT_MESSAGE, LOCATION_MESSAGE, SHARE_LOCATION_MESSAGE, VOICE_CALL, VIDEO_CALL, DOC_MESSAGE, GIF_MESSAGE, BLOCKED_CONTACT, UNBLOCKED_CONTACT, BUSINESS_CHAT, BUSINESS_TO_STANDARD, MESSAGES_ENCRYPTED, MESSAGES_NOW_ENCRYPTED, ENCRYPTION_KEY_CHANGED, MISSED_VOICE_CALL, MISSED_VIDEO_CALL, DELETED_MESSAGE, DELETED_BY_ADMIN, DELETED_BY_SENDER, GROUP_CREATED, USER_ADDED_TO_COMMUNITY, USER_ADDED_TO_GROUP, USER_JOINED_GROUP_FROM_COMMUNITY, USER_JOINED_GROUP_FROM_LINK, USER_JOINED_GROUP_FROM_INVITATION, USER_LEFT_GROUP, USER_REMOVED_FROM_GROUP, USER_COMMUNITY_ADMIN, URL_MESSAGE, GROUP_ICON_CHANGED, GROUP_ICON_DELETED, GROUP_DESCRIPTION_CHANGED, GROUP_DESCRIPTION_DELETED, SUBJECT_CHANGED, YOU_ADMIN, YOU_NOT_ADMIN, USER_ADMIN, WAITING_MESSAGE, STICKER_MESSAGE, REFUSED_VIDEO_CALL, REFUSED_VOICE_CALL, UNAVAILABLE_VIDEO_CALL, UNAVAILABLE_VOICE_CALL, UNKNOWN_VOICE_CALL, UNKNOWN_VIDEO_CALL, VIEW_ONCE_AUDIO_MESSAGE, VIEW_ONCE_IMAGE_MESSAGE, VIEW_ONCE_VIDEO_MESSAGE, CALL_MESSAGE, BUSINESS_META_SECURE_SERVICE, GROUP_INVITE, TEMPLATE_MESSAGE, TEMPLATE_QUOTE, POLL_MESSAGE, EPHEMERAL_DURATION_CHANGED, EPHEMERAL_SETTINGS_NOT_APPLIED, EPHEMERAL_CHANGED, EPHEMERAL_DEFAULT, EPHEMERAL_SAVE, GROUP_CHANGED_ONLY_ADMINS_CAN_ADD, GROUP_CHANGED_ONLY_ADMINS_CAN_SEND, GROUP_CHANGED_ALL_MEMBERS_CAN_SEND, GROUP_CHANGED_ONLY_ADMINS_CAN_EDIT, GROUP_CHANGED_ALL_MEMBERS_CAN_EDIT, GROUP_ONLY_ADMINS_CAN_SEND, CHANGED_DEVICE, CHANGED_NUMBER_TO, CHANGED_NUMBER_CHATTING_WITH_NEW, CHANGED_NUMBER_CHATTING_WITH_OLD, STANDARD_CHAT, SENDER_ADDED_TO_CONTACTS, SENDER_IN_CONTACTS, BUSINESS_OFFICIAL, GROUP_ADDED_TO_COMMUNITY, GROUP_REMOVED_FROM_COMMUNITY, COMMUNITY_MANAGEMENT_ACTION, COMMUNITY_WELCOME, UI_ELEMENTS, UI_ELEMENTS_QUOTE, CHAT_ADDED_PRIVACY, CHANNEL_ADDED_PRIVACY, CHANNEL_CREATED, ORDER_MESSAGE, PRODUCT_MESSAGE, BUSINESS_CHANGED_NAME, USER_JOINED_WHATSAPP, PINNED_MESSAGE, GROUP_NAME_CHANGED, AI_THIRD_PARTY, NEW_PARTICIPANTS_NEED_ADMIN_APPROVAL, RESET_GROUP_LINK, COMMUNITY_RENAMED, ANY_COMMUNITY_MEMBER_CAN_JOIN_GROUP, UNKNOWN_MESSAGE, OVER_256_MEMBERS_ONLY_ADMINS_CAN_EDIT, SECURITY_NOTIFICATIONS_NO_LONGER_AVAILABLE, CONTACTED_FIND_BUSINESSES, COMMUNITY_CHANGED_ONLY_ADMINS_CAN_ADD, COMMUNITY_CHANGED_ALL_MEMBERS_CAN_ADD, COMMUNITY_DESCRIPTION_CHANGED, COMMUNITY_NOT_AVAILABLE, GROUP_NOT_PART_OF_COMMUNITY, GROUP_CHANGED_ADMIN_APPROVAL_OFF, GROUP_CHANGED_ALL_MEMBERS_CAN_ADD, USER_REQUEST_TO_ADD_TO_GROUP, CHAT_STARTED_FROM_AD, MESSAGE_ASSOCIATION 
    }

    public static enum MessageStatus {
        MESSAGE_UNSENT, MESSAGE_SENT, MESSAGE_DELIVERED, MESSAGE_VIEWED
    }

    public static enum MessageQuotedType {
        QUOTE_NOT_FOUND, QUOTE_FOUND, QUOTE_STATUS, QUOTE_PRIVACY_GROUP, QUOTE_PRIVACY_GROUP_NOT_FOUND, QUOTE_CATALOG
    }

    @Override
    public int compareTo(Message o) {
        return Long.compare(getSortId(), o.getSortId());
    }

    public static void sort(List<Message> messages) {
        Collections.sort(messages);

        // Check if there are messages with sortId != 0 AND sortId == 0
        boolean zero = false;
        boolean notZero = false;
        for (Message m : messages) {
            if (m.sortId == 0) {
                zero = true;
            } else {
                notZero = true;
            }
            if (zero && notZero) {
                break;
            }
        }

        // If both are present, merge these two groups
        if (zero && notZero) {
            List<Message> l0 = new ArrayList<Message>();
            List<Message> l1 = new ArrayList<Message>();
            for (Message m : messages) {
                if (m.sortId == 0) {
                    l0.add(m);
                } else {
                    l1.add(m);
                }
            }
            messages.clear();
            int idx0 = 0;
            int idx1 = 0;
            while (idx0 < l0.size() || idx1 < l1.size()) {
                if (idx0 == l0.size()) {
                    messages.add(l1.get(idx1++));
                } else if (idx1 == l1.size()) {
                    messages.add(l0.get(idx0++));
                } else {
                    Message m0 = l0.get(idx0);
                    Message m1 = l1.get(idx1);
                    long t0 = m0.timeStamp == null ? m0.id : m0.timeStamp.getTime();
                    long t1 = m1.timeStamp == null ? m1.id : m1.timeStamp.getTime();
                    if (t0 < t1) {
                        messages.add(m0);
                        idx0++;
                    } else {
                        messages.add(m1);
                        idx1++;
                    }
                }
            }
        }
    }
}
