package dpf.sp.gpinf.indexer.util;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteException;

import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.FileSystemConfig;
import dpf.sp.gpinf.indexer.datasource.SleuthkitReader;
import iped3.io.SeekableInputStream;

public class SleuthkitInputStreamFactory extends SeekableInputStreamFactory {

    private static final Logger logger = LoggerFactory.getLogger(SleuthkitInputStreamFactory.class);

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
                        sleuthkitCase = SleuthkitCase.openCase(Paths.get(dataSource).toString());
                    } catch (TskCoreException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return sleuthkitCase;
    }

    @Override
    public SeekableInputStream getSeekableInputStream(String identifier) throws IOException {
        if (emptyContent) {
            return new EmptyInputStream();
        }
        FileSystemConfig fsConfig = ConfigurationManager.get().findObject(FileSystemConfig.class);
        long tskId = Long.valueOf(identifier);
        Content tskContent = getContentById(tskId);
        if (SleuthkitReader.sleuthCase == null || !fsConfig.isRobustImageReading()) {
            return new SleuthkitInputStream(tskContent);
        } else {
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
                return getSleuthkitCase().getContentById(id);

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
