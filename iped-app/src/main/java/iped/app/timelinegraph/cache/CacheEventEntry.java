package iped.app.timelinegraph.cache;

import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

/*
 * Represent a cache event entry on cache persistance
 */
public class CacheEventEntry {
    public String event;
    public IntArrayList docIds;
}