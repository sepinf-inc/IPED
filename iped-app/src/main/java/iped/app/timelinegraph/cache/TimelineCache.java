package iped.app.timelinegraph.cache;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeMap;

public class TimelineCache {
    Date startDate;
    Date endDate;
    String period;

    HashMap<String, TreeMap<Long, Integer>> cachesIndexes = new HashMap<String, TreeMap<Long, Integer>>();
    HashMap<String, CacheTimePeriodEntry[]> caches = new HashMap<String, CacheTimePeriodEntry[]>();
    HashMap<String, Reference<CacheTimePeriodEntry>[]> softCaches = new HashMap<String, Reference<CacheTimePeriodEntry>[]>();
    String lastCachePeriod = null;
    
    static HashSet<String> neverUnloadCache = new HashSet<String>();
    static {
        //neverUnloadCache.add("Day");
    }

    static TimelineCache singleton = new TimelineCache();

    static public TimelineCache get() {
        return singleton;
    }

    private TimelineCache() {
    }

    public void clean(String period, Date startDate, Date endDate) {
        if(!neverUnloadCache.contains(period)) {
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    CacheTimePeriodEntry[] cache = caches.get(period);
                    for (Iterator<String> iterator = caches.keySet().iterator(); iterator.hasNext();) {
                        String lperiod = (String) iterator.next();
                        if (lperiod.equals(period)) {
                            for (int i = 0; i < cache.length; i++) {
                                if (cache[i] != null && cache[i].date.before(startDate) && cache[i].date.after(endDate)) {
                                    cache[i] = null;// old cache entry eligible to garbage collection
                                }
                            }
                        }
                    }
                }
            };
            r.run();
        }
    }

    public void clear() {
        caches.clear();
    }

    public void cleanPeriod(String period) {
        if(!neverUnloadCache.contains(period)) {
            CacheTimePeriodEntry[] cache = caches.get(period);
            if (cache != null) {
                for (int i = 0; i < cache.length; i++) {
                    cache[i] = null;// old cache entry eligible to garbage collection
                }
            }
        }
    }
    
    public CacheTimePeriodEntry[] get(String className, int size) {
        if (!className.equals(lastCachePeriod)) {
            if (lastCachePeriod != null) {
                cleanPeriod(lastCachePeriod);
            }
            lastCachePeriod = className;
        }

        CacheTimePeriodEntry[] cache = caches.get(className);
        if (cache == null) {
            cache = new CacheTimePeriodEntry[size];
            caches.put(className, cache);
            if(hasSoftCacheFor(className)) {
                SoftReference<CacheTimePeriodEntry>[] softCache = new SoftReference[size];
                softCaches.put(className, softCache);
            }
        }

        return cache;
    }

    /* returns the index in the cache of the entry in the especified file position */
    public Integer getIndex(String className, long pos) {
        return cachesIndexes.get(className).get(pos);
    }

    public TreeMap<Long, Integer> getCachesIndexes(String className) {
        TreeMap<Long, Integer> cacheIndex = this.cachesIndexes.get(className);
        if(cacheIndex==null) {
            cacheIndex = new TreeMap<Long, Integer>();
            this.cachesIndexes.put(className, cacheIndex);
        }
        return cacheIndex;
    }

    public boolean hasSoftCacheFor(String className) {
        return className.equals("Day")||className.equals("Week")||className.equals("Month");
    }
}
