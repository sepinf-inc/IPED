package iped.parsers.signal;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

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

    public void testRejectsDatabaseWithoutMessageTable() throws Exception {
        createSchema("from_recipient_id");
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("DROP TABLE message");
        }
        assertFalse("Database without the message table should be rejected",
                new SignalExtractor(connection, "test.db").isValidSignalDatabase());
    }
}
