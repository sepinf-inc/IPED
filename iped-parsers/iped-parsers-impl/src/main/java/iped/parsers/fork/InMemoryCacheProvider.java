package iped.parsers.fork;

import java.io.File;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;

import iped.cache.ICacheProvider;

public class InMemoryCacheProvider implements ICacheProvider {

    private static final double HEAP_FACTOR = 0.05 / 400; // 5% divided by 400 chars

    private CacheManager cacheManager;

    private ResourcePoolsBuilder defaultResourcePoolsBuilder;

    public InMemoryCacheProvider() {
        initialize();
    }

    private void initialize() {

        cacheManager = CacheManagerBuilder.newCacheManagerBuilder() //
                .build(true);

        long maxHeapBytes = Runtime.getRuntime().maxMemory();
        defaultResourcePoolsBuilder = ResourcePoolsBuilder.newResourcePoolsBuilder() //
                .heap((long) (maxHeapBytes * HEAP_FACTOR), EntryUnit.ENTRIES);
    }

    @Override
    public File getCacheDir() {
        return null;
    }

    @Override
    public ResourcePoolsBuilder getDefaultResourcePoolsBuilder() {
        return defaultResourcePoolsBuilder;
    }

    @Override
    public <K, V> Cache<K, V> getOrCreateCache(String alias, Class<K> keyType, Class<V> valueType, ResourcePoolsBuilder resourcePoolsBuilder) {

        Cache<K, V> cache = cacheManager.getCache(alias, keyType, valueType);
        if (cache != null) {
            return cache;
        }

        return cacheManager.createCache(alias, //
                CacheConfigurationBuilder.newCacheConfigurationBuilder( //
                        keyType, //
                        valueType, //
                        resourcePoolsBuilder));
    }

}
