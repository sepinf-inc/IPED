package iped.engine.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.engine.util.Util;
import iped.utils.UTF8Properties;

public class CacheConfig extends AbstractPropertiesConfigurable {

    private static final long serialVersionUID = 3652846422867916097L;

    private static Logger logger = LoggerFactory.getLogger(CacheConfig.class);

    public static final String CONFIG_FILE = "CacheConfig.txt";

    private static final String MODE_PROP = "mode";
    private static final String CACHE_DIR_PROP = "cacheDir";
    private static final String REDIS_URL_PROP = "redisUrl";

    private static final String DISK_POOL_SIZE_IN_MB_PROP = "diskPoolSizeInMB";
    private static final String OFF_HEAP_POOL_SIZE_IN_MB_PROP = "offHeapPoolSizeInMB";
    private static final String HEAP_POOL_SIZE_IN_MB_PROP = "heapPoolSizeInMB";

    private static final int ONE_MB = 1024 * 1024;
    private static final double AUTO_HEAP_FACTOR = 0.005;
    private static final double AUTO_OFF_HEAP_FACTOR = 0.005;

    private static final String CACHE_DIR_TEMP = "temp";
    private static final String CACHE_DIR_GLOBAL = "global";

    private Mode mode;

    private File cacheDir;
    private String redisUrl;

    private long heapPoolSizeInMB;
    private long offHeapPoolSizeInMB;
    private long diskPoolSizeInMB;

    public enum Mode {
        onlyMemory, disk, redis;
    }

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

        mode = Mode.valueOf(properties.getProperty(MODE_PROP));

        switch (mode) {
        case disk:
            parserCacheDir(properties);
            break;

        case redis:
            parserRedisUrl(properties);
            break;

        default:
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
    }

    private void parserCacheDir(UTF8Properties properties) {
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

        diskPoolSizeInMB = Long.parseLong(properties.getProperty(DISK_POOL_SIZE_IN_MB_PROP));
    }

    private void parserRedisUrl(UTF8Properties properties) {
        String redisUrlValue = properties.getProperty(REDIS_URL_PROP);
        if (!StringUtils.startsWith(redisUrlValue, "redis:")) {
            throw new IllegalArgumentException("Invalid redisUrl: " + redisUrlValue + ". Use the form redis://<host>:<port>");
        }

        redisUrl = redisUrlValue;
    }

    public Mode getMode() {
        return mode;
    }

    public File getCacheDir() {
        return cacheDir;
    }

    public String getRedisUrl() {
        return redisUrl;
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
