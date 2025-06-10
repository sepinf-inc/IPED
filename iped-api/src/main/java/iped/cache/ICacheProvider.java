package iped.cache;

import org.ehcache.Cache;
import org.ehcache.config.builders.ResourcePoolsBuilder;

public interface ICacheProvider {

    public <K, V> Cache<K, V> getOrCreateCache(String alias, Class<K> keyType, Class<V> valueType, ResourcePoolsBuilder resourcePoolsBuilder);

    public ResourcePoolsBuilder getDefaultResourcePoolsBuilder();

    default <K, V> Cache<K, V> getOrCreateCache(String alias, Class<K> keyType, Class<V> valueType) {
        return getOrCreateCache(alias, keyType, valueType, getDefaultResourcePoolsBuilder());
    }
}
