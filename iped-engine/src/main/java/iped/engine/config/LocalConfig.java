package iped.engine.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Path;
import java.util.Random;

import org.slf4j.Logger;

import iped.utils.UTF8Properties;

public class LocalConfig extends AbstractPropertiesConfigurable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public static final String CONFIG_FILE = "LocalConfig.txt"; //$NON-NLS-1$

    public static final String SYS_PROP_APPEND = "iped.appending"; //$NON-NLS-1$

    private static final String HASH_DB = "hashesDB";

    public static final DirectoryStream.Filter<Path> filter = new Filter<Path>() {
        @Override
        public boolean accept(Path entry) throws IOException {
            return entry.endsWith(CONFIG_FILE);
        }
    };

    private boolean indexTempOnSSD = false;
    private boolean outputOnSSD = false;
    private File ipedTemp, indexTemp;
    private int numThreads;
    private File hashDbFile;

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return filter;
    }

    @Override
    public void processProperties(UTF8Properties properties) {

        Logger logger = Configuration.getInstance().logger;

        if (System.getProperty("java.io.basetmpdir") == null) { //$NON-NLS-1$
            System.setProperty("java.io.basetmpdir", System.getProperty("java.io.tmpdir")); //$NON-NLS-1$ //$NON-NLS-2$
        }

        String value;

        File newTmp = null, tmp = new File(System.getProperty("java.io.basetmpdir")); //$NON-NLS-1$

        value = properties.getProperty("indexTemp"); //$NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (ipedTemp == null) {
            if (value != null && !value.equalsIgnoreCase("default")) { //$NON-NLS-1$
                newTmp = new File(value);
                if (!newTmp.exists() && !newTmp.mkdirs()) {
                    if (logger != null)
                        logger.info("Fail to create temp directory" + newTmp.getAbsolutePath()); //$NON-NLS-1$
                } else {
                    tmp = newTmp;
                }
            }
            Random rand = new Random();
            ipedTemp = new File(tmp, "iped-temp" + rand.nextLong()); //$NON-NLS-1$
            if (!ipedTemp.mkdirs()) {
                tmp = new File(System.getProperty("java.io.basetmpdir")); //$NON-NLS-1$
                ipedTemp = new File(tmp, "iped-temp" + rand.nextLong()); //$NON-NLS-1$
                ipedTemp.mkdirs();
            }
            if (ipedTemp.exists()) {
                System.setProperty("java.io.tmpdir", ipedTemp.getAbsolutePath()); //$NON-NLS-1$
            }
            if (tmp == newTmp) {
                indexTemp = new File(ipedTemp, "index"); //$NON-NLS-1$
            }
        }
        if (ipedTemp != null) {
            ipedTemp.mkdirs();
        }

        value = properties.getProperty("numThreads"); //$NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (value != null && !value.equalsIgnoreCase("default")) { //$NON-NLS-1$
            numThreads = Integer.valueOf(value);
        } else {
            numThreads = Runtime.getRuntime().availableProcessors();
        }

        value = properties.getProperty("indexTempOnSSD"); //$NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (value != null && !value.isEmpty()) {
            indexTempOnSSD = Boolean.valueOf(value);
        }

        value = properties.getProperty("outputOnSSD"); //$NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (value != null && !value.isEmpty()) {
            outputOnSSD = Boolean.valueOf(value);
        }

        if (outputOnSSD || !indexTempOnSSD || Boolean.valueOf(System.getProperty(SYS_PROP_APPEND)))
            indexTemp = null;

        value = properties.getProperty(HASH_DB);
        if (value != null) {
            setHashDbFile(new File(value.trim()));
        }

    }

    public void setIndexerTemp(File temp) {
        ipedTemp = temp;
        indexTemp = new File(ipedTemp, "index"); //$NON-NLS-1$
    }

    public File getIndexerTemp() {
        return ipedTemp;
    }

    public boolean isIndexTempOnSSD() {
        return indexTempOnSSD;
    }

    public File getIndexTemp() {
        return indexTemp;
    }

    public boolean isOutputOnSSD() {
        return outputOnSSD;
    }

    public int getNumThreads() {
        return numThreads;
    }

    public File getHashDbFile() {
        return hashDbFile;
    }

    public void setHashDbFile(File hashDbFile) {
        this.hashDbFile = hashDbFile;
    }
}
