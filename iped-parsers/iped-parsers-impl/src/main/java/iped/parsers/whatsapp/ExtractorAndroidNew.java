package iped.parsers.whatsapp;

import static iped.parsers.whatsapp.Message.MessageType.APP_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.AUDIO_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.CONTACT_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.DELETED_FROM_SENDER;
import static iped.parsers.whatsapp.Message.MessageType.DELETED_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.ENCRIPTION_KEY_CHANGED;
import static iped.parsers.whatsapp.Message.MessageType.GIF_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_CREATED;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_DESCRIPTION_CHANGED;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_ICON_CHANGED;
import static iped.parsers.whatsapp.Message.MessageType.IMAGE_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.LOCATION_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.MESSAGES_ENCRYPTED;
import static iped.parsers.whatsapp.Message.MessageType.MESSAGES_NOW_ENCRYPTED;
import static iped.parsers.whatsapp.Message.MessageType.MISSED_VIDEO_CALL;
import static iped.parsers.whatsapp.Message.MessageType.MISSED_VOICE_CALL;
import static iped.parsers.whatsapp.Message.MessageType.SHARE_LOCATION_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.STICKER_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.SUBJECT_CHANGED;
import static iped.parsers.whatsapp.Message.MessageType.TEXT_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.UNKNOWN_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.USER_JOINED_GROUP;
import static iped.parsers.whatsapp.Message.MessageType.USER_JOINED_GROUP_FROM_LINK;
import static iped.parsers.whatsapp.Message.MessageType.USER_LEFT_GROUP;
import static iped.parsers.whatsapp.Message.MessageType.USER_REMOVED_FROM_GROUP;
import static iped.parsers.whatsapp.Message.MessageType.VIDEO_CALL;
import static iped.parsers.whatsapp.Message.MessageType.VIDEO_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.VOICE_CALL;
import static iped.parsers.whatsapp.Message.MessageType.WAITING_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.YOU_ADMIN;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import iped.parsers.sqlite.SQLite3DBParser;
import iped.parsers.whatsapp.Message.MessageStatus;
import iped.parsers.whatsapp.Message.MessageType;

/**
 *
 * @author Hauck
 */
public class ExtractorAndroidNew extends Extractor {


    public ExtractorAndroidNew(String itemPath, File databaseFile, WAContactsDirectory contacts, WAAccount account) {
        super(itemPath, databaseFile, contacts, account, false);
    }

    @Override
    protected List<Chat> extractChatList() throws WAExtractorException {
        List<Chat> list = new ArrayList<>();

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(SELECT_CHAT_VIEW)) {

                while (rs.next()) {
                    String contactId = rs.getString("contact"); //$NON-NLS-1$
                    WAContact remote = contacts.getContact(contactId);
                    Chat c = new Chat(remote);
                    c.setId(rs.getLong("id"));
                    c.setSubject(Util.getUTF8String(rs, "subject")); //$NON-NLS-1$
                    c.setGroupChat(contactId.endsWith("g.us")); //$NON-NLS-1$
                    if (!(contactId.endsWith("@status") || contactId.endsWith("@broadcast"))) { //$NON-NLS-1$ //$NON-NLS-2$
                        list.add(c);
                    }
                }

                for (Chat c : list) {
                    c.setMessages(extractMessages(conn, c));
                    c.getMessages().addAll(extractCalls(conn, c));
                    c.getMessages().sort((o1, o2) -> o1.getTimeStamp().compareTo(o2.getTimeStamp()));
                    if (c.isGroupChat()) {
                        setGroupMembers(c, conn, SELECT_GROUP_MEMBERS);
                    }
                }

            }
        } catch (SQLException ex) {
            throw new WAExtractorException(ex);
        }

        return list;
    }



    private void extractAddOns(Connection conn, Message m) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_ADD_ONS)) {
            stmt.setLong(1, m.getId());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                MessageAddOn addOn = new MessageAddOn();
                addOn.setFromMe(rs.getInt("fromMe") == 1);
                addOn.setRemoteResource(rs.getString("remoteResource"));
                addOn.setTimeStamp(new Date(rs.getLong("timestamp")));
                addOn.setStatus(rs.getInt("status"));
                addOn.setType(rs.getInt("type"));
                m.addMessageAddOn(addOn);
            }

        }
    }

    private List<Message> extractCalls(Connection conn, Chat c) throws SQLException {
        List<Message> messages = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(SELECT_CALLS)) {
            stmt.setFetchSize(1000);
            stmt.setLong(1, c.getId());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Message m = new Message();
                m.setRemoteId(c.getRemote().getFullId());
                int call_result = rs.getInt("call_result");
                if (account != null)
                    m.setLocalResource(account.getId());
                m.setRemoteResource(rs.getString("remoteId"));
                m.setId(rs.getLong("id"));
                m.setCallId(rs.getString("call_id"));
                if (rs.getInt("video_call") == 1) {
                    m.setMessageType(MessageType.UNKNOWN_VIDEO_CALL);
                    if (call_result == 5) {
                        m.setMessageType(VIDEO_CALL);
                    } else if (call_result == 4) {
                        m.setMessageType(MISSED_VIDEO_CALL);
                    } else if (call_result == 2) {
                        m.setMessageType(MessageType.REFUSED_VIDEO_CALL);
                    }
                } else {
                    m.setMessageType(MessageType.UNKNOWN_VOICE_CALL);
                    if (call_result == 5) {
                        m.setMessageType(VOICE_CALL);
                    } else if (call_result == 4) {
                        m.setMessageType(MISSED_VOICE_CALL);
                    } else if (call_result == 2) {
                        m.setMessageType(MessageType.REFUSED_VOICE_CALL);
                    }
                }
                m.setFromMe(rs.getInt("from_me") == 1);
                m.setMediaDuration(rs.getInt("duration"));
                m.setTimeStamp(new Date(rs.getLong("timestamp")));

                messages.add(m);
            }

        }

        return messages;
    }

    private List<Message> extractMessages(Connection conn, Chat c) throws SQLException {
        List<Message> messages = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(getSelectMessagesQuery(conn))) {
            stmt.setFetchSize(1000);
            stmt.setLong(1, c.getId());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Message m = new Message();
                if (account != null)
                    m.setLocalResource(account.getId());
                int type = rs.getInt("messageType"); //$NON-NLS-1$
                int status = rs.getInt("status"); //$NON-NLS-1$
                Integer edit_version;
                try {
                    edit_version = Integer.parseInt(SQLite3DBParser.getStringIfExists(rs, "edit_version"));
                } catch (NumberFormatException e) {
                    edit_version = null;
                }

                long media_size = rs.getLong("mediaSize"); //$NON-NLS-1$

                m.setId(rs.getLong("id")); //$NON-NLS-1$
                String remoteResource = rs.getString("remoteResource");
                if (remoteResource == null || remoteResource.isEmpty() || !c.isGroupChat()) {
                    remoteResource = c.getRemote().getFullId();
                }
                m.setRemoteResource(remoteResource); // $NON-NLS-1$
                m.setStatus(status); // $NON-NLS-1$
                m.setData(Util.getUTF8String(rs, "text_data")); //$NON-NLS-1$
                String caption = rs.getString("mediaCaption"); //$NON-NLS-1$
                if (caption == null || caption.isBlank()) {
                    caption = m.getData();
                }
                m.setFromMe(rs.getInt("fromMe") == 1); //$NON-NLS-1$
                m.setTimeStamp(new Date(rs.getLong("timestamp"))); //$NON-NLS-1$
                m.setMediaUrl(rs.getString("mediaUrl")); //$NON-NLS-1$
                m.setMediaMime(rs.getString("mediaMime")); //$NON-NLS-1$
                m.setMediaName(rs.getString("mediaName")); //$NON-NLS-1$
                m.setMediaCaption(caption); // $NON-NLS-1$
                m.setMediaHash(rs.getString("mediaHash"), true); //$NON-NLS-1$
                m.setMediaSize(media_size);
                m.setLatitude(rs.getDouble("latitude")); //$NON-NLS-1$
                m.setLongitude(rs.getDouble("longitude")); //$NON-NLS-1$
                m.setMessageType(decodeMessageType(type, status, edit_version, caption, rs.getInt("actionType")));
                m.setMediaDuration(rs.getInt("media_duration")); //$NON-NLS-1$
                if (m.getMessageType() == CONTACT_MESSAGE) {
                    m.setVcards(Arrays.asList(new String[] { Util.getUTF8String(rs, "vcard") }));
                }
                byte[] thumbData = rs.getBytes("thumbData"); //$NON-NLS-1$

                boolean hasAddOn = rs.getInt("hasAddOn") != 0;

                if (hasAddOn) {
                    extractAddOns(conn, m);
                }

                m.setThumbData(thumbData);
                if (m.isFromMe()) {
                    switch (m.getStatus()) {
                        case 4:
                            m.setMessageStatus(MessageStatus.MESSAGE_SENT);
                            break;
                        case 5:
                            m.setMessageStatus(MessageStatus.MESSAGE_DELIVERED);
                            break;
                        case 13:
                            m.setMessageStatus(MessageStatus.MESSAGE_VIEWED);
                            break;
                        case 0:
                            m.setMessageStatus(MessageStatus.MESSAGE_UNSENT);
                            break;
                        default:
                            break;
                    }
                }
                messages.add(m);

            }
        }
        return messages;
    }

    protected Message.MessageType decodeMessageType(int messageType, int status, Integer edit_version, String caption,
            int actionType) {
        Message.MessageType result = UNKNOWN_MESSAGE;
        switch (messageType) {
            case 0:
                result = TEXT_MESSAGE;
            case 7:
                switch (actionType) {
                    case 1:
                        result = SUBJECT_CHANGED;
                        break;
                    case 4:
                    case 12:
                        result = USER_JOINED_GROUP;
                        break;
                    case 5:
                        result = USER_LEFT_GROUP;
                        break;
                    case 6:
                        result = GROUP_ICON_CHANGED;
                        break;
                    case 7:
                    case 14:
                        result = USER_REMOVED_FROM_GROUP;
                        break;
                    case 11:
                        result = GROUP_CREATED;
                        break;
                    case 15:
                        result = YOU_ADMIN;
                        break;
                    case 18:
                        result = ENCRIPTION_KEY_CHANGED;
                        break;
                    case 19:
                        result = MESSAGES_NOW_ENCRYPTED;
                        break;
                    case 20:
                        result = USER_JOINED_GROUP_FROM_LINK;
                        break;
                    case 27:
                        result = GROUP_DESCRIPTION_CHANGED;
                        break;
                    case 46:
                        result = MessageType.BUSINESS_CHAT;
                        break;
                    case 67:
                        result = MESSAGES_ENCRYPTED;
                        break;
                    default:
                        break;
                }

                break;
            case 1:
                result = IMAGE_MESSAGE;
                break;
            case 2:
                result = AUDIO_MESSAGE;
                break;
            case 3:
                result = VIDEO_MESSAGE;
                break;
            case 4:
                result = CONTACT_MESSAGE;
                break;
            case 5:
                result = LOCATION_MESSAGE;
                break;
            case 8:
                if (caption != null) {
                    if (caption.equals("video")) { //$NON-NLS-1$
                        result = VIDEO_CALL;
                    } else if (caption.equals("audio")) { //$NON-NLS-1$
                        result = VOICE_CALL;
                    }
                }
                break;
            case 9:
                result = APP_MESSAGE;
                break;
            case 10:
                if (caption != null) {
                    if (caption.equals("video")) { //$NON-NLS-1$
                        result = MISSED_VIDEO_CALL;
                    } else if (caption.equals("audio")) { //$NON-NLS-1$
                        result = MISSED_VOICE_CALL;
                    }
                }
                break;
            case 11:
                result = WAITING_MESSAGE;
                break;
            case 13:
                result = GIF_MESSAGE;
                break;
            case 15:
                if (edit_version != null) {
                    if (edit_version == 5) {
                        result = DELETED_MESSAGE;
                    } else {
                        result = DELETED_FROM_SENDER;
                    }
                }
                break;
            case 16:
                result = SHARE_LOCATION_MESSAGE;
                break;
            case 20:
                result = STICKER_MESSAGE;
            default:
                break;
        }
        return result;
    }

    private static final String SELECT_CHAT_VIEW = "SELECT _id as id, raw_string_jid AS contact," //$NON-NLS-1$
            + " subject, created_timestamp as creation, sort_timestamp FROM chat_view ORDER BY sort_timestamp DESC"; //$NON-NLS-1$

    private static final String SELECT_ADD_ONS = "SELECT message_add_on_type as type,timestamp, status,jid.raw_string as remoteResource,from_me as fromMe FROM message_add_on m left join jid on jid._id=m.sender_jid_row_id where parent_message_row_id=?";

    private static String getSelectMessagesQuery(Connection conn) throws SQLException {
        String captionCol = SQLite3DBParser.checkIfColumnExists(conn, "message_media", "media_caption") ? "mm.media_caption" : "null";
        return "select m._id AS id,cv._id as chatId, cv.raw_string_jid "
                + " as remoteId, jid.raw_string as remoteResource, status, mv.vcard, m.text_data, "
                + " m.from_me as fromMe, m.timestamp as timestamp, message_url as mediaUrl,"
                + " mm.mime_type as mediaMime, mm.file_length as mediaSize, media_name as mediaName, "
                + " m.message_type as messageType, latitude, longitude, mm.media_duration, "
                + captionCol + " as mediaCaption, mm.file_hash as mediaHash, thumbnail as thumbData,"
                + " ms.action_type as actionType, m.message_add_on_flags as hasAddOn"
                + " from message m inner join chat_view cv on m.chat_row_id=cv._id"
                + " left join message_media mm on mm.message_row_id=m._id"
                + " left join jid on jid._id=m.sender_jid_row_id"
                + " left join message_location ml on m._id=ml.message_row_id "
                + " left join message_system ms on m._id=ms.message_row_id"
                + " left join message_vcard mv on m._id=mv.message_row_id"
                + " left join message_thumbnail mt on m._id=mt.message_row_id where chatId=? and status!=-1 ;";
    }

    private static final String SELECT_CALLS = "select c_l._id as id, c_l.call_id, c_l.video_call, c_l.duration, c_l.timestamp, c_l.call_result, c_l.from_me,\r\n"
            + " cv._id as chatId, cv.raw_string_jid as remoteId\r\n"
            + "  from call_log c_l inner join chat c on c_l.jid_row_id=c.jid_row_id inner join chat_view cv on cv._id=c._id\r\n"
            + "where chatId=?";

    private static final String SELECT_GROUP_MEMBERS = "select g._id as group_id, g.raw_string as group_name, u._id as user_id, u.raw_string as member "
            + "FROM group_participant_user gp inner join jid g on g._id=gp.group_jid_row_id inner join jid u on u._id=gp.user_jid_row_id where u.server='s.whatsapp.net' and u.type=0 and group_name=?"; //$NON-NLS-1$

}