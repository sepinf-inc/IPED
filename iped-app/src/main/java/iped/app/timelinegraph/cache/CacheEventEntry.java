package iped.app.timelinegraph.cache;

import java.util.ArrayList;

import org.roaringbitmap.RoaringBitmap;

/*
 * Represent a cache event entry on cache persistance
 */
public class CacheEventEntry {
    public String event;
    public RoaringBitmap docIds;
}