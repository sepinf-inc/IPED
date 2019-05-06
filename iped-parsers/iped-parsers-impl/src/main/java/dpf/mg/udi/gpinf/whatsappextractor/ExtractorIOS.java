package dpf.mg.udi.gpinf.whatsappextractor;

import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.*;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Pattern;

import com.google.common.collect.ImmutableSet;

import dpf.mg.udi.gpinf.whatsappextractor.Message.MessageStatus;
import dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType;


/**
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@dpf.gov.br>
 */
public class ExtractorIOS extends Extractor {
    
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); //$NON-NLS-1$

    public ExtractorIOS(File databaseFile, WAContactsDirectory contacts) {
        super(databaseFile, contacts);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT")); //$NON-NLS-1$
    }

    @Override
    protected List<Chat> extractChatList() throws WAExtractorException {
        List<Chat> list = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath()); //$NON-NLS-1$
                Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(SELECT_CHAT_LIST)) {
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
                    c.setMessages(extractMessages(conn, c));
                }
            }
        } catch (SQLException ex) {
            throw new WAExtractorException(ex);
        }

        return list;
    }

    private List<Message> extractMessages(Connection conn, Chat chat) throws SQLException {
        List<Message> messages = new ArrayList<>();
        String sql = chat.isGroupChat() ? SELECT_MESSAGES_GROUP : SELECT_MESSAGES_USER;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setFetchSize(1000);
            stmt.setLong(1, chat.getId());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Message m = new Message();
                m.setId(rs.getLong("id")); //$NON-NLS-1$
                m.setRemoteResource(rs.getString("remoteResource")); //$NON-NLS-1$
                m.setStatus(rs.getInt("status")); //$NON-NLS-1$
                m.setData(Util.getUTF8String(rs, "data")); //$NON-NLS-1$
                m.setFromMe(rs.getInt("fromMe") == 1); //$NON-NLS-1$
                if (m.isFromMe()) {
                	switch(m.getStatus()) {
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
                	} catch (IllegalArgumentException _) {} //ignore
                }
                messages.add(m);

            }
        }
        return messages;
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
    			result = USER_REMOVED_FROM_GROUP; //sender o removido, data quem removeu
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
    		// 10 / 13 -> desconhecida (aparece algumas vezes depois de informado conversa segura com nome do interlocutor)
    		// 10 / (9, 10, 14 ou 16) -> desconhecida (aparece algumas vezes depois de mudança de código com nome do interlocutor)
    		break;
    		
    	case 11:
    		result = GIF_MESSAGE;
    		break;
    	case 12:
    		//mensagem de sistema desconhecida
    		break; 
    	case 14:
    		result = DELETED_FROM_SENDER;
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
    /*
     * Filtragem por status da mensagem (ZMESSAGESTATUS):
     * 
     *  0 - Mensagens de sistema. 
     *  TODO: decodificar estas mensagens. Possiveis campos para realizar decodificacao: Z_OPT, ZGROUPEVENTTYPE, ZMESSAGETYPE, ZSPOTLIGHTSTATUS
     *  atualmente mensagens de sistema ignoradas
     *  
     *  1 - mensagens enviadas
     *  3 - mensagens enviadas
     *  5 - mensagens com mídia associada
     *  6 - mensagens
     *  8 - mensagens
     */

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
            + "ZGROUPEVENTTYPE as gEventType, ZMESSAGETYPE as messageType FROM ZWAMESSAGE "  //$NON-NLS-1$
            + "LEFT JOIN ZWAMEDIAITEM ON ZWAMESSAGE.Z_PK = ZWAMEDIAITEM.ZMESSAGE " //$NON-NLS-1$
            + "LEFT JOIN ZWAGROUPMEMBER ON ZWAGROUPMEMBER.ZCHATSESSION = chatId AND ZWAGROUPMEMBER.Z_PK = ZGROUPMEMBER " //$NON-NLS-1$
            + "WHERE chatId=? ORDER BY ZSORT"; //$NON-NLS-1$
    
    private static final String VCARD_SEPARATOR = "_$!<VCard-Separator>!$_"; //$NON-NLS-1$ 
    
    private static final Set<MessageType> MEDIA_MESSAGES = ImmutableSet.of(AUDIO_MESSAGE, VIDEO_MESSAGE, 
    		GIF_MESSAGE, APP_MESSAGE, IMAGE_MESSAGE);
}

