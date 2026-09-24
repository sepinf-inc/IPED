package iped.parsers.signal;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import junit.framework.TestCase;

/**
 * Checks which Signal Android schemas are accepted by {@link SignalExtractor}.
 *
 * The message table was introduced in Signal 6.7 (sms and mms merged into it),
 * but its sender column was named recipient_id until 6.19 renamed it to
 * from_recipient_id. Databases in between have all the expected tables and must
 * still be rejected, otherwise every message query fails and the chats come out
 * empty.
 */
public class SignalExtractorSchemaTest extends TestCase {

    private Connection connection;

    @Override
    protected void tearDown() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    private void createSchema(String senderColumn) throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("CREATE TABLE recipient (_id INTEGER PRIMARY KEY, e164 TEXT, "
                    + "profile_given_name TEXT, profile_family_name TEXT, profile_joined_name TEXT, "
                    + "system_joined_name TEXT, group_id TEXT)");
            st.executeUpdate("CREATE TABLE thread (_id INTEGER PRIMARY KEY, recipient_id INTEGER, date INTEGER)");
            st.executeUpdate("CREATE TABLE message (_id INTEGER PRIMARY KEY, thread_id INTEGER, "
                    + senderColumn + " INTEGER, date_sent INTEGER, date_received INTEGER, body TEXT, type INTEGER)");
        }
    }

    public void testAcceptsSchemaWithFromRecipientId() throws Exception {
        createSchema("from_recipient_id");
        assertTrue("Signal 6.19+ schema should be accepted",
                new SignalExtractor(connection, "test.db").isValidSignalDatabase());
    }

    public void testRejectsSchemaWithLegacyRecipientId() throws Exception {
        createSchema("recipient_id");
        assertFalse("Pre-6.19 schema should be rejected instead of yielding empty chats",
                new SignalExtractor(connection, "test.db").isValidSignalDatabase());
    }

    public void testCallsSurviveAnUnreadableCallTable() throws Exception {
        // A call table with an older, narrower column set makes the call query fail.
        // The call rows in the message table must then still be extracted, instead of
        // being dropped as replaced by rows that never loaded.
        createSchema("from_recipient_id");
        try (Statement st = connection.createStatement()) {
            // no timestamp/ringer columns, as in the first version of the table
            st.executeUpdate("CREATE TABLE call (_id INTEGER PRIMARY KEY, call_id INTEGER, "
                    + "message_id INTEGER, peer INTEGER, type INTEGER, direction INTEGER, event INTEGER)");
            st.executeUpdate("INSERT INTO call VALUES (1, 100, 1, 7, 0, 0, 3)");
            st.executeUpdate("INSERT INTO recipient (_id, e164) VALUES (7, '+5511900000007')");
            st.executeUpdate("INSERT INTO thread (_id, recipient_id, date) VALUES (1, 7, 1700000000000)");
            // MISSED_AUDIO_CALL_TYPE
            st.executeUpdate("INSERT INTO message (_id, thread_id, from_recipient_id, date_sent, "
                    + "date_received, body, type) VALUES (1, 1, 7, 1700000000000, 1700000000000, NULL, 3)");
        }

        List<SignalChat> chats = new SignalExtractor(connection, "test.db").extractChats();
        assertEquals("The thread must be extracted", 1, chats.size());
        assertEquals("The call must survive the failed call query", 1, chats.get(0).getMessages().size());
        assertEquals(SignalMessage.MessageType.CALL_MISSED,
                chats.get(0).getMessages().get(0).getMessageType());
    }

    public void testRejectsDatabaseWithoutMessageTable() throws Exception {
        createSchema("from_recipient_id");
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("DROP TABLE message");
        }
        assertFalse("Database without the message table should be rejected",
                new SignalExtractor(connection, "test.db").isValidSignalDatabase());
    }
}
