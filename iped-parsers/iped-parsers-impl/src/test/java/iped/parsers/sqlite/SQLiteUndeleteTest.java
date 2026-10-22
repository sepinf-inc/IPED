package iped.parsers.sqlite;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import fqlite.base.SqliteRow;
import fqlite.descriptor.TableDescriptor;
import fqlite.parser.SimpleSQLiteParser;
import fqlite.pattern.HeaderPattern;
import junit.framework.TestCase;

public class SQLiteUndeleteTest extends TestCase {

    private static final String DELETED_TEXT = "ya movi la plata a la cuenta nueva";

    /**
     * Creates a database like WhatsApp msgstore.db (INTEGER PRIMARY KEY, NOT NULL
     * integer columns holding 0/1, NULL text columns) and deletes a record in the
     * middle of a page, so its cell becomes a freeblock.
     */
    private static File createDatabase() throws Exception {
        File file = File.createTempFile("undelete", ".db");
        file.deleteOnExit();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
                Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA secure_delete=OFF");
            stmt.execute("PRAGMA journal_mode=DELETE");
            stmt.execute("CREATE TABLE message(_id INTEGER PRIMARY KEY AUTOINCREMENT,chat_row_id INTEGER NOT NULL,"
                    + "from_me INTEGER NOT NULL,key_id TEXT NOT NULL,status INTEGER,participant_hash TEXT,"
                    + "timestamp INTEGER,text_data TEXT,sort_id INTEGER NOT NULL DEFAULT 0)");
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO message(chat_row_id,from_me,key_id,status,timestamp,text_data,sort_id) VALUES (?,?,?,?,?,?,?)")) {
                for (int i = 0; i < 20; i++) {
                    long ts = 1789870000000L + i * 60000L;
                    ps.setLong(1, 137);
                    ps.setInt(2, i % 2);
                    ps.setString(3, String.format("F81656CFC18FE1CB3FBF3D4EA680A2%02d", i));
                    ps.setInt(4, i % 2 == 0 ? 0 : 13);
                    ps.setLong(5, ts);
                    ps.setString(6, i == 10 ? DELETED_TEXT : "message number " + i);
                    ps.setLong(7, ts);
                    ps.executeUpdate();
                }
            }
            stmt.execute("DELETE FROM message WHERE _id=11");
        }
        return file;
    }

    @Test
    public void testRecoverRecordDeletedIntoFreeblock() throws Exception {
        File db = createDatabase();
        try {
            SQLiteUndelete undelete = new SQLiteUndelete(db.toPath());
            undelete.addTableToRecover("message");
            undelete.setFixCarvingPatterns(true);
            Map<String, SQLiteUndeleteTable> result = undelete.undeleteData();

            List<SqliteRow> rows = result.get("message").getTableRows();
            SqliteRow recovered = rows.stream().filter(r -> DELETED_TEXT.equals(r.getTextValue("text_data")))
                    .findFirst().orElse(null);
            assertNotNull("deleted record was not recovered", recovered);
            assertTrue(recovered.isDeletedRow());
            assertEquals(137, recovered.getIntValue("chat_row_id"));
            assertEquals(0, recovered.getIntValue("from_me"));
            assertEquals("F81656CFC18FE1CB3FBF3D4EA680A210", recovered.getTextValue("key_id"));
            assertEquals(1789870600000L, recovered.getIntValue("timestamp"));

            for (SqliteRow row : rows) {
                assertTrue(row.isDeletedRow());
            }
        } finally {
            Files.deleteIfExists(db.toPath());
        }
    }

    @Test
    public void testHeaderPatternAcceptsValidSerialTypes() {
        TableDescriptor td = new SimpleSQLiteParser().parseTable(
                "CREATE TABLE message(_id INTEGER PRIMARY KEY AUTOINCREMENT,chat_row_id INTEGER NOT NULL,"
                        + "from_me INTEGER NOT NULL,key_id TEXT NOT NULL,status INTEGER,participant_hash TEXT,"
                        + "timestamp INTEGER,text_data TEXT,sort_id INTEGER NOT NULL DEFAULT 0)");
        HeaderPattern pattern = SQLiteHeaderPatternFixer.build(td);
        assertNotNull(pattern);
        assertEquals(10, pattern.size());

        // header length, _id (rowid alias, NULL), chat_row_id (1 byte int), from_me
        // (constant 1), key_id (32 bytes text), status (NULL), participant_hash (NULL),
        // timestamp (6 bytes int), text_data (empty text), sort_id (constant 0)
        int[] valid = { 11, 0, 1, 9, 77, 0, 0, 5, 13, 8 };
        for (int i = 0; i < valid.length; i++) {
            assertTrue("serial type " + valid[i] + " at " + i, pattern.get(i).match(valid[i]));
        }

        assertFalse(pattern.get(1).match(1)); // rowid alias is always NULL
        assertFalse(pattern.get(2).match(0)); // NOT NULL
        assertFalse(pattern.get(4).match(0)); // NOT NULL
        assertFalse(pattern.get(4).match(14)); // BLOB in TEXT column
        assertFalse(pattern.get(5).match(13)); // TEXT in INTEGER column
        assertFalse(pattern.get(5).match(7)); // REAL in INTEGER column
    }
}
