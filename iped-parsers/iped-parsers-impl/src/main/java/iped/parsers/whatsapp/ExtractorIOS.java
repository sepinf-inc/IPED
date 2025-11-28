package iped.parsers.whatsapp;

import static iped.parsers.whatsapp.Message.MessageType.AUDIO_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.BLOCKED_CONTACT;
import static iped.parsers.whatsapp.Message.MessageType.BUSINESS_CHANGED_NAME;
import static iped.parsers.whatsapp.Message.MessageType.BUSINESS_CHAT;
import static iped.parsers.whatsapp.Message.MessageType.BUSINESS_OFFICIAL;
import static iped.parsers.whatsapp.Message.MessageType.BUSINESS_TO_STANDARD;
import static iped.parsers.whatsapp.Message.MessageType.CHANGED_NUMBER_CHATTING_WITH_NEW;
import static iped.parsers.whatsapp.Message.MessageType.CHANGED_NUMBER_CHATTING_WITH_OLD;
import static iped.parsers.whatsapp.Message.MessageType.CHANNEL_ADDED_PRIVACY;
import static iped.parsers.whatsapp.Message.MessageType.CHANNEL_CREATED;
import static iped.parsers.whatsapp.Message.MessageType.CHAT_ADDED_PRIVACY;
import static iped.parsers.whatsapp.Message.MessageType.COMMUNITY_CHANGED_ALL_MEMBERS_CAN_ADD;
import static iped.parsers.whatsapp.Message.MessageType.COMMUNITY_CHANGED_ONLY_ADMINS_CAN_ADD;
import static iped.parsers.whatsapp.Message.MessageType.COMMUNITY_DESCRIPTION_CHANGED;
import static iped.parsers.whatsapp.Message.MessageType.COMMUNITY_MANAGEMENT_ACTION;
import static iped.parsers.whatsapp.Message.MessageType.COMMUNITY_NOT_AVAILABLE;
import static iped.parsers.whatsapp.Message.MessageType.COMMUNITY_WELCOME;
import static iped.parsers.whatsapp.Message.MessageType.CONTACT_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.DELETED_BY_SENDER;
import static iped.parsers.whatsapp.Message.MessageType.DOC_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.ENCRYPTION_KEY_CHANGED;
import static iped.parsers.whatsapp.Message.MessageType.EPHEMERAL_CHANGED;
import static iped.parsers.whatsapp.Message.MessageType.EPHEMERAL_DEFAULT;
import static iped.parsers.whatsapp.Message.MessageType.EPHEMERAL_SAVE;
import static iped.parsers.whatsapp.Message.MessageType.GIF_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_ADDED_TO_COMMUNITY;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_CHANGED_ALL_MEMBERS_CAN_EDIT;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_CHANGED_ALL_MEMBERS_CAN_SEND;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_CHANGED_ONLY_ADMINS_CAN_ADD;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_CHANGED_ONLY_ADMINS_CAN_EDIT;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_CHANGED_ONLY_ADMINS_CAN_SEND;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_CREATED;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_DESCRIPTION_CHANGED;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_DESCRIPTION_DELETED;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_ICON_CHANGED;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_ICON_DELETED;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_INVITE;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_NAME_CHANGED;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_NOT_PART_OF_COMMUNITY;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_REMOVED_FROM_COMMUNITY;
import static iped.parsers.whatsapp.Message.MessageType.IMAGE_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.LOCATION_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.MESSAGE_ASSOCIATION;
import static iped.parsers.whatsapp.Message.MessageType.MESSAGES_NOW_ENCRYPTED;
import static iped.parsers.whatsapp.Message.MessageType.MISSED_VIDEO_CALL;
import static iped.parsers.whatsapp.Message.MessageType.MISSED_VOICE_CALL;
import static iped.parsers.whatsapp.Message.MessageType.POLL_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.PRODUCT_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.SENDER_ADDED_TO_CONTACTS;
import static iped.parsers.whatsapp.Message.MessageType.STICKER_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.TEMPLATE_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.TEXT_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.UNBLOCKED_CONTACT;
import static iped.parsers.whatsapp.Message.MessageType.UNKNOWN_MEDIA_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.UNKNOWN_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.URL_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.USER_ADDED_TO_GROUP;
import static iped.parsers.whatsapp.Message.MessageType.USER_ADMIN;
import static iped.parsers.whatsapp.Message.MessageType.USER_JOINED_GROUP_FROM_COMMUNITY;
import static iped.parsers.whatsapp.Message.MessageType.USER_JOINED_GROUP_FROM_INVITATION;
import static iped.parsers.whatsapp.Message.MessageType.USER_JOINED_GROUP_FROM_LINK;
import static iped.parsers.whatsapp.Message.MessageType.USER_LEFT_GROUP;
import static iped.parsers.whatsapp.Message.MessageType.USER_REMOVED_FROM_GROUP;
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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

import fqlite.base.SqliteRow;
import iped.parsers.sqlite.SQLite3DBParser;
import iped.parsers.sqlite.SQLiteRecordValidator;
import iped.parsers.sqlite.SQLiteUndelete;
import iped.parsers.sqlite.SQLiteUndeleteTable;
import iped.parsers.whatsapp.Message.MessageQuotedType;
import iped.parsers.whatsapp.Message.MessageStatus;
import iped.parsers.whatsapp.Message.MessageType;
import iped.parsers.whatsapp.ProtoBufDecoder.Part;

/**
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@pf.gov.br>
 */
public abstract class ExtractorIOS extends Extractor {

    private static Logger logger = LoggerFactory.getLogger(ExtractorIOS.class);

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); //$NON-NLS-1$

    private boolean hasProfilePictureItemTable = false;
    private boolean hasZTitleColumn = false;
    private boolean hasZSTANZAIDAndZMETADATAColumns = false;
    private boolean hasZMOVIEDURATIONColumn;
    private SQLException parsingException = null;

    public ExtractorIOS(String itemPath, File databaseFile, WAContactsDirectory contacts, WAAccount account,
            boolean recoverDeletedRecords) {
        super(itemPath, databaseFile, contacts, account, recoverDeletedRecords);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT")); //$NON-NLS-1$
    }

    protected abstract Connection getConnection() throws SQLException;

    @Override
    protected List<Chat> extractChatList() throws WAExtractorException {
        extractPushNames();
        
        List<Chat> list;

        Map<String, SQLiteUndeleteTable> undeleteTables = null;
        SQLiteUndeleteTable messagesUndeletedTable = null;
        SQLiteUndeleteTable mediaItemUndeletedTable = null;
        SQLiteUndeleteTable groupMembersUndeletedTable = null;
        SQLiteUndeleteTable chatSessionUndeleteTable = null;

        // control retry parsing database in case of corrupted db
        // if database is corrupted, maybe recovering deleted data can
        // retrieve partial data
        boolean firstTry;
        boolean tryAgain;

        if (recoverDeletedRecords) {
            try {
                // try to recover deleted records
                var undelete = new SQLiteUndelete(databaseFile.toPath());
                undelete.addTableToRecover("ZWAMESSAGE"); //$NON-NLS-1$
                undelete.addRecordValidator("ZWAMESSAGE", new WAIOSMessageValidator()); //$NON-NLS-1$
                undelete.addTableToRecover("ZWAMEDIAITEM"); //$NON-NLS-1$
                undelete.addRecordValidator("ZWAMEDIAITEM", new WAIOSMediaItemValidator()); //$NON-NLS-1$
                undelete.addTableToRecover("ZWAGROUPMEMBER"); //$NON-NLS-1$
                undelete.addRecordValidator("ZWAGROUPMEMBER", new WAIOSGroupMemberValidator()); //$NON-NLS-1$
                undelete.addTableToRecover("ZWACHATSESSION"); //$NON-NLS-1$
                undelete.addRecordValidator("ZWACHATSESSION", new WAIOSChatSessionValidator()); //$NON-NLS-1$
                undelete.setRecoverOnlyDeletedRecords(false);

                undeleteTables = undelete.undeleteData();
                messagesUndeletedTable = undeleteTables.get("ZWAMESSAGE"); //$NON-NLS-1$
                mediaItemUndeletedTable = undeleteTables.get("ZWAMEDIAITEM"); //$NON-NLS-1$
                groupMembersUndeletedTable = undeleteTables.get("ZWAGROUPMEMBER"); //$NON-NLS-1$
                chatSessionUndeleteTable = undeleteTables.get("ZWACHATSESSION"); //$NON-NLS-1$
            } catch (Exception e) {
                logger.warn("Error recovering deleted records from iOS WhatsApp Database " + itemPath, e);
            }
        }

        Map<Long, List<SqliteRow>> undeletedMessages = messagesUndeletedTable == null ? Collections.emptyMap()
                : messagesUndeletedTable.getTableRowsGroupedByLongCol("ZCHATSESSION");
        Map<Long, SqliteRow> mediaItems = mediaItemUndeletedTable == null ? Collections.emptyMap()
                : mediaItemUndeletedTable.getRowsMappedByLongPrimaryKey("ZMESSAGE");
        Map<Long, SqliteRow> groupMembers = groupMembersUndeletedTable == null ? Collections.emptyMap()
                : groupMembersUndeletedTable.getRowsMappedByLongPrimaryKey("Z_PK");

        List<Chat> undeletedChats = recoverDeletedRecords ? undeleteChats(chatSessionUndeleteTable, contacts)
                : Collections.emptyList();

        Set<Long> activeChats = new HashSet<>();

        parsingException = null;
        firstTry = recoverDeletedRecords; // if recovery of deleted messages is disable, go straight to the second try

        do {
            list = new ArrayList<>();
            tryAgain = false;

            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                try {
                    hasProfilePictureItemTable = SQLite3DBParser.containsTable("ZWAPROFILEPICTUREITEM", conn);
                    hasZTitleColumn = SQLite3DBParser.checkIfColumnExists(conn, "ZWAMEDIAITEM", "ZTITLE");
                    hasZSTANZAIDAndZMETADATAColumns = SQLite3DBParser.checkIfColumnExists(conn, "ZWAMESSAGE",
                            "ZSTANZAID") && SQLite3DBParser.checkIfColumnExists(conn, "ZWAMEDIAITEM", "ZMETADATA");
                    hasZMOVIEDURATIONColumn = SQLite3DBParser.checkIfColumnExists(conn, "ZWAMEDIAITEM", "ZMOVIEDURATION");
                } catch (SQLException e) {
                    if (firstTry || !isSqliteCorruptException(e)) {
                        throw e;
                    } else if (parsingException == null) {
                        parsingException = e;
                    }
                }

                String chatListQuery = hasProfilePictureItemTable ? SELECT_CHAT_LIST : SELECT_CHAT_LIST_NO_PPIC;

                try (ResultSet rs = stmt.executeQuery(chatListQuery)) {
                    while (rs.next()) {
                        String contactId = rs.getString("contact"); //$NON-NLS-1$
                        WAContact remote = contacts.getContact(contactId);
                        Chat c = new Chat(remote);
                        c.setId(rs.getLong("id")); //$NON-NLS-1$
                        c.setSubject(Util.getUTF8String(rs, "subject")); //$NON-NLS-1$
                        if (contactId.endsWith(WAContact.waGroupSuffix)) {
                            c.setGroupChat(true);
                        } else if (contactId.endsWith(WAContact.waNewsletterSuffix)) {
                            c.setChannelChat(true);
                        } else if (contactId.endsWith(WAContact.waStatusSuffix)) {
                            c.setBroadcast(true);
                        }
                        c.setDeleted(rs.getInt("ZREMOVED") != 0);
                        remote.setAvatarPath(rs.getString("avatarPath")); //$NON-NLS-1$
                        if (recoverDeletedRecords) {
                            activeChats.add(c.getId());
                        }
                        list.add(c);
                    }
                } catch (SQLException ex) {
                    if (firstTry || !isSqliteCorruptException(ex)) {
                        throw ex;
                    } else if (parsingException == null) {
                        parsingException = ex;
                    }
                }

                for (Chat c : undeletedChats) {
                    if (!activeChats.contains(c.getId())) {
                        list.add(c);
                        if (firstTry && c.isDeleted()) {
                            logger.info("Recovered deleted chat for database " + itemPath //$NON-NLS-1$
                                    + " :" + c.getSubject() + " (" //$NON-NLS-1$ //$NON-NLS-2$
                                    + c.getRemote().getFullId() + ")"); //$NON-NLS-1$
                        }
                    }
                }

                // Extract messages of all non-group and group chats at once, not per chat
                Map<Long, Chat> idToChat = new HashMap<Long, Chat>();
                for (Chat c : list) {
                    idToChat.put(c.getId(), c);
                }
                extractMessages(conn, idToChat, firstTry, false);
                extractMessages(conn, idToChat, firstTry, true);

                for (Chat c : list) {
                    HashMap<String, Message> messagesMap = new HashMap<String, Message>();
                    for (Message m : c.getMessages()) {
                        if (m.getUuid() != null && !m.getUuid().isEmpty()) {
                            messagesMap.put(m.getUuid(), m);
                        }
                    }
                    if (messagesUndeletedTable != null && !undeletedMessages.isEmpty()) {
                        mergeUndeletedMessages(c, messagesMap, undeletedMessages, mediaItems, groupMembers, firstTry);
                    }
                    if (c.isGroupChat()) {
                        try {
                            setGroupMembers(c, conn, SELECT_GROUP_MEMBERS);
                        } catch (SQLException ex) {
                            if (firstTry || !isSqliteCorruptException(ex)) {
                                throw ex;
                            } else if (parsingException == null) {
                                parsingException = ex;
                            }
                        }
                    }

                    // Find quoted messages
                    findQuotedMessages(c.getMessages(), messagesMap, idToChat);
                }

                if (recoverDeletedRecords && !firstTry) {
                    if (list.size() > 0 && undeletedMessages.size() > 0) {
                        logger.info("Recovered deleted messages from corrupted database " + itemPath); //$NON-NLS-1$
                    } else {
                        logger.info("Was not able to recover messages from corrupted database " + itemPath); //$NON-NLS-1$
                        if (parsingException != null) {
                            throw parsingException;
                        }
                    }
                }
                if (!firstTry && !recoverDeletedRecords && parsingException != null) {
                    throw parsingException;
                }

            } catch (SQLException ex) {
                if (firstTry && recoverDeletedRecords) {
                    // if recovery of deleted records is enabled and failed with SQLITE_CORRUPT on
                    // first try,
                    // try again, ignoring error and recovering deleted records
                    if (isSqliteCorruptException(ex)) {
                        tryAgain = true;
                        logger.warn("Database " + itemPath + " is corrupt. Trying to recover data with fqlite");
                    }
                }
                if (!tryAgain) {
                    throw new WAExtractorException(ex);
                }
            }
            firstTry = false;
        } while (tryAgain);

        return cleanChatList(list);
    }

    private void extractMessages(Connection conn, Map<Long, Chat> idToChat, boolean firstTry, boolean isGroupChat)
            throws SQLException {

        String sql;
        if (hasZTitleColumn) {
            sql = isGroupChat ? SELECT_MESSAGES_GROUP : SELECT_MESSAGES_USER;
        } else {
            sql = isGroupChat ? SELECT_MESSAGES_GROUP_NOZTITLE : SELECT_MESSAGES_USER_NOZTITLE;
        }

        if (!hasZSTANZAIDAndZMETADATAColumns) {
            sql = sql.replace("ZWAMESSAGE.ZSTANZAID as uuid, ZWAMEDIAITEM.ZMETADATA as metadata, ", "");
        }

        if (!hasZMOVIEDURATIONColumn) {
            sql = sql.replace("ZWAMEDIAITEM.ZMOVIEDURATION as duration, ", "");
        }

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long chatId = rs.getLong("chatId");
                    Chat chat = idToChat.get(chatId);
                    if (chat != null && chat.isGroupChat() == isGroupChat) {
                        Message m = createMessageFromDB(rs, chat);
                        chat.add(m);
                    }
                }
            }
        } catch (SQLException e) {
            if (firstTry || !isSqliteCorruptException(e)) {
                // ignore sqlite corrupt error on second try
                // to try to recover deleted records instead
                throw e;
            } else if (parsingException == null) {
                parsingException = e;
            }
        }
    }

    private void mergeUndeletedMessages(Chat chat, Map<String, Message> messagesMap,
            Map<Long, List<SqliteRow>> undeletedMessages, Map<Long, SqliteRow> mediaItems,
            Map<Long, SqliteRow> groupMembers, boolean firstTry) throws SQLException {

        // Get deleted messages for this Chat
        List<SqliteRow> undeletedRows = undeletedMessages.get(chat.getId());
        if (undeletedRows != null && !undeletedRows.isEmpty()) {

            // Get active messages for this Chat
            Set<MessageWrapperForDuplicateRemoval> activeMessages = new HashSet<>();
            Map<Long, Message> activeMessageIds = new HashMap<>();
            for (Message m : chat.getMessages()) {
                activeMessages.add(new MessageWrapperForDuplicateRemoval(m));
                activeMessageIds.put(m.getId(), m);
            }

            for (SqliteRow row : undeletedRows) {
                try {
                    if (!firstTry || row.isDeletedRow()) {
                        Message m = createMessageFromUndeletedRecord(row, chat, mediaItems, groupMembers);
                        if (!activeMessages.contains(new MessageWrapperForDuplicateRemoval(m))) {
                            // Do not include deleted message if already there.
                            // Also remove messages with same id that have the same start text (possibly
                            // corrupted recovered record).
                            if (!activeMessageIds.containsKey(m.getId())
                                    || !compareMessagesAlmostTheSame(activeMessageIds.get(m.getId()), m)) {
                                if (m.getUuid() != null && !m.getUuid().isEmpty()) {
                                    messagesMap.put(m.getUuid(), m);
                                }
                                chat.add(m);
                            }
                        }
                    }
                } catch (SQLException e) {
                    logger.warn("Error creating undelete message for whatsapp ios", e); //$NON-NLS-1$
                } catch (RuntimeException e) {
                    logger.warn("Error creating undelete message for whatsapp ios", e); //$NON-NLS-1$
                }
            }

            Message.sort(chat.getMessages());
        }
    }

    private void findQuotedMessages(List<Message> messages, Map<String, Message> messagesMap, Map<Long, Chat> idToChat) {
        long fakeIds = 2000000000L;
        for (Message m : messages) {
            byte[] metadata = m.getMetaData();
            if (metadata != null) {
                List<Part> main = new ProtoBufDecoder(metadata).decode();
                String uuidQuote = ProtoBufDecoder.findString(main, 5);
                if (uuidQuote != null) {
                    m.setQuoted(true);

                    // Decode field 19 which contain referenced message information
                    List<Part> childs = ProtoBufDecoder.findChilds(main, 19);

                    Message messageQuote = messagesMap.get(uuidQuote);
                    if (messageQuote != null) {
                        messageQuote.setMessageQuotedType(MessageQuotedType.QUOTE_FOUND);
                    }else {                        
                        // Referenced message was deleted, so create a new message and fill with data
                        // extracted from referencing message metadata.
                        messageQuote = new Message();
                        messageQuote.setId(fakeIds--);
                        String contact = ProtoBufDecoder.findString(main, 6);
                        if (contact == null) {
                            messageQuote.setFromMe(true);
                        } else {
                            messageQuote.setRemoteResource(contact);
                        }

                        MessageType type = MessageType.TEXT_MESSAGE;
                        if (childs != null) {
                            for (Part c : childs) {
                                switch (c.getIdx()) {
                                    case 1:
                                        String text = c.getString();
                                        if (text != null) {
                                            messageQuote.setData(text);
                                        }
                                        break;

                                    case 3:
                                        type = MessageType.IMAGE_MESSAGE;
                                        Part p = c.getChild(3);
                                        if (p != null) {
                                            String caption = p.getString();
                                            if (caption != null) {
                                                messageQuote.setMediaCaption(caption);
                                            }
                                        }
                                        break;

                                    case 4:
                                        type = MessageType.CONTACT_MESSAGE;
                                        p = c.getChild(16);
                                        if (p != null) {
                                            String vcards = p.getString(true);
                                            if (vcards != null) {
                                                messageQuote.setVcards(
                                                        Arrays.asList(vcards.split(Pattern.quote(VCARD_SEPARATOR))));
                                            }
                                        }
                                        break;

                                    case 5:
                                        type = MessageType.LOCATION_MESSAGE;
                                        p = c.getChild(1);
                                        if (p != null) {
                                            Double latitude = p.getDouble();
                                            if (latitude != null) {
                                                messageQuote.setLatitude(latitude);
                                            }
                                        }
                                        p = c.getChild(2);
                                        if (p != null) {
                                            Double longitude = p.getDouble();
                                            if (longitude != null) {
                                                messageQuote.setLongitude(longitude);
                                            }
                                        }
                                        p = c.getChild(3);
                                        if (p != null) {
                                            text = p.getString();
                                            if (text != null) {
                                                messageQuote.setData(text);
                                            }
                                        }
                                        p = c.getChild(4);
                                        if (p != null) {
                                            String address = p.getString();
                                            if (address != null) {
                                                messageQuote.setAddress(address);
                                            }
                                        }
                                        break;

                                    case 6:
                                        p = c.getChild(2);
                                        String url = null;
                                        if (p != null) {
                                            url = p.getString();
                                            if (url != null) {
                                                messageQuote.setUrl(url);
                                            }
                                        }
                                        if (url != null) {
                                            type = MessageType.URL_MESSAGE;
                                            p = c.getChild(6);
                                            String caption = null;
                                            if (p != null) {
                                                caption = p.getString();
                                                if (caption != null) {
                                                    messageQuote.setMediaCaption(caption);
                                                }
                                            }
                                            p = c.getChild(1);
                                            if (p != null) {
                                                text = p.getString();
                                                if (text != null && !text.equals(url) && !text.equals(caption)) {
                                                    messageQuote.setData(text);
                                                }
                                            }
                                            p = c.getChild(5);
                                            if (p != null) {
                                                text = p.getString();
                                                String prev = messageQuote.getData();
                                                if (text != null && !text.isBlank() && !text.equals(url)
                                                        && !text.equals(caption) && !text.equals(prev)) {
                                                    if (prev != null && !prev.isBlank()) {
                                                        text = prev + "\n" + text;
                                                    }
                                                    messageQuote.setData(text);
                                                }
                                            }
                                        } else {
                                            p = c.getChild(1);
                                            if (p != null) {
                                                text = p.getString();
                                                if (text != null) {
                                                    messageQuote.setData(text);
                                                }
                                            }
                                        }
                                        break;

                                    case 7:
                                        type = MessageType.DOC_MESSAGE;
                                        p = c.getChild(8);
                                        if (p != null) {
                                            String caption = p.getString();
                                            if (caption != null) {
                                                messageQuote.setMediaCaption(caption);
                                            }
                                        }
                                        break;

                                    case 8:
                                        type = MessageType.AUDIO_MESSAGE;
                                        p = c.getChild(5);
                                        if (p != null) {
                                            Integer duration = p.getInteger();
                                            if (duration != null) {
                                                messageQuote.setDuration(duration);
                                            }
                                        }
                                        break;

                                    case 9:
                                        type = MessageType.VIDEO_MESSAGE;
                                        p = c.getChild(5);
                                        if (p != null) {
                                            Integer duration = p.getInteger();
                                            if (duration != null) {
                                                messageQuote.setDuration(duration);
                                            }
                                        }
                                        p = c.getChild(7);
                                        if (p != null) {
                                            String caption = p.getString();
                                            if (caption != null) {
                                                messageQuote.setMediaCaption(caption);
                                            }
                                        }
                                        break;
                                    case 30:
                                        type = MessageType.PRODUCT_MESSAGE;
                                        MessageProduct mp = decodeQuotedProductInfo(c, messageQuote);
                                        messageQuote.setProduct(mp);
                                        messageQuote.setMessageQuotedType(MessageQuotedType.QUOTE_CATALOG);
                                        messageQuote.setFromMe(false);
                                        if (mp != null)
                                            messageQuote.setRemoteResource(mp.getSeller());
                                        break;
                                    default:
                                        break;
                                }
                            }
                        }

                        messageQuote.setMessageType(type);

                        String contactQuote = ProtoBufDecoder.findString(main, 7);
                        if (contactQuote != null) {
                            // find quotes outside this chat
                            // is the reverse of real msg
                            messageQuote.setFromMe(!m.isFromMe());
                            // set remote resource if is not from me
                            messageQuote.setRemoteResource(m.getRemoteResource());
                            // just set default case if does not match order cases ...
                            messageQuote.setMessageQuotedType(MessageQuotedType.QUOTE_PRIVACY_GROUP_NOT_FOUND);
                            if (contactQuote.equals(WAContact.waStatusBroadcast)) {
                                messageQuote.setMessageQuotedType(MessageQuotedType.QUOTE_STATUS);

                            } else if (contactQuote.endsWith(WAContact.waGroupSuffix)) {

                                // set it first in case if not found
                                messageQuote.setQuotePrivateGroupName(contactQuote);
                                boolean found = false;
                                for (Chat cq : idToChat.values()) {
                                    // Find friendly group name and message id

                                    if (!cq.isGroupChat())
                                        continue;

                                    if (cq.getPrintId() != null && contactQuote.contains(cq.getPrintId()))
                                        messageQuote.setQuotePrivateGroupName(cq.getTitle());

                                    for (Message origin : cq.getMessages()) {
                                        if (origin.getUuid() != null && origin.getUuid().compareTo(uuidQuote) == 0) {
                                            messageQuote.setId(origin.getId());
                                            messageQuote.setMessageQuotedType(MessageQuotedType.QUOTE_PRIVACY_GROUP);
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

                    if (messageQuote.getThumbData() == null && childs != null) {
                        byte[] thumbData = null;
                        for (Part c : childs) {
                            Part p = c.getChild(16);
                            if (p != null) {
                                byte[] bytes = p.getBytes(true);
                                if (bytes != null && (thumbData == null || thumbData.length < bytes.length)) {
                                    thumbData = bytes;
                                }
                            }
                        }
                        if (thumbData != null) {
                            messageQuote.setThumbData(thumbData);
                        }
                    }
                    m.setMessageQuote(messageQuote);
                }
            }
        }
    }

    private Message createMessageFromDB(ResultSet rs, Chat chat) throws SQLException {
        Message m = new Message();
        if (account != null)
            m.setLocalResource(account.getId());
        m.setId(rs.getLong("id")); //$NON-NLS-1$
        String remoteResource = rs.getString("remoteResource");
        if (remoteResource == null || remoteResource.isEmpty() || !chat.isGroupOrChannelChat()) {
            remoteResource = chat.getRemote().getFullId();
        }
        m.setRemoteResource(remoteResource); // $NON-NLS-1$
        m.setStatus(rs.getInt("status")); //$NON-NLS-1$
        m.setData(rs.getString("data")); //$NON-NLS-1$
        m.setFromMe(rs.getInt("fromMe") == 1); //$NON-NLS-1$
        m.setSortId(rs.getLong("ZSORT"));
        if (m.isFromMe()) {
            switch (m.getStatus()) {
                case 1:
                    m.setMessageStatus(MessageStatus.MESSAGE_SENT);
                    break;
                case 6:
                    m.setMessageStatus(MessageStatus.MESSAGE_DELIVERED);
                    break;
                case 8:
                    m.setMessageStatus(MessageStatus.MESSAGE_VIEWED);
                    break;
                case 9:
                    m.setMessageStatus(MessageStatus.MESSAGE_UNSENT);
                    break;
                default:
                    break;
            }
        }
        try {
            m.setTimeStamp(dateFormat.parse(rs.getString("timestamp"))); //$NON-NLS-1$
        } catch (ParseException e) {
            throw new SQLException(e);
        }
        int gEventType = rs.getInt("gEventType"); //$NON-NLS-1$
        int messageType = rs.getInt("messageType"); //$NON-NLS-1$
        m.setMessageType(decodeMessageType(messageType, gEventType, m.getData()));
        if (m.getMessageType() != CONTACT_MESSAGE) {
            if (m.getMessageType() != LOCATION_MESSAGE && m.getMessageType() != DELETED_BY_SENDER) {
                m.setMediaMime(rs.getString("vCardString"));
            }
        } else {
            String vcards = rs.getString("vCardString"); //$NON-NLS-1$
            if (vcards != null) {
                m.setVcards(Arrays.asList(vcards.split(Pattern.quote(VCARD_SEPARATOR))));
            }
        }
        m.setMediaName(rs.getString("mediaName")); //$NON-NLS-1$
        m.setMediaSize(rs.getLong("mediaSize")); //$NON-NLS-1$

        String caption = rs.getString("mediaCaption");
        if (caption == null || caption.isBlank()) {
            caption = m.getData();
        }
        m.setMediaCaption(caption);

        m.setThumbpath(rs.getString("thumbpath")); //$NON-NLS-1$
        m.setUrl(rs.getString("url")); //$NON-NLS-1$
        m.setLatitude(rs.getDouble("latitude")); //$NON-NLS-1$
        m.setLongitude(rs.getDouble("longitude")); //$NON-NLS-1$
        if (MEDIA_MESSAGES.contains(m.getMessageType())) {
            try {
                m.setMediaHash(rs.getString("mediaHash"), true);
            } catch (IllegalArgumentException e) {
            } // ignore
        }
        byte[] receiptInfo = rs.getBytes("receiptInfo"); //$NON-NLS-1$
        m.setDeleted(false);
        if (receiptInfo != null) {
            decodeReceiptInfo(m, receiptInfo);
        }
        m.setForwarded(rs.getInt("forwarded") > 0);

        byte[] metadata = rs.getBytes("metadata");
        if (hasZSTANZAIDAndZMETADATAColumns) {
            m.setUuid(rs.getString("uuid"));
            m.setMetaData(metadata);
        }

        if (hasZMOVIEDURATIONColumn) {
            m.setDuration(rs.getInt("duration"));
        }
        
        switch (m.getMessageType()) {
            case TEXT_MESSAGE:
                try {
                if (metadata != null) {
                    m.setUrl(decodeUrl(metadata));
                }
                }catch (Exception e) {
                    e.printStackTrace();
                }
                break;
                
            case GROUP_CREATED:
                String user = rs.getString("remoteResource");
                if (user == null) {
                    m.setFromMe(true);
                    m.setRemoteResource(null);
                }
                break;

            case USER_ADDED_TO_GROUP:
            case USER_REMOVED_FROM_GROUP:
                String s0 = m.getData();
                if (s0 != null && !s0.isBlank()) {
                    if (s0.indexOf(";") >= 0) {
                        String[] s1 = s0.split(";", 2);
                        m.setRemoteResource(s1[0]);
                        String[] s2 = s1[1].split(",");
                        for (String s3 : s2) {
                            m.addUserAction(s3);
                        }
                    } else {
                        m.setRemoteResource(s0);
                        m.addUserAction(rs.getString("remoteResource"));
                    }
                    m.setData(null);
                } else {
                    m.setFromMe(true);
                    m.setRemoteResource(null);
                    m.addUserAction(rs.getString("remoteResource"));
                }
                break;

            case GROUP_ADDED_TO_COMMUNITY:
            case GROUP_REMOVED_FROM_COMMUNITY:
                s0 = m.getData();
                if (s0 != null && !s0.isBlank()) {
                    String key = "linked_groups";
                    int p0 = s0.indexOf(key);
                    if (p0 >= 0) {
                        int p1 = s0.indexOf(":", p0);
                        if (p1 > 0) {
                            int p2 = s0.indexOf("{", p1);
                            if (p2 > 0) {
                                int p3 = s0.indexOf("}", p2 + 1);
                                if (p3 > 0) {
                                    String[] groups = s0.substring(p2 + 1, p3).trim().split(",");
                                    StringBuilder sb = new StringBuilder();
                                    for (String s1 : groups) {
                                        String[] s2 = s1.split(":", 2);
                                        if (s2.length >= 2) {
                                            if (sb.length() > 0) {
                                                sb.append(", ");
                                            }
                                            sb.append(s2[1]);
                                        }
                                    }
                                    if (sb.length() > 0) {
                                        m.setData(sb.toString());
                                    }
                                }
                            }
                        }
                    }
                }
                break;

            case GROUP_NAME_CHANGED:
                s0 = m.getData();
                if (s0 != null && !s0.isBlank()) {
                    String key = "new_subject";
                    int p0 = s0.indexOf(key);
                    if (p0 >= 0) {
                        int p1 = s0.indexOf(":", p0);
                        if (p1 > 0) {
                            int p2 = s0.indexOf("\"", p1);
                            if (p2 > 0) {
                                int p3 = s0.indexOf("\"", p2 + 1);
                                if (p3 > 0) {
                                    m.setData(s0.substring(p2 + 1, p3).trim());
                                }
                            }
                        }
                    }
                }
                break;

            case EPHEMERAL_CHANGED:
                int duration = 0;
                String s = m.getData();
                if (s != null && !s.isBlank()) {
                    try {
                        duration = Integer.parseInt(s);
                    } catch (Exception e) {
                    }
                }
                m.setDuration(duration);
                m.setData("");
                break;
                
            case EPHEMERAL_DEFAULT:
                m.setDuration(decodeEphemeralDuration(metadata));
                break;

            case TEMPLATE_MESSAGE:
                m.setMessageTemplate(decodeTemplate(metadata, m));
                if (m.getMessageTemplate() != null && m.getMessageTemplate().getContent().equals(m.getData())) {
                    m.setData("");
                }
                break;

            case GROUP_INVITE:
                m.setGroupInviteName(decodeGroupInvite(metadata));
                break;

            case PRODUCT_MESSAGE:
                m.setProduct(decodeProductInfo(metadata, m));
                break;

            case URL_MESSAGE:
                decodeURLInfo(metadata, m);
                break;

            case LOCATION_MESSAGE:
            case SHARE_LOCATION_MESSAGE:
                m.setAddress(rs.getString("mediaHash"));
                break;

            case VOICE_CALL:
                duration = decodeCallDuration(metadata);
                m.setDuration(duration);
                if (duration == 0) {
                    m.setMessageType(MISSED_VOICE_CALL);
                }
                break;

            default:
                break;
        }

        return m;
    }

    private void decodeURLInfo(byte[] metadata, Message m) {
        List<Part> parts1 = new ProtoBufDecoder(metadata).decode();
        if (parts1 != null) {
            for (Part p1 : parts1) {
                if (p1.getIdx() == 3) {
                    String description = p1.getString();
                    if (description != null && !description.isBlank()) {
                        m.setData(description);
                    }
                } else if (p1.getIdx() == 19) {
                    Part p2 = p1.getChild(3);
                    if (p2 != null) {
                        Part p3 = p2.getChild(3);
                        if (p3 != null) {
                            String s = p3.getString();
                            if (s != null && !s.isBlank()) {
                                m.setMediaCaption(s);
                            }
                        }
                        p3 = p2.getChild(16);
                        if (p3 != null) {
                            byte[] bytes = p3.getBytes();
                            if (bytes != null) {
                                m.setThumbData(bytes);
                            }
                        }
                    }
                }
            }
        }
    }

    private MessageProduct decodeProductInfo(byte[] metadata, Message m) {
        Part p1 = new ProtoBufDecoder(metadata).decode(26);
        String title = null;
        String observation = null;
        String currency = null;
        String seller = null;
        int amount = 0;
        if (p1 != null) {
            Part p2 = p1.getChild(1);
            if (p2 != null) {
                Part p3 = p2.getChild(1);
                if (p3 != null) {
                    Part p4 = p3.getChild(1);
                    if (p4 != null) {
                        Part p5 = p4.getChild(16);
                        if (p5 != null) {
                            byte[] bytes = p5.getBytes();
                            if (bytes != null) {
                                m.setThumbData(bytes);
                            }
                        }
                    }
                    p4 = p3.getChild(3);
                    if (p4 != null) {
                        title = p4.getString();
                    }
                    p4 = p3.getChild(4);
                    if (p4 != null) {
                        observation = p4.getString();
                    }
                    p4 = p3.getChild(5);
                    if (p4 != null) {
                        currency = p4.getString();
                    }
                    p4 = p3.getChild(6);
                    if (p4 != null) {
                        String v = p4.getString();
                        if (v != null && !v.isBlank()) {
                            try {
                                amount = Integer.parseInt(v);
                            } catch (NumberFormatException e) {
                            }
                        }
                    }
                    p3 = p2.getChild(2);
                    if (p3 != null) {
                        seller = p3.getString();
                    }
                }
            }
        }
        if (title != null || currency != null || amount != 0 || seller != null) {
            return new MessageProduct(title, seller, currency, amount, observation);
        }
        return null;
    }

    private MessageProduct decodeQuotedProductInfo(Part p2, Message m) {
        String title = null;
        String observation = null;
        String currency = null;
        String seller = null;
        int amount = 0;
        if (p2 != null) {
            Part p3 = p2.getChild(1);
            if (p3 != null) {
                Part p4 = p3.getChild(1);
                if (p4 != null) {
                    Part p5 = p4.getChild(16);
                    if (p5 != null) {
                        byte[] bytes = p5.getBytes();
                        if (bytes != null) {
                            m.setThumbData(bytes);
                        }
                    }
                }
                p4 = p3.getChild(3);
                if (p4 != null) {
                    title = p4.getString();
                }
                p4 = p3.getChild(4);
                if (p4 != null) {
                    observation = p4.getString();
                }
                p4 = p3.getChild(5);
                if (p4 != null) {
                    currency = p4.getString();
                }
                p4 = p3.getChild(6);
                if (p4 != null) {
                    String v = p4.getString();
                    if (v != null && !v.isBlank()) {
                        try {
                            amount = Integer.parseInt(v);
                        } catch (NumberFormatException e) {
                        }
                    }
                }
                p3 = p2.getChild(2);
                if (p3 != null) {
                    seller = p3.getString();
                }
            }
        }
        if (title != null || currency != null || amount != 0 || seller != null) {
            return new MessageProduct(title, seller, currency, amount, observation);
        }
        return null;
    }

    private String decodeGroupInvite(byte[] metadata) {
        Part p1 = new ProtoBufDecoder(metadata).decode(28);
        if (p1 != null) {
            Part p2 = p1.getChild(4);
            if (p2 != null) {
                return p2.getString();
            }
        }
        return null;
    }

    private String decodeUrl(byte[] metadata) {
        Part p1 = new ProtoBufDecoder(metadata).decode(42);
        if (p1 != null) {
            Part p2 = p1.getChild(1);
            if (p2 != null) {
                Part p3 = p2.getChild(9);
                if (p3 != null) {
                    String s = p3.getString();
                    if (s != null && !s.isBlank()) {
                        return s;
                    }
                }
            }
        }
        return null;
    }

    private MessageTemplate decodeTemplate(byte[] metadata, Message m) {
        MessageTemplate t = null;
        List<Part> childs = new ProtoBufDecoder(metadata).decode();
        if (childs != null) {
            for (Part p1 : childs) {
                if (p1.getIdx() == 24 || p1.getIdx() == 43 || p1.getIdx() == 51) {
                    Part p2 = p1.getChild(1);
                    if (p2 != null) {
                        StringBuilder content = new StringBuilder();
                        for (int idx : new int[] { 2, 6, 7 }) {
                            Part p3 = p2.getChild(idx);
                            if (p3 != null) {
                                String s = p3.getString();
                                if (s != null && !s.isBlank()) {
                                    if (content.length() > 0) {
                                        content.append("\n");
                                    }
                                    content.append(s);
                                }
                            }
                        }
                        if (content.length() > 0) {
                            t = new MessageTemplate(content.toString());
                            List<Part> c2 = p2.getChilds();
                            if (c2 != null) {
                                for (Part p3 : c2) {
                                    if (p1.getIdx() == 24 && p3.getIdx() == 8) {
                                        Part p4 = p3.getChild(2);
                                        if (p4 != null) {
                                            Part p5 = p4.getChild(1);
                                            String text = "";
                                            if (p5 != null) {
                                                String s = p5.getString();
                                                if (s != null) {
                                                    text = s;
                                                }
                                            }
                                            p5 = p4.getChild(2);
                                            String extra = "";
                                            if (p5 != null) {
                                                String s = p5.getString();
                                                if (s != null) {
                                                    extra = s;
                                                }
                                            }
                                            if (!text.isBlank() || !extra.isBlank()) {
                                                t.addButton(new MessageTemplate.Button(text, extra));
                                            }
                                        }
                                    } else if ((p1.getIdx() == 51 && p3.getIdx() == 9)
                                            || (p1.getIdx() == 43 && p3.getIdx() == 5)) {
                                        List<Part> c3 = p3.getChilds();
                                        if (c3 != null) {
                                            for (Part p4 : c3) {
                                                if (p4.getIdx() == 2) {
                                                    Part p5 = p4.getChild(1);
                                                    if (p5 != null) {
                                                        String text = p5.getString();
                                                        if (text != null && !text.isBlank()) {
                                                            t.addButton(new MessageTemplate.Button(text, ""));
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Part p4 = p3.getChild(16);
                                    if (p4 != null) {
                                        byte[] bytes = p4.getBytes(true);
                                        if (bytes != null) {
                                            m.setThumbData(bytes);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return t;
    }

    private int decodeEphemeralDuration(byte[] metadata) {
        int seconds = 0;
        Part p1 = new ProtoBufDecoder(metadata).decode(36);
        if (p1 != null) {
            String s = p1.getString();
            if (s != null && !s.isBlank()) {
                try {
                    seconds = Integer.parseInt(s);
                } catch (NumberFormatException e) {
                }
            }
        }
        return seconds;
    }

    private int decodeCallDuration(byte[] metadata) {
        int seconds = 0;
        Part p1 = new ProtoBufDecoder(metadata).decode(87);
        if (p1 != null) {
            Part p2 = p1.getChild(1);
            if (p2 != null) {
                Part p3 = p2.getChild(3);
                if (p3 != null) {
                    String s = p3.getString();
                    if (s != null && !s.isBlank()) {
                        try {
                            seconds = Integer.parseInt(s);
                        } catch (NumberFormatException e) {
                        }
                    }
                }
            }
        }
        return seconds;
    }

    private Message createMessageFromUndeletedRecord(SqliteRow row, Chat chat, Map<Long, SqliteRow> mediaItems,
            Map<Long, SqliteRow> groupMembers) throws SQLException {
        Message m = new Message();
        if (account != null)
            m.setLocalResource(account.getId());
        m.setId(row.getIntValue("Z_PK")); //$NON-NLS-1$
        String remoteResource = null;
        if (chat.isGroupChat()) {
            long groupMemberID = row.getIntValue("ZGROUPMEMBER"); //$NON-NLS-1$
            SqliteRow groupMemberRow = groupMembers.get(groupMemberID);
            if (groupMemberRow != null) {
                remoteResource = groupMemberRow.getTextValue("ZMEMBERJID"); //$NON-NLS-1$
            }
        } else {
            remoteResource = row.getTextValue("ZFROMJID"); //$NON-NLS-1$
        }
        if (remoteResource == null || remoteResource.isEmpty() || !chat.isGroupChat()) {
            remoteResource = chat.getRemote().getFullId();
        }
        m.setRemoteResource(remoteResource); // $NON-NLS-1$
        m.setStatus((int) row.getIntValue("ZMESSAGESTATUS")); //$NON-NLS-1$
        m.setData(row.getTextValue("ZTEXT")); //$NON-NLS-1$
        m.setFromMe(row.getIntValue("ZISFROMME") == 1); //$NON-NLS-1$
        if (m.isFromMe()) {
            switch (m.getStatus()) {
                case 1:
                    m.setMessageStatus(MessageStatus.MESSAGE_SENT);
                    break;
                case 6:
                    m.setMessageStatus(MessageStatus.MESSAGE_DELIVERED);
                    break;
                case 8:
                    m.setMessageStatus(MessageStatus.MESSAGE_VIEWED);
                    break;
                case 9:
                    m.setMessageStatus(MessageStatus.MESSAGE_UNSENT);
                    break;
                default:
                    break;
            }
        }
        try {
            m.setTimeStamp(new Date((row.getIntValue("ZMESSAGEDATE") + 978307200L) * 1000)); //$NON-NLS-1$
        } catch (RuntimeException e) {
        }
        int gEventType = (int) row.getIntValue("ZGROUPEVENTTYPE"); //$NON-NLS-1$
        int messageType = (int) row.getIntValue("ZMESSAGETYPE"); //$NON-NLS-1$
        m.setMessageType(decodeMessageType(messageType, gEventType, m.getData()));
        SqliteRow mediaItem = mediaItems.get(m.getId());
        if (mediaItem != null) {
            try {
                if (m.getMessageType() != CONTACT_MESSAGE) {
                    if (m.getMessageType() != LOCATION_MESSAGE && m.getMessageType() != DELETED_BY_SENDER) {
                        m.setMediaMime(mediaItem.getTextValue("ZVCARDSTRING"));
                    }
                } else {
                    String vcards = mediaItem.getTextValue("ZVCARDSTRING"); //$NON-NLS-1$
                    if (vcards != null) {
                        m.setVcards(Arrays.asList(vcards.split(Pattern.quote(VCARD_SEPARATOR))));
                    }
                }
            } catch (RuntimeException e) {
            }
            m.setMediaName(mediaItem.getTextValue("ZMEDIALOCALPATH")); //$NON-NLS-1$
            m.setMediaSize(mediaItem.getIntValue("ZFILESIZE")); //$NON-NLS-1$
            m.setMediaCaption(mediaItem.getTextValue("ZTITLE")); //$NON-NLS-1$
            m.setThumbpath(mediaItem.getTextValue("ZXMPPTHUMBPATH")); //$NON-NLS-1$
            m.setUrl(mediaItem.getTextValue("ZMEDIAURL")); //$NON-NLS-1$
            m.setLatitude(mediaItem.getFloatValue("ZLATITUDE")); //$NON-NLS-1$
            m.setLongitude(mediaItem.getFloatValue("ZLONGITUDE")); //$NON-NLS-1$

            // This block must be before "if (MEDIA_MESSAGES.contains(m.getMessageType()))",
            // otherwise Media Hash won't be set. See issue #1921.
            if (messageType == 0 && m.getData() == null) {
                if (m.getMediaMime() != null) {
                    var mediaMime = m.getMediaMime();
                    if (mediaMime != null) {
                        if (mediaMime.startsWith("image")) {
                            m.setMessageType(IMAGE_MESSAGE);
                        } else if (mediaMime.startsWith("video")) {
                            m.setMessageType(VIDEO_MESSAGE);
                        } else if (mediaMime.startsWith("application")) {
                            m.setMessageType(DOC_MESSAGE);
                        } else if (mediaMime.startsWith("audio")) {
                            m.setMessageType(AUDIO_MESSAGE);
                        } else if (m.getMediaCaption() != null) {
                            m.setMessageType(UNKNOWN_MEDIA_MESSAGE);
                        }
                    }
                } else if (m.getMediaCaption() != null) {
                    m.setMessageType(UNKNOWN_MEDIA_MESSAGE);
                }
            }

            if (MEDIA_MESSAGES.contains(m.getMessageType())) {
                try {
                    m.setMediaHash(mediaItem.getTextValue("ZVCARDNAME"), true); //$NON-NLS-1$
                } catch (IllegalArgumentException e) {
                } // ignore
            }
        }
        m.setDeleted(row.isDeletedRow());
        return m;
    }

    private void decodeReceiptInfo(Message m, byte[] receiptInfo) {
        List<Part> parts1 = new ProtoBufDecoder(receiptInfo).decode();
        if (parts1 == null) {
            return;
        }
        for (Part p1 : parts1) {
            if (p1.getIdx() == 7) {
                List<Part> parts2 = p1.getChilds();
                if (parts2 != null) {
                    for (Part p2 : parts2) {
                        if (p2.getIdx() == 1) {
                            // Reactions from others: 7 -> 1 -> (2:Contact, 3:Reaction, 4:TimeStamp)
                            List<Part> parts3 = p2.getChilds();
                            if (parts3 != null) {
                                MessageAddOn a = new MessageAddOn();
                                for (Part p3 : parts3) {
                                    String s3 = p3.getString();
                                    if (s3 != null) {
                                        if (p3.getIdx() == 2) {
                                            a.setRemoteResource(s3);
                                        } else if (p3.getIdx() == 3) {
                                            a.setReaction(s3);
                                        } else if (p3.getIdx() == 4) {
                                            a.setTimeStamp(new Date(Long.parseLong(s3)));
                                        }
                                    }
                                }
                                m.addMessageAddOn(a);
                            }
                        } else if (p2.getIdx() == 2) {
                            // Reactions from the owner: 7 -> 2 -> (2:Reaction, 3:TimeStamp)
                            List<Part> parts3 = p2.getChilds();
                            if (parts3 != null) {
                                MessageAddOn a = new MessageAddOn();
                                a.setFromMe(true);
                                for (Part p3 : parts3) {
                                    String s3 = p3.getString();
                                    if (s3 != null) {
                                        if (p3.getIdx() == 2) {
                                            a.setReaction(s3);
                                        } else if (p3.getIdx() == 3) {
                                            a.setTimeStamp(new Date(Long.parseLong(s3)));
                                        }
                                    }
                                }
                                m.addMessageAddOn(a);
                            }
                        }
                    }
                }
            }
            if (p1.getIdx() == 8 && m.getMessageType() == POLL_MESSAGE) {
                List<Part> parts2 = p1.getChilds();
                if (parts2 != null) {
                    for (Part p2 : parts2) {
                        if (p2.getIdx() == 2) {
                            String s2 = p2.getString();
                            if (s2 != null && !s2.isBlank()) {
                                // Poll question
                                m.setData(s2);
                            }
                        }
                        if (p2.getIdx() == 3) {
                            List<Part> parts3 = p2.getChilds();
                            if (parts3 != null) {
                                for (Part p3 : parts3) {
                                    if (p3.getIdx() == 1) {
                                        String s3 = p3.getString();
                                        if (s3 != null && !s3.isBlank()) {
                                            // Poll option
                                            m.addPollOption(new PollOption(s3, 0));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private List<Chat> undeleteChats(SQLiteUndeleteTable undeleteChatsSessions, WAContactsDirectory contacts) {
        List<Chat> result = new ArrayList<>();

        if (undeleteChatsSessions != null && !undeleteChatsSessions.getTableRows().isEmpty()) {
            for (SqliteRow row : undeleteChatsSessions.getTableRows()) {
                String contactId = row.getTextValue("ZCONTACTJID"); //$NON-NLS-1$
                WAContact contact = contacts.getContact(contactId);
                Chat c = new Chat(contact);
                c.setId(row.getIntValue("Z_PK")); //$NON-NLS-1$
                c.setDeleted(row.getIntValue("ZREMOVED") != 0 || row.isDeletedRow());
                c.setSubject(row.getTextValue("ZPARTNERNAME")); //$NON-NLS-1$
                if (contactId.endsWith(WAContact.waGroupSuffix)) {
                    c.setGroupChat(true);
                } else if (contactId.endsWith(WAContact.waNewsletterSuffix)) {
                    c.setChannelChat(true);
                } else if (contactId.endsWith(WAContact.waStatusSuffix)) {
                    c.setBroadcast(true);
                }
                result.add(c);
            }
        }

        return result;
    }

    protected Message.MessageType decodeMessageType(int messageType, int gEventType, String data) {
        Message.MessageType result = UNKNOWN_MESSAGE;
        switch (messageType) {
            case 0:
                result = TEXT_MESSAGE;
                break;

            case 1:
                result = IMAGE_MESSAGE;
                break;

            case 2:
                result = VIDEO_MESSAGE;
                break;

            case 3:
                result = AUDIO_MESSAGE;
                break;

            case 4:
                result = CONTACT_MESSAGE;
                break;

            case 5:
                result = LOCATION_MESSAGE;
                break;

            case 6:
                switch (gEventType) {
                    case 1:
                        // "data" contains the new group name
                        result = GROUP_NAME_CHANGED;
                        break;

                    case 2:
                        result = USER_ADDED_TO_GROUP;
                        break;

                    case 3:
                        result = USER_LEFT_GROUP;
                        break;

                    case 4:
                        result = GROUP_ICON_CHANGED;
                        break;

                    case 5:
                        result = GROUP_ICON_DELETED;
                        break;

                    case 7:
                        // "sender" user was removed by "data" user
                        result = USER_REMOVED_FROM_GROUP;
                        break;

                    case 9:
                        result = CHANGED_NUMBER_CHATTING_WITH_OLD;
                        break;

                    case 10:
                        result = YOU_ADMIN;
                        break;

                    case 11:
                        result = YOU_NOT_ADMIN;
                        break;

                    case 12:
                        result = GROUP_CREATED;
                        break;

                    case 13:
                        result = COMMUNITY_NOT_AVAILABLE;
                        break;

                    case 15:
                        result = USER_JOINED_GROUP_FROM_LINK;
                        break;

                    case 17:
                        result = GROUP_DESCRIPTION_CHANGED;
                        break;

                    case 18:
                        result = GROUP_CHANGED_ONLY_ADMINS_CAN_EDIT;
                        break;

                    case 19:
                        result = GROUP_CHANGED_ALL_MEMBERS_CAN_EDIT;
                        break;

                    case 20:
                        result = GROUP_CHANGED_ONLY_ADMINS_CAN_SEND;
                        break;

                    case 21:
                        result = GROUP_CHANGED_ALL_MEMBERS_CAN_SEND;
                        break;

                    case 22:
                        result = GROUP_DESCRIPTION_DELETED;
                        break;
                        
                    case 23:
                        result = USER_JOINED_GROUP_FROM_INVITATION;
                        break;

                    case 26:
                        result = EPHEMERAL_CHANGED;
                        break;

                    case 30:
                        result = USER_JOINED_GROUP_FROM_COMMUNITY;
                        break;

                    case 31:
                    case 61:
                        result = GROUP_ADDED_TO_COMMUNITY;
                        break;

                    case 33:
                        result = GROUP_REMOVED_FROM_COMMUNITY;
                        break;

                    case 39:
                        result = USER_ADMIN;
                        break;
                        
                    case 42:
                        result = GROUP_CHANGED_ONLY_ADMINS_CAN_ADD;
                        break;

                    case 43:
                        result = GROUP_NOT_PART_OF_COMMUNITY;
                        break;

                    case 50:
                        // multiple users added to group
                        result = USER_ADDED_TO_GROUP;
                        break;

                    case 51:
                        result = COMMUNITY_MANAGEMENT_ACTION;
                        break;

                    case 56:
                        // new group name change, 
                        // DATA is {"previous_subject":"Old Name","new_subject":"New Name"}
                        result = GROUP_NAME_CHANGED;
                        break;
                        
                    case 57:
                        result = COMMUNITY_DESCRIPTION_CHANGED;
                        break;
                        
                    case 60:
                        result = COMMUNITY_WELCOME;
                        break;

                    case 64:
                        if ("1".equals(data)) {
                            result = COMMUNITY_CHANGED_ALL_MEMBERS_CAN_ADD;
                        } else {
                            result = COMMUNITY_CHANGED_ONLY_ADMINS_CAN_ADD;
                        }
                        break;
                }
                break;

            case 7:
                result = URL_MESSAGE;
                break;

            case 8:
                result = DOC_MESSAGE;
                break;

            case 10:
                switch (gEventType) {
                    case 2:
                        result = MESSAGES_NOW_ENCRYPTED;
                        break;

                    case 1:
                    case 45:
                        result = MISSED_VOICE_CALL;
                        break;

                    case 3:
                    case 36:
                        result = ENCRYPTION_KEY_CHANGED;
                        break;

                    case 4:
                    case 46:
                        result = MISSED_VIDEO_CALL;
                        break;

                    case 5:
                        result = CHANGED_NUMBER_CHATTING_WITH_OLD;
                        break;

                    case 6:
                        result = CHANGED_NUMBER_CHATTING_WITH_NEW;
                        break;

                    case 21:
                        // Missed voice call (group)
                        result = MISSED_VOICE_CALL;
                        break;

                    case 22:
                        // Missed video call (group)
                        result = MISSED_VIDEO_CALL;
                        break;

                    case 26:
                        result = BUSINESS_CHAT;
                        break;

                    case 29:
                        result = BUSINESS_CHANGED_NAME;
                        break;

                    case 30:
                        result = BUSINESS_TO_STANDARD;
                        break;

                    case 34:
                        result = BLOCKED_CONTACT;
                        break;

                    case 35:
                        result = UNBLOCKED_CONTACT;
                        break;

                    case 25:
                    case 38:
                        result = BUSINESS_OFFICIAL;
                        break;

                    case 40:
                    case 41:
                        // Started a video call (group)
                        result = VIDEO_CALL;
                        break;

                    case 47:
                        result = EPHEMERAL_SAVE;
                        break;

                    case 51:
                        result = CHAT_ADDED_PRIVACY;
                        break;

                    case 56:
                        result = SENDER_ADDED_TO_CONTACTS;
                        break;
                }
                break;

            case 11:
                result = GIF_MESSAGE;
                break;

            case 12:
                result = WAITING_MESSAGE;
                break;

            case 14:
                result = DELETED_BY_SENDER;
                break;

            case 15:
                result = STICKER_MESSAGE;
                break;

            case 19:
            case 20:
            case 23:
            case 24:
            case 30:
            case 32:
            case 41:
            case 42:
                result = TEMPLATE_MESSAGE;
                break;

            case 26:
                result = PRODUCT_MESSAGE;
                break;

            case 27:
                result = GROUP_INVITE;
                break;

            case 28:
                result = EPHEMERAL_DEFAULT;
                break;

            case 25:
            case 31:
            case 34:
                // Quote of a template
                result = TEXT_MESSAGE;
                break;

            case 38:
                result = VIEW_ONCE_IMAGE_MESSAGE;
                break;

            case 39:
                result = VIEW_ONCE_VIDEO_MESSAGE;
                break;

            case 46:
                result = POLL_MESSAGE;
                break;

            case 53:
                result = VIEW_ONCE_AUDIO_MESSAGE;
                break;

            case 54:
                // Instant video message (circle shaped video)
                result = VIDEO_MESSAGE;
                break;

            case 55:
                switch (gEventType) {
                    case 1:
                        result = CHANNEL_CREATED;                        
                        break;
                    
                    case 4:
                        result = CHANNEL_ADDED_PRIVACY;                        
                        break;
                }
                break;
                
            case 59:
                result = VOICE_CALL;
                break;
            
            case 66:
                result = MESSAGE_ASSOCIATION;
                break;
        }
        return result;
    }

    public void extractPushNames() {
        // Extract names associated to numbers (jid), for contacts not in contacts list.
        Exception ex = null;
        try (Connection conn = getConnection()) {
            boolean hasPushNameTable = false;
            try {
                if (SQLite3DBParser.containsTable("ZWAPROFILEPUSHNAME", conn)) {
                    hasPushNameTable = true;
                }
                if (hasPushNameTable) {
                    try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(SELECT_PUSH_NAMES)) {
                        while (rs.next()) {
                            String jid = rs.getString("jid");
                            if (jid != null && !jid.isBlank() && !contacts.hasContact(Util.getNameFromId(jid))) {
                                String name = rs.getString("pushname");
                                if (name != null && !name.isBlank() && !jid.startsWith(name)) {
                                    WAContact c = contacts.getContact(jid);
                                    c.setDisplayName("~" + name);
                                }
                            }
                        }
                    } catch (SQLException e) {
                        ex = e;
                    }
                }
            } catch (Exception e) {
                ex = e;
            }
        } catch (SQLException e) {
            ex = e;
        }
        if (ex != null) {
            logger.warn("Error reading push names from WhatsApp iOS database " + itemPath, ex);
        }
    }

    /**
     * Static query strings
     */
    private static final String SELECT_CHAT_LIST = "SELECT ZWACHATSESSION.Z_PK as id, ZCONTACTJID AS contact, " //$NON-NLS-1$
            + "ZPARTNERNAME as subject, ZLASTMESSAGEDATE, ZPATH as avatarPath, ZREMOVED " //$NON-NLS-1$
            + "FROM ZWACHATSESSION " //$NON-NLS-1$
            + "LEFT JOIN ZWAPROFILEPICTUREITEM ON ZWAPROFILEPICTUREITEM.ZJID = ZWACHATSESSION.ZCONTACTJID " //$NON-NLS-1$
            + "ORDER BY ZLASTMESSAGEDATE DESC"; //$NON-NLS-1$

    private static final String SELECT_CHAT_LIST_NO_PPIC = "SELECT ZWACHATSESSION.Z_PK as id, ZCONTACTJID AS contact, " //$NON-NLS-1$
            + "ZPARTNERNAME as subject, ZLASTMESSAGEDATE, NULL as avatarPath, 0 as ZREMOVED " //$NON-NLS-1$
            + "FROM ZWACHATSESSION " //$NON-NLS-1$
            + "ORDER BY ZLASTMESSAGEDATE DESC"; //$NON-NLS-1$

    private static final String SELECT_PUSH_NAMES = "select ZJID as jid, ZPUSHNAME as pushname from ZWAPROFILEPUSHNAME";
    
    private static final String SELECT_GROUP_MEMBERS = "select CS.ZCONTACTJID as `group`, ZMEMBERJID as member from ZWAGROUPMEMBER GM "
            + "inner join ZWACHATSESSION CS on GM.ZCHATSESSION=CS.Z_PK where `group`=?";

    private static final String SELECT_MESSAGES_USER = "SELECT ZWAMESSAGE.Z_PK AS id, ZCHATSESSION " //$NON-NLS-1$
            + "as chatId, ZFROMJID AS remoteResource, ZMESSAGESTATUS AS status, ZTEXT AS data, " //$NON-NLS-1$
            + "ZISFROMME AS fromMe, datetime(ZMESSAGEDATE + 978307200, 'unixepoch') AS timestamp, " //$NON-NLS-1$
            + "ZVCARDSTRING as vCardString, ZFILESIZE as mediaSize, ZMEDIALOCALPATH " //$NON-NLS-1$
            + "as mediaName, ZVCARDNAME as mediaHash, ZTITLE as mediaCaption, " //$NON-NLS-1$
            + "ZLATITUDE as latitude, ZLONGITUDE as longitude, ZMEDIAURL as url, ZXMPPTHUMBPATH as thumbpath, " //$NON-NLS-1$
            + "INFO.ZRECEIPTINFO as receiptInfo, " //$NON-NLS-1$
            + "(1 << 7 & ZFLAGS) as forwarded, " //$NON-NLS-1$
            + "ZWAMESSAGE.ZSTANZAID as uuid, ZWAMEDIAITEM.ZMETADATA as metadata, " //$NON-NLS-1$
            + "ZWAMEDIAITEM.ZMOVIEDURATION as duration, "
            + "ZGROUPEVENTTYPE as gEventType, ZMESSAGETYPE as messageType, ZSORT FROM ZWAMESSAGE " //$NON-NLS-1$
            + "LEFT JOIN ZWAMEDIAITEM ON ZWAMESSAGE.Z_PK = ZWAMEDIAITEM.ZMESSAGE " //$NON-NLS-1$
            + "LEFT JOIN ZWAMESSAGEINFO INFO ON INFO.Z_PK = ZWAMESSAGE.ZMESSAGEINFO " //$NON-NLS-1$
            + "ORDER BY ZSORT"; //$NON-NLS-1$

    private static final String SELECT_MESSAGES_GROUP = "SELECT ZWAMESSAGE.Z_PK AS id, ZWAMESSAGE.ZCHATSESSION " //$NON-NLS-1$
            + "as chatId, ZMEMBERJID AS remoteResource, ZMESSAGESTATUS AS status, ZTEXT AS data, " //$NON-NLS-1$
            + "ZISFROMME AS fromMe, datetime(ZMESSAGEDATE + 978307200, 'unixepoch') AS timestamp, " //$NON-NLS-1$
            + "ZVCARDSTRING as vCardString, ZFILESIZE as mediaSize, ZMEDIALOCALPATH " //$NON-NLS-1$
            + "as mediaName, ZVCARDNAME as mediaHash, ZTITLE as mediaCaption, " //$NON-NLS-1$
            + "ZLATITUDE as latitude, ZLONGITUDE as longitude, ZMEDIAURL as url, ZXMPPTHUMBPATH as thumbpath, " //$NON-NLS-1$
            + "INFO.ZRECEIPTINFO as receiptInfo, " //$NON-NLS-1$
            + "(1 << 7 & ZFLAGS) as forwarded, " //$NON-NLS-1$
            + "ZWAMESSAGE.ZSTANZAID as uuid, ZWAMEDIAITEM.ZMETADATA as metadata, " //$NON-NLS-1$
            + "ZWAMEDIAITEM.ZMOVIEDURATION as duration, "
            + "ZGROUPEVENTTYPE as gEventType, ZMESSAGETYPE as messageType, ZSORT FROM ZWAMESSAGE " //$NON-NLS-1$
            + "LEFT JOIN ZWAMEDIAITEM ON ZWAMESSAGE.Z_PK = ZWAMEDIAITEM.ZMESSAGE " //$NON-NLS-1$
            + "LEFT JOIN ZWAMESSAGEINFO INFO ON INFO.Z_PK = ZWAMESSAGE.ZMESSAGEINFO " //$NON-NLS-1$
            + "LEFT JOIN ZWAGROUPMEMBER ON ZWAGROUPMEMBER.ZCHATSESSION = chatId AND ZWAGROUPMEMBER.Z_PK = ZGROUPMEMBER " //$NON-NLS-1$
            + "ORDER BY ZSORT"; //$NON-NLS-1$
    
    private static final String SELECT_MESSAGES_USER_NOZTITLE = "SELECT ZWAMESSAGE.Z_PK AS id, ZCHATSESSION " //$NON-NLS-1$
            + "as chatId, ZFROMJID AS remoteResource, ZMESSAGESTATUS AS status, ZTEXT AS data, " //$NON-NLS-1$
            + "ZISFROMME AS fromMe, datetime(ZMESSAGEDATE + 978307200, 'unixepoch') AS timestamp, " //$NON-NLS-1$
            + "ZVCARDSTRING as vCardString, ZFILESIZE as mediaSize, ZMEDIALOCALPATH " //$NON-NLS-1$
            + "as mediaName, ZVCARDNAME as mediaHash, NULL as mediaCaption, " //$NON-NLS-1$
            + "ZLATITUDE as latitude, ZLONGITUDE as longitude, ZMEDIAURL as url, ZXMPPTHUMBPATH as thumbpath, " //$NON-NLS-1$
            + "INFO.ZRECEIPTINFO as receiptInfo, " //$NON-NLS-1$
            + "(1 << 7 & ZFLAGS) as forwarded, " //$NON-NLS-1$
            + "ZWAMESSAGE.ZSTANZAID as uuid, ZWAMEDIAITEM.ZMETADATA as metadata, " //$NON-NLS-1$           
            + "ZWAMEDIAITEM.ZMOVIEDURATION as duration, "
            + "ZGROUPEVENTTYPE as gEventType, ZMESSAGETYPE as messageType, ZSORT FROM ZWAMESSAGE " //$NON-NLS-1$
            + "LEFT JOIN ZWAMEDIAITEM ON ZWAMESSAGE.Z_PK = ZWAMEDIAITEM.ZMESSAGE " //$NON-NLS-1$
            + "LEFT JOIN ZWAMESSAGEINFO INFO ON INFO.Z_PK = ZWAMESSAGE.ZMESSAGEINFO " //$NON-NLS-1$
            + "ORDER BY ZSORT"; //$NON-NLS-1$

    private static final String SELECT_MESSAGES_GROUP_NOZTITLE = "SELECT ZWAMESSAGE.Z_PK AS id, ZWAMESSAGE.ZCHATSESSION " //$NON-NLS-1$
            + "as chatId, ZMEMBERJID AS remoteResource, ZMESSAGESTATUS AS status, ZTEXT AS data, " //$NON-NLS-1$
            + "ZISFROMME AS fromMe, datetime(ZMESSAGEDATE + 978307200, 'unixepoch') AS timestamp, " //$NON-NLS-1$
            + "ZVCARDSTRING as vCardString, ZFILESIZE as mediaSize, ZMEDIALOCALPATH " //$NON-NLS-1$
            + "as mediaName, ZVCARDNAME as mediaHash, NULL as mediaCaption, " //$NON-NLS-1$
            + "ZLATITUDE as latitude, ZLONGITUDE as longitude, ZMEDIAURL as url, ZXMPPTHUMBPATH as thumbpath, " //$NON-NLS-1$
            + "INFO.ZRECEIPTINFO as receiptInfo, " //$NON-NLS-1$
            + "(1 << 7 & ZFLAGS) as forwarded, " //$NON-NLS-1$
            + "ZWAMESSAGE.ZSTANZAID as uuid, ZWAMEDIAITEM.ZMETADATA as metadata, " //$NON-NLS-1$          
            + "ZWAMEDIAITEM.ZMOVIEDURATION as duration, "
            + "ZGROUPEVENTTYPE as gEventType, ZMESSAGETYPE as messageType, ZSORT FROM ZWAMESSAGE " //$NON-NLS-1$
            + "LEFT JOIN ZWAMEDIAITEM ON ZWAMESSAGE.Z_PK = ZWAMEDIAITEM.ZMESSAGE " //$NON-NLS-1$
            + "LEFT JOIN ZWAGROUPMEMBER ON ZWAGROUPMEMBER.ZCHATSESSION = chatId AND ZWAGROUPMEMBER.Z_PK = ZGROUPMEMBER " //$NON-NLS-1$
            + "LEFT JOIN ZWAMESSAGEINFO INFO ON INFO.Z_PK = ZWAMESSAGE.ZMESSAGEINFO " //$NON-NLS-1$
            + "ORDER BY ZSORT"; //$NON-NLS-1$

    private static final String VCARD_SEPARATOR = "_$!<VCard-Separator>!$_"; //$NON-NLS-1$

    private static final Set<MessageType> MEDIA_MESSAGES = ImmutableSet.of(AUDIO_MESSAGE, VIDEO_MESSAGE, GIF_MESSAGE,
            DOC_MESSAGE, IMAGE_MESSAGE, VIEW_ONCE_AUDIO_MESSAGE, VIEW_ONCE_IMAGE_MESSAGE, VIEW_ONCE_VIDEO_MESSAGE);

    private static class WAIOSMessageValidator implements SQLiteRecordValidator {

        @Override
        public boolean validateRecord(SqliteRow row) {
            try {
                long chatSession = row.getIntValue("ZCHATSESSION"); //$NON-NLS-1$
                if (chatSession <= 0 || chatSession > Integer.MAX_VALUE) {
                    return false;
                }

                String remoteId = row.getTextValue("ZFROMJID"); //$NON-NLS-1$
                if (remoteId == null
                        || !(remoteId.endsWith(WAContact.waSuffix) || remoteId.endsWith(WAContact.waNewsletterSuffix)
                                || remoteId.endsWith(WAContact.waStatusBroadcast)
                                || remoteId.endsWith(WAContact.waStatusSuffix))) {
                    return false;
                }

                long fromMe = row.getIntValue("ZISFROMME"); //$NON-NLS-1$
                if (fromMe != 0 && fromMe != 1) {
                    return false;
                }

                long status = row.getIntValue("ZMESSAGESTATUS"); //$NON-NLS-1$
                if (status < 0 || status >= 100) {
                    return false;
                }

                long timestamp = row.getIntValue("ZMESSAGEDATE"); //$NON-NLS-1$
                timestamp += 978307200L;
                if (timestamp < 1230768000L || timestamp > 2461449600L) {
                    return false;
                }
                return true;
            } catch (Exception e) {
            }
            return false;
        }

    }

    private static class WAIOSMediaItemValidator implements SQLiteRecordValidator {

        @Override
        public boolean validateRecord(SqliteRow row) {
            try {
                long message = row.getIntValue("ZMESSAGE"); //$NON-NLS-1$
                if (message < 0) {
                    return false;
                }
                row.getTextValue("ZVCARDSTRING"); //$NON-NLS-1$
                row.getTextValue("ZMEDIALOCALPATH"); //$NON-NLS-1$
                row.getTextValue("ZTITLE"); //$NON-NLS-1$
                row.getIntValue("ZFILESIZE"); //$NON-NLS-1$
                row.getTextValue("ZXMPPTHUMBPATH"); //$NON-NLS-1$
                row.getTextValue("ZMEDIAURL"); //$NON-NLS-1$
                row.getTextValue("ZVCARDNAME"); //$NON-NLS-1$
                row.getFloatValue("ZLATITUDE"); //$NON-NLS-1$
                row.getFloatValue("ZLONGITUDE"); //$NON-NLS-1$
                return true;
            } catch (Exception e) {
            }
            return false;
        }
    }

    private static class WAIOSGroupMemberValidator implements SQLiteRecordValidator {

        @Override
        public boolean validateRecord(SqliteRow row) {
            try {
                long pk = row.getIntValue("Z_PK"); //$NON-NLS-1$
                if (pk <= 0) {
                    return false;
                }
                var memberjid = row.getTextValue("ZMEMBERJID"); //$NON-NLS-1$
                if (memberjid != null) {
                    return true;
                }
            } catch (Exception e) {
            }
            return false;
        }
    }

    private static class WAIOSChatSessionValidator implements SQLiteRecordValidator {

        @Override
        public boolean validateRecord(SqliteRow row) {
            try {
                long pk = row.getIntValue("Z_PK"); //$NON-NLS-1$
                if (pk <= 0) {
                    return false;
                }
                var contactjid = row.getTextValue("ZCONTACTJID"); //$NON-NLS-1$
                if (contactjid == null) {
                    return false;
                }
                var partnername = row.getTextValue("ZPARTNERNAME"); //$NON-NLS-1$
                if (partnername == null) {
                    return false;
                }
                return true;
            } catch (Exception e) {
            }
            return false;
        }
    }
}
