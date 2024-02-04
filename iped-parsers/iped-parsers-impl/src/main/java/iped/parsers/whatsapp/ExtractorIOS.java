package iped.parsers.whatsapp;

import static iped.parsers.whatsapp.Message.MessageType.AUDIO_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.BLOCKED_CONTACT;
import static iped.parsers.whatsapp.Message.MessageType.BUSINESS_CHANGED_NAME;
import static iped.parsers.whatsapp.Message.MessageType.BUSINESS_CHAT;
import static iped.parsers.whatsapp.Message.MessageType.BUSINESS_OFFICIAL;
import static iped.parsers.whatsapp.Message.MessageType.BUSINESS_TO_STANDARD;
import static iped.parsers.whatsapp.Message.MessageType.CHANGED_NUMBER_CHATTING_WITH_NEW;
import static iped.parsers.whatsapp.Message.MessageType.CHANGED_NUMBER_CHATTING_WITH_OLD;
import static iped.parsers.whatsapp.Message.MessageType.CONTACT_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.DELETED_BY_SENDER;
import static iped.parsers.whatsapp.Message.MessageType.DOC_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.ENCRYPTION_KEY_CHANGED;
import static iped.parsers.whatsapp.Message.MessageType.EPHEMERAL_DEFAULT;
import static iped.parsers.whatsapp.Message.MessageType.EPHEMERAL_SAVE;
import static iped.parsers.whatsapp.Message.MessageType.GIF_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_CREATED;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_ICON_CHANGED;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_ICON_DELETED;
import static iped.parsers.whatsapp.Message.MessageType.GROUP_INVITE;
import static iped.parsers.whatsapp.Message.MessageType.IMAGE_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.LOCATION_MESSAGE;
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
import static iped.parsers.whatsapp.Message.MessageType.USER_JOINED_GROUP;
import static iped.parsers.whatsapp.Message.MessageType.USER_LEFT_GROUP;
import static iped.parsers.whatsapp.Message.MessageType.USER_REMOVED_FROM_GROUP;
import static iped.parsers.whatsapp.Message.MessageType.USERS_JOINED_GROUP;
import static iped.parsers.whatsapp.Message.MessageType.VIDEO_CALL;
import static iped.parsers.whatsapp.Message.MessageType.VIDEO_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.VIEW_ONCE_IMAGE_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.VIEW_ONCE_VIDEO_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.WAITING_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.YOU_ADMIN;

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
import iped.parsers.whatsapp.Message.MessageStatus;
import iped.parsers.whatsapp.Message.MessageType;
import iped.parsers.whatsapp.ProtoBufDecoder.Part;

/**
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@pf.gov.br>
 */
public class ExtractorIOS extends Extractor {

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

    @Override
    protected List<Chat> extractChatList() throws WAExtractorException {
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
                        if (!(contactId.endsWith("@status") || contactId.endsWith("@broadcast"))) { //$NON-NLS-1$ //$NON-NLS-2$
                            WAContact remote = contacts.getContact(contactId);
                            Chat c = new Chat(remote);
                            c.setId(rs.getLong("id")); //$NON-NLS-1$
                            c.setSubject(Util.getUTF8String(rs, "subject")); //$NON-NLS-1$
                            c.setGroupChat(contactId.endsWith("g.us")); //$NON-NLS-1$
                            c.setDeleted(rs.getInt("ZREMOVED") != 0);
                            remote.setAvatarPath(rs.getString("avatarPath")); //$NON-NLS-1$
                            if (recoverDeletedRecords) {
                                activeChats.add(c.getId());
                            }
                            list.add(c);
                        }
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
                    findQuotedMessages(c.getMessages(), messagesMap);
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

            Collections.sort(chat.getMessages());
        }
    }

    private void findQuotedMessages(List<Message> messages, Map<String, Message> messagesMap) {
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
                    if (messageQuote == null) {
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

                                    default:
                                        break;
                                }
                            }
                        }

                        messageQuote.setMessageType(type);
                        messageQuote.setDeleted(true);
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
        if (remoteResource == null || remoteResource.isEmpty() || !chat.isGroupChat()) {
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
        m.setMessageType(decodeMessageType(messageType, gEventType));
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
            case EPHEMERAL_DEFAULT:
                m.setDuration(decodeEphemeralDuration(metadata));
                break;

            case TEMPLATE_MESSAGE:
                m.setMessageTemplate(decodeTemplate(metadata));
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

    private MessageTemplate decodeTemplate(byte[] metadata) {
        MessageTemplate t = null;
        Part p1 = new ProtoBufDecoder(metadata).decode(24);
        if (p1 != null) {
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
                    Part p3 = p2.getChild(8);
                    if (p3 != null) {
                        Part p4 = p3.getChild(2);
                        if (p4 != null) {
                            Part p5 = p4.getChild(1);
                            String text = "";
                            if (p5 != null) {
                                text = p5.getString();
                            }
                            p5 = p4.getChild(2);
                            String extra = "";
                            if (p5 != null) {
                                extra = p5.getString();
                            }
                            t.addButton(new MessageTemplate.Button(text, extra));
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

    private Message createMessageFromUndeletedRecord(SqliteRow row, Chat chat, Map<Long, SqliteRow> mediaItems,
            Map<Long, SqliteRow> groupMemebers) throws SQLException {
        Message m = new Message();
        if (account != null)
            m.setLocalResource(account.getId());
        m.setId(row.getIntValue("Z_PK")); //$NON-NLS-1$
        String remoteResource = null;
        if (chat.isGroupChat()) {
            long groupMemberID = row.getIntValue("ZGROUPMEMBER"); //$NON-NLS-1$
            SqliteRow groupMemberRow = groupMemebers.get(groupMemberID);
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
        m.setMessageType(decodeMessageType(messageType, gEventType));
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
                if (!(contactId.endsWith("@status") || contactId.endsWith("@broadcast"))) { //$NON-NLS-1$ //$NON-NLS-2$
                    WAContact contact = contacts.getContact(contactId);
                    Chat c = new Chat(contact);
                    c.setId(row.getIntValue("Z_PK")); //$NON-NLS-1$
                    c.setDeleted(row.getIntValue("ZREMOVED") != 0 || row.isDeletedRow());
                    c.setSubject(row.getTextValue("ZPARTNERNAME")); //$NON-NLS-1$
                    c.setGroupChat(contactId.endsWith("g.us")); //$NON-NLS-1$
                    result.add(c);
                }
            }
        }

        return result;
    }

    protected Message.MessageType decodeMessageType(int messageType, int gEventType) {
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
                if (gEventType == 12) {
                    result = GROUP_CREATED;
                } else if (gEventType == 2) {
                    result = USER_JOINED_GROUP;
                } else if (gEventType == 3) {
                    result = USER_LEFT_GROUP;
                } else if (gEventType == 7) {
                    result = USER_REMOVED_FROM_GROUP; // sender o removido, data quem removeu
                } else if (gEventType == 50) {
                    result = USERS_JOINED_GROUP;
                } else if (gEventType == 4) {
                    result = GROUP_ICON_CHANGED;
                } else if (gEventType == 5) {
                    result = GROUP_ICON_DELETED;
                } else if (gEventType == 10) {
                    result = YOU_ADMIN;
                }
                // 6 / 14 (provavelmente : você foi removido do grupo)
                // 6 / 9 (pode ser autorizacao de adm de grupo)
                break;
            case 7:
                result = URL_MESSAGE;
                break;
            case 8:
                result = DOC_MESSAGE;
                break;
            case 10:
                if (gEventType == 2) {
                    result = MESSAGES_NOW_ENCRYPTED;
                } else if (gEventType == 1 || gEventType == 45) {
                    result = MISSED_VOICE_CALL;
                } else if (gEventType == 3) {
                    result = ENCRYPTION_KEY_CHANGED;
                } else if (gEventType == 4 || gEventType == 46) {
                    result = MISSED_VIDEO_CALL;
                } else if (gEventType == 5) {
                    result = CHANGED_NUMBER_CHATTING_WITH_OLD;
                } else if (gEventType == 6) {
                    result = CHANGED_NUMBER_CHATTING_WITH_NEW;
                } else if (gEventType == 22) {
                    // Missed *group* video call
                    result = MISSED_VIDEO_CALL;
                } else if (gEventType == 26) {
                    result = BUSINESS_CHAT;
                } else if (gEventType == 29) {
                    result = BUSINESS_CHANGED_NAME;
                } else if (gEventType == 30) {
                    result = BUSINESS_TO_STANDARD;
                } else if (gEventType == 34) {
                    result = BLOCKED_CONTACT;
                } else if (gEventType == 35) {
                    result = UNBLOCKED_CONTACT;
                } else if (gEventType == 25 || gEventType == 38) {
                    result = BUSINESS_OFFICIAL;
                } else if (gEventType == 40 || gEventType == 41) {
                    // Started a video call (group)
                    result = VIDEO_CALL;
                } else if (gEventType == 47) {
                    result = EPHEMERAL_SAVE;
                } else if (gEventType == 56) {
                    result = SENDER_ADDED_TO_CONTACTS;
                }
                // 10 / 13 -> desconhecida (aparece algumas vezes depois de informado conversa
                // segura com nome do interlocutor)
                // 10 / (9, 10, 14 ou 16) -> desconhecida (aparece algumas vezes depois de
                // mudança de código com nome do interlocutor)
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
            case 32:
                // Forwarded template message. Handle as TEXT seems enough to show its content.
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
        }
        return result;
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
            DOC_MESSAGE, IMAGE_MESSAGE);

    private static class WAIOSMessageValidator implements SQLiteRecordValidator {

        @Override
        public boolean validateRecord(SqliteRow row) {
            try {
                long chatSession = row.getIntValue("ZCHATSESSION"); //$NON-NLS-1$
                if (chatSession <= 0 || chatSession > Integer.MAX_VALUE) {
                    return false;
                }

                String remoteId = row.getTextValue("ZFROMJID"); //$NON-NLS-1$
                if (remoteId == null || !(remoteId.endsWith("whatsapp.net") || remoteId.endsWith("g.us"))) { //$NON-NLS-1$ //$NON-NLS-2$
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
