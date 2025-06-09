package iped.engine.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.ehcache.PersistentCacheManager;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.MemoryUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.engine.util.Util;
import iped.utils.UTF8Properties;

public class CacheConfig extends AbstractPropertiesConfigurable implements AutoCloseable {

    private static final long serialVersionUID = 3652846422867916097L;

    private static Logger logger = LoggerFactory.getLogger(CacheConfig.class);

    public static final String CONFIG_FILE = "CacheConfig.txt";

    private static final int ONE_MB = 1024 * 1024;
    private static final double AUTO_HEAP_FACTOR = 0.005;
    private static final double AUTO_OFF_HEAP_FACTOR = 0.005;

    private static final String CACHE_DIR_TEMP = "temp";
    private static final String CACHE_DIR_GLOBAL = "global";

    private PersistentCacheManager cacheManager;

    private ResourcePoolsBuilder defaultResourcePoolsBuilder;

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

        String cacheDirValue = properties.getProperty("cacheDir");
        File cacheDir;
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

        String heapPoolSizeValue = properties.getProperty("heapPoolSizeInMB");
        long heapPoolSizeInMB;
        if ("auto".equalsIgnoreCase(heapPoolSizeValue)) {
            // If "auto", calculate 0.5% of the maximum Java heap size (-Xmx).
            long maxHeapBytes = Runtime.getRuntime().maxMemory();
            heapPoolSizeInMB = (long) (maxHeapBytes * AUTO_HEAP_FACTOR / ONE_MB);
            logger.info("Auto-configured heapPoolSizeInMB to: {} MB", heapPoolSizeInMB);
        } else {
            heapPoolSizeInMB = Long.parseLong(heapPoolSizeValue);
        }

        String offHeapPoolSizeValue = properties.getProperty("offHeapPoolSizeInMB");
        long offHeapPoolSizeInMB;
        if ("auto".equalsIgnoreCase(offHeapPoolSizeValue)) {
            // If "auto", calculate 0.5% of the total physical system memory.
            // This provides a more reliable measure for available off-heap memory.
            long totalPhysicalMemoryBytes = Util.getPhysicalMemorySize();
            offHeapPoolSizeInMB = (long) (totalPhysicalMemoryBytes * AUTO_OFF_HEAP_FACTOR / ONE_MB);
            logger.info("Auto-configured offHeapPoolSizeInMB to: {} MB", offHeapPoolSizeInMB);
        } else {
            offHeapPoolSizeInMB = Long.parseLong(offHeapPoolSizeValue);
        }

        long diskPoolSizeInMB = Long.parseLong(properties.getProperty("diskPoolSizeInMB"));

        cacheManager = CacheManagerBuilder.newCacheManagerBuilder()//
                .with(CacheManagerBuilder.persistence(cacheDir))//
                .build(true);

        defaultResourcePoolsBuilder = ResourcePoolsBuilder.newResourcePoolsBuilder() //
                .heap(heapPoolSizeInMB, MemoryUnit.MB) //
                .offheap(offHeapPoolSizeInMB, MemoryUnit.MB) //
                .disk(diskPoolSizeInMB, MemoryUnit.MB, true);
    }

    public ResourcePoolsBuilder getDefaultResourcePoolsBuilder() {
        return defaultResourcePoolsBuilder;
    }

    public PersistentCacheManager getCacheManager() {
        return cacheManager;
    }

    @Override
    public void close() throws Exception {
        if (cacheManager != null) {
            cacheManager.close();
            cacheManager = null;
        }
    }

}
