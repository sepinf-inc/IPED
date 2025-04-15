package iped.engine.sleuthkit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map.Entry;
import java.util.Properties;

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

    private SleuthkitCase sleuthkitCase;
    private Content content;
    private boolean emptyContent = false;
    private boolean passthroughContent = false;

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

    public SleuthkitInputStreamFactory(SleuthkitCase sleuthkitCase, Content content, boolean emptyContent) {
        this(sleuthkitCase);
        if (content != null) {
            this.content = content;
        } else {
            this.emptyContent = emptyContent;
        }
    }

    public void setPassthroughContent(boolean value){
        this.passthroughContent = value;
    }

    @Override
    public boolean returnsEmptyInputStream() {

        if (passthroughContent)
            return true;
        else
            return this.emptyContent;
    }

    public SleuthkitCase getSleuthkitCase() {
        if (sleuthkitCase == null) {
            synchronized (this) {
                if (sleuthkitCase == null) {
                    try {
                        File tskDB = Paths.get(dataSource).toFile();
                        if (!SleuthkitReader.isTSKPatched()) {
                            tskDB = getWriteableDBFile(tskDB);
                        }
                        sleuthkitCase = openSleuthkitCase(tskDB.getAbsolutePath());

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
     * @throws TskCoreException
     */
    public static SleuthkitCase openSleuthkitCase(String tskDBPath) throws TskCoreException {
        Properties sysProps = System.getProperties();
        SleuthkitCase sleuthkitCase = SleuthkitCase.openCase(tskDBPath);
        for (Entry<Object, Object> entry : System.getProperties().entrySet()) {
            sysProps.setProperty(entry.getKey().toString(), entry.getValue().toString());
        }
        System.setProperties(sysProps);
        return sleuthkitCase;
    }

    public static File getWriteableDBFile(File sleuthkitDB) throws IOException {
        if (!IOUtil.canWrite(sleuthkitDB) || !IOUtil.canCreateFile(sleuthkitDB.getParentFile())) {
            File tmpCaseFile = new File(System.getProperty("java.io.basetmpdir"), //$NON-NLS-1$
                    "sleuthkit-" + sleuthkitDB.lastModified() + ".db"); //$NON-NLS-1$
            if (!tmpCaseFile.exists() || tmpCaseFile.length() != sleuthkitDB.length()) {
                IOUtil.copyFile(sleuthkitDB, tmpCaseFile);
            }
            return tmpCaseFile;
        }
        return sleuthkitDB;
    }

    @Override
    public SeekableInputStream getSeekableInputStream(String identifier) throws IOException {
        if (emptyContent) {
            return new EmptyInputStream();
        }
        FileSystemConfig fsConfig = ConfigurationManager.get().findObject(FileSystemConfig.class);

        boolean isSpecialFileSystem = false;
        long identifier_number = Long.valueOf(identifier);
        long type = (identifier_number >> 63) & 1L;
        String address = "0";
        long tskId = 0;
       
        if (type == 0){
            tskId = identifier_number;
        }else{
            tskId = (identifier_number & (8191L << 50)) >> 50;
            address = Long.toString((identifier_number & (1125899906842623L)));
            isSpecialFileSystem = true;
        }      
       
        Content tskContent = getContentById(tskId);
        if (SleuthkitReader.sleuthCase == null || !fsConfig.isRobustImageReading()) {
            if (!isSpecialFileSystem){
                return new SleuthkitInputStream(tskContent);
            }else{
                    return SpecialFileSystem.getSeekableInputStream(new SleuthkitInputStream(tskContent),tskId,address);
            }
        } else {
            SleuthkitClient sleuthProcess = SleuthkitClient.get();
            try {
                if (!isSpecialFileSystem){
                    return sleuthProcess.getInputStream((int) tskId, tskContent.getUniquePath());
                }else{
                    return SpecialFileSystem.getSeekableInputStream(sleuthProcess.getInputStream((int) tskId, tskContent.getUniquePath()),tskId,address);
                }
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
