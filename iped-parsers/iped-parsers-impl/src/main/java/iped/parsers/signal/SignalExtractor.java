package iped.parsers.signal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            "date_sent, date_received, body, type FROM message WHERE thread_id = ? " +
            "ORDER BY COALESCE(NULLIF(date_sent, 0), date_received) ASC";

    private static final String SELECT_GROUP_TITLE =
            "SELECT title FROM groups WHERE recipient_id = ?";

    private static final String SELECT_GROUP_MEMBERS =
            "SELECT gm.recipient_id FROM group_membership gm " +
            "INNER JOIN groups g ON g.group_id = gm.group_id WHERE g.recipient_id = ?";

    // The call table (since Signal 6.7) holds every call event with its direction and
    // outcome. Its message_id is nullable, so calls with no row in the message table
    // exist and would be missed if only messages were read.
    private static final String SELECT_CALLS =
            "SELECT _id, message_id, peer, type, direction, event, timestamp, ringer " +
            "FROM call WHERE peer = ? ORDER BY timestamp ASC";

    // MessageTypes.BASE_TYPE_MASK in Signal-Android: the lower 5 bits of the type
    // column hold the base type, the remaining bits are flags.
    private static final long BASE_TYPE_MASK = 0x1F;

    // CallTable.Type / Direction / Event codes in Signal-Android
    private static final int CALL_TYPE_AUDIO = 0, CALL_TYPE_VIDEO = 1, CALL_TYPE_GROUP = 3,
            CALL_TYPE_AD_HOC = 4;
    private static final int CALL_DIRECTION_OUTGOING = 1;
    private static final int CALL_EVENT_ONGOING = 0, CALL_EVENT_ACCEPTED = 1,
            CALL_EVENT_NOT_ACCEPTED = 2, CALL_EVENT_MISSED = 3, CALL_EVENT_DELETE = 4,
            CALL_EVENT_GENERIC_GROUP = 5, CALL_EVENT_JOINED = 6, CALL_EVENT_RINGING = 7,
            CALL_EVENT_DECLINED = 8, CALL_EVENT_OUTGOING_RING = 9,
            CALL_EVENT_MISSED_NOTIFICATION_PROFILE = 10;

    private static final String VALIDATE_TABLES =
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' " +
            "AND name IN ('recipient','thread','message')";

    // Identifies the device owner: the recipient who appears most frequently as
    // the sender in outgoing messages. The base types below are Signal's
    // OUTGOING_MESSAGE_TYPES (MessageTypes.java).
    // Returns the full recipient row in one query using a correlated subquery.
    private static final String SELECT_SELF =
            "SELECT r._id, r.e164, r.profile_given_name, r.profile_family_name, " +
            "r.profile_joined_name, r.system_joined_name, r.group_id " +
            "FROM recipient r " +
            "WHERE r._id = (" +
            "  SELECT from_recipient_id FROM message " +
            "  WHERE (type & 31) IN (2,11,21,22,23,24,25,26,28) AND from_recipient_id > 0 " +
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
            if (!rs.next() || rs.getInt(1) != 3) {
                return false;
            }
        } catch (SQLException e) {
            return false;
        }
        // The message table alone is not enough: it already exists since Signal
        // 6.7 (sms and mms were merged into it), but from_recipient_id was only
        // added in 6.19. Without this check such databases would be accepted and
        // then yield empty chats, since every message query would fail.
        if (!hasColumn("message", "from_recipient_id")) {
            LOGGER.warn("Signal database at {} predates version 6.19 (message.from_recipient_id is missing), skipping it",
                    itemPath);
            return false;
        }
        // The recipient query selects these; missing any of them would fail it and leave
        // every thread without a contact, which reads as an empty case
        return hasColumns("recipient", "e164", "profile_given_name", "profile_family_name",
                    "profile_joined_name", "system_joined_name", "group_id")
                && hasColumns("thread", "recipient_id", "date")
                && hasColumns("message", "thread_id", "date_sent", "date_received", "body", "type");
    }

    private boolean hasTable(String table) {
        try (PreparedStatement st = connection
                .prepareStatement("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name = ?")) {
            st.setString(1, table);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private static SignalMessage.MessageType callMessageType(int type, int direction, int event) {
        if (event == CALL_EVENT_MISSED || event == CALL_EVENT_MISSED_NOTIFICATION_PROFILE)
            return SignalMessage.MessageType.CALL_MISSED;
        if (type == CALL_TYPE_GROUP || type == CALL_TYPE_AD_HOC)
            return SignalMessage.MessageType.CALL_GROUP;
        return direction == CALL_DIRECTION_OUTGOING ? SignalMessage.MessageType.CALL_OUTGOING
                : SignalMessage.MessageType.CALL_INCOMING;
    }

    /** Human readable description of a call event, e.g. "Incoming video call (declined)". */
    private static String callDescription(int type, int direction, int event) {
        StringBuilder sb = new StringBuilder(48);
        if (event != CALL_EVENT_GENERIC_GROUP)
            sb.append(direction == CALL_DIRECTION_OUTGOING ? "Outgoing " : "Incoming ");
        switch (type) {
            case CALL_TYPE_VIDEO:  sb.append("video call"); break;
            case CALL_TYPE_GROUP:  sb.append("group call"); break;
            case CALL_TYPE_AD_HOC: sb.append("call link call"); break;
            case CALL_TYPE_AUDIO:
            default:               sb.append("audio call"); break;
        }
        String outcome = callOutcome(event);
        if (outcome != null)
            sb.append(" (").append(outcome).append(")");
        return sb.toString();
    }

    private static String callOutcome(int event) {
        switch (event) {
            case CALL_EVENT_ONGOING:       return "ongoing";
            case CALL_EVENT_ACCEPTED:      return "accepted";
            case CALL_EVENT_NOT_ACCEPTED:  return "not accepted";
            case CALL_EVENT_MISSED:        return "missed";
            case CALL_EVENT_MISSED_NOTIFICATION_PROFILE: return "missed, notification profile";
            case CALL_EVENT_DELETE:        return "deleted";
            case CALL_EVENT_GENERIC_GROUP: return null; // the call type already says it
            case CALL_EVENT_JOINED:        return "joined";
            case CALL_EVENT_RINGING:       return "ringing";
            case CALL_EVENT_DECLINED:      return "declined";
            case CALL_EVENT_OUTGOING_RING: return "ringing";
            default:                       return null;
        }
    }

    /** All columns the queries need must be there, or the case would come out empty. */
    private boolean hasColumns(String table, String... columns) {
        for (String column : columns) {
            if (!hasColumn(table, column)) {
                LOGGER.warn("Signal database at {} has no {}.{} column, skipping it", itemPath, table, column);
                return false;
            }
        }
        return true;
    }

    private boolean hasColumn(String table, String column) {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("Could not read columns of table {} from {}: {}", table, itemPath, e.getMessage());
        }
        return false;
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
        boolean callsAvailable = hasTable("call");
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

                // Calls are read first: only the message rows they actually replace are
                // skipped, so a call event that exists only in the message table (or one
                // whose query failed) is still extracted
                Set<Long> replacedMessageIds = new HashSet<>();
                List<SignalMessage> calls = callsAvailable
                        ? loadCalls(threadId, recipientId, recipients, replacedMessageIds)
                        : new ArrayList<>();

                List<SignalMessage> messages = loadMessages(threadId, recipients, replacedMessageIds);
                messages.addAll(calls);
                // SQLite sorts rows with no usable date first; keep that order here so the
                // report does not depend on whether the database has a call table
                messages.sort(Comparator.comparing(SignalExtractor::effectiveDate,
                        Comparator.nullsFirst(Comparator.naturalOrder())));
                chat.setMessages(messages);

                // Groups are kept even when empty, since their membership is evidence on
                // its own; 1:1 threads only when they have messages or an identified contact
                if (!chat.getMessages().isEmpty() || contact.isGroup() || contact.isIdentified()) {
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

    private static Date effectiveDate(SignalMessage m) {
        return m.getDateSent() != null ? m.getDateSent() : m.getDateReceived();
    }

    /**
     * Loads the call events of a conversation from the call table, which carries the
     * direction and the outcome of each call. Calls whose message row was deleted have
     * a null message_id and only show up here.
     */
    private List<SignalMessage> loadCalls(long threadId, long peerRecipientId,
            Map<Long, SignalContact> recipients, Set<Long> replacedMessageIds) {
        List<SignalMessage> calls = new ArrayList<>();
        try (PreparedStatement st = connection.prepareStatement(SELECT_CALLS)) {
            st.setLong(1, peerRecipientId);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    int type = rs.getInt("type");
                    int direction = rs.getInt("direction");
                    int event = rs.getInt("event");

                    long messageId = rs.getLong("message_id");
                    if (!rs.wasNull())
                        replacedMessageIds.add(messageId);

                    SignalMessage m = new SignalMessage();
                    m.setId(rs.getLong("_id"));
                    m.setThreadId(threadId);
                    m.setFromRecipientId(peerRecipientId);
                    m.setMessageType(callMessageType(type, direction, event));
                    m.setCallDetail(callDescription(type, direction, event));
                    m.setFromMe(direction == CALL_DIRECTION_OUTGOING);
                    if (!m.isFromMe()) {
                        // On a group thread the peer is the group itself; the caller is the
                        // ringer, and is left unset when the column is absent or null
                        long ringer = rs.getLong("ringer");
                        boolean hasRinger = !rs.wasNull();
                        boolean groupCall = type == CALL_TYPE_GROUP || type == CALL_TYPE_AD_HOC;
                        // On a group thread the peer is the group, so the caller comes from
                        // the ringer column, which Signal only fills for ring events; with
                        // no ringer the caller was simply not recorded and stays unset
                        long senderId = groupCall ? ringer : peerRecipientId;
                        if (!groupCall || hasRinger) {
                            m.setSender(recipients.get(senderId));
                            m.setFromRecipientId(senderId);
                        } else {
                            m.setFromRecipientId(0);
                        }
                    }

                    long timestamp = rs.getLong("timestamp");
                    if (timestamp > 0) {
                        m.setDateSent(new Date(timestamp));
                        m.setDateReceived(new Date(timestamp));
                    }
                    calls.add(m);
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("Error loading Signal calls from {}: {}", itemPath, e.getMessage());
        }
        return calls;
    }

    private List<SignalMessage> loadMessages(long threadId, Map<Long, SignalContact> recipients,
            Set<Long> replacedMessageIds) {
        List<SignalMessage> messages = new ArrayList<>();
        try (PreparedStatement st = connection.prepareStatement(SELECT_MESSAGES)) {
            st.setLong(1, threadId);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    long messageId = rs.getLong("_id");
                    // Already covered by the richer row from the call table
                    if (replacedMessageIds.contains(messageId))
                        continue;

                    SignalMessage m = new SignalMessage();
                    m.setId(messageId);
                    m.setThreadId(threadId);
                    long fromRecipientId = rs.getLong("from_recipient_id");
                    m.setFromRecipientId(fromRecipientId);
                    m.setSender(recipients.get(fromRecipientId));

                    long dateSentMs = rs.getLong("date_sent");
                    if (dateSentMs > 0)
                        m.setDateSent(new Date(dateSentMs));

                    long dateReceivedMs = rs.getLong("date_received");
                    if (dateReceivedMs > 0)
                        m.setDateReceived(new Date(dateReceivedMs));

                    m.setBody(rs.getString("body"));

                    SignalMessage.MessageType msgType = classifyMessageType(rs.getLong("type"));
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

    // Signal message type classification based on the lower 5 bits.
    // Values taken from MessageTypes.java (BASE_TYPE_MASK area) in Signal-Android.
    private static SignalMessage.MessageType classifyMessageType(long rawType) {
        switch ((int) (rawType & BASE_TYPE_MASK)) {
            case 20: // BASE_INBOX_TYPE
                return SignalMessage.MessageType.INCOMING;
            case 21: // BASE_OUTBOX_TYPE
            case 22: // BASE_SENDING_TYPE
            case 23: // BASE_SENT_TYPE
            case 24: // BASE_SENT_FAILED_TYPE
            case 25: // BASE_PENDING_SECURE_SMS_FALLBACK
            case 26: // BASE_PENDING_INSECURE_SMS_FALLBACK
            case 28: // BASE_SENDING_SKIPPED_TYPE
                return SignalMessage.MessageType.OUTGOING;
            case 1:  // INCOMING_AUDIO_CALL_TYPE
            case 10: // INCOMING_VIDEO_CALL_TYPE
                return SignalMessage.MessageType.CALL_INCOMING;
            case 2:  // OUTGOING_AUDIO_CALL_TYPE
            case 11: // OUTGOING_VIDEO_CALL_TYPE
                return SignalMessage.MessageType.CALL_OUTGOING;
            case 3:  // MISSED_AUDIO_CALL_TYPE
            case 8:  // MISSED_VIDEO_CALL_TYPE
                return SignalMessage.MessageType.CALL_MISSED;
            case 12: // GROUP_CALL_TYPE
                return SignalMessage.MessageType.CALL_GROUP;
            default:
                // JOINED_TYPE, UNSUPPORTED_MESSAGE_TYPE, BASE_DRAFT_TYPE, profile
                // changes, group updates and every other event message
                return SignalMessage.MessageType.SYSTEM;
        }
    }
}
