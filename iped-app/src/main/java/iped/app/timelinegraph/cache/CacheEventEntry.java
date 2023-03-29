package iped.app.timelinegraph.cache;

import com.zaxxer.sparsebits.SparseBitSet;

/*
 * Represent a cache event entry on cache persistance
 */
public class CacheEventEntry {
    public String event;
    public SparseBitSet docIds;
}