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

/**
 * Manages the lifecycle of PreviewRepository instances, ensuring one instance
 * per database folder (case).
 */
public class PreviewRepositoryManager {

    private static final Logger logger = LoggerFactory.getLogger(PreviewRepositoryManager.class);

    private static final String DB_NAME = "previews";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";
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

        // Create new data source
        File db = new File(baseFolder, DB_NAME);
        String dbUrl = "jdbc:h2:" + db.getAbsolutePath();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbUrl);
        config.setUsername(DB_USER);
        config.setPassword(DB_PASSWORD);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        int maxPoolSize = Optional
                .ofNullable(ConfigurationManager.get())
                .map(cm -> cm.findObject(LocalConfig.class))
                .map(LocalConfig::getNumThreads)
                .orElse(1);
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