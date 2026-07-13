package iped.utils;

import java.util.concurrent.locks.ReentrantLock;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

public class LockManager<T> {

    // This cache will create locks on demand and allow them to be garbage-collected
    // when they are no longer in use.
    private final LoadingCache<T, ReentrantLock> lockCache = Caffeine
            .newBuilder()
            .weakValues()
            .build(key -> new ReentrantLock());

    public ReentrantLock getLock(T key) {
        return lockCache.get(key);
    }
}