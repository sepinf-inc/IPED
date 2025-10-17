package iped.engine.preview;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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
    private static final ConcurrentHashMap<File, PreviewRepository> repositoryMap = new ConcurrentHashMap<>();

    private static final HashMap<File, HikariConfig> repositoryConfigMap = new HashMap<>();

    /**
     * Prepares the configuration for a writable PreviewRepository instance for a given database folder.
     * This configuration will be used when the get() method is called for the first time for this folder.
     * The database will be locked for exclusive access by this process.
     *
     * @param baseFolder The folder where the "previews.mv.db" file is or will be located.
     * @throws IllegalStateException if a configuration for this folder already exists.
     */
    public static synchronized void configureWritable(File baseFolder){
        configure(baseFolder, false);
    }

    /**
     * Prepares the configuration for a read-only PreviewRepository instance for a given database folder.
     * This configuration will be used when the get() method is called for the first time for this folder.
     * The database can be accessed by multiple read-only processes simultaneously.
     *
     * @param baseFolder The folder where the "previews.mv.db" file is or will be located.
     * @throws IllegalStateException if a configuration for this folder already exists.
     */
    public static synchronized void configureReadOnly(File baseFolder)  {
        configure(baseFolder, true);
    }

    /**
     * Creates a singleton PreviewRepository instance for the given database folder.
     *
     * @param baseFolder The folder where the "previews.mv.db" file is or will be located.
     * @param readOnly A flag indicating whether the database should be opened in read-only mode.
     * @throws IllegalStateException if a configuration for this folder already exists.
     */
    private static synchronized void configure(File baseFolder, boolean readOnly) {

        if (repositoryConfigMap.containsKey(baseFolder)) {
            throw new IllegalStateException("Repository already configured: " + baseFolder);
        }

        logger.info("Configuring {} PreviewRepository for: {}", readOnly ? "read-only" : "writable", baseFolder);

        // Use the "async" mode to prevent FileChannel from being closed with
        // ClosedByInterruptException when the thread is interrupted.
        // This issue can occur in the UI when rapidly changing the selected item
        // (e.g., holding down the arrow key).
        // Reference: https://github.com/h2database/h2database/pull/228#issuecomment-186668500
        File dbFile = new File(baseFolder, DB_NAME);
        String dbUrl = "jdbc:h2:async:" + dbFile.getAbsolutePath() + ";CACHE_SIZE=" + H2_CACHE_SIZE + ";DB_CLOSE_ON_EXIT=TRUE";
        if (readOnly) {
            dbUrl += ";ACCESS_MODE_DATA=r";
        }

        // maxPoolSize is numThreads when processing a case and DEFAULT_POOL_SIZE otherwise
        int maxPoolSize = Manager.getInstance() != null ? Optional
                .ofNullable(ConfigurationManager.get())
                .map(cm -> cm.findObject(LocalConfig.class))
                .map(LocalConfig::getNumThreads)
                .orElse(DEFAULT_POOL_SIZE) : DEFAULT_POOL_SIZE;

        // Create config
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbUrl);
        config.setMaximumPoolSize(maxPoolSize);

        repositoryConfigMap.put(baseFolder, config);
    }

    /**
     * Retrieves the singleton PreviewRepository instance for the given database folder.
     * If the instance does not exist, it will be created using the previously set configuration.
     * NOTE: This method is not re-entrant. After a repository is closed with close(), it cannot be retrieved again.
     *
     * @param baseFolder The folder of the desired repository.
     * @return The singleton PreviewRepository instance.
     * @throws SQLException if there is an error creating the database connection pool or initializing the table.
     * @throws IllegalStateException if the repository has not been configured with configureWritable() or configureReadOnly() first.
     */
    public static PreviewRepository get(File baseFolder) throws SQLException {

        PreviewRepository repository = repositoryMap.get(baseFolder);
        if (repository != null) {
            return repository;
        }

        synchronized (PreviewRepositoryManager.class) {

            repository = repositoryMap.get(baseFolder);
            if (repository != null) {
                return repository;
            }

            HikariConfig config = repositoryConfigMap.get(baseFolder);
            if (config == null) {
                throw new IllegalStateException(
                        "Repository not configured. Call configureWritable/ReadOnly() first for: " + baseFolder);
            }

            HikariDataSource dataSource = new HikariDataSource(config);

            // Ensure table exists
            try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute(CREATE_TABLE_SQL);
            }

            // Create and cache the repository instance
            repository = new PreviewRepository(dataSource, config.getJdbcUrl().contains("ACCESS_MODE_DATA=r"));
            repositoryMap.put(baseFolder, repository);
            repositoryConfigMap.put(baseFolder, null);
            logger.info("Created and initialized PreviewRepository for: {}", baseFolder);

            return repository;
        }
    }

    /**
     * Closes the connection pool and removes the PreviewRepository instance associated with the given folder.
     *
     * @param baseFolder The folder whose repository should be closed.
     */
    public static synchronized void close(File baseFolder) {
        repositoryConfigMap.remove(baseFolder);
        PreviewRepository repository = repositoryMap.remove(baseFolder);
        if (repository != null) {
            repository.close();
            logger.info("Closed PreviewRepository for: {}", baseFolder);
        }
    }
}