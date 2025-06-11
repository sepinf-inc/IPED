package iped.engine.cache;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.CachePersistenceException;
import org.ehcache.PersistentCacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.MemoryUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.cache.ICacheProvider;
import iped.engine.config.CacheConfig;
import iped.engine.core.Manager;

public class CacheProvider implements ICacheProvider, AutoCloseable {

    private static Logger logger = LoggerFactory.getLogger(CacheProvider.class);

    private static Map<CacheConfig, CacheProvider> instances = new HashMap<>();

    private CacheConfig config;
    private CacheManager cacheManager;
    private ResourcePoolsBuilder defaultResourcePoolsBuilder;

    public static synchronized CacheProvider getInstance(CacheConfig cacheConfig) {
        CacheProvider instance = instances.get(cacheConfig);
        if (instance == null) {
            instances.put(cacheConfig, instance = new CacheProvider(cacheConfig));
            instance.initialize();
        }
        return instance;
    }

    private CacheProvider(CacheConfig config) {
        this.config = config;
    }

    private void initialize() {

        boolean onlyInMemory = config.getCacheDir() == null || Manager.getInstance() == null || Manager.getInstance().getCaseData().isIpedReport();
        if (onlyInMemory) {
            logger.info("Initialing cache in memory");
            cacheManager = CacheManagerBuilder.newCacheManagerBuilder() //
                    .build(true);
        } else {
            logger.info("Initialing cache from folder {}", config.getCacheDir().getAbsolutePath());
            cacheManager = CacheManagerBuilder.newCacheManagerBuilder() //
                    .with(CacheManagerBuilder.persistence(config.getCacheDir()))//
                    .build(true);
        }

        defaultResourcePoolsBuilder = ResourcePoolsBuilder.newResourcePoolsBuilder() //
                .heap(config.getHeapPoolSizeInMB(), MemoryUnit.MB) //
                .offheap(config.getOffHeapPoolSizeInMB(), MemoryUnit.MB);

        if (!onlyInMemory) {
            defaultResourcePoolsBuilder = defaultResourcePoolsBuilder.disk(config.getDiskPoolSizeInMB(), MemoryUnit.MB, true);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            cacheManager.close();
        }));
    }

    @Override
    public ResourcePoolsBuilder getDefaultResourcePoolsBuilder() {
        return defaultResourcePoolsBuilder;
    }

    @Override
    public File getCacheDir() {
        return config.getCacheDir();
    }

    @Override
    public synchronized <K, V> Cache<K, V> getOrCreateCache(String alias, Class<K> keyType, Class<V> valueType,
            ResourcePoolsBuilder resourcePoolsBuilder) {

        Cache<K, V> cache = cacheManager.getCache(alias, keyType, valueType);
        if (cache != null) {
            return cache;
        }

        try {

            return createCache(alias, keyType, valueType, resourcePoolsBuilder);

        } catch (IllegalStateException e) {
            try {
                if (cacheManager instanceof PersistentCacheManager) {
                    logger.warn("Invalid cache. Destroying and creating a new one: " + alias);
                    ((PersistentCacheManager) cacheManager).destroyCache(alias);
                } else {
                    throw e;
                }
            } catch (CachePersistenceException cpe) {
                logger.error("Error destroying cache: " + alias, cpe);
            }
            return createCache(alias, keyType, valueType, resourcePoolsBuilder);
        }
    }

    private <K, V> Cache<K, V> createCache(String alias, Class<K> keyType, Class<V> valueType, ResourcePoolsBuilder resourcePoolsBuilder) {
        return cacheManager.createCache(alias, //
                CacheConfigurationBuilder.newCacheConfigurationBuilder( //
                        keyType, //
                        valueType, //
                        resourcePoolsBuilder));
    }

    @Override
    public synchronized void close() throws Exception {
        if (cacheManager != null) {
            cacheManager.close();
            cacheManager = null;
        }
    }
}
