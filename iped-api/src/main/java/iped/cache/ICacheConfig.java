package iped.cache;

import org.ehcache.Cache;
import org.ehcache.config.builders.ResourcePoolsBuilder;

public interface ICacheConfig {

    public <K, V> Cache<K, V> getOrCreateCache(String alias, Class<K> keyType, Class<V> valueType, ResourcePoolsBuilder resourcePoolsBuilder);

    public <K, V> Cache<K, V> getOrCreateCache(String alias, Class<K> keyType, Class<V> valueType);

}
