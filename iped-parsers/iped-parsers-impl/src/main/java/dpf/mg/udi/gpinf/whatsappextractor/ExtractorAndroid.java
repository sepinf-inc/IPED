package dpf.mg.udi.gpinf.whatsappextractor;

import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.APP_MESSAGE;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.AUDIO_MESSAGE;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.CONTACT_MESSAGE;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.DELETED_FROM_SENDER;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.DELETED_MESSAGE;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.ENCRIPTION_KEY_CHANGED;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.GIF_MESSAGE;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.GROUP_CREATED;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.GROUP_DESCRIPTION_CHANGED;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.GROUP_ICON_CHANGED;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.IMAGE_MESSAGE;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.LOCATION_MESSAGE;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.MESSAGES_NOW_ENCRYPTED;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.MISSED_VIDEO_CALL;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.MISSED_VOICE_CALL;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.SHARE_LOCATION_MESSAGE;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.SUBJECT_CHANGED;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.TEXT_MESSAGE;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.UNKNOWN_MESSAGE;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.USER_JOINED_GROUP;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.USER_JOINED_GROUP_FROM_LINK;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.USER_LEFT_GROUP;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.USER_REMOVED_FROM_GROUP;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.VIDEO_CALL;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.VIDEO_MESSAGE;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.VOICE_CALL;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.WAITING_MESSAGE;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.YOU_ADMIN;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.STICKER_MESSAGE;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import dpf.mg.udi.gpinf.whatsappextractor.Message.MessageStatus;
import dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType;

/**
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@dpf.gov.br>
 */
public class ExtractorAndroid extends Extractor {

    private boolean hasThumbTable = false;

    public ExtractorAndroid(File databaseFile, WAContactsDirectory contacts, WAAccount account) {
        super(databaseFile, contacts, account);
    }

    @Override
    protected List<Chat> extractChatList() throws WAExtractorException {
        List<Chat> list = new ArrayList<>();

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            boolean hasSortTimestamp = databaseHasSortTimestamp(conn);
            hasThumbTable = databaseHashThumbnailsTable(conn);
            String selectChatQuery = hasSortTimestamp ? SELECT_CHAT_LIST : SELECT_CHAT_LIST_NO_SORTTIMESTAMP;
            try (ResultSet rs = stmt.executeQuery(selectChatQuery)) {

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
                    c.setMessages(extractMessages(conn, c.getRemote(), c.isGroupChat()));
                    if (c.isGroupChat()) {
                        setGroupMembers(c, conn);
                    }
                }

            }
        } catch (SQLException ex) {
            throw new WAExtractorException(ex);
        }

        return list;
    }

    private void setGroupMembers(Chat c, Connection conn) throws WAExtractorException {

        try (PreparedStatement stmt = conn.prepareStatement(SELECT_GROUP_MEMBERS)) {
            stmt.setString(1, c.getRemote().getFullId());
            try (ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    String memberId = rs.getString("member");
                    if (!memberId.trim().isEmpty()) {
                        c.getGroupmembers().add(contacts.getContact(memberId));
                    }
                }

            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new WAExtractorException(ex);
        }

    }

    private boolean databaseHasSortTimestamp(Connection conn) throws SQLException {
        boolean result = false;
        DatabaseMetaData md = conn.getMetaData();
        try (ResultSet rs = md.getColumns(null, null, "chat_list", "sort_timestamp")) { //$NON-NLS-1$ //$NON-NLS-2$
            if (rs.next()) {
                result = true;
            }
        }
        return result;
    }

    private boolean databaseHashThumbnailsTable(Connection conn) throws SQLException {
        boolean result = false;
        try (Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(VERIFY_THUMBS_TABLE_EXISTS)) {
                if (rs.next()) {
                    result = true;
                }
            }
        }
        return result;
    }

    private List<Message> extractMessages(Connection conn, WAContact remote, boolean isGroupChat) throws SQLException {
        List<Message> messages = new ArrayList<>();
        try (PreparedStatement stmt = conn
                .prepareStatement(hasThumbTable ? SELECT_MESSAGES_THUMBS_TABLE : SELECT_MESSAGES_NO_THUMBS_TABLE)) {
            stmt.setFetchSize(1000);
            String id = remote.getId();
            id += isGroupChat ? "@g.us" : "@s.whatsapp.net"; //$NON-NLS-1$ //$NON-NLS-2$
            stmt.setString(1, id);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Message m = new Message();
                if (account != null)
                    m.setLocalResource(account.getId());
                int type = rs.getInt("messageType"); //$NON-NLS-1$
                int status = rs.getInt("status"); //$NON-NLS-1$
                String caption = rs.getString("mediaCaption"); //$NON-NLS-1$
                int edit_version = rs.getInt("edit_version"); //$NON-NLS-1$
                int media_size = rs.getInt("mediaSize"); //$NON-NLS-1$
                m.setId(rs.getLong("id")); //$NON-NLS-1$
                String remoteResource = rs.getString("remoteResource");
                if (remoteResource == null || remoteResource.isEmpty() || !isGroupChat) {
                    remoteResource = remote.getFullId();
                }
                m.setRemoteResource(remoteResource); // $NON-NLS-1$
                m.setStatus(status); // $NON-NLS-1$
                m.setData(Util.getUTF8String(rs, "data")); //$NON-NLS-1$
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
                m.setMessageType(decodeMessageType(type, status, edit_version, caption, media_size));
                m.setMediaDuration(rs.getInt("media_duration")); //$NON-NLS-1$
                if (m.getMessageType() == CONTACT_MESSAGE) {
                    m.setVcards(Arrays.asList(new String[] { m.getData() }));
                }
                byte[] thumbData = rs.getBytes("rawData"); //$NON-NLS-1$
                if (thumbData == null) {
                    thumbData = rs.getBytes("thumbData"); //$NON-NLS-1$
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

    protected Message.MessageType decodeMessageType(int messageType, int status, int edit_version, String caption,
            int mediaSize) {
        Message.MessageType result = UNKNOWN_MESSAGE;
        switch (messageType) {
            case 0:
                if (status == 6) {
                    switch (mediaSize) {
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
                        default:
                            break;
                    }
                } else {
                    result = TEXT_MESSAGE;
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
                if (edit_version == 5) {
                    result = DELETED_MESSAGE;
                } else {
                    result = DELETED_FROM_SENDER;
                }
                break; 
            case 16:
                result = SHARE_LOCATION_MESSAGE;
                break;
            case 20:
            	result=STICKER_MESSAGE;
            default:
                break;
        }
        return result;
    }

    /**
     * ** static strings ***
     */
    private static final String SELECT_CHAT_LIST = "SELECT _id as id,key_remote_jid AS contact," //$NON-NLS-1$
            + " subject, creation, sort_timestamp FROM chat_list ORDER BY sort_timestamp DESC"; //$NON-NLS-1$

    private static final String SELECT_CHAT_LIST_NO_SORTTIMESTAMP = "SELECT _id as id,key_remote_jid AS contact," //$NON-NLS-1$
            + " subject, creation FROM chat_list ORDER BY creation DESC"; //$NON-NLS-1$

    /*
     * Filtragem por status de mensagem (status): -1 - mensagens de sistema 0 -
     * mensagens 1 - ? 4 - mensagens 5 - mensagens 6 - ligacao / audio 7 - mensagens
     * 8 - audio enviado 10 - audio recebido 12 - mensagens 13 - mensagens
     */
    private static final String SELECT_MESSAGES_NO_THUMBS_TABLE = "SELECT _id AS id, key_remote_jid " //$NON-NLS-1$
            + "as remoteId, remote_resource AS remoteResource, status, data, " //$NON-NLS-1$
            + "key_from_me as fromMe, timestamp, media_url as mediaUrl, " //$NON-NLS-1$
            + "media_mime_type as mediaMime, media_size as mediaSize, media_name as mediaName, " //$NON-NLS-1$
            + "media_wa_type as messageType, null as thumbData, edit_version, latitude, longitude, media_duration, " //$NON-NLS-1$
            + "media_caption as mediaCaption, media_hash as mediaHash, raw_data as rawData FROM " //$NON-NLS-1$
            + "messages WHERE remoteId=? and status!=-1 ORDER BY timestamp"; //$NON-NLS-1$

    private static final String SELECT_MESSAGES_THUMBS_TABLE = "SELECT _id AS id, messages.key_remote_jid " //$NON-NLS-1$
            + "as remoteId, remote_resource AS remoteResource, status, data, " //$NON-NLS-1$
            + "messages.key_from_me as fromMe, messages.timestamp as timestamp, media_url as mediaUrl, " //$NON-NLS-1$
            + "media_mime_type as mediaMime, media_size as mediaSize, media_name as mediaName, " //$NON-NLS-1$
            + "media_wa_type as messageType, raw_data as rawData, edit_version, latitude, longitude, media_duration, " //$NON-NLS-1$
            + "media_caption as mediaCaption, media_hash as mediaHash, thumbnail as thumbData FROM " //$NON-NLS-1$
            + "messages LEFT JOIN message_thumbnails ON (messages.key_id = message_thumbnails.key_id " //$NON-NLS-1$
            + "AND messages.key_remote_jid = message_thumbnails.key_remote_jid " //$NON-NLS-1$
            + "AND messages.key_from_me = message_thumbnails.key_from_me) " //$NON-NLS-1$
            + "WHERE remoteId=? and status!=-1 ORDER BY timestamp"; //$NON-NLS-1$

    private static final String VERIFY_THUMBS_TABLE_EXISTS = "SELECT name FROM sqlite_master " //$NON-NLS-1$
            + "WHERE type='table' AND name='message_thumbnails'"; //$NON-NLS-1$

    private static final String SELECT_GROUP_MEMBERS = "select gjid as `group`,jid as member from chat_list cl "
            + " inner join group_participants gp on cl.key_remote_jid=gp.gjid where `group`=?";

}
