package iped.engine.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.engine.util.Util;
import iped.utils.UTF8Properties;

public class CacheConfig extends AbstractPropertiesConfigurable {

    private static final long serialVersionUID = 3652846422867916097L;

    private static Logger logger = LoggerFactory.getLogger(CacheConfig.class);

    public static final String CONFIG_FILE = "CacheConfig.txt";

    private static final String CACHE_DIR_PROP = "cacheDir";
    private static final String DISK_POOL_SIZE_IN_MB_PROP = "diskPoolSizeInMB";
    private static final String OFF_HEAP_POOL_SIZE_IN_MB_PROP = "offHeapPoolSizeInMB";
    private static final String HEAP_POOL_SIZE_IN_MB_PROP = "heapPoolSizeInMB";

    private static final int ONE_MB = 1024 * 1024;
    private static final double AUTO_HEAP_FACTOR = 0.005;
    private static final double AUTO_OFF_HEAP_FACTOR = 0.005;

    private static final String CACHE_DIR_TEMP = "temp";
    private static final String CACHE_DIR_GLOBAL = "global";

    private static File caseDir;

    private File cacheDir;

    private long heapPoolSizeInMB;
    private long offHeapPoolSizeInMB;
    private long diskPoolSizeInMB;

    public static final DirectoryStream.Filter<Path> filter = new Filter<Path>() {
        @Override
        public boolean accept(Path entry) throws IOException {
            return entry.endsWith(CONFIG_FILE);
        }
    };

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return filter;
    }

    @Override
    public void processProperties(UTF8Properties properties) {

        String cacheDirValue = properties.getProperty(CACHE_DIR_PROP);
        switch (cacheDirValue) {
        case CACHE_DIR_GLOBAL:
            cacheDir = new File(System.getProperty("user.home"), ".iped/ehcache");
            break;

        case CACHE_DIR_TEMP:
            try {
                cacheDir = Files.createTempDirectory("ehcache").toFile();
            } catch (IOException e) {
                throw new RuntimeException("Error creating cacheDir", e);
            }
            break;

        default:
            cacheDir = new File(cacheDirValue);
            if (!cacheDir.isDirectory() || !cacheDir.mkdirs()) {
                throw new IllegalArgumentException("cacheDir is not valid: " + cacheDir);
            }
            break;
        }

        String heapPoolSizeValue = properties.getProperty(HEAP_POOL_SIZE_IN_MB_PROP);
        if ("auto".equalsIgnoreCase(heapPoolSizeValue)) {
            // If "auto", calculate 0.5% of the maximum Java heap size (-Xmx).
            long maxHeapBytes = Runtime.getRuntime().maxMemory();
            heapPoolSizeInMB = (long) (maxHeapBytes * AUTO_HEAP_FACTOR / ONE_MB);
            logger.info("Auto-configured heapPoolSizeInMB to {} MB", heapPoolSizeInMB);
        } else {
            heapPoolSizeInMB = Long.parseLong(heapPoolSizeValue);
        }

        String offHeapPoolSizeValue = properties.getProperty(OFF_HEAP_POOL_SIZE_IN_MB_PROP);
        if ("auto".equalsIgnoreCase(offHeapPoolSizeValue)) {
            // If "auto", calculate 0.5% of the total physical system memory.
            // This provides a more reliable measure for available off-heap memory.
            long totalPhysicalMemoryBytes = Util.getPhysicalMemorySize();
            offHeapPoolSizeInMB = (long) (totalPhysicalMemoryBytes * AUTO_OFF_HEAP_FACTOR / ONE_MB);
            logger.info("Auto-configured offHeapPoolSizeInMB to {} MB", offHeapPoolSizeInMB);
        } else {
            offHeapPoolSizeInMB = Long.parseLong(offHeapPoolSizeValue);
        }

        diskPoolSizeInMB = Long.parseLong(properties.getProperty(DISK_POOL_SIZE_IN_MB_PROP));

    }

    public File getCacheDir() {
        return cacheDir;
    }

    public long getHeapPoolSizeInMB() {
        return heapPoolSizeInMB;
    }

    public long getOffHeapPoolSizeInMB() {
        return offHeapPoolSizeInMB;
    }

    public long getDiskPoolSizeInMB() {
        return diskPoolSizeInMB;
    }

}
