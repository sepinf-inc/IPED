package dpf.sp.gpinf.indexer.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.DirectoryStream.Filter;
import java.util.Date;
import java.util.Locale;

import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.impl.NoOpLog;
import org.apache.tika.fork.ForkParser2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.ConstantsViewer;
import dpf.sp.gpinf.indexer.IndexFiles;
import dpf.sp.gpinf.indexer.util.CustomLoader.CustomURLClassLoader;

public class LocalConfig extends AbstractPropertiesConfigurable {
    public static final String CONFIG_FILE = "LocalConfig.txt"; //$NON-NLS-1$

    public static final DirectoryStream.Filter<Path> filter = new Filter<Path>() {
        @Override
        public boolean accept(Path entry) throws IOException {
            return entry.endsWith(CONFIG_FILE);
        }
    };

    boolean indexTempOnSSD = false;
    boolean outputOnSSD = false;
    File indexerTemp, indexTemp;
    int numThreads;

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return filter;
    }

    public void processConfig(Path resource) throws IOException {
        super.processConfig(resource);

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
        if (indexerTemp == null) {
            if (value != null && !value.equalsIgnoreCase("default")) { //$NON-NLS-1$
                newTmp = new File(value);
                if (!newTmp.exists() && !newTmp.mkdirs()) {
                    if (logger != null)
                        logger.info("Fail to create temp directory" + newTmp.getAbsolutePath()); //$NON-NLS-1$
                } else {
                    tmp = newTmp;
                }
            }
            indexerTemp = new File(tmp, "indexador-temp" + new Date().getTime()); //$NON-NLS-1$
            if (!indexerTemp.mkdirs()) {
                tmp = new File(System.getProperty("java.io.basetmpdir")); //$NON-NLS-1$
                indexerTemp = new File(tmp, "indexador-temp" + new Date().getTime()); //$NON-NLS-1$
                indexerTemp.mkdirs();
            }
            if (indexerTemp.exists()) {
                System.setProperty("java.io.tmpdir", indexerTemp.getAbsolutePath()); //$NON-NLS-1$
            }
            if (tmp == newTmp) {
                indexTemp = new File(indexerTemp, "index"); //$NON-NLS-1$
            }
        }
        if (indexerTemp != null) {
            indexerTemp.mkdirs();
        }
        ConstantsViewer.indexerTemp = indexerTemp;

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

        if (outputOnSSD || !indexTempOnSSD
                || (IndexFiles.getInstance() != null && IndexFiles.getInstance().appendIndex))
            indexTemp = null;
    }

    public File getIndexerTemp() {
        return indexerTemp;
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
}
