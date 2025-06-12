package iped.engine.cache;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
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
import iped.engine.config.CacheConfig.Mode;
import iped.engine.core.Manager;
import redis.clients.jedis.JedisPool;

public class CacheProvider implements ICacheProvider, AutoCloseable {

    private static Logger logger = LoggerFactory.getLogger(CacheProvider.class);

    private static Map<CacheConfig, CacheProvider> instances = new HashMap<>();

    private CacheConfig config;
    private CacheManager cacheManager;
    private ResourcePoolsBuilder defaultResourcePoolsBuilder;

    private JedisPool jedisPool;

    private Mode mode;

    public static synchronized CacheProvider getInstance(CacheConfig cacheConfig) {
        CacheProvider instance = instances.get(cacheConfig);
        if (instance == null) {
            instances.put(cacheConfig, instance = new CacheProvider(cacheConfig));
        }
        return instance;
    }

    private CacheProvider(CacheConfig config) {
        this.config = config;
        initialize();
    }

    private void initialize() {

        mode = config.getMode();

        // only allow disk mode in processing
        if (mode == Mode.disk && (Manager.getInstance() == null || Manager.getInstance().getCaseData().isIpedReport())) {
            mode = Mode.onlyMemory;
        }

        switch (mode) {
        case onlyMemory:
            logger.error("Initialing cache in memory");
            cacheManager = CacheManagerBuilder.newCacheManagerBuilder() //
                    .build(true);
            break;

        case disk:
            logger.error("Initialing cache from folder {}", config.getCacheDir().getAbsolutePath());
            cacheManager = CacheManagerBuilder.newCacheManagerBuilder() //
                    .with(CacheManagerBuilder.persistence(config.getCacheDir()))//
                    .build(true);
            break;

        case redis:
            logger.error("Initialing cache redis {}", config.getRedisUrl());
            jedisPool = new JedisPool();
            cacheManager = CacheManagerBuilder.newCacheManagerBuilder() //
                    .build(true);
            break;
        }

        defaultResourcePoolsBuilder = ResourcePoolsBuilder.newResourcePoolsBuilder() //
                .heap(config.getHeapPoolSizeInMB(), MemoryUnit.MB) //
                .offheap(config.getOffHeapPoolSizeInMB(), MemoryUnit.MB);

        if (mode == Mode.disk) {
            defaultResourcePoolsBuilder = defaultResourcePoolsBuilder.disk(config.getDiskPoolSizeInMB(), MemoryUnit.MB, true);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            close();
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

        if (cacheManager == null) {
            throw new IllegalStateException("CacheProvider was closed()");
        }

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

        CacheConfigurationBuilder<K, V> configurationBuilder = CacheConfigurationBuilder.newCacheConfigurationBuilder( //
                keyType, //
                valueType, //
                resourcePoolsBuilder);

        if (mode == Mode.redis) {
            configurationBuilder = configurationBuilder.withLoaderWriter(new RedisCacheLoaderWriter<K, V>(jedisPool, alias + ":", valueType));
        }

        return cacheManager.createCache(alias, configurationBuilder);
    }

    @Override
    public synchronized void close() {
        if (cacheManager != null) {
            cacheManager.close();
            cacheManager = null;
            synchronized (CacheProvider.class) {
                instances.remove(config);
            }
        }
        if (jedisPool != null) {
            jedisPool.close();
            jedisPool = null;
        }
    }

    public static synchronized void closeAll() {
        List.copyOf(instances.values()).stream().forEach(CacheProvider::close);
        instances.clear();
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        initialize();
    }
}
