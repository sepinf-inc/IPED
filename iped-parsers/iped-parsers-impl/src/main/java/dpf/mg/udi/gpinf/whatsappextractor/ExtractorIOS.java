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
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.UNKNOWN_MESSAGE;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.URL_MESSAGE;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.USERS_JOINED_GROUP;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.USER_JOINED_GROUP;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.USER_LEFT_GROUP;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.USER_REMOVED_FROM_GROUP;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.VIDEO_MESSAGE;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.YOU_ADMIN;

import java.io.File;
import java.nio.charset.StandardCharsets;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Pattern;

import org.apache.tika.parser.ParseContext;
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

    private static Logger logger = LoggerFactory.getLogger(ExtractorAndroid.class);
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); //$NON-NLS-1$

    public ExtractorIOS(File databaseFile, WAContactsDirectory contacts, WAAccount account, ParseContext context) {
        super(databaseFile, contacts, account, context);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT")); //$NON-NLS-1$
    }

    @Override
    protected List<Chat> extractChatList() throws WAExtractorException {
        List<Chat> list = new ArrayList<>();

        SQLiteUndelete undelete = new SQLiteUndelete(databaseFile.toPath());
        undelete.addTableToRecover("ZWAMESSAGE"); //$NON-NLS-1$
        undelete.addRecordValidator("ZWAMESSAGE", new WAIOSMessageValidator()); //$NON-NLS-1$
        undelete.addTableToRecover("ZWAMEDIAITEM"); //$NON-NLS-1$
        undelete.addTableToRecoverOnlyDeleted("ZWAMEDIAITEM"); //$NON-NLS-1$
        undelete.setRecoverOnlyDeletedRecords(false);
        Map<String, SQLiteUndeleteTable> undeleteTables = undelete.undeleteData();
        SQLiteUndeleteTable messagesUndeletedTable = undeleteTables.get("ZWAMESSAGE"); //$NON-NLS-1$
        SQLiteUndeleteTable mediaInfoUndeletedTable = undeleteTables.get("ZWAMEDIAITEM"); //$NON-NLS-1$

        Map<Long, List<SqliteRow>> undeletedMessages = new HashMap<>();
        if (messagesUndeletedTable != null) {
            messagesUndeletedTable.getTableRows().stream().forEach(row -> {
                long chatId = messagesUndeletedTable.getIntValue(row, "ZCHATSESSION"); //$NON-NLS-1$
                if (chatId > 0) {
                    List<SqliteRow> rows = undeletedMessages.get(chatId);
                    if (rows == null) {
                        rows = new ArrayList<>();
                        undeletedMessages.put(chatId, rows);
                    }
                    rows.add(row);
                }
            });
        }

        Map<Long, SqliteRow> mediaInfos = new HashMap<>();
        if (mediaInfoUndeletedTable != null) {
            mediaInfoUndeletedTable.getTableRows().stream().forEach(row -> {
                long mediaInfoPK = mediaInfoUndeletedTable.getIntValue(row, "ZMESSAGE"); //$NON-NLS-1$
                if (mediaInfoPK > 0) {
                    mediaInfos.put(mediaInfoPK, row);
                }
            });
        }

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            boolean hasProfilePictureItemTable = SQLite3DBParser.containsTable("ZWAPROFILEPICTUREITEM", conn); //$NON-NLS-1$
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
                        list.add(c);
                    }
                }

                for (Chat c : list) {
                    c.setMessages(extractMessages(conn, c, undeletedMessages, messagesUndeletedTable, mediaInfos));
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

    private List<Message> extractMessages(Connection conn, Chat chat, Map<Long, List<SqliteRow>> undeletedMessages,
                                          SQLiteUndeleteTable undeleteTable, Map<Long, SqliteRow> mediaInfos) throws SQLException {
        List<Message> messages = new ArrayList<>();
        String sql = chat.isGroupChat() ? SELECT_MESSAGES_GROUP : SELECT_MESSAGES_USER;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setFetchSize(1000);
            stmt.setLong(1, chat.getId());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                messages.add(createMessageFromDB(rs, chat));
            }
        }

        // get deleted messages
        List<SqliteRow> undeletedRows = undeletedMessages.getOrDefault(chat.getId(), Collections.emptyList());
        for (SqliteRow row : undeletedRows) {
            try {
                Message m = createMessageFromUndeletedRecord(row, chat, mediaInfos);
                messages.add(m);
            } catch (SQLException e) {
            } catch (RuntimeException e) {
                logger.warn(e.toString());
            }
        }

        Collections.sort(messages, (a, b) -> a.getTimeStamp().compareTo(b.getTimeStamp()));
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
        m.setData(Util.getUTF8String(rs, "data")); //$NON-NLS-1$
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

    private Message createMessageFromUndeletedRecord(SqliteRow row, Chat chat, Map<Long, SqliteRow> mediaInfos) throws SQLException {
        Message m = new Message();
        if (account != null)
            m.setLocalResource(account.getId());
        m.setId(row.getIntValue("Z_PK")); //$NON-NLS-1$
        String remoteResource = row.getTextValue("ZFROMJID"); //$NON-NLS-1$
        if (remoteResource == null || remoteResource.isEmpty() || !chat.isGroupChat()) {
            remoteResource = chat.getRemote().getFullId();
        }
        m.setRemoteResource(remoteResource); // $NON-NLS-1$
        m.setStatus((int) row.getIntValue("ZMESSAGESTATUS")); //$NON-NLS-1$
        byte [] dataBytes = row.getBlobValue("ZTEXT"); //$NON-NLS-1$
        if (dataBytes != null) {
            m.setData(new String(dataBytes, StandardCharsets.UTF_8));
        }
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
        SqliteRow mediaInfo = mediaInfos.get(m.getId());
        if (mediaInfo != null) {
            try {
                if (m.getMessageType() != CONTACT_MESSAGE) {
                    m.setMediaMime(mediaInfo.getTextValue("ZVCARDSTRING")); //$NON-NLS-1$
                } else {
                    String vcards = mediaInfo.getTextValue("ZVCARDSTRING"); //$NON-NLS-1$
                    if (vcards != null) {
                        m.setVcards(Arrays.asList(vcards.split(Pattern.quote(VCARD_SEPARATOR))));
                    }
                }
            } catch (RuntimeException e) {
            }
            m.setMediaName(mediaInfo.getTextValue("ZMEDIALOCALPATH")); //$NON-NLS-1$
            m.setMediaSize(mediaInfo.getIntValue("ZFILESIZE")); //$NON-NLS-1$
            m.setMediaCaption(mediaInfo.getTextValue("ZTITLE")); //$NON-NLS-1$
            m.setThumbpath(mediaInfo.getTextValue("ZXMPPTHUMBPATH")); //$NON-NLS-1$
            m.setUrl(mediaInfo.getTextValue("ZMEDIAURL")); //$NON-NLS-1$
            m.setLatitude(mediaInfo.getFloatValue("ZLATITUDE")); //$NON-NLS-1$
            m.setLongitude(mediaInfo.getFloatValue("ZLONGITUDE")); //$NON-NLS-1$
            if (MEDIA_MESSAGES.contains(m.getMessageType())) {
                try {
                    m.setMediaHash(mediaInfo.getTextValue("ZVCARDNAME"), true);
                } catch (IllegalArgumentException e) {
                } // ignore
            }
        }
        m.setDeleted(true);
        return m;
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
            + "ZISFROMME AS fromMe, datetime(ZMESSAGEDATE + 978307200,'unixepoch') AS timestamp, " //$NON-NLS-1$
            + "ZVCARDSTRING as vCardString, ZFILESIZE as mediaSize, ZMEDIALOCALPATH " //$NON-NLS-1$
            + "as mediaName, ZVCARDNAME as mediaHash, ZTITLE as mediaCaption, " //$NON-NLS-1$
            + "ZLATITUDE as latitude, ZLONGITUDE as longitude, ZMEDIAURL as url, ZXMPPTHUMBPATH as thumbpath, " //$NON-NLS-1$
            + "ZGROUPEVENTTYPE as gEventType, ZMESSAGETYPE as messageType FROM ZWAMESSAGE " //$NON-NLS-1$
            + "LEFT JOIN ZWAMEDIAITEM ON ZWAMESSAGE.Z_PK = ZWAMEDIAITEM.ZMESSAGE " //$NON-NLS-1$
            + "WHERE chatId=? ORDER BY ZSORT"; //$NON-NLS-1$

    private static final String SELECT_MESSAGES_GROUP = "SELECT ZWAMESSAGE.Z_PK AS id, ZWAMESSAGE.ZCHATSESSION " //$NON-NLS-1$
            + "as chatId, ZMEMBERJID AS remoteResource, ZMESSAGESTATUS AS status, ZTEXT AS data, " //$NON-NLS-1$
            + "ZISFROMME AS fromMe, datetime(ZMESSAGEDATE + 978307200,'unixepoch') AS timestamp, " //$NON-NLS-1$
            + "ZVCARDSTRING as vCardString, ZFILESIZE as mediaSize, ZMEDIALOCALPATH " //$NON-NLS-1$
            + "as mediaName, ZVCARDNAME as mediaHash, ZTITLE as mediaCaption, " //$NON-NLS-1$
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
}
