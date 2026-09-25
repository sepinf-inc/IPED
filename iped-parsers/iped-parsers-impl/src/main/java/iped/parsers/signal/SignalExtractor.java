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
    // to_recipient_id is unused; aci and username are read only when present, since
    // older schemas do not have them.
    private static final String SELECT_RECIPIENTS_COLUMNS =
            "SELECT _id, e164, profile_given_name, profile_family_name, " +
            "profile_joined_name, system_joined_name, group_id";

    private static final String COL_ACI = "aci";
    private static final String COL_USERNAME = "username";

    private static final String SELECT_THREADS =
            "SELECT _id, recipient_id, date FROM thread ORDER BY date DESC";

    private static final String SELECT_MESSAGES_COLUMNS =
            "SELECT _id, thread_id, from_recipient_id, date_sent, date_received, body, type";

    private static final String SELECT_MESSAGES_TAIL =
            " FROM message WHERE thread_id = ? ORDER BY COALESCE(NULLIF(date_sent, 0), date_received) ASC";

    // Columns Signal added along the way. They all exist from 6.19 on, but are read only
    // when present so that a schema variant does not fail the whole query.
    private static final String COL_LATEST_REVISION = "latest_revision_id";
    private static final String COL_REMOTE_DELETED = "remote_deleted";
    private static final String COL_STORY_TYPE = "story_type";
    private static final String COL_PARENT_STORY = "parent_story_id";
    private static final String COL_SCHEDULED_DATE = "scheduled_date";

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

    // Identifies the device owner: the recipient who appears most frequently as the
    // sender in outgoing messages. The base types are Signal's OUTGOING_MESSAGE_TYPES
    // (MessageTypes.java).
    private static final String SELECT_SELF_ID =
            "SELECT from_recipient_id FROM message " +
            "WHERE (type & 31) IN (2,11,21,22,23,24,25,26,28) AND from_recipient_id > 0 " +
            "GROUP BY from_recipient_id ORDER BY COUNT(*) DESC LIMIT 1";

    private final Connection connection;
    private final String itemPath;
    private List<String> optionalMessageColumns;
    private Map<Long, SignalContact> recipients;

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
             ResultSet rs = st.executeQuery(SELECT_SELF_ID)) {
            if (rs.next()) {
                // Taken from the same map as every other contact, so the owner is not
                // identified one way here and another way as the sender of their messages
                return recipients().get(rs.getLong("from_recipient_id"));
            }
        } catch (SQLException e) {
            LOGGER.warn("Could not identify device owner from {}: {}", itemPath, e.getMessage());
        }
        return null;
    }

    /**
     * True when the file can be read as SQLite at all. An encrypted database (Signal
     * encrypts it by default) fails here, and there is nothing any parser can do with it.
     */
    public boolean isReadableDatabase() {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM sqlite_master")) {
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
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
        return loadThreads(recipients());
    }

    /** The recipient table, loaded once per database. */
    private Map<Long, SignalContact> recipients() {
        if (recipients == null)
            recipients = loadRecipients();
        return recipients;
    }

    private Map<Long, SignalContact> loadRecipients() {
        Map<Long, SignalContact> map = new HashMap<>();
        List<String> optional = new ArrayList<>();
        for (String column : new String[] { COL_ACI, COL_USERNAME }) {
            if (hasColumn("recipient", column))
                optional.add(column);
        }
        StringBuilder sql = new StringBuilder(SELECT_RECIPIENTS_COLUMNS);
        for (String column : optional)
            sql.append(", ").append(column);
        sql.append(" FROM recipient");

        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql.toString())) {
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
                if (optional.contains(COL_ACI))
                    c.setAci(rs.getString(COL_ACI));
                if (optional.contains(COL_USERNAME))
                    c.setUsername(rs.getString(COL_USERNAME));
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
                if (contact == null) {
                    LOGGER.warn("Signal thread {} in {} was skipped: its recipient {} is not in the database",
                            threadId, itemPath, recipientId);
                    continue;
                }

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
                    boolean hasMessageRow = !rs.wasNull();

                    SignalMessage m = new SignalMessage();
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
                        // With no ringer on a group call the caller was not recorded, so the
                        // sender is left unset rather than pointing at the group itself
                        if (!groupCall || hasRinger)
                            m.setSender(recipients.get(groupCall ? ringer : peerRecipientId));
                    }

                    long timestamp = rs.getLong("timestamp");
                    if (timestamp > 0) {
                        m.setDateSent(new Date(timestamp));
                        m.setDateReceived(new Date(timestamp));
                    }
                    calls.add(m);
                    // Only now, with the call row built: otherwise a failure halfway
                    // through would drop the message row too and lose the call entirely
                    if (hasMessageRow)
                        replacedMessageIds.add(messageId);
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("Error loading Signal calls from {}: {}", itemPath, e.getMessage());
        }
        return calls;
    }

    /** The optional columns this database actually has, resolved once. */
    private List<String> optionalMessageColumns() {
        if (optionalMessageColumns == null) {
            optionalMessageColumns = new ArrayList<>();
            for (String column : new String[] { COL_LATEST_REVISION, COL_REMOTE_DELETED,
                    COL_STORY_TYPE, COL_PARENT_STORY, COL_SCHEDULED_DATE }) {
                if (hasColumn("message", column))
                    optionalMessageColumns.add(column);
            }
        }
        return optionalMessageColumns;
    }

    private List<SignalMessage> loadMessages(long threadId, Map<Long, SignalContact> recipients,
            Set<Long> replacedMessageIds) {
        List<SignalMessage> messages = new ArrayList<>();
        List<String> optional = optionalMessageColumns();
        StringBuilder sql = new StringBuilder(SELECT_MESSAGES_COLUMNS);
        for (String column : optional)
            sql.append(", ").append(column);
        sql.append(SELECT_MESSAGES_TAIL);

        try (PreparedStatement st = connection.prepareStatement(sql.toString())) {
            st.setLong(1, threadId);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    long messageId = rs.getLong("_id");
                    // Already covered by the richer row from the call table
                    if (replacedMessageIds.contains(messageId))
                        continue;

                    // Stories are not conversation messages. For replies, ParentStoryId
                    // serializes a group reply as a positive id and a direct reply as a
                    // negative one, and Signal's own conversation query keeps
                    // parent_story_id <= 0: a direct reply to a story is a message in the
                    // conversation, a group reply belongs to the story view
                    if (optional.contains(COL_STORY_TYPE) && rs.getInt(COL_STORY_TYPE) != 0)
                        continue;
                    if (optional.contains(COL_PARENT_STORY) && rs.getLong(COL_PARENT_STORY) > 0)
                        continue;

                    SignalMessage m = new SignalMessage();
                    m.setSender(recipients.get(rs.getLong("from_recipient_id")));

                    long dateSentMs = rs.getLong("date_sent");
                    if (dateSentMs > 0)
                        m.setDateSent(new Date(dateSentMs));

                    long dateReceivedMs = rs.getLong("date_received");
                    if (dateReceivedMs > 0)
                        m.setDateReceived(new Date(dateReceivedMs));

                    m.setBody(rs.getString("body"));

                    // Signal hides superseded revisions and deleted messages from the
                    // conversation; here they are kept and flagged, since the edit history
                    // and the deletion are themselves evidence
                    if (optional.contains(COL_LATEST_REVISION)) {
                        rs.getLong(COL_LATEST_REVISION);
                        m.setEarlierRevision(!rs.wasNull());
                    }
                    if (optional.contains(COL_REMOTE_DELETED))
                        m.setRemoteDeleted(rs.getInt(COL_REMOTE_DELETED) != 0);
                    // Composed with "send later" and never sent: Signal keeps it out of the
                    // conversation, and its date is a future one
                    if (optional.contains(COL_SCHEDULED_DATE)) {
                        // -1 means "not scheduled", and NULL (read as 0) must not be taken
                        // for a schedule either: only a real date counts
                        long scheduledDate = rs.getLong(COL_SCHEDULED_DATE);
                        m.setScheduled(!rs.wasNull() && scheduledDate > 0);
                    }

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
