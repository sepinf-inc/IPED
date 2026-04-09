package iped.engine.sleuthkit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteException;

import iped.engine.config.ConfigurationManager;
import iped.engine.config.FileSystemConfig;
import iped.engine.datasource.SleuthkitReader;
import iped.io.SeekableInputStream;
import iped.utils.EmptyInputStream;
import iped.utils.IOUtil;
import iped.utils.SeekableInputStreamFactory;

public class SleuthkitInputStreamFactory extends SeekableInputStreamFactory {

    private static final Logger logger = LoggerFactory.getLogger(SleuthkitInputStreamFactory.class);

    private static final ConcurrentHashMap<Path, SleuthkitCase> sleuthkitCaseMap = new ConcurrentHashMap<>();

    private SleuthkitCase sleuthkitCase;
    private Content content;
    private boolean emptyContent = false;

    public SleuthkitInputStreamFactory(Path dataSource) {
        super(dataSource.toUri());
    }

    private SleuthkitInputStreamFactory(SleuthkitCase sleuthkitCase) {
        this(Paths.get(sleuthkitCase.getDbDirPath(), sleuthkitCase.getDatabaseName()));
        this.sleuthkitCase = sleuthkitCase;
    }

    /**
     * Constructor used for optimization purposes. If you use this, this factory
     * will be specific for the item content passed and can't be reused for other
     * items.
     * 
     * @param sleuthkitCase
     * @param content
     */
    public SleuthkitInputStreamFactory(SleuthkitCase sleuthkitCase, Content content) {
        this(sleuthkitCase);
        if (content != null) {
            this.content = content;
        } else {
            this.emptyContent = true;
        }
    }

    @Override
    public boolean returnsEmptyInputStream() {
        return this.emptyContent;
    }

    public SleuthkitCase getSleuthkitCase() {
        if (sleuthkitCase == null) {
            synchronized (this) {
                if (sleuthkitCase == null) {
                    try {
                        logger.info("Opening Sleuthkit case for data source: " + dataSource);
                        sleuthkitCase = openSleuthkitCase(Paths.get(dataSource));

                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return sleuthkitCase;
    }

    /**
     * Workaround for https://github.com/sepinf-inc/IPED/issues/1176
     * 
     * @param tskDB
     * @return
     */
    public synchronized static SleuthkitCase openSleuthkitCase(Path tskDBPath) throws IOException, TskCoreException {
        Path normalizedDBPath = tskDBPath.toAbsolutePath().normalize();

        if (!SleuthkitReader.isTSKPatched()) {
            normalizedDBPath = getWriteableDBPath(normalizedDBPath, false).toAbsolutePath().normalize();
        }

        return sleuthkitCaseMap.computeIfAbsent(normalizedDBPath, (key) -> {

            Properties sysProps = System.getProperties();
            try {
                SleuthkitCase sleuthkitCase = SleuthkitCase.openCase(key.toString());

                return sleuthkitCase;

            } catch (TskCoreException e) {
                throw new RuntimeException(e);

            } finally {

                // restore system properties after openCase
                for (Entry<Object, Object> entry : System.getProperties().entrySet()) {
                    sysProps.setProperty(entry.getKey().toString(), entry.getValue().toString());
                }
                System.setProperties(sysProps);
            }
        });
    }

    public static Path getWriteableDBPath(Path sleuthkitDB, boolean alwaysGetCopy) throws IOException {
        if (alwaysGetCopy || !IOUtil.canWrite(sleuthkitDB.toFile()) || !IOUtil.canCreateFile(sleuthkitDB.getParent().toFile())) {

            String tmpCaseDBName = "sleuthkit-" + Files.getLastModifiedTime(sleuthkitDB).to(TimeUnit.SECONDS) + ".db";
            Path tmpCasePath = Paths.get(System.getProperty("java.io.basetmpdir"), tmpCaseDBName);

            if (!Files.exists(tmpCasePath) || Files.size(tmpCasePath) != Files.size(sleuthkitDB)) {
                Files.copy(sleuthkitDB, tmpCasePath, StandardCopyOption.REPLACE_EXISTING);
            }
            tmpCasePath.toFile().setWritable(true);

            return tmpCasePath;
        }
        return sleuthkitDB;
    }

    @Override
    public SeekableInputStream getSeekableInputStream(String identifier) throws IOException {
        if (emptyContent) {
            return new EmptyInputStream();
        }
        FileSystemConfig fsConfig = ConfigurationManager.get().findObject(FileSystemConfig.class);
        long tskId = Long.valueOf(identifier);
        Content tskContent = getContentById(tskId);
        if (!fsConfig.isRobustImageReading()) {
            return new SleuthkitInputStream(tskContent);
        } else {
            try {
                SleuthkitClient.initSleuthkitServers(new File(getSleuthkitCase().getDbDirPath(), getSleuthkitCase().getDatabaseName()));
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
            SleuthkitClient sleuthProcess = SleuthkitClient.get();
            try {
                return sleuthProcess.getInputStream((int) tskId, tskContent.getUniquePath());
            } catch (TskCoreException e) {
                throw new IOException(e);
            }
        }

    }

    public Content getContentById(long id) throws IOException {
        if (content != null) {
            return content;
        }
        long start = System.currentTimeMillis() / 1000;
        while (true) {
            try {
                synchronized (this) {
                    return getSleuthkitCase().getContentById(id);
                }

            } catch (TskCoreException e) {
                handleTskCoreException(e, start);
            }
        }
    }

    private void handleTskCoreException(TskCoreException e, long start) throws IOException {
        if (e.getCause() instanceof SQLiteException) {
            long now = System.currentTimeMillis() / 1000;
            int errorCode = ((SQLiteException) e.getCause()).getErrorCode();
            logger.warn("SQLite error " + errorCode + " after " + (now - start)
                    + "s reading sleuthkit DB, trying again...");
            if (now - start > 3600)
                throw new RuntimeException("Timeout after 1h retrying!", e); //$NON-NLS-1$
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e1) {
                IOException e2 = new IOException(e);
                e2.addSuppressed(e1);
                throw e2;
            }
        } else {
            throw new IOException(e);
        }
    }

}
