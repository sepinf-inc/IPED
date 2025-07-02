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
import iped.engine.config.CacheConfig.CacheMode;
import iped.engine.core.Manager;
import redis.clients.jedis.JedisPool;

public class CacheProvider implements ICacheProvider, AutoCloseable, Serializable {

    private static final long serialVersionUID = 4341757434440803033L;

    private static Logger logger = LoggerFactory.getLogger(CacheProvider.class);

    private static Map<CacheConfig, CacheProvider> instances = new HashMap<>();

    private transient CacheManager cacheManager;
    private transient ResourcePoolsBuilder defaultResourcePoolsBuilder;

    private transient JedisPool jedisPool;

    private CacheConfig config;
    private CacheMode mode;

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

    // Runs initialize() after deserialization
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        initialize();
    }

    private void initialize() {

        config.postProcess(Manager.getInstance());

        mode = config.getMode();

        switch (mode) {
        case memoryOnly:
            logger.info("Initializing cache in memory only");
            cacheManager = CacheManagerBuilder.newCacheManagerBuilder() //
                    .build(true);
            break;

        case disk:
            logger.info("Initializing cache with disk folder {}", config.getCacheDir().getAbsolutePath());
            cacheManager = CacheManagerBuilder.newCacheManagerBuilder() //
                    .with(CacheManagerBuilder.persistence(config.getCacheDir()))//
                    .build(true);
            break;

        case redis:
            logger.info("Initializing cache with redis {}", config.getRedisUrl());
            jedisPool = new JedisPool();
            cacheManager = CacheManagerBuilder.newCacheManagerBuilder() //
                    .build(true);
            break;
        }

        defaultResourcePoolsBuilder = ResourcePoolsBuilder.newResourcePoolsBuilder() //
                .heap(config.getHeapPoolSize(), MemoryUnit.B) //
                .offheap(config.getOffHeapPoolSize(), MemoryUnit.B);

        if (mode == CacheMode.disk) {
            defaultResourcePoolsBuilder = defaultResourcePoolsBuilder.disk(config.getDiskPoolSize(), MemoryUnit.B, true);
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
            throw new IllegalStateException("CacheProvider was not initialized()");
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

        if (mode == CacheMode.redis) {
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
}
