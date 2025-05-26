package iped.parsers.threema;

import static iped.parsers.threema.Message.MessageType.APP_MESSAGE;
import static iped.parsers.threema.Message.MessageType.AUDIO_MESSAGE;
import static iped.parsers.threema.Message.MessageType.GIF_MESSAGE;
import static iped.parsers.threema.Message.MessageType.GROUP_CALL_ENDED;
import static iped.parsers.threema.Message.MessageType.GROUP_CALL_STARTED;
import static iped.parsers.threema.Message.MessageType.GROUP_CREATOR_LEFT;
import static iped.parsers.threema.Message.MessageType.GROUP_ICON_CHANGED;
import static iped.parsers.threema.Message.MessageType.GROUP_NOTE_ENDED;
import static iped.parsers.threema.Message.MessageType.GROUP_NOTE_STARTED;
import static iped.parsers.threema.Message.MessageType.GROUP_RENAMED;
import static iped.parsers.threema.Message.MessageType.IMAGE_MESSAGE;
import static iped.parsers.threema.Message.MessageType.MISSED_CALL;
import static iped.parsers.threema.Message.MessageType.REJECTED_CALL;
import static iped.parsers.threema.Message.MessageType.REJECTED_CALL_BUSY;
import static iped.parsers.threema.Message.MessageType.REJECTED_CALL_DISABLED;
import static iped.parsers.threema.Message.MessageType.REJECTED_CALL_OFF_HOURS;
import static iped.parsers.threema.Message.MessageType.REJECTED_CALL_TIMEOUT;
import static iped.parsers.threema.Message.MessageType.SELF_ADDED_TO_GROUP;
import static iped.parsers.threema.Message.MessageType.SELF_LEFT_GROUP;
import static iped.parsers.threema.Message.MessageType.SELF_REMOVED_FROM_GROUP;
import static iped.parsers.threema.Message.MessageType.THREEMA_CALL;
import static iped.parsers.threema.Message.MessageType.UNKNOWN_CALL_RESPONSE;
import static iped.parsers.threema.Message.MessageType.UNKNOWN_MEDIA_MESSAGE;
import static iped.parsers.threema.Message.MessageType.UNKNOWN_MESSAGE;
import static iped.parsers.threema.Message.MessageType.USER_JOINED_GROUP;
import static iped.parsers.threema.Message.MessageType.USER_LEFT_GROUP;
import static iped.parsers.threema.Message.MessageType.USER_REMOVED_FROM_GROUP;
import static iped.parsers.threema.Message.MessageType.VIDEO_MESSAGE;
import static iped.parsers.threema.Message.MessageType.WORK_CONSUMER_INFO;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import org.apache.tika.io.TemporaryResources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;

import iped.parsers.threema.Message.MessageStatus;
import iped.parsers.threema.Message.MessageType;

/**
 *
 * @author André Rodrigues Costa <andre.arc@pf.gov.br>
 */
public abstract class ExtractorIOS extends Extractor {

    private static final Logger logger = LoggerFactory.getLogger(ExtractorIOS.class);

    public ExtractorIOS(TemporaryResources tmp, String itemPath, File databaseFile, ThreemaAccount account, boolean recoverDeletedRecords) {
        super(tmp, itemPath, databaseFile, account, recoverDeletedRecords);
        // $NON-NLS-1$
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT")); //$NON-NLS-1$
    }

    @Override
    protected abstract Connection getConnection() throws SQLException;

    @Override
    protected List<Chat> extractChatList() {
        List<Chat> list;

        list = new ArrayList<>();

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(SELECT_CHAT_LIST);

            while (rs.next()) {
                Chat c = new Chat();
                c.setId(rs.getLong("CHAT_ID"));

                if (rs.getString("GROUP_ID") != null) {
                    c.setGroupChat(true);
                    c.setSubject(rs.getString("GROUP_NAME"));
                    c.setImage(rs.getBytes("GROUP_IMAGE"));

                    // Update user identity, this information is only available if user joined a
                    // group
                    account.setIdentity(rs.getString("GROUP_MY_IDENTITY"));
                } else {
                    ThreemaContact contact = new ThreemaContact(rs.getString("CONTACT_FIRSTNAME"), rs.getString("CONTACT_LASTNAME"), rs.getString("CONTACT_NICKNAME"), rs.getString("CONTACT_IDENTITY"), null);
                    contact.setAvatar(rs.getBytes("CONTACT_IMAGE"));
                    c.setContact(contact);
                    c.setSubject(contact.getFullId());
                    c.setImage(contact.getAvatar());
                }

                list.add(c);
            }

            for (Chat c : list) {
                if (c.isGroupChat()) {
                    setGroupMembers(c, conn, SELECT_GROUP_MEMBERS);
                }
                c.setMessages(extractMessages(conn, c));
            }

        } catch (Exception ex) {
            logger.warn("Database " + itemPath + " is corrupt.");
        }

        return cleanChatList(list);
    }

    private List<Message> extractMessages(Connection conn, Chat chat) throws SQLException {

        List<Message> messages = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(SELECT_MESSAGES)) {
            stmt.setFetchSize(1000);
            stmt.setLong(1, chat.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Message m = createMessageFromDB(rs, chat);
                    messages.add(m);
                }
            }
        } catch (Exception ex) {
            logger.warn("Database " + itemPath + " is corrupt.");
        }

        return messages;
    }

    private Message createMessageFromDB(ResultSet rs, Chat chat) throws SQLException {
        Message m = new Message();

        m.setId(rs.getLong("MESSAGE_ID")); //$NON-NLS-1$
        m.setFromMe(rs.getInt("IS_FROM_ME") == 1); //$NON-NLS-1$

        if (!chat.isGroupChat()) {
            m.setRemoteResource(chat.getSubject()); // $NON-NLS-1$
        } else if (!m.isFromMe()) {
            ThreemaContact contact = new ThreemaContact(rs.getString("CONTACT_FIRSTNAME"), rs.getString("CONTACT_LASTNAME"), rs.getString("CONTACT_NICKNAME"), rs.getString("CONTACT_IDENTITY"), null);
            m.setRemoteResource(contact.getFullId());
        }

        if (rs.getBoolean("IS_READ"))
            m.setMessageStatus(MessageStatus.MESSAGE_VIEWED);
        else if (rs.getBoolean("IS_DELIVERED"))
            m.setMessageStatus(MessageStatus.MESSAGE_DELIVERED);
        else if (rs.getBoolean("SEND_FAILED"))
            m.setMessageStatus(MessageStatus.MESSAGE_UNSENT);
        else
            m.setMessageStatus(MessageStatus.MESSAGE_SENT);

        m.setText(rs.getString("MESSAGE_TEXT")); //$NON-NLS-1$
        m.setTimeStamp(new Date((rs.getInt("SEND_DATE") + 978307200L) * 1000)); //$NON-NLS-1$
        m.setMediaMime(rs.getString("FILE_MIMETYPE"));

        int messageType = rs.getInt("MESSAGE_TYPE");

        if (messageType != 0) {
            switch (messageType) {
                case 1:
                    m.setMessageType(GROUP_RENAMED);
                    break;
                case 2:
                    m.setMessageType(USER_LEFT_GROUP);
                    break;
                case 3:
                    m.setMessageType(USER_JOINED_GROUP);
                    break;
                case 4:
                    m.setMessageType(USER_REMOVED_FROM_GROUP);
                    break;
                case 5:
                    m.setMessageType(SELF_ADDED_TO_GROUP);
                    break;
                case 6:
                    m.setMessageType(SELF_REMOVED_FROM_GROUP);
                    break;
                case 7:
                    m.setMessageType(MISSED_CALL);
                    break;
                case 8:
                    m.setMessageType(REJECTED_CALL);
                    break;
                case 9:
                    m.setMessageType(REJECTED_CALL_BUSY);
                    break;
                case 10:
                    m.setMessageType(REJECTED_CALL_TIMEOUT);
                    break;
                case 11:
                    m.setMessageType(THREEMA_CALL);
                    break;
                case 12:
                    m.setMessageType(REJECTED_CALL_DISABLED);
                    break;
                case 13:
                    m.setMessageType(UNKNOWN_CALL_RESPONSE);
                    break;
                case 14:
                    m.setMessageType(WORK_CONSUMER_INFO);
                    break;
                case 15:
                    m.setMessageType(REJECTED_CALL_OFF_HOURS);
                    break;
                case 16:
                    m.setMessageType(SELF_LEFT_GROUP);
                    break;
                case 17:
                    m.setMessageType(GROUP_NOTE_STARTED);
                    break;
                case 18:
                    m.setMessageType(GROUP_NOTE_ENDED);
                    break;
                case 19:
                    m.setMessageType(GROUP_CREATOR_LEFT);
                    break;
                case 32:
                    m.setMessageType(GROUP_ICON_CHANGED);
                    break;
                case 33:
                case 34:
                    m.setMessageType(GROUP_CALL_STARTED);
                    break;
                case 35:
                    m.setMessageType(GROUP_CALL_ENDED);
                    break;
                default:
                    m.setMessageType(UNKNOWN_MESSAGE);
                    break;
            }
        }

        if (m.getMediaMime() != null) {
            if (m.getMediaMime().startsWith("image")) {
                m.setMessageType(IMAGE_MESSAGE);
            } else if (m.getMediaMime().startsWith("video")) {
                m.setMessageType(VIDEO_MESSAGE);
            } else if (m.getMediaMime().startsWith("application")) {
                m.setMessageType(APP_MESSAGE);
            } else if (m.getMediaMime().startsWith("audio")) {
                m.setMessageType(AUDIO_MESSAGE);
            } else {
                m.setMessageType(UNKNOWN_MEDIA_MESSAGE);
            }
        }

        m.setMediaName(rs.getString("FILE_NAME")); //$NON-NLS-1$
        m.setMediaSize(rs.getLong("FILE_SIZE")); //$NON-NLS-1$

        byte[] data = rs.getBytes("FILE_DATA");
        if (data != null) {
            if (data.length == 38) {
                m.setDataName(new String(data, 1, data.length - 2, StandardCharsets.US_ASCII));
            } else {
                try (ByteArrayInputStream bais = new ByteArrayInputStream(data, 1, data.length - 1)) {
                    Path temp = tmp.createTempFile();
                    Files.copy(bais, temp, StandardCopyOption.REPLACE_EXISTING);
                    m.setData(temp.toFile());
                } catch (IOException e1) {
                    logger.error("Unable to extract Threema attachment from {} {}", itemPath, e1.toString());
                }
            }
        }
        m.setThumbnail(rs.getBytes("THUMBNAIL_DATA")); //$NON-NLS-1$
        m.setLatitude(rs.getDouble("LATITUDE")); //$NON-NLS-1$
        m.setLongitude(rs.getDouble("LONGITUDE")); //$NON-NLS-1$

        if (rs.getString("MESSAGE_JSON") != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode message_json = mapper.readTree(rs.getString("MESSAGE_JSON"));
                JsonNode media_description = message_json.get("d");
                if (media_description != null)
                    m.setMediaDescription(media_description.asText());
            } catch (JsonProcessingException e) {
                logger.warn("Error parsing Threema Message JSON: " + rs.getString("MESSAGE_JSON"));
            }
        }

        if (rs.getString("MESSAGE_ARGS") != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode message_json = mapper.readTree(rs.getString("MESSAGE_ARGS"));
                JsonNode call_initiator = message_json.get("CallInitiator");

                if (call_initiator != null) {
                    JsonNode call_time = message_json.get("CallTime");

                    if (call_time != null) {
                        m.setMessageType(THREEMA_CALL);
                        m.setMediaDuration(call_time.asText());
                    } else {
                        m.setMessageType(MISSED_CALL);
                    }
                }

            } catch (JsonProcessingException e) {
                logger.warn("Error parsing Threema Message JSON: " + rs.getString("MESSAGE_JSON"));
            }
        }

        return m;
    }

    /**
     * ** static strings ***
     */
    private static final String SELECT_CHAT_LIST = "SELECT " + "ZCONVERSATION.Z_PK as CHAT_ID," //$NON-NLS-2$
            + "ZCONVERSATION.ZGROUPID as GROUP_ID, ZCONVERSATION.ZGROUPNAME as GROUP_NAME, ZGROUPIMAGE.ZDATA as GROUP_IMAGE, ZMESSAGE.ZDATE as LASTMESSAGE, ZCONVERSATION.ZGROUPMYIDENTITY as GROUP_MY_IDENTITY, " //$NON-NLS-1$
            + "ZCONTACT.Z_PK as CONTACT_ID, ZCONTACT.ZFIRSTNAME as CONTACT_FIRSTNAME, ZCONTACT.ZLASTNAME as CONTACT_LASTNAME, ZCONTACT.ZPUBLICNICKNAME as CONTACT_NICKNAME, ZCONTACTIMAGE.ZDATA as CONTACT_IMAGE, ZCONTACT.ZIDENTITY as CONTACT_IDENTITY "
            + "FROM ZCONVERSATION " + "LEFT JOIN ZCONTACT ON ZCONVERSATION.ZCONTACT = ZCONTACT.Z_PK " + "LEFT JOIN ZMESSAGE ON ZCONVERSATION.ZLASTMESSAGE = ZMESSAGE.Z_PK "
            + "LEFT JOIN ZIMAGEDATA AS ZCONTACTIMAGE ON ZCONTACT.ZCONTACTIMAGE = ZCONTACTIMAGE.Z_PK " + "LEFT JOIN ZIMAGEDATA AS ZGROUPIMAGE ON ZCONVERSATION.ZGROUPIMAGE = ZGROUPIMAGE.Z_PK " + "ORDER BY ZMESSAGE.ZDATE DESC"; //$NON-NLS-3$

    private static final String SELECT_MESSAGES = "SELECT " //$NON-NLS-1$
            + "ZMESSAGE.Z_PK as MESSAGE_ID, ZMESSAGE.ZTEXT as MESSAGE_TEXT, ZMESSAGE.ZISOWN as IS_FROM_ME, ZMESSAGE.ZREAD as IS_READ, ZMESSAGE.ZDELIVERED as IS_DELIVERED, " //$NON-NLS-1$
            + "ZMESSAGE.ZSENDFAILED as SEND_FAILED, ZMESSAGE.ZSENDER as SENDER_ID, ZMESSAGE.ZDATE as SEND_DATE, " //$NON-NLS-1$
            + "ZCONTACT.ZFIRSTNAME as CONTACT_FIRSTNAME, ZCONTACT.ZLASTNAME as CONTACT_LASTNAME, ZCONTACT.ZPUBLICNICKNAME as CONTACT_NICKNAME, ZCONTACT.ZIDENTITY as CONTACT_IDENTITY, "
            + "ZMESSAGE.ZDELIVERYDATE as DELIVERY_DATE, ZMESSAGE.ZREADDATE as READ_DATE, ZMESSAGE.ZLATITUDE as LATITUDE, ZMESSAGE.ZLONGITUDE as LONGITUDE, " //$NON-NLS-1$
            + "ZMESSAGE.ZFILENAME as FILE_NAME, ZMESSAGE.ZFILESIZE as FILE_SIZE, ZMESSAGE.ZJSON as FILE_JSON, ZMESSAGE.ZMIMETYPE as FILE_MIMETYPE, ZMESSAGE.ZTYPE as FILE_TYPE, " //$NON-NLS-1$
            + "ZIMAGEDATA.ZDATA as THUMBNAIL_DATA, ZFILEDATA.ZDATA as FILE_DATA, ZMESSAGE.ZJSON as MESSAGE_JSON, ZMESSAGE.ZARG as MESSAGE_ARGS, ZMESSAGE.ZTYPE1 as MESSAGE_TYPE " //$NON-NLS-1$
            + "FROM ZMESSAGE " //$NON-NLS-1$
            + "LEFT JOIN ZIMAGEDATA on ZMESSAGE.ZTHUMBNAIL = ZIMAGEDATA.Z_PK " //$NON-NLS-1$
            + "LEFT JOIN ZFILEDATA on ZMESSAGE.ZDATA = ZFILEDATA.Z_PK " //$NON-NLS-1$
            + "LEFT JOIN ZCONTACT on ZMESSAGE.ZSENDER = ZCONTACT.Z_PK " + "WHERE ZMESSAGE.ZCONVERSATION = ?"; //$NON-NLS-2$

    private static final String SELECT_GROUP_MEMBERS = "SELECT " //$NON-NLS-1$
            + "ZCONTACT.Z_PK as CONTACT_ID, ZCONTACT.ZFIRSTNAME as CONTACT_FIRSTNAME, ZCONTACT.ZLASTNAME as CONTACT_LASTNAME, ZCONTACT.ZPUBLICNICKNAME as CONTACT_NICKNAME, ZIMAGEDATA.ZDATA as CONTACT_IMAGE, ZCONTACT.ZIDENTITY as CONTACT_IDENTITY "
            + "FROM ZMESSAGE " + "INNER JOIN ZCONTACT on ZCONTACT.Z_PK = ZMESSAGE.ZSENDER " + "LEFT JOIN ZIMAGEDATA on ZIMAGEDATA.Z_PK = ZCONTACT.ZCONTACTIMAGE " + "WHERE ZMESSAGE.ZCONVERSATION = ?";

    private static final Set<MessageType> MEDIA_MESSAGES = ImmutableSet.of(AUDIO_MESSAGE, VIDEO_MESSAGE, GIF_MESSAGE, APP_MESSAGE, IMAGE_MESSAGE);

}
