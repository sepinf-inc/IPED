package iped.parsers.signal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignalExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SignalExtractor.class);

    // Signal Android column names (RecipientTable.kt / MessageTable.kt).
    // aci and to_recipient_id are intentionally omitted: aci was added in v5.15
    // (late 2021) and breaks the query on older backups; to_recipient_id is unused.
    private static final String SELECT_RECIPIENTS =
            "SELECT _id, e164, profile_given_name, profile_family_name, " +
            "profile_joined_name, system_joined_name, group_id FROM recipient";

    private static final String SELECT_THREADS =
            "SELECT _id, recipient_id, date FROM thread ORDER BY date DESC";

    private static final String SELECT_MESSAGES =
            "SELECT _id, thread_id, from_recipient_id, " +
            "date_sent, date_received, body, type FROM message WHERE thread_id = ? ORDER BY date_sent ASC";

    private static final String SELECT_GROUP_TITLE =
            "SELECT title FROM groups WHERE recipient_id = ?";

    private static final String SELECT_GROUP_MEMBERS =
            "SELECT gm.recipient_id FROM group_membership gm " +
            "INNER JOIN groups g ON g.group_id = gm.group_id WHERE g.recipient_id = ?";

    private static final String VALIDATE_TABLES =
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' " +
            "AND name IN ('recipient','thread','message')";

    // Identifies the device owner: the recipient who appears most frequently as
    // the sender in outgoing messages (from_recipient_id for type & 31 in {0..5}).
    // Returns the full recipient row in one query using a correlated subquery.
    private static final String SELECT_SELF =
            "SELECT r._id, r.e164, r.profile_given_name, r.profile_family_name, " +
            "r.profile_joined_name, r.system_joined_name, r.group_id " +
            "FROM recipient r " +
            "WHERE r._id = (" +
            "  SELECT from_recipient_id FROM message " +
            "  WHERE (type & 31) IN (0,1,2,3,4,5) AND from_recipient_id > 0 " +
            "  GROUP BY from_recipient_id ORDER BY COUNT(*) DESC LIMIT 1" +
            ")";

    private final Connection connection;
    private final String itemPath;

    public SignalExtractor(Connection connection, String itemPath) {
        this.connection = connection;
        this.itemPath = itemPath;
    }

    /**
     * Identifies the device owner by finding the recipient who most frequently
     * appears as sender in outgoing messages. Returns null when no outgoing
     * messages exist or the recipient cannot be resolved.
     */
    public SignalContact findSelfContact() {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(SELECT_SELF)) {
            if (rs.next()) {
                return new SignalContact(
                        rs.getLong("_id"),
                        rs.getString("e164"),
                        rs.getString("profile_given_name"),
                        rs.getString("profile_family_name"),
                        rs.getString("profile_joined_name"),
                        rs.getString("system_joined_name"),
                        rs.getString("group_id"));
            }
        } catch (SQLException e) {
            LOGGER.warn("Could not identify device owner from {}: {}", itemPath, e.getMessage());
        }
        return null;
    }

    public boolean isValidSignalDatabase() {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(VALIDATE_TABLES)) {
            return rs.next() && rs.getInt(1) == 3;
        } catch (SQLException e) {
            return false;
        }
    }

    public List<SignalChat> extractChats() {
        Map<Long, SignalContact> recipients = loadRecipients();
        return loadThreads(recipients);
    }

    private Map<Long, SignalContact> loadRecipients() {
        Map<Long, SignalContact> map = new HashMap<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(SELECT_RECIPIENTS)) {
            while (rs.next()) {
                long id = rs.getLong("_id");
                SignalContact c = new SignalContact(
                        id,
                        rs.getString("e164"),
                        rs.getString("profile_given_name"),
                        rs.getString("profile_family_name"),
                        rs.getString("profile_joined_name"),
                        rs.getString("system_joined_name"),
                        rs.getString("group_id"));
                map.put(id, c);
            }
        } catch (SQLException e) {
            LOGGER.warn("Error loading Signal recipients from {}: {}", itemPath, e.getMessage());
        }
        return map;
    }

    private List<SignalChat> loadThreads(Map<Long, SignalContact> recipients) {
        List<SignalChat> chats = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(SELECT_THREADS)) {
            while (rs.next()) {
                long threadId = rs.getLong("_id");
                long recipientId = rs.getLong("recipient_id");
                SignalContact contact = recipients.get(recipientId);
                if (contact == null)
                    continue;

                SignalChat chat = new SignalChat();
                chat.setId(threadId);
                chat.setContact(contact);

                if (contact.isGroup()) {
                    chat.setGroupTitle(loadGroupTitle(recipientId));
                    loadGroupMembers(recipientId, recipients, chat);
                }

                chat.setMessages(loadMessages(threadId));

                if (!chat.getMessages().isEmpty() || !contact.getDisplayName().equals("Unknown")) {
                    chats.add(chat);
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("Error loading Signal threads from {}: {}", itemPath, e.getMessage());
        }
        return chats;
    }

    private String loadGroupTitle(long recipientId) {
        try (PreparedStatement st = connection.prepareStatement(SELECT_GROUP_TITLE)) {
            st.setLong(1, recipientId);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next())
                    return rs.getString("title");
            }
        } catch (SQLException e) {
            LOGGER.warn("Error loading Signal group title: {}", e.getMessage());
        }
        return null;
    }

    private void loadGroupMembers(long recipientId, Map<Long, SignalContact> recipients, SignalChat chat) {
        try (PreparedStatement st = connection.prepareStatement(SELECT_GROUP_MEMBERS)) {
            st.setLong(1, recipientId);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    long memberId = rs.getLong("recipient_id");
                    SignalContact member = recipients.get(memberId);
                    if (member != null)
                        chat.getParticipants().add(member);
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("Error loading Signal group members: {}", e.getMessage());
        }
    }

    private List<SignalMessage> loadMessages(long threadId) {
        List<SignalMessage> messages = new ArrayList<>();
        try (PreparedStatement st = connection.prepareStatement(SELECT_MESSAGES)) {
            st.setLong(1, threadId);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    SignalMessage m = new SignalMessage();
                    m.setId(rs.getLong("_id"));
                    m.setThreadId(threadId);
                    m.setFromRecipientId(rs.getLong("from_recipient_id"));

                    long dateSentMs = rs.getLong("date_sent");
                    if (dateSentMs > 0)
                        m.setDateSent(new Date(dateSentMs));

                    long dateReceivedMs = rs.getLong("date_received");
                    if (dateReceivedMs > 0)
                        m.setDateReceived(new Date(dateReceivedMs));

                    m.setBody(rs.getString("body"));

                    SignalMessage.MessageType msgType = classifyMessageType(rs.getInt("type"));
                    m.setMessageType(msgType);
                    // outgoing calls are initiated by self; missed/incoming are from the other party
                    m.setFromMe(msgType == SignalMessage.MessageType.OUTGOING
                            || msgType == SignalMessage.MessageType.CALL_OUTGOING);

                    messages.add(m);
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("Error loading Signal messages for thread {}: {}", threadId, e.getMessage());
        }
        return messages;
    }

    // Signal message type classification based on the lower 5 bits (MessageTypes.kt)
    private static SignalMessage.MessageType classifyMessageType(int rawType) {
        switch (rawType & 0x1F) {
            case 0:  // BASE_OUTBOX_TYPE
            case 2:  // BASE_SENT_TYPE
            case 3:  // BASE_PENDING_SECURE_SMS_FALLBACK
            case 4:  // BASE_PENDING_INSECURE_SMS_FALLBACK
            case 5:  // BASE_SENDING_TYPE
                return SignalMessage.MessageType.OUTGOING;
            case 20: // BASE_INBOX_TYPE
                return SignalMessage.MessageType.INCOMING;
            case 1:  // OUTGOING_AUDIO_CALL_TYPE
                return SignalMessage.MessageType.CALL_OUTGOING;
            case 21: // INCOMING_AUDIO_CALL_TYPE
                return SignalMessage.MessageType.CALL_INCOMING;
            case 22: // MISSED_AUDIO_CALL_TYPE
            case 25: // REJECTED_AUDIO_CALL_TYPE
                return SignalMessage.MessageType.CALL_MISSED;
            default:
                return SignalMessage.MessageType.SYSTEM;
        }
    }
}
