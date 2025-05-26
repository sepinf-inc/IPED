package iped.parsers.whatsapp;

import static iped.parsers.whatsapp.Message.MessageType.AUDIO_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.BUSINESS_CHAT;
import static iped.parsers.whatsapp.Message.MessageType.CONTACT_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.DELETED_BY_ADMIN;
import static iped.parsers.whatsapp.Message.MessageType.DELETED_BY_SENDER;
import static iped.parsers.whatsapp.Message.MessageType.DELETED_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.DOC_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.ENCRYPTION_KEY_CHANGED;
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
import static iped.parsers.whatsapp.Message.MessageType.UNKNOWN_MEDIA_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.UNKNOWN_MESSAGE;
import static iped.parsers.whatsapp.Message.MessageType.USER_ADDED_TO_GROUP;
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
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fqlite.base.SqliteRow;
import iped.parsers.sqlite.SQLite3DBParser;
import iped.parsers.sqlite.SQLiteRecordValidator;
import iped.parsers.sqlite.SQLiteUndelete;
import iped.parsers.sqlite.SQLiteUndeleteTable;
import iped.parsers.sqlite.SQLiteUndeleteTableResultSetAdapter;
import iped.parsers.whatsapp.Message.MessageQuotedType;
import iped.parsers.whatsapp.Message.MessageStatus;

/**
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@pf.gov.br>
 */
public abstract class ExtractorAndroid extends Extractor {

    private static Logger logger = LoggerFactory.getLogger(ExtractorAndroid.class);

    private boolean hasSortTimestamp = false;
    private boolean hasThumbTable = true;
    private boolean hasEditVersionCol = false;
    private boolean hasChatView = true;
    private boolean hasMediaCaptionCol = false;
    private boolean hasSubjectCol = false;
    private boolean hasMediaDurationCol = false;
    private boolean hasGroupParticiantsTable = true;
    private boolean hasForwardedCol = false;
    private boolean hasQuoteCol = false;
    private SQLException parsingException = null;

    public ExtractorAndroid(String itemPath, File databaseFile, WAContactsDirectory contacts, WAAccount account, boolean recoverDeletedRecords) {
        super(itemPath, databaseFile, contacts, account, recoverDeletedRecords);
    }

    protected abstract Connection getConnection() throws SQLException;

    @Override
    protected List<Chat> extractChatList() throws WAExtractorException {
        List<Chat> list;

        SQLiteUndeleteTable undeletedMessagesTable = null;
        SQLiteUndeleteTable undeleteChatTable = null;
        SQLiteUndeleteTable undeleteChatListTable = null;
        SQLiteUndeleteTable undeleteJIDTable = null;
        
        // control retry parsing database in case of corrupted db
        // if database is corrupted, maybe recovering deleted data can
        // retrieve partial data
        boolean firstTry;
        boolean tryAgain;

        if (recoverDeletedRecords) {
            try {
                SQLiteUndelete undelete = new SQLiteUndelete(databaseFile.toPath());
                undelete.addTableToRecover("messages"); //$NON-NLS-1$
                undelete.addRecordValidator("messages", new WAAndroidMessageValidator()); //$NON-NLS-1$
                undelete.addTableToRecover("chat"); //$NON-NLS-1$
                undelete.addRecordValidator("chat", new WAAndroidChatValidator()); //$NON-NLS-1$
                undelete.addTableToRecover("jid"); //$NON-NLS-1$
                undelete.addRecordValidator("jid", new WAAndroidJIDValidator()); //$NON-NLS-1$
                undelete.addTableToRecover("chat_list"); //$NON-NLS-1$
                undelete.addRecordValidator("chat_list", new WAAndroidChatListValidator()); //$NON-NLS-1$
                undelete.setRecoverOnlyDeletedRecords(false);

                var undeleteData = undelete.undeleteData();
                undeletedMessagesTable = undeleteData.get("messages"); //$NON-NLS-1$
                undeleteChatTable = undeleteData.get("chat"); //$NON-NLS-1$
                undeleteChatListTable = undeleteData.get("chat_list"); //$NON-NLS-1$
                undeleteJIDTable = undeleteData.get("jid"); //$NON-NLS-1$

            } catch (Exception e) {
                logger.warn("Error recovering deleted records from Android WhatsApp Database " + itemPath, e); //$NON-NLS-1$
            }
        }

        Map<String, List<SqliteRow>> undeletedMessages = undeletedMessagesTable == null ?
                Collections.emptyMap()
                : undeletedMessagesTable.getTableRowsGroupedByTextCol("key_remote_jid"); //$NON-NLS-1$

        List<Chat> undeletedChats = recoverDeletedRecords ?
                undeleteChats(undeleteChatListTable, undeleteChatTable, undeleteJIDTable, contacts)
                : Collections.emptyList();

        Set<String> activeChats = new HashSet<>();

        parsingException = null;
        firstTry = recoverDeletedRecords; // if recovery of deleted messages is disable, go straight to the second try

        do {
            list = new ArrayList<>();
            tryAgain = false;

            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                try {
                    hasSortTimestamp = databaseHasSortTimestamp(conn);
                    hasChatView = databaseHasChatView(conn);
                    hasThumbTable = SQLite3DBParser.containsTable("message_thumbnails", conn); //$NON-NLS-1$
                    hasGroupParticiantsTable = SQLite3DBParser.containsTable("group_participants", conn); //$NON-NLS-1$
                    hasEditVersionCol = SQLite3DBParser.checkIfColumnExists(conn, "messages", "edit_version"); //$NON-NLS-1$ //$NON-NLS-2$
                    hasMediaCaptionCol = SQLite3DBParser.checkIfColumnExists(conn, "messages", "media_caption"); //$NON-NLS-1$ //$NON-NLS-2$
                    hasMediaDurationCol = SQLite3DBParser.checkIfColumnExists(conn, "messages", "media_duration"); //$NON-NLS-1$ //$NON-NLS-2$
                    hasForwardedCol = SQLite3DBParser.checkIfColumnExists(conn, "messages", "forwarded"); //$NON-NLS-1$ //$NON-NLS-2$
                    hasQuoteCol = SQLite3DBParser.checkIfColumnExists(conn, "messages", "quoted_row_id"); 
                    if (!hasChatView) {
                        hasSubjectCol = SQLite3DBParser.checkIfColumnExists(conn, "chat_list", "subject"); //$NON-NLS-1$ //$NON-NLS-2$
                    }
                } catch (SQLException e) {
                    if (firstTry || !isSqliteCorruptException(e)) {
                        throw e;
                    } else if (parsingException == null) {
                       parsingException = e;
                    }
                }

                String selectChatQuery;
                if (hasChatView) {
                    selectChatQuery = SELECT_CHAT_VIEW;
                } else if (hasSortTimestamp) {
                    selectChatQuery = SELECT_CHAT_LIST;
                } else if (hasSubjectCol) {
                    selectChatQuery = SELECT_CHAT_LIST_NO_SORTTIMESTAMP;
                } else {
                    selectChatQuery = SELECT_CHAT_LIST_NO_SUBJECT;
                }

                try (ResultSet rs = stmt.executeQuery(selectChatQuery)) {

                    while (rs.next()) {
                        String contactId = rs.getString("contact"); //$NON-NLS-1$
                        WAContact remote = contacts.getContact(contactId);
                        Chat c = new Chat(remote);
                        c.setId(rs.getLong("id")); //$NON-NLS-1$
                        c.setSubject(Util.getUTF8String(rs, "subject")); //$NON-NLS-1$
                        c.setGroupChat(contactId.endsWith("g.us")); //$NON-NLS-1$
                        if (recoverDeletedRecords) {
                            activeChats.add(contactId);
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
                    String remoteId = c.getRemote().getId();
                    remoteId += c.isGroupChat() ? "@g.us" : WAContact.waSuffix;
                    if (!activeChats.contains(remoteId)) {
                        list.add(c);
                        if (firstTry && c.isDeleted()) {
                            logger.info("Recovered deleted chat for database " //$NON-NLS-1$
                                        + itemPath + " :" + c.getSubject() //$NON-NLS-1$
                                        + " (" + c.getRemote().getFullId() + ")"); //$NON-NLS-1$ //$NON-NLS-2$
                        }
                    }
                }

                for (Chat c : list) {
                    c.setMessages(extractMessages(conn, c.getRemote(), c.isGroupChat(), undeletedMessages,
                            undeletedMessagesTable, hasThumbTable, hasEditVersionCol, firstTry));
                    if (c.isGroupChat()) {
                        try {
                            setGroupMembers(c, conn, hasGroupParticiantsTable ? SELECT_GROUP_MEMBERS : null);
                        } catch (SQLException ex) {
                            if (firstTry || !isSqliteCorruptException(ex)) {
                                throw ex;
                            } else if (parsingException == null) {
                                parsingException = ex;
                            }
                        }
                    }
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
                    // if recovery of deleted records is enabled and failed with SQLITE_CORRUPT on first try,
                    // try again, ignoring error and recovering deleted records
                    if (isSqliteCorruptException(ex)) {
                        tryAgain = true;
                        logger.warn("Database " + itemPath + " is corrupt. Trying to recover data with fqlite"); //$NON-NLS-1$ //$NON-NLS-2$
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

    private List<Chat> undeleteChats(SQLiteUndeleteTable undeleteChatListTable,
            SQLiteUndeleteTable undeleteChatTable, SQLiteUndeleteTable undeleteJIDTable, WAContactsDirectory contacts) {
        List<Chat> result = new ArrayList<>();
        
        if (undeleteChatListTable != null && undeleteChatListTable.getTableRows() != null && !undeleteChatListTable.getTableRows().isEmpty()) {
            // this is the case of a database with the table "chat_list"
            
            for (SqliteRow row : undeleteChatListTable.getTableRows()) {
                String contactId = row.getTextValue("key_remote_jid"); //$NON-NLS-1$
                WAContact contact = contacts.getContact(contactId);
                Chat c = new Chat(contact);
                c.setId(row.getIntValue("_id"));
                c.setDeleted(row.isDeletedRow());
                c.setSubject(row.getTextValue("subject")); //$NON-NLS-1$
                c.setGroupChat(contactId.endsWith("g.us")); //$NON-NLS-1$
                result.add(c);
            }
        } else if (undeleteChatTable != null && undeleteChatTable.getTableRows() != null && !undeleteChatTable.getTableRows().isEmpty()) {
            if (undeleteJIDTable != null && undeleteJIDTable.getTableRows() != null && !undeleteJIDTable.getTableRows().isEmpty()) {
                // this is the case of a database with the view chat_view, that joins the tables chat and jid
                // in this case the join has to be done in java, instead of SQL
                
                var jid_rows = undeleteJIDTable.getTableRowsGroupedByLongCol("_id"); //$NON-NLS-1$
                for (SqliteRow row : undeleteChatTable.getTableRows()) {
                    long jid_row_id = row.getIntValue("jid_row_id"); //$NON-NLS-1$
                    if (jid_rows.containsKey(jid_row_id)) {
                        SqliteRow jid_row = jid_rows.get(jid_row_id).get(0);
                        String contactId = jid_row.getTextValue("raw_string"); //$NON-NLS-1$
                        WAContact contact = contacts.getContact(contactId);
                        Chat c = new Chat(contact);
                        c.setId(row.getIntValue("_id"));
                        c.setDeleted(true);
                        c.setSubject(row.getTextValue("subject")); //$NON-NLS-1$
                        c.setGroupChat(contactId.endsWith("g.us")); //$NON-NLS-1$
                        result.add(c);
                    }
                }
            }
        }
        
        return result;
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

    private boolean databaseHasChatView(Connection conn) throws SQLException {
        boolean result = false;
        DatabaseMetaData md = conn.getMetaData();
        try (ResultSet rs = md.getColumns(null, null, "chat_view", "raw_string_jid")) { //$NON-NLS-1$ //$NON-NLS-2$
            if (rs.next()) {
                result = true;
            }
        }
        return result;
    }

    private List<Message> extractMessages(Connection conn, WAContact remote, boolean isGroupChat,
            Map<String, List<SqliteRow>> undeletedMessages, SQLiteUndeleteTable undeleteTable, boolean hasThumbTable,
            boolean hasEditVersionCol, boolean firstTry) throws SQLException {
        List<Message> messages = new ArrayList<>();
        
        boolean recoverDeleted = undeleteTable != null && !undeletedMessages.isEmpty();

        String id = remote.getId();
        id += isGroupChat ? "@g.us" : WAContact.waSuffix;
        
        Set<MessageWrapperForDuplicateRemoval> activeMessages = new HashSet<>();
        Map<Long, Message> activeMessageIds = new HashMap<>();

        List<Message> messagesQuotes = new ArrayList<>();
        HashMap<String, Message> messagesMapUuid = new HashMap<String, Message>();
        HashMap<Long, Message> messagesMapIdQuote = new HashMap<Long, Message>();

        if (hasQuoteCol){
            String queryQuote = getMessagesQueryQuote(hasThumbTable, hasEditVersionCol, hasMediaDurationCol, hasMediaCaptionCol);

            try (PreparedStatement stmt = conn.prepareStatement(queryQuote)) {
                stmt.setFetchSize(1000);
                stmt.setString(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Message m = createMessageFromDBRow(rs, remote, isGroupChat, false, hasThumbTable, hasEditVersionCol);
                        messagesQuotes.add(m);   
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

        String query = getMessagesQuery(hasThumbTable, hasEditVersionCol, hasMediaDurationCol, hasMediaCaptionCol, hasForwardedCol, hasQuoteCol);

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setFetchSize(1000);
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Message m = createMessageFromDBRow(rs, remote, isGroupChat, false, hasThumbTable, hasEditVersionCol);
                    if (recoverDeleted) {
                        activeMessages.add(new MessageWrapperForDuplicateRemoval(m));
                        activeMessageIds.put(m.getId(), m);
                    }
                    messagesMapIdQuote.put(m.getIdQuote(),m);
                    if (m.getUuid() != null && !m.getUuid().isEmpty()) {
                        messagesMapUuid.put(m.getUuid(), m);
                    }                    
                    messages.add(m);
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

        if (recoverDeleted) {
            // get deleted messages
            SQLiteUndeleteTableResultSetAdapter rs = new SQLiteUndeleteTableResultSetAdapter(
                    undeletedMessages.getOrDefault(id, Collections.emptyList()), undeleteTable.getColumnNames(),
                    MESSAGES_TABLE_COL_MAP);
            while (rs.next()) {
                try {
                    if (!firstTry || rs.getCurrentRow().isDeletedRow()) {
                    Message m = createMessageFromDBRow(rs, remote, isGroupChat, rs.getCurrentRow().isDeletedRow(), hasThumbTable, hasEditVersionCol);

                    if (!activeMessages.contains(new MessageWrapperForDuplicateRemoval(m))) { //do not include deleted message if already there
                        if (!activeMessageIds.containsKey(m.getId()) ||
                            !compareMessagesAlmostTheSame(activeMessageIds.get(m.getId()), m)) { //also remove messages with same id that have the same start text (possibly corrupted recovered record)
                            messagesMapIdQuote.put(m.getIdQuote(),m);
                            if (m.getUuid() != null && !m.getUuid().isEmpty()) {
                                messagesMapUuid.put(m.getUuid(), m);
                            }                            
                            messages.add(m);
                        }
                    }}
                } catch (SQLException e) {
                    logger.warn("Error creating undeleted message", e); //$NON-NLS-1$
                } catch (RuntimeException e) {
                    logger.warn("Error creating undeleted message", e); //$NON-NLS-1$
                }
            }
    
            Message.sort(messages);
        }

        //Find quote messages
        long fakeIds = 2000000000L;
        for (Message mq: messagesQuotes){
            Message m = messagesMapIdQuote.get(mq.getId());
            if (m != null){// Has quote
                Message original = messagesMapUuid.get(mq.getUuid());//Try to find orginal message in messages
                if (original != null){// has found original message reference, more complete
                    original.setMessageQuotedType(MessageQuotedType.QUOTE_FOUND);
                    m.setMessageQuote(original);
                }else{// not found original message reference, get info from message_quotes table, less complete
                    mq.setMessageQuotedType(MessageQuotedType.QUOTE_NOT_FOUND);
                    mq.setId(fakeIds--);
                    m.setMessageQuote(mq);
                }
                m.setQuoted(true);
            }
        }
        messagesMapIdQuote.clear();
        messagesMapUuid.clear();

        return messages;
    }

    private Message createMessageFromDBRow(ResultSet rs, WAContact remote, boolean isGroupChat, boolean deleted,
            boolean hasThumbTable, boolean hasEditVersionCol) throws SQLException {
        Message m = new Message();
        if (account != null)
            m.setLocalResource(account.getId());
        int type = rs.getInt("messageType"); //$NON-NLS-1$
        int status = rs.getInt("status"); //$NON-NLS-1$
        String caption = SQLite3DBParser.getStringIfExists(rs, "mediaCaption"); //$NON-NLS-1$
        String str = null;
        try {
            str = SQLite3DBParser.getStringIfExists(rs, "edit_version"); //$NON-NLS-1$
        } catch (IndexOutOfBoundsException ignore) {
            // ignore possible error in retrieving this field from undeleted records
        }
        Integer edit_version = str != null ? Integer.parseInt(str) : null;
        long media_size = rs.getLong("mediaSize"); //$NON-NLS-1$
        m.setId(rs.getLong("id")); //$NON-NLS-1$
        String remoteResource = rs.getString("remoteResource"); //$NON-NLS-1$
        if (remoteResource == null || remoteResource.isEmpty() || !isGroupChat) {
            remoteResource = remote.getFullId();
        }
        m.setRemoteResource(remoteResource); // $NON-NLS-1$
        m.setStatus(status); // $NON-NLS-1$
        m.setData(rs.getString("data")); //$NON-NLS-1$
        m.setFromMe(rs.getInt("fromMe") == 1); //$NON-NLS-1$
        m.setTimeStamp(new Date(rs.getLong("timestamp"))); //$NON-NLS-1$
        m.setMediaUrl(rs.getString("mediaUrl")); //$NON-NLS-1$
        m.setMediaMime(rs.getString("mediaMime")); //$NON-NLS-1$
        m.setMediaName(rs.getString("mediaName")); //$NON-NLS-1$
        m.setMediaCaption(caption); // $NON-NLS-1$
        try {
            m.setMediaHash(rs.getString("mediaHash"), true); //$NON-NLS-1$
        } catch (IllegalArgumentException ignore) {
            // ignore possible failure in base64 decoding due to garbage from undeleted records
        }
        m.setMediaSize(media_size);
        m.setLatitude(rs.getDouble("latitude")); //$NON-NLS-1$
        m.setLongitude(rs.getDouble("longitude")); //$NON-NLS-1$
        m.setMessageType(decodeMessageType(type, status, edit_version, caption, (int) media_size));
        m.setDuration(SQLite3DBParser.getIntIfExists(rs, "media_duration")); //$NON-NLS-1$
        if (m.getMessageType() == CONTACT_MESSAGE) {
            m.setVcards(Arrays.asList(new String[] { m.getData() }));
        }
        byte[] thumbData = null;
        if (hasThumbTable) {
            try {
                thumbData = rs.getBytes("thumbData"); //$NON-NLS-1$
            } catch (SQLException e) {
            }
        }

        if (thumbData == null) {
            try {
                thumbData = rs.getBytes("rawData"); //$NON-NLS-1$
            } catch (SQLException e) {
            }
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
        if (m.getMessageType() == TEXT_MESSAGE && m.getData() == null) {
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
        m.setDeleted(deleted);
        m.setForwarded(hasForwardedCol && (rs.getInt("forwarded") > 0));

        if (hasQuoteCol){
            m.setIdQuote(rs.getInt("quoted_row_id"));
            m.setUuid(rs.getString("key_id"));
        }

        return m;
    }

    protected Message.MessageType decodeMessageType(int messageType, int status, Integer edit_version, String caption,
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
                            result = USER_ADDED_TO_GROUP;
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
                        case 67:
                            result = MESSAGES_ENCRYPTED;
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
                result = DOC_MESSAGE;
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
            case 64:
                if (status == 0) {
                    result = DELETED_BY_ADMIN;
                }
                break;
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

    private static final String SELECT_CHAT_VIEW = "SELECT _id as id, raw_string_jid AS contact," //$NON-NLS-1$
            + " subject, created_timestamp as creation, sort_timestamp FROM chat_view ORDER BY sort_timestamp DESC"; //$NON-NLS-1$
    
    private static final String SELECT_CHAT_LIST_NO_SUBJECT = "SELECT _id as id,key_remote_jid AS contact, " //$NON-NLS-1$
            + " null as subject, 1230768000000 as creation FROM chat_list"; //$NON-NLS-1$

    private static String getMessagesQuery(boolean hasThumbTable, boolean hasEditVersion, boolean hasMediaDuration, boolean hasMediaCaption, boolean hasForwarded, boolean hasQuote) {
        String query;
        if (hasThumbTable) {
            query = SELECT_MESSAGES_THUMBS_TABLE;
        } else {
            query = SELECT_MESSAGES_NO_THUMBS_TABLE;
        }
        if (!hasEditVersion) {
            query = query.replace("edit_version, ", "");
        }
        if (!hasMediaDuration) {
            query = query.replace("media_duration, ", "");
        }
        if (!hasMediaCaption) {
            query = query.replace("media_caption as mediaCaption, ", "null as mediaCaption, ");
        }
        if (!hasForwarded) {
            query = query.replace("(forwarded & 1) as forwarded, ", "");
        }
        if (!hasQuote) {
            query = query.replace("quoted_row_id, messages.key_id, ", "");
        }
        return query;
    }

    private static String getMessagesQueryQuote(boolean hasThumbTable, boolean hasEditVersion, boolean hasMediaDuration, boolean hasMediaCaption) {
        String query;
        if (hasThumbTable) {
            query = SELECT_QUOTES_THUMBS_TABLE;
        } else {
            query = SELECT_QUOTES_NO_THUMBS_TABLE;
        }
        if (!hasEditVersion) {
            query = query.replace("edit_version, ", "");
        }
        if (!hasMediaDuration) {
            query = query.replace("media_duration, ", "");
        }
        if (!hasMediaCaption) {
            query = query.replace("media_caption as mediaCaption, ", "null as mediaCaption, ");
        }
        return query;
    }

    /*
     * Filtragem por status de mensagem (status): -1 - mensagens de sistema 0 -
     * mensagens 1 - ? 4 - mensagens 5 - mensagens 6 - ligacao / audio 7 - mensagens
     * 8 - audio enviado 10 - audio recebido 12 - mensagens 13 - mensagens
     */
    private static final String SELECT_MESSAGES_NO_THUMBS_TABLE = "SELECT _id AS id, key_remote_jid " //$NON-NLS-1$
            + "as remoteId, remote_resource AS remoteResource, status, data, " //$NON-NLS-1$
            + "key_from_me as fromMe, timestamp, media_url as mediaUrl, " //$NON-NLS-1$
            + "media_mime_type as mediaMime, media_size as mediaSize, media_name as mediaName, " //$NON-NLS-1$
            + "media_wa_type as messageType, null as thumbData, latitude, longitude, " //$NON-NLS-1$
            + "edit_version, " //$NON-NLS-1$
            + "media_duration, " //$NON-NLS-1$
            + "media_caption as mediaCaption, " //$NON-NLS-1$
            + "(forwarded & 1) as forwarded, " //$NON-NLS-1$
            + "quoted_row_id, messages.key_id, " //$NON-NLS-1$
            + "media_hash as mediaHash, raw_data as rawData FROM " //$NON-NLS-1$
            + "messages WHERE remoteId=? and status!=-1 ORDER BY timestamp"; //$NON-NLS-1$

    private static final String SELECT_MESSAGES_THUMBS_TABLE = "SELECT _id AS id, messages.key_remote_jid " //$NON-NLS-1$
            + "as remoteId, remote_resource AS remoteResource, status, data, " //$NON-NLS-1$
            + "messages.key_from_me as fromMe, messages.timestamp as timestamp, media_url as mediaUrl, " //$NON-NLS-1$
            + "media_mime_type as mediaMime, media_size as mediaSize, media_name as mediaName, " //$NON-NLS-1$
            + "media_wa_type as messageType, raw_data as rawData, latitude, longitude, " //$NON-NLS-1$
            + "edit_version, " //$NON-NLS-1$
            + "media_duration, " //$NON-NLS-1$
            + "media_caption as mediaCaption, " //$NON-NLS-1$
            + "(forwarded & 1) as forwarded, " //$NON-NLS-1$
            + "quoted_row_id, messages.key_id, " //$NON-NLS-1$          
            + "media_hash as mediaHash, thumbnail as thumbData FROM " //$NON-NLS-1$          
            + "messages LEFT JOIN message_thumbnails ON (messages.key_id = message_thumbnails.key_id " //$NON-NLS-1$
            + "AND messages.key_remote_jid = message_thumbnails.key_remote_jid " //$NON-NLS-1$
            + "AND messages.key_from_me = message_thumbnails.key_from_me) " //$NON-NLS-1$
            + "WHERE remoteId=? and status!=-1 ORDER BY timestamp"; //$NON-NLS-1$

    // to address a field must use ` instead of '
    private static final String SELECT_GROUP_MEMBERS = "select gjid as 'group', jid as member FROM group_participants where `group`=?"; //$NON-NLS-1$

    private static final String SELECT_QUOTES_NO_THUMBS_TABLE = "SELECT mq._id AS id, mq.key_remote_jid "
            + "as remoteId, mq.remote_resource AS remoteResource, mq.status, mq.data, "
            + "mq.key_from_me as fromMe, mq.timestamp, mq.media_url as mediaUrl, "
            + "mq.media_mime_type as mediaMime, mq.media_size as mediaSize, mq.media_name as mediaName, "
            + "mq.media_wa_type as messageType, null as thumbData, mq.latitude, mq.longitude, "
            + "mq.edit_version, "
            + "mq.media_duration, "
            + "mq.media_caption as mediaCaption, "
            + "null as forwarded, " //$NON-NLS-1$            
            + "mq.quoted_row_id, mq.key_id, "         
            + "mq.media_hash as mediaHash, mq.raw_data as rawData FROM messages_quotes mq "
            + "WHERE remoteId=? and mq.status!=-1 ORDER BY mq.timestamp";

    private static final String SELECT_QUOTES_THUMBS_TABLE = "SELECT mq._id AS id, mq.key_remote_jid " //$NON-NLS-1$
            + "as remoteId, mq.remote_resource AS remoteResource, mq.status, mq.data, " //$NON-NLS-1$
            + "mq.key_from_me as fromMe, mq.timestamp as timestamp, mq.media_url as mediaUrl, " //$NON-NLS-1$
            + "mq.media_mime_type as mediaMime, mq.media_size as mediaSize, mq.media_name as mediaName, " //$NON-NLS-1$
            + "mq.media_wa_type as messageType, mq.raw_data as rawData, mq.latitude, mq.longitude, " //$NON-NLS-1$
            + "mq.edit_version, " //$NON-NLS-1$
            + "mq.media_duration, " //$NON-NLS-1$
            + "mq.media_caption as mediaCaption, " //$NON-NLS-1$
            + "null as forwarded, " //$NON-NLS-1$             
            + "mq.quoted_row_id, mq.key_id, " //$NON-NLS-1$          
            + "mq.media_hash as mediaHash, thumbnail as thumbData FROM messages_quotes mq " //$NON-NLS-1$         
            + "LEFT JOIN message_thumbnails ON (mq.key_id = message_thumbnails.key_id " //$NON-NLS-1$
            + "AND mq.key_remote_jid = message_thumbnails.key_remote_jid " //$NON-NLS-1$
            + "AND mq.key_from_me = message_thumbnails.key_from_me) " //$NON-NLS-1$
            + "WHERE remoteId=? and mq.status!=-1 ORDER BY mq.timestamp"; //$NON-NLS-1$

    private static final Map<String, String> MESSAGES_TABLE_COL_MAP = new HashMap<>();

    static {
        MESSAGES_TABLE_COL_MAP.put("id", "_id");
        MESSAGES_TABLE_COL_MAP.put("remoteId", "key_remote_jid");
        MESSAGES_TABLE_COL_MAP.put("remoteResource", "remote_resource");
        MESSAGES_TABLE_COL_MAP.put("fromMe", "key_from_me");
        MESSAGES_TABLE_COL_MAP.put("mediaUrl", "media_url");
        MESSAGES_TABLE_COL_MAP.put("mediaMime", "media_mime_type");
        MESSAGES_TABLE_COL_MAP.put("mediaSize", "media_size");
        MESSAGES_TABLE_COL_MAP.put("mediaName", "media_name");
        MESSAGES_TABLE_COL_MAP.put("messageType", "media_wa_type");
        MESSAGES_TABLE_COL_MAP.put("rawData", "raw_data");
        MESSAGES_TABLE_COL_MAP.put("mediaCaption", "media_caption");
        MESSAGES_TABLE_COL_MAP.put("mediaHash", "media_hash");
        MESSAGES_TABLE_COL_MAP.put("thumbData", "thumbnail");
    }

    private static class WAAndroidMessageValidator implements SQLiteRecordValidator {

        @Override
        public boolean validateRecord(SqliteRow row) {
            try {
                String remoteId = row.getTextValue("key_remote_jid");
                if (remoteId == null || !(remoteId.endsWith("whatsapp.net") || remoteId.endsWith("g.us"))) {
                    return false;
                }

                long fromMe = row.getIntValue("key_from_me");
                if (fromMe != 0 && fromMe != 1) {
                    return false;
                }

                long status = row.getIntValue("status");
                if (status < 0 || status >= 100) {
                    return false;
                }

                long timestamp = row.getIntValue("timestamp");
                if (timestamp < 1230768000000L || timestamp > 2461449600000L) {
                    return false;
                }
                return true;
            } catch (Exception e) {
            }
            return false;
        }

    }
    
    private static class WAAndroidChatListValidator implements SQLiteRecordValidator {
 
        @Override
        public boolean validateRecord(SqliteRow row) {
            try {
                long _id = row.getIntValue("_id");
                if (_id <= 0 ) {
                    return false;
                }

                String remoteId = row.getTextValue("key_remote_jid");
                if (remoteId == null || !(remoteId.endsWith("whatsapp.net") || remoteId.endsWith("g.us"))) {
                    return false;
                }

                String subject = row.getTextValue("subject");
                if (subject == null || subject.isBlank()) {
                    return false;
                }

                long creation = row.getIntValue("creation");
                if (creation < 1230768000000L || creation > 2461449600000L) {
                    return false;
                }
                return true;
            } catch (Exception e) {
            }
            return false;
        }

    }
    
    private static class WAAndroidChatValidator implements SQLiteRecordValidator {
        
        @Override
        public boolean validateRecord(SqliteRow row) {
            try {
                long _id = row.getIntValue("_id");
                if (_id <= 0 ) {
                    return false;
                }

                long jid_row_id = row.getIntValue("jid_row_id");
                if (jid_row_id <= 0 ) {
                    return false;
                }

                String subject = row.getTextValue("subject");
                if (subject == null || subject.isBlank()) {
                    return false;
                }

                long creation = row.getIntValue("creation_timestamp");
                if (creation < 1230768000000L || creation > 2461449600000L) {
                    return false;
                }
                return true;
            } catch (Exception e) {
            }
            return false;
        }

    }
    
    private static class WAAndroidJIDValidator implements SQLiteRecordValidator {
        
        @Override
        public boolean validateRecord(SqliteRow row) {
            try {
                long _id = row.getIntValue("_id");
                if (_id <= 0 ) {
                    return false;
                }

                String raw_string = row.getTextValue("raw_string");
                if (raw_string == null || !(raw_string.endsWith("whatsapp.net") || raw_string.endsWith("g.us"))) {
                    return false;
                }

                return true;
            } catch (Exception e) {
            }
            return false;
        }

    }
}
