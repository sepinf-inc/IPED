package iped.engine.preview;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import iped.engine.config.ConfigurationManager;
import iped.engine.config.LocalConfig;
import iped.engine.core.Manager;

/**
 * Manages the lifecycle of PreviewRepository instances, ensuring one instance
 * per database folder (case).
 */
public class PreviewRepositoryManager {

    private static final Logger logger = LoggerFactory.getLogger(PreviewRepositoryManager.class);

    private static final String DB_NAME = "previews";

    private static final int H2_CACHE_SIZE = 64 * 1024; // in KB
    private static final int DEFAULT_POOL_SIZE = 8;

    private static final String CREATE_TABLE_SQL = "CREATE TABLE IF NOT EXISTS previews (id VARBINARY(16) PRIMARY KEY, data BLOB)";

    // Map to track different storages/connections in multicases
    private static final HashMap<File, PreviewRepository> repositoryMap = new HashMap<>();

    /**
     * Gets or creates a singleton PreviewRepository instance for the given database folder.
     *
     * @param baseFolder The folder where the "previews.mv.db" file is or will be located.
     * @return The PreviewRepository instance.
     * @throws SQLException if there is an error creating the database connection or table.
     */
    public static synchronized PreviewRepository get(File baseFolder) throws SQLException {
        PreviewRepository repository = repositoryMap.get(baseFolder);
        if (repository != null) {
            return repository;
        }

        // Use the "async" mode to prevent FileChannel from being closed with
        // ClosedByInterruptException when the thread is interrupted.
        // This issue can occur in the UI when rapidly changing the selected item
        // (e.g., holding down the arrow key).
        // Reference: https://github.com/h2database/h2database/pull/228#issuecomment-186668500
        //
        // AUTO_SERVER=TRUE allows the database to be opened from multiple processes,
        // for example when generating a report.
        File db = new File(baseFolder, DB_NAME);
        String dbUrl = "jdbc:h2:async:" + db.getAbsolutePath() + ";AUTO_SERVER=TRUE;CACHE_SIZE=" + H2_CACHE_SIZE;

        // maxPoolSize is numThreads when processing a case and DEFAULT_POOL_SIZE otherwise
        int maxPoolSize = Manager.getInstance() != null ? Optional
                .ofNullable(ConfigurationManager.get())
                .map(cm -> cm.findObject(LocalConfig.class))
                .map(LocalConfig::getNumThreads)
                .orElse(DEFAULT_POOL_SIZE) : DEFAULT_POOL_SIZE;

        // Create new data source
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbUrl);
        config.setMaximumPoolSize(maxPoolSize);

        HikariDataSource dataSource = new HikariDataSource(config);

        // Ensure table exists
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_TABLE_SQL);
        }

        // Create and cache the repository instance
        repository = new PreviewRepository(dataSource);
        repositoryMap.put(baseFolder, repository);

        logger.info("Created PreviewRepository for: {}", baseFolder.getAbsolutePath());
        return repository;
    }

    /**
     * Closes and removes the PreviewRepository associated with the given database folder.
     *
     * @param baseFolder The folder whose repository should be closed.
     * @throws SQLException if there is an error closing the data source.
     */
    public static synchronized void close(File baseFolder) throws SQLException {
        PreviewRepository repository = repositoryMap.remove(baseFolder);
        if (repository != null) {
            repository.close();
            logger.info("Closed PreviewRepository for: {}", baseFolder.getAbsolutePath());
        }
    }
}