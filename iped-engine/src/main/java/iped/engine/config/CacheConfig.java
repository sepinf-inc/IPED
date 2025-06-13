package iped.engine.config;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.engine.sleuthkit.SleuthkitServer;
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
    private static final double DEFAULT_AUTO_HEAP_FACTOR = 0.005; // 0.5%
    private static final double DEFAULT_AUTO_OFF_HEAP_FACTOR = 0.01; // 1%

    private static final String CACHE_DIR_TEMP = "temp";
    private static final String CACHE_DIR_GLOBAL = "global";

    private Mode mode;

    private File cacheDir;
    private String redisUrl;

    private long heapPoolSize;
    private long offHeapPoolSize;
    private long diskPoolSize;

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

    // Runs initialize() after deserialization
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();

        // if SleuthkitServer or ForkServer is running, the Xmx is smaller, so adjust the factors
        String mainClass = Util.getMainClass();
        if (StringUtils.equalsAny(mainClass, SleuthkitServer.class.getName(), "iped.parsers.fork.ForkServer")) {
            parseAutoHeapPoolSize(0.02); // 2%
            parseAutoOffHeapPoolSize(0.04); // 4%
        }
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

        String heapPoolSizeInMBValue = properties.getProperty(HEAP_POOL_SIZE_IN_MB_PROP);
        if ("auto".equalsIgnoreCase(heapPoolSizeInMBValue)) {
            parseAutoHeapPoolSize(DEFAULT_AUTO_HEAP_FACTOR);
        } else {
            heapPoolSize = Long.parseLong(heapPoolSizeInMBValue) * ONE_MB;
        }

        String offHeapPoolSizeInMBValue = properties.getProperty(OFF_HEAP_POOL_SIZE_IN_MB_PROP);
        if ("auto".equalsIgnoreCase(offHeapPoolSizeInMBValue)) {
            parseAutoOffHeapPoolSize(DEFAULT_AUTO_OFF_HEAP_FACTOR);
        } else {
            offHeapPoolSize = Long.parseLong(offHeapPoolSizeInMBValue) * ONE_MB;
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

        diskPoolSize = Long.parseLong(properties.getProperty(DISK_POOL_SIZE_IN_MB_PROP)) * ONE_MB;
    }

    private void parserRedisUrl(UTF8Properties properties) {
        String redisUrlValue = properties.getProperty(REDIS_URL_PROP);
        if (!StringUtils.startsWith(redisUrlValue, "redis:")) {
            throw new IllegalArgumentException("Invalid redisUrl: " + redisUrlValue + ". Use the form redis://<host>:<port>");
        }

        redisUrl = redisUrlValue;
    }

    /**
     * If "auto", calculate x% of the maximum Java heap size (-Xmx).
     */
    private void parseAutoHeapPoolSize(double factor) {
        long maxHeapBytes = Runtime.getRuntime().maxMemory();
        heapPoolSize = (long) (maxHeapBytes * factor);
        logger.error("Auto-configured heapPoolSize to {} MB", (long) (heapPoolSize / ONE_MB));
    }

    /**
     * If "auto", calculate x% of the maximum direct memory size (-XX:MaxDirectMemorySize).
     */
    private void parseAutoOffHeapPoolSize(double factor) {

        long totalPhysicalMemoryBytes = Util.getMaxDirectMemory();
        offHeapPoolSize = (long) (totalPhysicalMemoryBytes * factor);
        logger.error("Auto-configured offHeapPoolSize to {} MB", (long) (offHeapPoolSize / ONE_MB));

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

    public long getHeapPoolSize() {
        return heapPoolSize;
    }

    public long getOffHeapPoolSize() {
        return offHeapPoolSize;
    }

    public long getDiskPoolSize() {
        return diskPoolSize;
    }
}
