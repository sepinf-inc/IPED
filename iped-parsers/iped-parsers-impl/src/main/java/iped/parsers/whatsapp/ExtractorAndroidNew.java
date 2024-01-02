package iped.parsers.whatsapp;

import static iped.parsers.whatsapp.Message.MessageType.APP_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.AUDIO_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.BLOCKED_CONTACT;
import static iped.parsers.whatsapp.Message.MessageType.BUSINESS_CHAT;
import static iped.parsers.whatsapp.Message.MessageType.BUSINESS_META_SECURE_SERVICE;
import static iped.parsers.whatsapp.Message.MessageType.CALL_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.CONTACT_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.DELETED_BY_ADMIN;
import static iped.parsers.whatsapp.Message.MessageType.DELETED_BY_SENDER;
import static iped.parsers.whatsapp.Message.MessageType.DELETED_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.ENCRYPTION_KEY_CHANGED;
import static iped.parsers.whatsapp.Message.MessageType.EPHEMERAL_DURATION_CHANGED;
import static iped.parsers.whatsapp.Message.MessageType.GIF_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_CREATED;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_DESCRIPTION_CHANGED;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_ICON_CHANGED;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_INVITE;
import static iped.parsers.whatsapp.Message.MessageType.IMAGE_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.LOCATION_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.MESSAGES_ENCRYPTED;
import static iped.parsers.whatsapp.Message.MessageType.MESSAGES_NOW_ENCRYPTED;
import static iped.parsers.whatsapp.Message.MessageType.MISSED_VIDEO_CALL;
import static iped.parsers.whatsapp.Message.MessageType.MISSED_VOICE_CALL;
import static iped.parsers.whatsapp.Message.MessageType.POLL_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.REFUSED_VIDEO_CALL;
import static iped.parsers.whatsapp.Message.MessageType.REFUSED_VOICE_CALL;
import static iped.parsers.whatsapp.Message.MessageType.SHARE_LOCATION_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.STICKER_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.SUBJECT_CHANGED;
import static iped.parsers.whatsapp.Message.MessageType.TEMPLATE_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.TEXT_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.UNAVAILABLE_VIDEO_CALL;
import static iped.parsers.whatsapp.Message.MessageType.UNAVAILABLE_VOICE_CALL;
import static iped.parsers.whatsapp.Message.MessageType.UNBLOCKED_CONTACT;
import static iped.parsers.whatsapp.Message.MessageType.UNKNOWN_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.UNKNOWN_VIDEO_CALL;
import static iped.parsers.whatsapp.Message.MessageType.UNKNOWN_VOICE_CALL;
import static iped.parsers.whatsapp.Message.MessageType.USER_JOINED_GROUP;
import static iped.parsers.whatsapp.Message.MessageType.USER_JOINED_GROUP_FROM_LINK;
import static iped.parsers.whatsapp.Message.MessageType.USER_LEFT_GROUP;
import static iped.parsers.whatsapp.Message.MessageType.USER_REMOVED_FROM_GROUP;
import static iped.parsers.whatsapp.Message.MessageType.VIDEO_CALL;
import static iped.parsers.whatsapp.Message.MessageType.VIDEO_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.VIEW_ONCE_IMAGE_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.VIEW_ONCE_VIDEO_MESSAGE;
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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import iped.parsers.sqlite.SQLite3DBParser;
import iped.parsers.whatsapp.Message.MessageStatus;

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
        Map<Long, Chat> idToChat = new HashMap<Long, Chat>();

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
                        idToChat.put(c.getId(), c);
                    }
                }

                extractMessages(conn, idToChat);
                extractCalls(conn, idToChat);

                for (Chat c : list) {
                    Collections.sort(c.getMessages());
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

    private boolean isUnblocked(Connection conn, long id) throws SQLException {
        boolean isUnblocked = false;
        String query = getSelectBlockedQuery(conn);
        if (query != null) {
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setLong(1, id);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    isUnblocked = rs.getInt("isBlocked") == 0;
                }
            }
        }
        return isUnblocked;
    }

    private void extractAddOns(Connection conn, Message m, boolean hasReactionTable) throws SQLException {
        String query = hasReactionTable ? SELECT_ADD_ONS_REACTIONS : SELECT_ADD_ONS;
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setLong(1, m.getId());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                MessageAddOn addOn = new MessageAddOn();
                addOn.setFromMe(rs.getInt("fromMe") == 1);
                addOn.setRemoteResource(rs.getString("remoteResource"));
                addOn.setTimeStamp(new Date(rs.getLong("timestamp")));
                addOn.setStatus(rs.getInt("status"));
                addOn.setType(rs.getInt("type"));
                if (hasReactionTable) {
                    addOn.setReaction(rs.getString("reaction"));
                }
                m.addMessageAddOn(addOn);
            }
        }
    }

    private void extractCalls(Connection conn, Map<Long, Chat> idToChat) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_CALLS)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                long chatId = rs.getLong("chatId");
                Chat c = idToChat.get(chatId);
                if (c == null) {
                    continue;
                }
                Message m = new Message();
                m.setRemoteId(c.getRemote().getFullId());
                int call_result = rs.getInt("call_result");
                if (account != null)
                    m.setLocalResource(account.getId());
                m.setRemoteResource(rs.getString("remoteId"));
                m.setId(rs.getLong("id"));
                m.setCallId(rs.getString("call_id"));
                if (rs.getInt("video_call") == 1) {
                    m.setMessageType(UNKNOWN_VIDEO_CALL);
                    if (call_result == 5) {
                        m.setMessageType(VIDEO_CALL);
                    } else if (call_result == 4) {
                        m.setMessageType(MISSED_VIDEO_CALL);
                    } else if (call_result == 2) {
                        m.setMessageType(REFUSED_VIDEO_CALL);
                    } else if (call_result == 3) {
                        m.setMessageType(UNAVAILABLE_VIDEO_CALL);
                    }
                } else {
                    m.setMessageType(UNKNOWN_VOICE_CALL);
                    if (call_result == 5) {
                        m.setMessageType(VOICE_CALL);
                    } else if (call_result == 4) {
                        m.setMessageType(MISSED_VOICE_CALL);
                    } else if (call_result == 2) {
                        m.setMessageType(REFUSED_VOICE_CALL);
                    } else if (call_result == 3) {
                        m.setMessageType(UNAVAILABLE_VOICE_CALL);
                    }
                }
                m.setFromMe(rs.getInt("from_me") == 1);
                m.setMediaDuration(rs.getInt("duration"));
                m.setTimeStamp(new Date(rs.getLong("timestamp")));

                c.getMessages().add(m);
            }

        }
    }

    private void extractTemplateInfo(Connection conn, Message m) throws SQLException {
        MessageTemplate t = null;
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_TEMPLATE)) {
            stmt.setLong(1, m.getId());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                t = new MessageTemplate(rs.getString("content"));
            }
        }

        if (t != null) {
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_TEMPLATE_BUTTON)) {
                stmt.setLong(1, m.getId());
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    t.addButton(new MessageTemplate.Button(rs.getString("text"), rs.getString("extra")));
                }
            }
            m.setMessageTemplate(t);
        }
    }

    private void extractPollOptions(Connection conn, Message m) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_POLL_OPTION)) {
            stmt.setLong(1, m.getId());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String name = rs.getString("name");
                int total = rs.getInt("total");
                m.addPollOption(new PollOption(name, total));
            }
        }
    }

    private void extractEphemeralDuration(Connection conn, Message m) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_EPHEMERAL_SETTING)) {
            stmt.setLong(1, m.getId());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                // Use mediaDuration to avoid creating another field
                m.setMediaDuration(rs.getInt("duration"));
            }
        }
    }

    private void extractMessages(Connection conn, Map<Long, Chat> idToChat) throws SQLException {
        boolean hasReactionTable = SQLite3DBParser.containsTable("message_add_on_reaction", conn);
        boolean hasTemplateTables = SQLite3DBParser.containsTable("message_template", conn)
                && SQLite3DBParser.containsTable("message_template_button", conn);
        boolean hasPollOptionTable = SQLite3DBParser.containsTable("message_poll_option", conn);
        boolean hasEphemeralSettingTable = SQLite3DBParser.containsTable("message_ephemeral_setting", conn);

        try (PreparedStatement stmt = conn.prepareStatement(getSelectMessagesQuery(conn))) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                long chatId = rs.getLong("chatId");
                Chat c = idToChat.get(chatId);
                if (c == null) {
                    continue;
                }
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
                m.setMessageType(decodeMessageType(type, status, edit_version, caption, rs.getInt("actionType"),
                        rs.getInt("bizStateId")));
                m.setMediaDuration(rs.getInt("media_duration")); //$NON-NLS-1$
                if (m.getMessageType() == CONTACT_MESSAGE) {
                    m.setVcards(Arrays.asList(new String[] { Util.getUTF8String(rs, "vcard") }));
                }
                byte[] thumbData = rs.getBytes("thumbData"); //$NON-NLS-1$
                if (thumbData == null) {
                    thumbData = rs.getBytes("thumbData2");
                }
                m.setThumbData(thumbData);

                boolean hasAddOn = rs.getInt("hasAddOn") != 0;

                if (hasAddOn) {
                    extractAddOns(conn, m, hasReactionTable);
                }

                if (hasPollOptionTable && m.getMessageType() == POLL_MESSAGE) {
                    extractPollOptions(conn, m);
                }
                
                if (hasEphemeralSettingTable && m.getMessageType() == EPHEMERAL_DURATION_CHANGED) {
                    extractEphemeralDuration(conn, m);
                }

                if (m.getMessageType() == BLOCKED_CONTACT && isUnblocked(conn, m.getId())) {
                    m.setMessageType(UNBLOCKED_CONTACT);
                }

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
                m.setForwarded(rs.getInt("forwarded") > 0);
                m.setUuid(rs.getString("uuid"));
                m.setGroupInviteName(rs.getString("groupInviteName"));
                m.setSortId(rs.getLong("sortId"));

                if (hasTemplateTables && m.getMessageType() == TEMPLATE_MESSAGE) {
                    extractTemplateInfo(conn, m);
                }

                c.getMessages().add(m);
            }
        }

        long fakeIds = 2000000000L;
        for (Chat c : idToChat.values()) {
            HashMap<Long, Message> messagesMap = new HashMap<Long, Message>();
            HashMap<String, Message> messagesMapUuid = new HashMap<String, Message>();
            for (Message m : c.getMessages()) {
                messagesMap.put(m.getId(), m);
                if (m.getUuid() != null && !m.getUuid().isEmpty()) {
                    messagesMapUuid.put(m.getUuid(), m);
                }
            }
            // Find quote messages
            List<Message> messagesQuotes = extractQuoteMessages(conn, c);
            for (Message mq : messagesQuotes) {
                Message m = messagesMap.get(mq.getId());
                if (m != null) {// Has quote
                    Message original = messagesMapUuid.get(mq.getUuid());// Try to find original message in messages
                    if (original != null) {// has found original message reference, more complete
                        m.setMessageQuote(original);
                    } else {// not found original message reference, get info from message_quotes table,
                            // less complete
                        mq.setDeleted(true);
                        mq.setId(fakeIds--);
                        m.setMessageQuote(mq);
                    }
                    m.setQuoted(true);
                }
            }
            messagesMap.clear();
            messagesMapUuid.clear();
        }

    }

    private List<Message> extractQuoteMessages(Connection conn, Chat c) throws SQLException {

        List<Message> messages = new ArrayList<>();
        String query = getSelectMessagesQuotesQuery(conn);
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setLong(1, c.getId());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {

                Message m = new Message();
                if (account != null)
                    m.setLocalResource(account.getId());
                int type = rs.getInt("messageType"); //$NON-NLS-1$
                long media_size = rs.getLong("mediaSize"); //$NON-NLS-1$

                m.setId(rs.getLong("id")); //$NON-NLS-1$
                String remoteResource = rs.getString("remoteResource");
                if (remoteResource == null || remoteResource.isEmpty() || !c.isGroupChat()) {
                    remoteResource = c.getRemote().getFullId();
                }
                m.setRemoteResource(remoteResource); // $NON-NLS-1$
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
                m.setMediaHash(rs.getString("mediaHash"), true); //$NON-NLS-1$
                m.setMediaSize(media_size);
                m.setLatitude(rs.getDouble("latitude")); //$NON-NLS-1$
                m.setLongitude(rs.getDouble("longitude")); //$NON-NLS-1$
                m.setMessageType(decodeMessageType(type, -1, -1, caption, -1, -1));
                m.setMediaDuration(rs.getInt("media_duration")); //$NON-NLS-1$
                if (m.getMessageType() == CONTACT_MESSAGE) {
                    m.setVcards(Arrays.asList(new String[] { Util.getUTF8String(rs, "vcard") }));
                }

                byte[] thumbData = rs.getBytes("thumbData"); //$NON-NLS-1$

                if (m.getMessageType() == BLOCKED_CONTACT && isUnblocked(conn, m.getId())) {
                    m.setMessageType(UNBLOCKED_CONTACT);
                }
                m.setThumbData(thumbData);

                m.setUuid(rs.getString("uuid"));

                messages.add(m);
            }
        }

        return messages;
    }

    protected Message.MessageType decodeMessageType(int messageType, int status, Integer edit_version, String caption,
            int actionType, int bizStateId) {
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
                        result = ENCRYPTION_KEY_CHANGED;
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
                        result = BUSINESS_CHAT;
                        break;
                    case 58:
                        result = BLOCKED_CONTACT;
                        break;
                    case 67:
                        if (bizStateId == 10) {
                            result = BUSINESS_META_SECURE_SERVICE;
                        } else {
                            result = MESSAGES_ENCRYPTED;
                        }
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
                        result = DELETED_BY_SENDER;
                    }
                } else {
                    if (status == 0) {
                        result = DELETED_BY_SENDER;
                    } else if (status == 4 || status == 5) {
                        result = DELETED_MESSAGE;
                    }
                }
                break;
            case 16:
                result = SHARE_LOCATION_MESSAGE;
                break;
            case 20:
                result = STICKER_MESSAGE;
                break;
            case 24:
                result = GROUP_INVITE;
                break;
            case 25:
            case 27:
            case 28:
                result = TEMPLATE_MESSAGE;
                break;
            case 36:
                result = EPHEMERAL_DURATION_CHANGED;
                break;
            case 42:
                result = VIEW_ONCE_IMAGE_MESSAGE;
                break;
            case 43:
                result = VIEW_ONCE_VIDEO_MESSAGE;
                break;
            case 64:
                if (status == 0) {
                    result = DELETED_BY_ADMIN;
                }
                break;
            case 66:
                result = POLL_MESSAGE;
                break;
            case 90:
                // Newer databases also have entries to any call in messages table
                result = CALL_MESSAGE;
                break;
            default:
                break;
        }
        return result;
    }

    private static final String SELECT_CHAT_VIEW = "SELECT _id as id, raw_string_jid AS contact," //$NON-NLS-1$
            + " subject, created_timestamp as creation, sort_timestamp FROM chat_view ORDER BY sort_timestamp DESC"; //$NON-NLS-1$

    private static final String SELECT_ADD_ONS = "SELECT message_add_on_type as type,timestamp, status,jid.raw_string as remoteResource,from_me as fromMe FROM message_add_on m left join jid on jid._id=m.sender_jid_row_id where parent_message_row_id=?";

    private static final String SELECT_ADD_ONS_REACTIONS = "SELECT message_add_on_type as type,timestamp, status,jid.raw_string as remoteResource,from_me as fromMe, r.reaction as reaction"
            + " FROM message_add_on m"
            + " left join jid on jid._id=m.sender_jid_row_id"
            + " left join message_add_on_reaction r on r.message_add_on_row_id=m._id"
            + " where parent_message_row_id=?";

    private static final String SELECT_TEMPLATE = "SELECT content_text_data as content FROM message_template where message_row_id=?";

    private static final String SELECT_POLL_OPTION = "SELECT option_name as name, vote_total as total FROM message_poll_option where message_row_id=? order by _id";

    private static final String SELECT_EPHEMERAL_SETTING = "SELECT setting_duration as duration FROM message_ephemeral_setting where message_row_id=?";
    
    private static final String SELECT_TEMPLATE_BUTTON = "SELECT text_data as text, extra_data as extra FROM message_template_button where message_row_id=? order by _id";

    private static String getSelectMessagesQuery(Connection conn) throws SQLException {
        String captionCol = SQLite3DBParser.checkIfColumnExists(conn, "message_media", "media_caption")
                ? "mm.media_caption"
                : "null";

        String sortCol = SQLite3DBParser.checkIfColumnExists(conn, "message", "sort_id")
                ? "m.sort_id"
                : "0";

        String mhtCol = "null";
        String mhtTableJoin = "";
        if (SQLite3DBParser.containsTable("media_hash_thumbnail", conn)) {
            mhtCol = "mht.thumbnail";
            mhtTableJoin = " left join media_hash_thumbnail mht on mm.file_hash=mht.media_hash";
        }

        String bizStateCol = "0";
        String bizStateTableJoin = "";
        if (SQLite3DBParser.containsTable("message_system_initial_privacy_provider", conn)) {
            bizStateCol = "msipp.biz_state_id";
            bizStateTableJoin = " left join message_system_initial_privacy_provider msipp on m._id=msipp.message_row_id";
        }

        String grpInvCol = "null";
        String grpInvTableJoin = "";
        if (SQLite3DBParser.containsTable("message_group_invite", conn)) {
            grpInvCol = "mgi.group_name";
            grpInvTableJoin = " left join message_group_invite mgi on m._id=mgi.message_row_id";
        }

        return "select m._id AS id,cv._id as chatId, cv.raw_string_jid "
                + " as remoteId, jid.raw_string as remoteResource, status, mv.vcard, m.text_data,"
                + " m.from_me as fromMe, m.timestamp as timestamp, message_url as mediaUrl,"
                + " mm.mime_type as mediaMime, mm.file_length as mediaSize, media_name as mediaName,"
                + " m.message_type as messageType, latitude, longitude, mm.media_duration, " + captionCol
                + " as mediaCaption, mm.file_hash as mediaHash, mt.thumbnail as thumbData, m.key_id as uuid,"
                + " ms.action_type as actionType, m.message_add_on_flags as hasAddOn,"
                + " (m.origination_flags & 1) as forwarded,"
                + " " + mhtCol + " as thumbData2,"
                + " " + bizStateCol + " as bizStateId,"
                + " " + grpInvCol + " as groupInviteName,"
                + " " + sortCol + " as sortId"
                + " from message m inner join chat_view cv on m.chat_row_id=cv._id"
                + " left join message_media mm on mm.message_row_id=m._id"
                + " left join jid on jid._id=m.sender_jid_row_id"
                + " left join message_location ml on m._id=ml.message_row_id "
                + " left join message_system ms on m._id=ms.message_row_id"
                + " left join message_vcard mv on m._id=mv.message_row_id"
                + mhtTableJoin
                + bizStateTableJoin
                + grpInvTableJoin
                + " left join message_thumbnail mt on m._id=mt.message_row_id where status!=-1";
    }

    private static String getSelectMessagesQuotesQuery(Connection conn) throws SQLException {
        String captionCol = SQLite3DBParser.checkIfColumnExists(conn, "message_quoted_media", "media_caption")
                ? "mm.media_caption"
                : "null";
        return "select mq.message_row_id as id,cv._id as chatId, cv.raw_string_jid as remoteId,"
                + " jid.raw_string as remoteResource, mv.vcard, mq.text_data,"
                + " mq.from_me as fromMe, mq.timestamp as timestamp, message_url as mediaUrl,"
                + " mm.mime_type as mediaMime, mm.file_length as mediaSize, media_name as mediaName,"
                + " mq.message_type as messageType, latitude, longitude, mm.media_duration, " + captionCol
                + " as mediaCaption, mm.file_hash as mediaHash, mm.thumbnail as thumbData,"
				+ " mq.key_id as uuid"
                + " from message_quoted mq inner join chat_view cv on mq.chat_row_id=cv._id"
                + " left join message_quoted_media mm on mm.message_row_id=mq.message_row_id"
                + " left join jid on jid._id=mq.sender_jid_row_id"
                + " left join message_quoted_location ml on mq.message_row_id=ml.message_row_id"
                + " left join message_quoted_vcard mv on mq.message_row_id=mv.message_row_id"
                + " where chatId=?";
    }

    private static String getSelectBlockedQuery(Connection conn) throws SQLException {
        if (!SQLite3DBParser.containsTable("message_system_block_contact", conn)) {
            return null;
        }
        return "select is_blocked as isBlocked from message_system_block_contact where message_row_id=?";
    }

    private static final String SELECT_CALLS = "select c_l._id as id, c_l.call_id, c_l.video_call, c_l.duration,"
            + " c_l.timestamp, c_l.call_result, c_l.from_me, cv._id as chatId, cv.raw_string_jid as remoteId"
            + " from call_log c_l inner join chat c on c_l.jid_row_id=c.jid_row_id inner join chat_view cv on cv._id=c._id";

    private static final String SELECT_GROUP_MEMBERS = "select g._id as group_id, g.raw_string as group_name, u._id as user_id, u.raw_string as member "
            + "FROM group_participant_user gp inner join jid g on g._id=gp.group_jid_row_id inner join jid u on u._id=gp.user_jid_row_id where u.server='s.whatsapp.net' and u.type=0 and group_name=?"; //$NON-NLS-1$

}
