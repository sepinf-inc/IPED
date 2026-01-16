package iped.engine.task;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.JournalMode;
import org.sqlite.SQLiteConfig.SynchronousMode;
import org.sqlite.SQLiteConfig.TempStore;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;

/**
 * Task responsible for storing item metadata in a SQLite database.
 *
 * Optimizations applied:
 * - WAL journal mode for better concurrency
 * - Prepared statements with batch inserts
 * - Explicit transaction management
 * - Indexes created after bulk insert for better performance
 * - Optimized PRAGMA settings
 */
public class DatabaseTask extends AbstractTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseTask.class);

    private static final String DATABASE_NAME = "iped.db";

    /**
     * Number of items to accumulate before executing a batch insert.
     * Larger values improve throughput but use more memory.
     */
    private static final int BATCH_SIZE = 1000;

    /**
     * Number of items to accumulate before committing a transaction.
     * Larger values improve throughput but increase risk of data loss on crash.
     */
    private static final int COMMIT_THRESHOLD = 10000;

    /**
     * SQLite cache size in KB. Negative value means KB, positive means pages.
     * 65536 KB = 64 MB
     */
    private static final int CACHE_SIZE_KB = 65536;

    /**
     * Busy timeout in milliseconds. Prevents "database is locked" errors.
     */
    private static final int BUSY_TIMEOUT_MS = 60000;

    private static volatile boolean schemaDone = false;
    private static final Object schemaLock = new Object();

    private Connection connection;
    private PreparedStatement insertStatement;
    private final ArrayList<IItem> itemBuffer = new ArrayList<>(BATCH_SIZE);
    private int uncommittedCount = 0;

    private static final String CREATE_TABLE_SQL =
        "CREATE TABLE IF NOT EXISTS ITEMS (" +
        "ID            INTEGER PRIMARY KEY NOT NULL," +
        "PARENTID      INTEGER," +
        "SLEUTHID      INTEGER," +
        "NAME          TEXT NOT NULL," +
        "TYPE          TEXT NOT NULL," +
        "CATEGORY      TEXT NOT NULL," +
        "PATH          TEXT NOT NULL," +
        "EXPORT        TEXT," +
        "HASH          TEXT," +
        "MIMETYPE      TEXT," +
        "LENGTH        INTEGER," +
        "CARVEDOFFSET  INTEGER," +
        "ISCARVED      INTEGER," +
        "ISSUBITEM     INTEGER," +
        "HASCHILD      INTEGER," +
        "ISROOT        INTEGER," +
        "ISDIR         INTEGER," +
        "ISDELETED     INTEGER," +
        "TIMEOUT       INTEGER," +
        "MODIFIED      TEXT," +
        "CREATED       TEXT," +
        "ACCESSED      TEXT)";

    private static final String INSERT_SQL =
        "INSERT INTO ITEMS VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Collections.emptyList();
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        connection = createOptimizedConnection();

        synchronized (schemaLock) {
            if (!schemaDone) {
                createSchema();
                schemaDone = true;
            }
        }

        insertStatement = connection.prepareStatement(INSERT_SQL);
    }

    /**
     * Creates a SQLite connection with optimized settings for bulk inserts.
     */
    private Connection createOptimizedConnection() throws SQLException {
        SQLiteConfig config = new SQLiteConfig();

        // WAL mode allows concurrent reads during writes
        config.setJournalMode(JournalMode.WAL);

        // NORMAL sync is a good balance between safety and performance
        // (FULL is safest but slowest, OFF is fastest but risks corruption)
        config.setSynchronous(SynchronousMode.NORMAL);

        // Store temp tables in memory for better performance
        config.setTempStore(TempStore.MEMORY);

        // Increase cache size for better read/write performance
        config.setCacheSize(CACHE_SIZE_KB);

        // 4KB page size is optimal for SSDs
        config.setPageSize(4096);

        // Prevent "database is locked" errors during concurrent access
        config.setBusyTimeout(BUSY_TIMEOUT_MS);

        String dbPath = this.output.getCanonicalPath() + "/" + DATABASE_NAME;
        Connection conn = config.createConnection("jdbc:sqlite:" + dbPath);

        // Disable auto-commit for explicit transaction control
        conn.setAutoCommit(false);

        return conn;
    }

    /**
     * Creates the database schema (table only, indexes are created in finish()).
     */
    private void createSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(CREATE_TABLE_SQL);
            connection.commit();
        }
    }

    /**
     * Creates indexes after all data has been inserted.
     * This is much faster than maintaining indexes during bulk insert.
     */
    private void createIndexes() throws SQLException {
        LOGGER.info("Creating database indexes...");
        long startTime = System.currentTimeMillis();

        try (Statement stmt = connection.createStatement()) {
            // Index on hash for duplicate detection and lookups
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_items_hash ON ITEMS(HASH)");

            // Index on category for filtering by type
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_items_category ON ITEMS(CATEGORY)");

            // Index on mimetype for media type filtering
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_items_mimetype ON ITEMS(MIMETYPE)");

            // Index on parent for tree navigation
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_items_parentid ON ITEMS(PARENTID)");

            // Partial index on deleted items (usually a small subset)
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_items_deleted ON ITEMS(ISDELETED) WHERE ISDELETED = 1");

            // Partial index on carved items (usually a small subset)
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_items_carved ON ITEMS(ISCARVED) WHERE ISCARVED = 1");

            // Update query planner statistics
            stmt.executeUpdate("ANALYZE");
        }

        long elapsed = System.currentTimeMillis() - startTime;
        LOGGER.info("Database indexes created in {} ms", elapsed);
    }

    @Override
    public void finish() throws Exception {
        // Flush any remaining items in the buffer
        if (!itemBuffer.isEmpty()) {
            flushBatch();
        }

        // Final commit before creating indexes
        if (uncommittedCount > 0) {
            connection.commit();
            uncommittedCount = 0;
        }

        // Create indexes after all inserts (much faster than maintaining during insert)
        createIndexes();
        connection.commit();

        // Cleanup resources
        if (insertStatement != null) {
            insertStatement.close();
            insertStatement = null;
        }

        if (connection != null && !connection.isClosed()) {
            connection.close();
            connection = null;
        }
    }

    @Override
    protected void process(IItem evidence) throws Exception {
        if (!evidence.isQueueEnd()) {
            itemBuffer.add(evidence);
            if (itemBuffer.size() < BATCH_SIZE) {
                return;
            }
        }

        if (!itemBuffer.isEmpty()) {
            flushBatch();
        }
    }

    /**
     * Flushes the item buffer to the database using batch insert.
     */
    private void flushBatch() throws SQLException {
        for (IItem item : itemBuffer) {
            bindItemToStatement(item);
            insertStatement.addBatch();
        }

        insertStatement.executeBatch();
        uncommittedCount += itemBuffer.size();
        itemBuffer.clear();

        // Commit periodically to avoid holding too much in memory
        if (uncommittedCount >= COMMIT_THRESHOLD) {
            connection.commit();
            uncommittedCount = 0;
        }
    }

    /**
     * Binds an item's properties to the prepared statement parameters.
     */
    private void bindItemToStatement(IItem item) throws SQLException {
        insertStatement.setInt(1, item.getId());
        insertStatement.setObject(2, item.getParentId());
        insertStatement.setObject(3, null); // SLEUTHID - not used in current implementation
        insertStatement.setString(4, item.getName());
        insertStatement.setString(5, item.getType() != null ? item.getType() : "");
        insertStatement.setString(6, item.getCategories() != null ? item.getCategories() : "");
        insertStatement.setString(7, item.getPath() != null ? item.getPath() : "");
        insertStatement.setString(8, item.getIdInDataSource());
        insertStatement.setString(9, item.getHash());
        insertStatement.setString(10, item.getMediaType() != null ?
            item.getMediaType().getBaseType().toString() : null);
        insertStatement.setObject(11, item.getLength());
        insertStatement.setObject(12, item.getFileOffset());
        insertStatement.setInt(13, item.isCarved() ? 1 : 0);
        insertStatement.setInt(14, item.isSubItem() ? 1 : 0);
        insertStatement.setInt(15, item.hasChildren() ? 1 : 0);
        insertStatement.setInt(16, item.isRoot() ? 1 : 0);
        insertStatement.setInt(17, item.isDir() ? 1 : 0);
        insertStatement.setInt(18, item.isDeleted() ? 1 : 0);
        insertStatement.setInt(19, item.isTimedOut() ? 1 : 0);
        insertStatement.setString(20, item.getModDate() != null ?
            item.getModDate().toString() : null);
        insertStatement.setString(21, item.getCreationDate() != null ?
            item.getCreationDate().toString() : null);
        insertStatement.setString(22, item.getAccessDate() != null ?
            item.getAccessDate().toString() : null);
    }
}
