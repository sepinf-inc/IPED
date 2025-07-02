package iped.cache;

import java.io.File;

import org.ehcache.Cache;
import org.ehcache.config.builders.ResourcePoolsBuilder;

public interface ICacheProvider {

    public File getCacheDir();

    public ResourcePoolsBuilder getDefaultResourcePoolsBuilder();

    public <K, V> Cache<K, V> getOrCreateCache(String alias, Class<K> keyType, Class<V> valueType, ResourcePoolsBuilder resourcePoolsBuilder);

    default <K, V> Cache<K, V> getOrCreateCache(String alias, Class<K> keyType, Class<V> valueType) {
        return getOrCreateCache(alias, keyType, valueType, getDefaultResourcePoolsBuilder());
    }
}
