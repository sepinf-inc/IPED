package dpf.mg.udi.gpinf.whatsappextractor;

import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.APP_MESSAGE;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.AUDIO_MESSAGE;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.CONTACT_MESSAGE;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.DELETED_FROM_SENDER;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.ENCRIPTION_KEY_CHANGED;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.GIF_MESSAGE;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.GROUP_CREATED;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.GROUP_ICON_CHANGED;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.GROUP_ICON_DELETED;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.IMAGE_MESSAGE;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.LOCATION_MESSAGE;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.MESSAGES_NOW_ENCRYPTED;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.MISSED_VIDEO_CALL;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.MISSED_VOICE_CALL;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.STICKER_MESSAGE;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.TEXT_MESSAGE;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.UNKNOWN_MEDIA_MESSAGE;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.UNKNOWN_MESSAGE;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.URL_MESSAGE;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.USERS_JOINED_GROUP;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.USER_JOINED_GROUP;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.USER_LEFT_GROUP;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.USER_REMOVED_FROM_GROUP;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.VIDEO_MESSAGE;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.YOU_ADMIN;

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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

import dpf.mg.udi.gpinf.sqlite.SQLiteRecordValidator;
import dpf.mg.udi.gpinf.sqlite.SQLiteUndelete;
import dpf.mg.udi.gpinf.sqlite.SQLiteUndeleteTable;
import dpf.mg.udi.gpinf.whatsappextractor.Message.MessageStatus;
import dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType;
import dpf.sp.gpinf.indexer.parsers.jdbc.SQLite3DBParser;
import fqlite.base.SqliteRow;

/**
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@dpf.gov.br>
 */
public class ExtractorIOS extends Extractor {

    private static Logger logger = LoggerFactory.getLogger(ExtractorIOS.class);

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); //$NON-NLS-1$
    
    private boolean hasProfilePictureItemTable = false;
    private boolean hasZTitleColumn = false;
    private SQLException parsingException = null;

    public ExtractorIOS(String itemPath, File databaseFile, WAContactsDirectory contacts, WAAccount account, boolean recoverDeletedRecords) {
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

        List<Chat> undeletedChats = recoverDeletedRecords ?
                undeleteChats(chatSessionUndeleteTable, contacts)
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

                for (Chat c : list) {
                    c.setMessages(extractMessages(conn, c, undeletedMessages, messagesUndeletedTable, mediaItems,
                            groupMembers, firstTry));
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

    private List<Message> extractMessages(Connection conn, Chat chat, Map<Long, List<SqliteRow>> undeletedMessages,
            SQLiteUndeleteTable undeleteTable, Map<Long, SqliteRow> mediaItems, Map<Long, SqliteRow> groupMembers,
            boolean firstTry)
            throws SQLException {
        List<Message> messages = new ArrayList<>();
        
        boolean recoverDeleted = undeleteTable != null && !undeletedMessages.isEmpty();
        
        String sql;
        if (hasZTitleColumn) {
            sql = chat.isGroupChat() ? SELECT_MESSAGES_GROUP : SELECT_MESSAGES_USER;
        } else {
            sql = chat.isGroupChat() ? SELECT_MESSAGES_GROUP_NOZTITLE : SELECT_MESSAGES_USER_NOZTITLE;
        }
        
        Set<MessageWrapperForDuplicateRemoval> activeMessages = new HashSet<>();
        Map<Long, Message> activeMessageIds = new HashMap<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setFetchSize(1000);
            stmt.setLong(1, chat.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Message m = createMessageFromDB(rs, chat);
                    if (recoverDeleted) {
                        activeMessages.add(new MessageWrapperForDuplicateRemoval(m));
                        activeMessageIds.put(m.getId(), m);
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
            List<SqliteRow> undeletedRows = undeletedMessages.getOrDefault(chat.getId(), Collections.emptyList());
            for (SqliteRow row : undeletedRows) {
                try {
                    if (!firstTry || row.isDeletedRow()) {
                    Message m = createMessageFromUndeletedRecord(row, chat, mediaItems, groupMembers);
                    if (!activeMessages.contains(new MessageWrapperForDuplicateRemoval(m))) { //do not include deleted message if already there
                        if (!activeMessageIds.containsKey(m.getId()) ||
                            !compareMessagesAlmostTheSame(activeMessageIds.get(m.getId()), m)) { //also remove messages with same id that have the same start text (possibly corrupted recovered record)
                            messages.add(m);
                        }
                    }
                    }
                } catch (SQLException e) {
                    logger.warn("Error creating undelete message for whatsapp ios", e); //$NON-NLS-1$
                } catch (RuntimeException e) {
                    logger.warn("Error creating undelete message for whatsapp ios", e); //$NON-NLS-1$
                }
            }
    
            Collections.sort(messages, (a, b) -> a.getTimeStamp().compareTo(b.getTimeStamp()));
        }

        return messages;
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
            m.setMediaMime(rs.getString("vCardString")); //$NON-NLS-1$
        } else {
            String vcards = rs.getString("vCardString"); //$NON-NLS-1$
            if (vcards != null) {
                m.setVcards(Arrays.asList(vcards.split(Pattern.quote(VCARD_SEPARATOR))));
            }
        }
        m.setMediaName(rs.getString("mediaName")); //$NON-NLS-1$
        m.setMediaSize(rs.getLong("mediaSize")); //$NON-NLS-1$
        m.setMediaCaption(rs.getString("mediaCaption")); //$NON-NLS-1$
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
        m.setDeleted(false);
        return m;
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
                    m.setMediaMime(mediaItem.getTextValue("ZVCARDSTRING")); //$NON-NLS-1$
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
            if (MEDIA_MESSAGES.contains(m.getMessageType())) {
                try {
                    m.setMediaHash(mediaItem.getTextValue("ZVCARDNAME"), true); //$NON-NLS-1$
                } catch (IllegalArgumentException e) {
                } // ignore
            }
        }
        if (messageType == 0 && m.getData() == null) {
            if (m.getMediaMime() != null) {
                var mediaMime = m.getMediaMime();
                if (mediaMime != null) {
                    if (mediaMime.startsWith("image")) {
                        m.setMessageType(IMAGE_MESSAGE);
                    } else if (mediaMime.startsWith("video")) {
                        m.setMessageType(VIDEO_MESSAGE);
                    } else if (mediaMime.startsWith("application")) {
                        m.setMessageType(APP_MESSAGE);
                    } else if (mediaMime.startsWith("audio")) {
                        m.setMessageType(AUDIO_MESSAGE);
                    } else if (m.getMediaCaption() != null ){
                        m.setMessageType(UNKNOWN_MEDIA_MESSAGE);
                    }
                }
            } else if (m.getMediaCaption() != null) {
                m.setMessageType(UNKNOWN_MEDIA_MESSAGE);
            }
        }
        m.setDeleted(row.isDeletedRow());
        return m;
    }

    private List<Chat> undeleteChats(SQLiteUndeleteTable undeleteChatsSessions, WAContactsDirectory contacts) {
        List<Chat> result = new LinkedList<>();

        if (undeleteChatsSessions != null && !undeleteChatsSessions.getTableRows().isEmpty()) {
            for (SqliteRow row : undeleteChatsSessions.getTableRows()) {
                String contactId = row.getTextValue("ZCONTACTJID"); //$NON-NLS-1$
                if (!(contactId.endsWith("@status") || contactId.endsWith("@broadcast"))) { //$NON-NLS-1$ //$NON-NLS-2$
                    WAContact contact = contacts.getContact(contactId);
                    Chat c = new Chat(contact);
                    c.setId(row.getIntValue("Z_PK")); //$NON-NLS-1$
                    c.setDeleted(row.isDeletedRow());
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
            case 7:
                result = URL_MESSAGE;
            case 8:
                result = APP_MESSAGE;
                break;
            case 10:
                if (gEventType == 2) {
                    result = MESSAGES_NOW_ENCRYPTED;
                } else if (gEventType == 1) {
                    result = MISSED_VOICE_CALL;
                } else if (gEventType == 3) {
                    result = ENCRIPTION_KEY_CHANGED;
                } else if (gEventType == 4) {
                    result = MISSED_VIDEO_CALL;
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
                // mensagem de sistema desconhecida
                break;
            case 14:
                result = DELETED_FROM_SENDER;
                break;
            case 15:
                result = STICKER_MESSAGE;
                break;
        }
        return result;
    }

    /**
     * ** static strings ***
     */
    private static final String SELECT_CHAT_LIST = "SELECT ZWACHATSESSION.Z_PK as id, ZCONTACTJID AS contact, " //$NON-NLS-1$
            + "ZPARTNERNAME as subject, ZLASTMESSAGEDATE, ZPATH as avatarPath " //$NON-NLS-1$
            + "FROM ZWACHATSESSION " //$NON-NLS-1$
            + "LEFT JOIN ZWAPROFILEPICTUREITEM ON ZWAPROFILEPICTUREITEM.ZJID = ZWACHATSESSION.ZCONTACTJID " //$NON-NLS-1$
            + "ORDER BY ZLASTMESSAGEDATE DESC"; //$NON-NLS-1$

    private static final String SELECT_CHAT_LIST_NO_PPIC = "SELECT ZWACHATSESSION.Z_PK as id, ZCONTACTJID AS contact, " //$NON-NLS-1$
            + "ZPARTNERNAME as subject, ZLASTMESSAGEDATE, NULL as avatarPath " //$NON-NLS-1$
            + "FROM ZWACHATSESSION " //$NON-NLS-1$
            + "ORDER BY ZLASTMESSAGEDATE DESC"; //$NON-NLS-1$
    /*
     * Filtragem por status da mensagem (ZMESSAGESTATUS):
     * 
     * 0 - Mensagens de sistema. TODO: decodificar estas mensagens. Possiveis campos
     * para realizar decodificacao: Z_OPT, ZGROUPEVENTTYPE, ZMESSAGETYPE,
     * ZSPOTLIGHTSTATUS atualmente mensagens de sistema ignoradas
     * 
     * 1 - mensagens enviadas 3 - mensagens enviadas 5 - mensagens com mídia
     * associada 6 - mensagens 8 - mensagens
     */

    private static final String SELECT_GROUP_MEMBERS = "select CS.ZCONTACTJID as `group`, ZMEMBERJID as member from ZWAGROUPMEMBER GM "
            + "inner join ZWACHATSESSION CS on GM.ZCHATSESSION=CS.Z_PK where `group`=?";

    private static final String SELECT_MESSAGES_USER = "SELECT ZWAMESSAGE.Z_PK AS id, ZCHATSESSION " //$NON-NLS-1$
            + "as chatId, ZFROMJID AS remoteResource, ZMESSAGESTATUS AS status, ZTEXT AS data, " //$NON-NLS-1$
            + "ZISFROMME AS fromMe, datetime(ZMESSAGEDATE + 978307200, 'unixepoch') AS timestamp, " //$NON-NLS-1$
            + "ZVCARDSTRING as vCardString, ZFILESIZE as mediaSize, ZMEDIALOCALPATH " //$NON-NLS-1$
            + "as mediaName, ZVCARDNAME as mediaHash, ZTITLE as mediaCaption, " //$NON-NLS-1$
            + "ZLATITUDE as latitude, ZLONGITUDE as longitude, ZMEDIAURL as url, ZXMPPTHUMBPATH as thumbpath, " //$NON-NLS-1$
            + "ZGROUPEVENTTYPE as gEventType, ZMESSAGETYPE as messageType FROM ZWAMESSAGE " //$NON-NLS-1$
            + "LEFT JOIN ZWAMEDIAITEM ON ZWAMESSAGE.Z_PK = ZWAMEDIAITEM.ZMESSAGE " //$NON-NLS-1$
            + "WHERE chatId=? ORDER BY ZSORT"; //$NON-NLS-1$

    private static final String SELECT_MESSAGES_GROUP = "SELECT ZWAMESSAGE.Z_PK AS id, ZWAMESSAGE.ZCHATSESSION " //$NON-NLS-1$
            + "as chatId, ZMEMBERJID AS remoteResource, ZMESSAGESTATUS AS status, ZTEXT AS data, " //$NON-NLS-1$
            + "ZISFROMME AS fromMe, datetime(ZMESSAGEDATE + 978307200, 'unixepoch') AS timestamp, " //$NON-NLS-1$
            + "ZVCARDSTRING as vCardString, ZFILESIZE as mediaSize, ZMEDIALOCALPATH " //$NON-NLS-1$
            + "as mediaName, ZVCARDNAME as mediaHash, ZTITLE as mediaCaption, " //$NON-NLS-1$
            + "ZLATITUDE as latitude, ZLONGITUDE as longitude, ZMEDIAURL as url, ZXMPPTHUMBPATH as thumbpath, " //$NON-NLS-1$
            + "ZGROUPEVENTTYPE as gEventType, ZMESSAGETYPE as messageType FROM ZWAMESSAGE " //$NON-NLS-1$
            + "LEFT JOIN ZWAMEDIAITEM ON ZWAMESSAGE.Z_PK = ZWAMEDIAITEM.ZMESSAGE " //$NON-NLS-1$
            + "LEFT JOIN ZWAGROUPMEMBER ON ZWAGROUPMEMBER.ZCHATSESSION = chatId AND ZWAGROUPMEMBER.Z_PK = ZGROUPMEMBER " //$NON-NLS-1$
            + "WHERE chatId=? ORDER BY ZSORT"; //$NON-NLS-1$
    
    private static final String SELECT_MESSAGES_USER_NOZTITLE = "SELECT ZWAMESSAGE.Z_PK AS id, ZCHATSESSION " //$NON-NLS-1$
            + "as chatId, ZFROMJID AS remoteResource, ZMESSAGESTATUS AS status, ZTEXT AS data, " //$NON-NLS-1$
            + "ZISFROMME AS fromMe, datetime(ZMESSAGEDATE + 978307200, 'unixepoch') AS timestamp, " //$NON-NLS-1$
            + "ZVCARDSTRING as vCardString, ZFILESIZE as mediaSize, ZMEDIALOCALPATH " //$NON-NLS-1$
            + "as mediaName, ZVCARDNAME as mediaHash, NULL as mediaCaption, " //$NON-NLS-1$
            + "ZLATITUDE as latitude, ZLONGITUDE as longitude, ZMEDIAURL as url, ZXMPPTHUMBPATH as thumbpath, " //$NON-NLS-1$
            + "ZGROUPEVENTTYPE as gEventType, ZMESSAGETYPE as messageType FROM ZWAMESSAGE " //$NON-NLS-1$
            + "LEFT JOIN ZWAMEDIAITEM ON ZWAMESSAGE.Z_PK = ZWAMEDIAITEM.ZMESSAGE " //$NON-NLS-1$
            + "WHERE chatId=? ORDER BY ZSORT"; //$NON-NLS-1$

    private static final String SELECT_MESSAGES_GROUP_NOZTITLE = "SELECT ZWAMESSAGE.Z_PK AS id, ZWAMESSAGE.ZCHATSESSION " //$NON-NLS-1$
            + "as chatId, ZMEMBERJID AS remoteResource, ZMESSAGESTATUS AS status, ZTEXT AS data, " //$NON-NLS-1$
            + "ZISFROMME AS fromMe, datetime(ZMESSAGEDATE + 978307200, 'unixepoch') AS timestamp, " //$NON-NLS-1$
            + "ZVCARDSTRING as vCardString, ZFILESIZE as mediaSize, ZMEDIALOCALPATH " //$NON-NLS-1$
            + "as mediaName, ZVCARDNAME as mediaHash, NULL' as mediaCaption, " //$NON-NLS-1$
            + "ZLATITUDE as latitude, ZLONGITUDE as longitude, ZMEDIAURL as url, ZXMPPTHUMBPATH as thumbpath, " //$NON-NLS-1$
            + "ZGROUPEVENTTYPE as gEventType, ZMESSAGETYPE as messageType FROM ZWAMESSAGE " //$NON-NLS-1$
            + "LEFT JOIN ZWAMEDIAITEM ON ZWAMESSAGE.Z_PK = ZWAMEDIAITEM.ZMESSAGE " //$NON-NLS-1$
            + "LEFT JOIN ZWAGROUPMEMBER ON ZWAGROUPMEMBER.ZCHATSESSION = chatId AND ZWAGROUPMEMBER.Z_PK = ZGROUPMEMBER " //$NON-NLS-1$
            + "WHERE chatId=? ORDER BY ZSORT"; //$NON-NLS-1$

    private static final String VCARD_SEPARATOR = "_$!<VCard-Separator>!$_"; //$NON-NLS-1$

    private static final Set<MessageType> MEDIA_MESSAGES = ImmutableSet.of(AUDIO_MESSAGE, VIDEO_MESSAGE, GIF_MESSAGE,
            APP_MESSAGE, IMAGE_MESSAGE);

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
