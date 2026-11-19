package iped.parsers.util;

import java.util.LinkedHashMap;
import java.util.Map;

public class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private static final long serialVersionUID = 1991785631453545826L;
    private final int maxSize;

    public LRUCache(int maxSize) {
        super(maxSize, 0.75f, true); 
        this.maxSize = maxSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxSize;
    }
}