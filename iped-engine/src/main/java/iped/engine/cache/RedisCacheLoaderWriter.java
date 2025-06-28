package iped.engine.cache;

import org.ehcache.spi.loaderwriter.CacheLoaderWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

public class RedisCacheLoaderWriter<K, V> implements CacheLoaderWriter<K, V> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisCacheLoaderWriter.class);

    private final JedisPool jedisPool;
    private final Class<V> valueType;
    private final String keyPrefix;

    private final Gson gson = new GsonBuilder() //
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE) //
            .create();

    public RedisCacheLoaderWriter(JedisPool jedisPool, String keyPrefix, Class<V> valueType) {
        this.jedisPool = jedisPool;
        this.valueType = valueType;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public void write(K key, V value) {
        LOGGER.debug("Write-through for key '{}'", key);

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(keyPrefix + key.toString(), gson.toJson(value));
        } catch (JedisException e) {
            LOGGER.error("Write error: {}", e.getMessage());
            LOGGER.warn("Could not write data to Redis for key '{}'", key, e);
        }
    }

    @Override
    public V load(K key) throws Exception {
        LOGGER.debug("Ehcache miss for key '{}'. Loading from Redis...", key);
        String jsonPayload;
        try (Jedis jedis = jedisPool.getResource()) {

            jsonPayload = jedis.get(keyPrefix + key.toString());
            if (jsonPayload == null) {
                LOGGER.debug("Redis MISS for key '{}'", key);
                return null;
            }

            LOGGER.debug("Redis HIT for key '{}'", key);
            return gson.fromJson(jsonPayload, valueType);

        } catch (JedisException e) {
            LOGGER.error("Load error: {}", key, e.getMessage());
            LOGGER.warn("Could not load from Redis for key '{}'", key, e);
            return null; // Don't crash app if Redis is down
        }
    }

    @Override
    public void delete(K key) {
        // Standard implementation to delete from Redis
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(keyPrefix + key.toString());
        } catch (JedisException e) {
            LOGGER.error("Delete error: {}", key, e.getMessage());
            LOGGER.warn("Could not delete from Redis for key '{}'", key, e);
        }
    }
}