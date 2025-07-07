package iped.parsers.whatsapp;

import static iped.parsers.whatsapp.Message.MessageType.AI_THIRD_PARTY;
import static iped.parsers.whatsapp.Message.MessageType.ANY_COMMUNITY_MEMBER_CAN_JOIN_GROUP;
import static iped.parsers.whatsapp.Message.MessageType.AUDIO_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.BLOCKED_CONTACT;
import static iped.parsers.whatsapp.Message.MessageType.BUSINESS_CHAT;
import static iped.parsers.whatsapp.Message.MessageType.BUSINESS_META_SECURE_SERVICE;
import static iped.parsers.whatsapp.Message.MessageType.BUSINESS_OFFICIAL;
import static iped.parsers.whatsapp.Message.MessageType.CALL_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.CHANGED_DEVICE;
import static iped.parsers.whatsapp.Message.MessageType.CHANGED_NUMBER_TO;
import static iped.parsers.whatsapp.Message.MessageType.CHANNEL_ADDED_PRIVACY;
import static iped.parsers.whatsapp.Message.MessageType.CHANNEL_CREATED;
import static iped.parsers.whatsapp.Message.MessageType.CHAT_ADDED_PRIVACY;
import static iped.parsers.whatsapp.Message.MessageType.CHAT_STARTED_FROM_AD;
import static iped.parsers.whatsapp.Message.MessageType.COMMUNITY_MANAGEMENT_ACTION;
import static iped.parsers.whatsapp.Message.MessageType.COMMUNITY_RENAMED;
import static iped.parsers.whatsapp.Message.MessageType.COMMUNITY_WELCOME;
import static iped.parsers.whatsapp.Message.MessageType.CONTACTED_FIND_BUSINESSES;
import static iped.parsers.whatsapp.Message.MessageType.CONTACT_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.DELETED_BY_ADMIN;
import static iped.parsers.whatsapp.Message.MessageType.DELETED_BY_SENDER;
import static iped.parsers.whatsapp.Message.MessageType.DELETED_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.DOC_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.ENCRYPTION_KEY_CHANGED;
import static iped.parsers.whatsapp.Message.MessageType.EPHEMERAL_CHANGED;
import static iped.parsers.whatsapp.Message.MessageType.EPHEMERAL_DEFAULT;
import static iped.parsers.whatsapp.Message.MessageType.EPHEMERAL_DURATION_CHANGED;
import static iped.parsers.whatsapp.Message.MessageType.EPHEMERAL_SAVE;
import static iped.parsers.whatsapp.Message.MessageType.EPHEMERAL_SETTINGS_NOT_APPLIED;
import static iped.parsers.whatsapp.Message.MessageType.GIF_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_ADDED_TO_COMMUNITY;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_CHANGED_ADMIN_APPROVAL_OFF;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_CHANGED_ALL_MEMBERS_CAN_ADD;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_CHANGED_ALL_MEMBERS_CAN_EDIT;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_CHANGED_ALL_MEMBERS_CAN_SEND;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_CHANGED_ONLY_ADMINS_CAN_ADD;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_CHANGED_ONLY_ADMINS_CAN_EDIT;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_CHANGED_ONLY_ADMINS_CAN_SEND;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_CREATED;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_DESCRIPTION_CHANGED;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_ICON_CHANGED;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_INVITE;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_ONLY_ADMINS_CAN_SEND;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_REMOVED_FROM_COMMUNITY;
import static iped.parsers.whatsapp.Message.MessageType.IMAGE_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.LOCATION_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.MESSAGE_ASSOCIATION;
import static iped.parsers.whatsapp.Message.MessageType.MESSAGES_ENCRYPTED;
import static iped.parsers.whatsapp.Message.MessageType.MESSAGES_NOW_ENCRYPTED;
import static iped.parsers.whatsapp.Message.MessageType.MISSED_VIDEO_CALL;
import static iped.parsers.whatsapp.Message.MessageType.MISSED_VOICE_CALL;
import static iped.parsers.whatsapp.Message.MessageType.NEW_PARTICIPANTS_NEED_ADMIN_APPROVAL;
import static iped.parsers.whatsapp.Message.MessageType.ORDER_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.OVER_256_MEMBERS_ONLY_ADMINS_CAN_EDIT;
import static iped.parsers.whatsapp.Message.MessageType.PINNED_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.POLL_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.PRODUCT_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.REFUSED_VIDEO_CALL;
import static iped.parsers.whatsapp.Message.MessageType.REFUSED_VOICE_CALL;
import static iped.parsers.whatsapp.Message.MessageType.RESET_GROUP_LINK;
import static iped.parsers.whatsapp.Message.MessageType.SECURITY_NOTIFICATIONS_NO_LONGER_AVAILABLE;
import static iped.parsers.whatsapp.Message.MessageType.SENDER_IN_CONTACTS;
import static iped.parsers.whatsapp.Message.MessageType.SHARE_LOCATION_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.STANDARD_CHAT;
import static iped.parsers.whatsapp.Message.MessageType.STICKER_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.SUBJECT_CHANGED;
import static iped.parsers.whatsapp.Message.MessageType.TEMPLATE_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.TEMPLATE_QUOTE;
import static iped.parsers.whatsapp.Message.MessageType.TEXT_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.UI_ELEMENTS;
import static iped.parsers.whatsapp.Message.MessageType.UI_ELEMENTS_QUOTE;
import static iped.parsers.whatsapp.Message.MessageType.UNAVAILABLE_VIDEO_CALL;
import static iped.parsers.whatsapp.Message.MessageType.UNAVAILABLE_VOICE_CALL;
import static iped.parsers.whatsapp.Message.MessageType.UNBLOCKED_CONTACT;
import static iped.parsers.whatsapp.Message.MessageType.UNKNOWN_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.UNKNOWN_VIDEO_CALL;
import static iped.parsers.whatsapp.Message.MessageType.UNKNOWN_VOICE_CALL;
import static iped.parsers.whatsapp.Message.MessageType.USER_ADDED_TO_COMMUNITY;
import static iped.parsers.whatsapp.Message.MessageType.USER_ADDED_TO_GROUP;
import static iped.parsers.whatsapp.Message.MessageType.USER_COMMUNITY_ADMIN;
import static iped.parsers.whatsapp.Message.MessageType.USER_JOINED_GROUP_FROM_COMMUNITY;
import static iped.parsers.whatsapp.Message.MessageType.USER_JOINED_GROUP_FROM_INVITATION;
import static iped.parsers.whatsapp.Message.MessageType.USER_JOINED_GROUP_FROM_LINK;
import static iped.parsers.whatsapp.Message.MessageType.USER_JOINED_WHATSAPP;
import static iped.parsers.whatsapp.Message.MessageType.USER_LEFT_GROUP;
import static iped.parsers.whatsapp.Message.MessageType.USER_REMOVED_FROM_GROUP;
import static iped.parsers.whatsapp.Message.MessageType.USER_REQUEST_TO_ADD_TO_GROUP;
import static iped.parsers.whatsapp.Message.MessageType.VIDEO_CALL;
import static iped.parsers.whatsapp.Message.MessageType.VIDEO_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.VIEW_ONCE_AUDIO_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.VIEW_ONCE_IMAGE_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.VIEW_ONCE_VIDEO_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.VOICE_CALL;
import static iped.parsers.whatsapp.Message.MessageType.WAITING_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.YOU_ADMIN;
import static iped.parsers.whatsapp.Message.MessageType.YOU_NOT_ADMIN;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import iped.parsers.sqlite.SQLite3DBParser;
import iped.parsers.whatsapp.Message.MessageQuotedType;
import iped.parsers.whatsapp.Message.MessageStatus;

/**
 *
 * @author Hauck
 */
public abstract class ExtractorAndroidNew extends Extractor {

    public ExtractorAndroidNew(String itemPath, File databaseFile, WAContactsDirectory contacts, WAAccount account) {
        super(itemPath, databaseFile, contacts, account, false);
    }

    protected abstract Connection getConnection() throws SQLException;

    @Override
    protected List<Chat> extractChatList() throws WAExtractorException {
        List<Chat> list = new ArrayList<>();
        Map<Long, Chat> idToChat = new HashMap<Long, Chat>();

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(SELECT_CHATS)) {

                while (rs.next()) {
                    String contactId = rs.getString("contact"); //$NON-NLS-1$
                    WAContact remote = contacts.getContact(contactId);
                    Chat c = new Chat(remote);
                    c.setId(rs.getLong("id"));
                    c.setSubject(Util.getUTF8String(rs, "subject")); //$NON-NLS-1$
                    if (contactId.endsWith(WAContact.waGroupSuffix)) {
                        c.setGroupChat(true);
                    } else if (contactId.endsWith(WAContact.waNewsletterSuffix)) {
                        c.setChannelChat(true);
                    } else if (contactId.endsWith(WAContact.waStatusSuffix)) {
                        c.setBroadcast(true);
                    }
                    list.add(c);
                    idToChat.put(c.getId(), c);
                }

                extractMessages(conn, idToChat);
                extractCalls(conn, idToChat);

                for (Chat c : list) {
                    Message.sort(c.getMessages());
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
                String remoteResource = rs.getString("remoteResource");
                if (hasReactionTable && remoteResource == null) {
                    remoteResource = rs.getString("remoteResource2");
                }
                addOn.setRemoteResource(remoteResource);
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
                long chatId = rs.getLong("groupChatId");
                if (chatId == 0) {
                    chatId = rs.getLong("chatId");
                }
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
                    } else if (call_result == 2) {
                        m.setMessageType(MISSED_VIDEO_CALL);
                    } else if (call_result == 4) {
                        m.setMessageType(REFUSED_VIDEO_CALL);
                    } else if (call_result == 3) {
                        m.setMessageType(UNAVAILABLE_VIDEO_CALL);
                    }
                } else {
                    m.setMessageType(UNKNOWN_VOICE_CALL);
                    if (call_result == 5) {
                        m.setMessageType(VOICE_CALL);
                    } else if (call_result == 2) {
                        m.setMessageType(MISSED_VOICE_CALL);
                    } else if (call_result == 4) {
                        m.setMessageType(REFUSED_VOICE_CALL);
                    } else if (call_result == 3) {
                        m.setMessageType(UNAVAILABLE_VOICE_CALL);
                    }
                }
                m.setFromMe(rs.getInt("from_me") == 1);
                m.setDuration(rs.getInt("duration"));
                m.setTimeStamp(new Date(rs.getLong("timestamp")));

                c.add(m);
            }

        }
    }

    private void extractTemplateInfo(Connection conn, Message m) throws SQLException {
        MessageTemplate t = null;
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_TEMPLATE)) {
            stmt.setLong(1, m.getId());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String content = rs.getString("content");
                String footer = rs.getString("footer");
                if (footer != null && !footer.isBlank()) {
                    if (content == null || content.isBlank()) {
                        content = footer;
                    } else {
                        content += "\n" + footer;
                    }
                }
                t = new MessageTemplate(content);
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

    private void extractUsersGroupAction(Connection conn, Message m) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_USERS_GROUP_ACTION)) {
            stmt.setLong(1, m.getId());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String name = rs.getString("raw_string");
                m.addUserAction(name);
            }
        }
    }

    private void extractChangedNumber(Connection conn, Message m) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_SYSTEM_NUMBER_CHANGE)) {
            stmt.setLong(1, m.getId());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                m.addUserAction(rs.getString("oldUser"));
                m.addUserAction(rs.getString("newUser"));
            }
        }
    }

    private void extractRevokedInfo(Connection conn, Message m) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_REVOKED)) {
            stmt.setLong(1, m.getId());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                m.addUserAction(rs.getString("admin"));
            }
        }
    }

    private void extractOrderInfo(Connection conn, Message m) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_ORDER)) {
            stmt.setLong(1, m.getId());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                m.setProduct(new MessageOrder(rs.getString("title"), rs.getString("seller"), rs.getInt("count"),
                        rs.getString("currency"), rs.getInt("amount"), rs.getString("description")));
            }
        }
    }

    private void extractProductInfo(Connection conn, Message m) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_PRODUCT)) {
            stmt.setLong(1, m.getId());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                m.setProduct(new MessageProduct(rs.getString("title"), rs.getString("seller"), rs.getString("currency"),
                        rs.getInt("amount"), rs.getString("description")));
            }
        }
    }

    private void extractQuotedProductInfo(Connection conn, Message m) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_QUOTED_PRODUCT)) {
            stmt.setLong(1, m.getId());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                m.setProduct(new MessageProduct(rs.getString("title"), rs.getString("seller"), rs.getString("currency"),
                        rs.getInt("amount"), rs.getString("description")));
            }
        }
    }

    private void extractEphemeralDuration(Connection conn, Message m) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_EPHEMERAL_SETTING)) {
            stmt.setLong(1, m.getId());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                m.setDuration(rs.getInt("duration"));
            }
        }
    }

    private void extractMessages(Connection conn, Map<Long, Chat> idToChat) throws SQLException {
        boolean hasReactionTable = SQLite3DBParser.containsTable("message_add_on_reaction", conn);
        boolean hasTemplateTables = SQLite3DBParser.containsTable("message_template", conn)
                && SQLite3DBParser.containsTable("message_template_button", conn);
        boolean hasPollOptionTable = SQLite3DBParser.containsTable("message_poll_option", conn);
        boolean hasEphemeralSettingTable = SQLite3DBParser.containsTable("message_ephemeral_setting", conn);
        boolean hasSystemChat = SQLite3DBParser.containsTable("message_system_chat_participant", conn);
        boolean hasSystemNumberChangeTable = SQLite3DBParser.containsTable("message_system_number_change", conn);
        boolean hasRevokedTable = SQLite3DBParser.containsTable("message_revoked", conn);
        boolean hasOrderTable = SQLite3DBParser.containsTable("message_order", conn);
        boolean hasProductTable = SQLite3DBParser.containsTable("message_product", conn);

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
                if (remoteResource == null || remoteResource.isEmpty() || (!c.isGroupOrChannelChat() && !c.isBroadcast())) {
                    remoteResource = c.getRemote().getFullId();
                }
                m.setRemoteResource(remoteResource); // $NON-NLS-1$
                m.setStatus(status); // $NON-NLS-1$
                m.setData(Util.getUTF8String(rs, "text_data")); //$NON-NLS-1$
                String caption = rs.getString("mediaCaption"); //$NON-NLS-1$
                if (caption == null || caption.isBlank()) {
                    caption = m.getData();
                }
                m.setFromMe(rs.getInt("fromMe") == 1 && type != 7); //$NON-NLS-1$
                m.setTimeStamp(new Date(rs.getLong("timestamp"))); //$NON-NLS-1$
                m.setMediaUrl(rs.getString("mediaUrl")); //$NON-NLS-1$
                m.setMediaMime(rs.getString("mediaMime")); //$NON-NLS-1$
                m.setMediaName(rs.getString("mediaName")); //$NON-NLS-1$
                m.setMediaCaption(caption); // $NON-NLS-1$
                m.setMediaHash(rs.getString("mediaHash"), true); //$NON-NLS-1$
                m.setMediaSize(media_size);
                m.setLatitude(rs.getDouble("latitude")); //$NON-NLS-1$
                m.setLongitude(rs.getDouble("longitude")); //$NON-NLS-1$

                int actionType = rs.getInt("actionType");
                m.setMessageType(decodeMessageType(type, status, edit_version, caption, actionType,
                        rs.getInt("bizStateId"), rs.getInt("privacyType"),  m.getMediaMime()));
                
                if (m.getMessageType() == EPHEMERAL_SETTINGS_NOT_APPLIED) {
                    // Ignore this type of message, as it does nothing and it is not visible in the application itself.
                    continue;
                }
                
                m.setDuration(rs.getInt("media_duration")); //$NON-NLS-1$
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

                if (hasEphemeralSettingTable && (m.getMessageType() == EPHEMERAL_DURATION_CHANGED
                        || m.getMessageType() == EPHEMERAL_DEFAULT || m.getMessageType() == EPHEMERAL_CHANGED)) {
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
                m.setUiElements(rs.getString("uiElem"));

                if (hasTemplateTables && m.getMessageType() == TEMPLATE_MESSAGE) {
                    extractTemplateInfo(conn, m);
                }

                if (hasSystemChat
                        && (m.getMessageType() == USER_ADDED_TO_COMMUNITY
                                || m.getMessageType() == USER_ADDED_TO_GROUP
                                || m.getMessageType() == USER_REQUEST_TO_ADD_TO_GROUP
                                || m.getMessageType() == USER_COMMUNITY_ADMIN
                                || m.getMessageType() == USER_REMOVED_FROM_GROUP
                                || m.getMessageType() == USER_JOINED_GROUP_FROM_COMMUNITY
                                || m.getMessageType() == USER_JOINED_GROUP_FROM_LINK
                                || m.getMessageType() == USER_LEFT_GROUP)) {
                    extractUsersGroupAction(conn, m);
                }

                if (actionType == 13 && !m.getUsersAction().isEmpty()) {
                    m.setRemoteResource(m.getUsersAction().get(0));
                }

                if (hasSystemNumberChangeTable && m.getMessageType() == CHANGED_NUMBER_TO) {
                    extractChangedNumber(conn, m);
                }

                if (hasRevokedTable && m.getMessageType() == DELETED_BY_ADMIN) {
                    extractRevokedInfo(conn, m);
                }

                if (hasOrderTable && m.getMessageType() == ORDER_MESSAGE) {
                    extractOrderInfo(conn, m);
                }

                if (hasProductTable && m.getMessageType() == PRODUCT_MESSAGE) {
                    extractProductInfo(conn, m);
                }

                long edit = rs.getLong("editTimestamp");
                if (edit != 0) {
                    m.setEditTimeStamp(new Date(edit));
                }

                c.add(m);
            }
        }

        long fakeIds = 2000000000L;
        Map<Long, List<Message>> messagesQuotesPerChatId = extractQuoteMessages(conn);
        HashMap<Long, Message> messagesMap = new HashMap<Long, Message>();
        HashMap<String, Message> messagesMapUuid = new HashMap<String, Message>();
        for (long chatId : idToChat.keySet()) {
            List<Message> messagesQuotes = messagesQuotesPerChatId.get(chatId);
            if (messagesQuotes != null) {
                Chat c = idToChat.get(chatId);
                for (Message m : c.getMessages()) {
                    messagesMap.put(m.getId(), m);
                    if (m.getUuid() != null && !m.getUuid().isEmpty()) {
                        messagesMapUuid.put(m.getUuid(), m);
                    }
                }

                // Find quote messages
                for (Message mq : messagesQuotes) {
                    Message m = messagesMap.get(mq.getId());
                    if (m != null) {
                        // Has quote
                        // Try to find original message in messages
                        Message original = messagesMapUuid.get(mq.getUuid());
                        if (original != null) {
                            // Has found original message reference
                            original.setMessageQuotedType(MessageQuotedType.QUOTE_FOUND);
                            m.setMessageQuote(original);
                        } else if (mq.getProduct() != null) {
                            // Quoted Catalog
                            mq.setMessageQuotedType(MessageQuotedType.QUOTE_CATALOG);
                            mq.setId(fakeIds--);
                            m.setMessageQuote(mq);
                        } else {
                            // Original message reference not found
                            if (chatId == mq.getQuoteChatId()) {
                                // Message in same chat
                                long editId = mq.getEditId();
                                if (messagesMap.get(editId) != null) {
                                    // Quoted was edited
                                    mq.setMessageQuotedType(MessageQuotedType.QUOTE_FOUND);
                                    mq.setId(editId);
                                } else {
                                    // Quoted message cannot be found again ( maybe deleted or not in the DB)
                                    mq.setMessageQuotedType(MessageQuotedType.QUOTE_NOT_FOUND);
                                    mq.setId(fakeIds--);
                                }
                            } else {
                                // Message is quoted private group or quoted status
                                mq.setMessageQuotedType(MessageQuotedType.QUOTE_PRIVACY_GROUP_NOT_FOUND);
                                // Just set default case if does not match order cases ...
                                mq.setId(fakeIds--);
                                String remoteId = mq.getRemoteId();
                                if (remoteId != null) {
                                    if (remoteId.equals(WAContact.waStatusBroadcast)) {
                                        mq.setMessageQuotedType(MessageQuotedType.QUOTE_STATUS);
                                    } else if (remoteId.endsWith(WAContact.waGroupSuffix)) {
                                        // Set it first in case if not found
                                        mq.setQuotePrivateGroupName(remoteId);
                                        boolean found = false;
                                        for (Chat cq : idToChat.values()) {
                                            // Find friendly group name and message Id
                                            if (!cq.isGroupChat())
                                                continue;

                                            if (cq.getPrintId() != null && remoteId.contains(cq.getPrintId())) {
                                                mq.setQuotePrivateGroupName(cq.getTitle());
                                            }

                                            for (Message ori : cq.getMessages()) {
                                                if (ori.getUuid() != null && mq.getUuid() != null
                                                        && ori.getUuid().compareTo(mq.getUuid()) == 0) {
                                                    mq.setId(ori.getId());
                                                    mq.setMessageQuotedType(MessageQuotedType.QUOTE_PRIVACY_GROUP);
                                                    found = true;
                                                    break;
                                                }
                                            }
                                            if (found)
                                                break;
                                        }

                                    }
                                }
                            }
                            m.setMessageQuote(mq);
                        }
                        m.setQuoted(true);

                        if (mq.getRemoteResource() == null || mq.getRemoteResource().isBlank()
                                || !c.isGroupOrChannelChat()) {
                            mq.setRemoteResource(c.getRemote().getFullId());
                        }
                    }
                }
                messagesMap.clear();
                messagesMapUuid.clear();
            }
        }
    }

    private Map<Long, List<Message>> extractQuoteMessages(Connection conn) throws SQLException {

        Map<Long, List<Message>> messagesPerChatId = new HashMap<Long, List<Message>>();
        String query = getSelectMessagesQuotesQuery(conn);
        boolean hasQuotedProductTable = SQLite3DBParser.containsTable("message_quoted_product", conn);

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {

                Message m = new Message();
                if (account != null)
                    m.setLocalResource(account.getId());
                int type = rs.getInt("messageType"); //$NON-NLS-1$
                long media_size = rs.getLong("mediaSize"); //$NON-NLS-1$

                m.setId(rs.getLong("id")); //$NON-NLS-1$
                m.setRemoteResource(rs.getString("remoteResource")); // $NON-NLS-1$
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
                m.setMessageType(decodeMessageType(type, -1, -1, caption, -1, -1, -1, m.getMediaMime()));
                m.setDuration(rs.getInt("media_duration")); //$NON-NLS-1$
                if (m.getMessageType() == CONTACT_MESSAGE) {
                    m.setVcards(Arrays.asList(new String[] { Util.getUTF8String(rs, "vcard") }));
                }

                byte[] thumbData = rs.getBytes("thumbData"); //$NON-NLS-1$

                if (m.getMessageType() == BLOCKED_CONTACT && isUnblocked(conn, m.getId())) {
                    m.setMessageType(UNBLOCKED_CONTACT);
                }
                m.setThumbData(thumbData);

                m.setUuid(rs.getString("uuid"));

                m.setEditId(rs.getLong("edit_row_id"));

                m.setQuoteChatId(rs.getLong("chatId"));

                m.setRemoteId(rs.getString("remoteId"));

                long chatId = rs.getLong("parent_message_chat_row_id");

                if (hasQuotedProductTable && m.getMessageType() == PRODUCT_MESSAGE) {
                    extractQuotedProductInfo(conn, m);
                }

                List<Message> messages = messagesPerChatId.get(chatId);
                if (messages == null) {
                    messagesPerChatId.put(chatId, messages = new ArrayList<Message>());
                }
                messages.add(m);
            }
        }

        return messagesPerChatId;
    }

    protected Message.MessageType decodeMessageType(int messageType, int status, Integer edit_version, String caption,
            int actionType, int bizStateId, int privacyType, String mediaMime) {
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
                        result = USER_ADDED_TO_GROUP;
                        break;
                    case 5:
                    case 13:
                        result = USER_LEFT_GROUP;
                        break;
                    case 6:
                        result = GROUP_ICON_CHANGED;
                        break;
                    case 7:
                    case 14:
                        result = USER_REMOVED_FROM_GROUP;
                        break;
                    case 10:
                    case 28:
                        result = CHANGED_NUMBER_TO;
                        break;
                    case 11:
                        result = GROUP_CREATED;
                        break;
                    case 15:
                        result = YOU_ADMIN;
                        break;
                    case 16:
                        result = YOU_NOT_ADMIN;
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
                    case 21:
                        result = RESET_GROUP_LINK;
                        break;
                    case 27:
                        result = GROUP_DESCRIPTION_CHANGED;
                        break;
                    case 29:
                        result = GROUP_CHANGED_ONLY_ADMINS_CAN_EDIT;
                        break;
                    case 30:
                        result = GROUP_CHANGED_ALL_MEMBERS_CAN_EDIT;
                        break;
                    case 31:
                        result = GROUP_CHANGED_ONLY_ADMINS_CAN_SEND;
                        break;
                    case 32:
                        result = GROUP_CHANGED_ALL_MEMBERS_CAN_SEND;
                        break;
                    case 33:
                        result = GROUP_ONLY_ADMINS_CAN_SEND;
                        break;
                    // TODO: Handle payments notification system messages
                    // case 39:
                    // result = PAYMENT_NOTIFICATION;
                    // break;
                    case 46:
                        result = BUSINESS_CHAT;
                        break;
                    case 47:
                        result = BUSINESS_OFFICIAL;
                        break;
                    case 50:
                        result = STANDARD_CHAT;
                        break;
                    case 52:
                        result = USER_JOINED_GROUP_FROM_INVITATION;
                        break;
                    case 56:
                        result = EPHEMERAL_CHANGED;
                        break;
                    case 57:
                        result = CHANGED_DEVICE;
                        break;
                    case 58:
                        result = BLOCKED_CONTACT;
                        break;
                    case 59:
                        result = EPHEMERAL_DURATION_CHANGED;
                        break;
                    case 60:
                        // Message present in the table, but not shown in the application itself.
                        // Can be ignored as nothing was changed.
                        result = EPHEMERAL_SETTINGS_NOT_APPLIED;
                        break;
                    case 63:
                        result = SECURITY_NOTIFICATIONS_NO_LONGER_AVAILABLE;
                        break;
                    // TODO: Handle business related notification (no extra tables/fields)
                    // case 63:
                    // result = ???;
                    // break;
                    case 67:
                        if (bizStateId == 10) {
                            result = BUSINESS_META_SECURE_SERVICE;
                        } else {
                            result = MESSAGES_ENCRYPTED;
                        }
                        break;
                    case 68:
                        result = EPHEMERAL_DEFAULT;
                        break;
                    case 69:
                        if (privacyType == 1) {
                            result = MESSAGES_ENCRYPTED;
                        } else {
                            result = BUSINESS_META_SECURE_SERVICE;
                        }                        
                        break;
                    case 70:
                        result = CALL_MESSAGE;
                        break;
                    case 76:
                        result = CONTACTED_FIND_BUSINESSES;
                        break;                        
                    case 75:
                    case 108:
                        result = GROUP_ADDED_TO_COMMUNITY;
                        break;
                    case 78:
                        result = GROUP_REMOVED_FROM_COMMUNITY;
                        break;
                    case 79:
                        result = USER_JOINED_GROUP_FROM_COMMUNITY;
                        break;
                    case 80:
                        result = EPHEMERAL_SAVE;
                        break;
                    case 81:
                        result = USER_COMMUNITY_ADMIN;
                        break;
                    case 84:
                        result = NEW_PARTICIPANTS_NEED_ADMIN_APPROVAL;
                        break;
                    case 85:
                        result = GROUP_CHANGED_ADMIN_APPROVAL_OFF;
                        break;
                    case 87:
                    case 88:
                    case 95:
                    case 110:
                    case 115:
                        result = COMMUNITY_MANAGEMENT_ACTION;
                        break;
                    case 90:
                        result = USER_ADDED_TO_COMMUNITY;
                        break;
                    case 91:
                        result = GROUP_CHANGED_ALL_MEMBERS_CAN_ADD;
                        break;
                    case 92:
                        result = GROUP_CHANGED_ONLY_ADMINS_CAN_ADD;
                        break;
                    case 99:
                        result = CHAT_ADDED_PRIVACY;
                        break;
                    case 102:
                        result = ANY_COMMUNITY_MEMBER_CAN_JOIN_GROUP;
                        break;
                    case 107:
                        result = COMMUNITY_RENAMED;
                        break;
                    case 118:
                        result = PINNED_MESSAGE;
                        break;
                    case 120:
                        result = USER_REQUEST_TO_ADD_TO_GROUP;
                        break;
                    case 123:
                    case 124:
                    case 125:
                    case 131:
                        result = COMMUNITY_WELCOME;
                        break;
                    case 129:
                        result = SENDER_IN_CONTACTS;
                        break;
                    case 132:
                        result = CHANNEL_CREATED;
                        break;
                    case 134:
                        result = CHANNEL_ADDED_PRIVACY;
                        break;
                    case 136:
                        result = USER_JOINED_WHATSAPP;
                        break;
                    case 142:
                        result = OVER_256_MEMBERS_ONLY_ADMINS_CAN_EDIT;
                        break;
                    case 155:
                        result = AI_THIRD_PARTY;
                        break;
                    case 158:
                        result = CHAT_STARTED_FROM_AD;
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
            case 14:
                result = CONTACT_MESSAGE;
                break;
            case 5:
                result = LOCATION_MESSAGE;
                break;
            case 8:
                result = CALL_MESSAGE;
                if (caption != null) {
                    if (caption.equals("video")) { //$NON-NLS-1$
                        result = VIDEO_CALL;
                    } else if (caption.equals("audio")) { //$NON-NLS-1$
                        result = VOICE_CALL;
                    }
                }
                break;
            case 9:
                result = DOC_MESSAGE;
                break;
            case 10:
                result = CALL_MESSAGE;
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
                    } else if (status == 4 || status == 5 || status == 13) {
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
            case 23:
                result = PRODUCT_MESSAGE;
                break;
            case 24:
                result = GROUP_INVITE;
                break;
            case 25:
            case 26:
            case 27:
            case 28:
                result = TEMPLATE_MESSAGE;
                break;
            case 32:
                result = TEMPLATE_QUOTE;
                break;
            case 36:
            case 56:
                result = EPHEMERAL_DURATION_CHANGED;
                break;
            case 42:
                result = VIEW_ONCE_IMAGE_MESSAGE;
                break;
            case 43:
                result = VIEW_ONCE_VIDEO_MESSAGE;
                break;
            case 44:
                result = ORDER_MESSAGE;
                break;
            case 45:
            case 55:
            case 57:
                result = UI_ELEMENTS;
                break;
            case 46:
            case 49:
                result = UI_ELEMENTS_QUOTE;
                break;
            case 64:
                if (status == 0 || status == 4 || status == 5) {
                    result = DELETED_BY_ADMIN;
                }
                break;
            case 66:
            case 106:
                result = POLL_MESSAGE;
                break;
            case 81:
            case 103:
                // Quote with media
                result = TEXT_MESSAGE;
                if (mediaMime != null && !mediaMime.isBlank()) {
                    mediaMime = mediaMime.toLowerCase();
                    if (mediaMime.contains("video")) {
                        result = VIDEO_MESSAGE;
                    } else if (mediaMime.contains("audio")) {
                        result = AUDIO_MESSAGE;
                    } else if (mediaMime.contains("image")) {
                        result = IMAGE_MESSAGE;
                    }
                }
                break;
            case 82:
                result = VIEW_ONCE_AUDIO_MESSAGE;
                break;
            case 90:
                // Newer databases also have entries to any call in messages table
                result = CALL_MESSAGE;
                break;
            case 99:
                result = MESSAGE_ASSOCIATION;
                break;
            default:
                break;
        }
        return result;
    }

    private static final String SELECT_CHATS = "SELECT c._id as id, j.raw_string AS contact, c.subject, c.created_timestamp as creation,"
            + " sort_timestamp FROM chat c, jid j WHERE c.jid_row_id = j._id ORDER BY c.sort_timestamp DESC";

    private static final String SELECT_ADD_ONS = "SELECT message_add_on_type as type,timestamp, status,jid.raw_string as remoteResource,from_me as fromMe FROM message_add_on m left join jid on jid._id=m.sender_jid_row_id where parent_message_row_id=?";

    private static final String SELECT_ADD_ONS_REACTIONS = "SELECT message_add_on_type as type, timestamp, status,"
            + " jid.raw_string as remoteResource, jid2.raw_string as remoteResource2, from_me as fromMe, r.reaction as reaction"
            + " FROM message_add_on m"
            + " left join jid on jid._id=m.sender_jid_row_id"
            + " left join chat on chat._id=m.chat_row_id"
            + " left join jid jid2 on jid2._id=chat.jid_row_id"
            + " left join message_add_on_reaction r on r.message_add_on_row_id=m._id"
            + " where parent_message_row_id=?";

    private static final String SELECT_TEMPLATE = "SELECT content_text_data as content, footer_text_data as footer FROM message_template where message_row_id=?";

    private static final String SELECT_USERS_GROUP_ACTION = "select raw_string from message_system_chat_participant inner join jid on user_jid_row_id = jid._id where message_row_id=? order by _id";
    
    private static final String SELECT_SYSTEM_NUMBER_CHANGE = "select old.raw_string as oldUser, new.raw_string as newUser from message_system_number_change left join jid old on old_jid_row_id = old._id left join jid new on new_jid_row_id = new._id where message_row_id=?";

    private static final String SELECT_REVOKED = "select raw_string as admin from message_revoked left join jid on admin_jid_row_id = jid._id where message_row_id=?";

    private static final String SELECT_ORDER = "select raw_string as seller, order_title as title,"
            + " item_count as count, currency_code as currency, total_amount_1000 as amount, message as description"
            + " from message_order left join jid on seller_jid = jid._id where message_row_id=?";
    
    private static final String SELECT_PRODUCT = "select raw_string as seller, title,"
            + " currency_code as currency, amount_1000 as amount, description"
            + " from message_product left join jid on business_owner_jid = jid._id where message_row_id=?";

    private static final String SELECT_QUOTED_PRODUCT = "select raw_string as seller, title,"
            + " currency_code as currency, amount_1000 as amount, description"
            + " from message_quoted_product left join jid on business_owner_jid = jid._id where message_row_id=?";    

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

        String privacyTypeCol = "0";
        String privacyTypeTableJoin = "";
        if (SQLite3DBParser.containsTable("message_system_business_state", conn)) {
            privacyTypeCol = "msbs.privacy_message_type";
            privacyTypeTableJoin = " left join message_system_business_state msbs on m._id=msbs.message_row_id";
        }

        String grpInvCol = "null";
        String grpInvTableJoin = "";
        if (SQLite3DBParser.containsTable("message_group_invite", conn)) {
            grpInvCol = "mgi.group_name";
            grpInvTableJoin = " left join message_group_invite mgi on m._id=mgi.message_row_id";
        }

        String uiElemCol = "null";
        String uiElemTableJoin = "";
        if (SQLite3DBParser.containsTable("message_ui_elements", conn)) {
            uiElemCol = "mue.element_content";
            uiElemTableJoin = " left join message_ui_elements mue on m._id=mue.message_row_id";
        }

        String editCol = "0";
        String editTableJoin = "";
        if (SQLite3DBParser.containsTable("message_edit_info", conn)) {
            editCol = "mei.edited_timestamp";
            editTableJoin = " left join message_edit_info mei on m._id=mei.message_row_id";
        }

        return "select m._id AS id,m.chat_row_id as chatId, chatJid.raw_string as remoteId,"
                + " jid.raw_string as remoteResource, status, mv.vcard, m.text_data,"
                + " m.from_me as fromMe, m.timestamp as timestamp, message_url as mediaUrl,"
                + " mm.mime_type as mediaMime, mm.file_length as mediaSize, media_name as mediaName,"
                + " m.message_type as messageType, latitude, longitude, mm.media_duration, " + captionCol
                + " as mediaCaption, mm.file_hash as mediaHash, mt.thumbnail as thumbData, m.key_id as uuid,"
                + " ms.action_type as actionType, m.message_add_on_flags as hasAddOn,"
                + " (m.origination_flags & 1) as forwarded,"
                + " " + mhtCol + " as thumbData2,"
                + " " + bizStateCol + " as bizStateId,"
                + " " + privacyTypeCol + " as privacyType,"
                + " " + grpInvCol + " as groupInviteName,"
                + " " + sortCol + " as sortId,"
                + " " + uiElemCol + " as uiElem,"
                + " " + editCol + " as editTimestamp"
                + " from message m"
                + " left join chat on m.chat_row_id=chat._id"
                + " left join jid chatJid on chatJid._id=chat.jid_row_id"
                + " left join message_media mm on mm.message_row_id=m._id"
                + " left join jid on jid._id=m.sender_jid_row_id"
                + " left join message_location ml on m._id=ml.message_row_id "
                + " left join message_system ms on m._id=ms.message_row_id"
                + " left join message_vcard mv on m._id=mv.message_row_id"
                + mhtTableJoin
                + bizStateTableJoin
                + privacyTypeTableJoin
                + grpInvTableJoin
                + uiElemTableJoin
                + editTableJoin
                + " left join message_thumbnail mt on m._id=mt.message_row_id where status!=-1";
    }

    private static String getSelectMessagesQuotesQuery(Connection conn) throws SQLException {
        String captionCol = SQLite3DBParser.checkIfColumnExists(conn, "message_quoted_media", "media_caption")
                ? "mm.media_caption"
                : "null";
        String editCol = "null as edit_row_id,";
        String editTableJoin = "";
        if (SQLite3DBParser.containsTable("message_edit_info", conn)) {
            editCol = "mei.message_row_id as edit_row_id,";
            editTableJoin = " left join message_edit_info mei on mei.original_key_id=mq.key_id";
        }                
        return "select mq.message_row_id as id,mq.chat_row_id as chatId, chatJid.raw_string as remoteId,"
                + " jid.raw_string as remoteResource, mv.vcard, mq.text_data, mq.parent_message_chat_row_id,"
                + " mq.from_me as fromMe, mq.timestamp as timestamp, message_url as mediaUrl,"
                + " mm.mime_type as mediaMime, mm.file_length as mediaSize, media_name as mediaName,"
                + " mq.message_type as messageType, latitude, longitude, mm.media_duration, " + captionCol
                + " as mediaCaption, mm.file_hash as mediaHash, mm.thumbnail as thumbData, " + editCol
                + " mq.key_id as uuid"
                + " from message_quoted mq"
                + " left join chat on mq.chat_row_id=chat._id"
                + " left join jid chatJid on chatJid._id=chat.jid_row_id"
                + " left join message_quoted_media mm on mm.message_row_id=mq.message_row_id"
                + " left join jid on jid._id=mq.sender_jid_row_id"
                + " left join message_quoted_location ml on mq.message_row_id=ml.message_row_id"
                + " left join message_quoted_vcard mv on mq.message_row_id=mv.message_row_id"
                + editTableJoin;
    }

    private static String getSelectBlockedQuery(Connection conn) throws SQLException {
        if (!SQLite3DBParser.containsTable("message_system_block_contact", conn)) {
            return null;
        }
        return "select is_blocked as isBlocked from message_system_block_contact where message_row_id=?";
    }

    private static final String SELECT_CALLS = "select log._id as id, log.call_id, log.video_call, log.duration,"
            + " log.timestamp, log.call_result, log.from_me,"
            + " jid.raw_string as remoteId,"
            + " chat1._id as chatId,"
            + " chat2._id as groupChatId"
            + " from call_log log"
            + " left join jid on jid._id = log.jid_row_id"
            + " left join chat chat1 on chat1.jid_row_id = log.jid_row_id"
            + " left join chat chat2 on chat2.jid_row_id = log.group_jid_row_id";

    private static final String SELECT_GROUP_MEMBERS = "select g._id as group_id, g.raw_string as group_name, u._id as user_id, u.raw_string as member "
            + "FROM group_participant_user gp inner join jid g on g._id=gp.group_jid_row_id inner join jid u on u._id=gp.user_jid_row_id where u.server='s.whatsapp.net' and u.type=0 and group_name=?"; //$NON-NLS-1$

}
