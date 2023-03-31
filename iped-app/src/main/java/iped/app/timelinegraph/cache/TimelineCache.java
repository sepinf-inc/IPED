package iped.app.timelinegraph.cache;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class TimelineCache {
    Date startDate;
    Date endDate;
    String period;

    HashMap<String, Map<Long, Integer>> cachesIndexes = new HashMap<String, Map<Long, Integer>>();
    HashMap<String, CacheTimePeriodEntry[]> caches = new HashMap<String, CacheTimePeriodEntry[]>();
    String lastCachePeriod = null;

    static TimelineCache singleton = new TimelineCache();

    static public TimelineCache get() {
        return singleton;
    }

    private TimelineCache() {
    }

    public void clean(String period, Date startDate, Date endDate) {
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

    public void clear() {
        caches.clear();
    }

    public CacheTimePeriodEntry[] get(String className, int size) {
        if (!(className.equals("Day") || className.equals("Month") || className.equals("Quarter") || className.equals("Year") || className.equals("Week"))) {
            return null;
        }
        if (!className.equals(lastCachePeriod)) {
            if (lastCachePeriod != null) {
                CacheTimePeriodEntry[] cache = caches.get(lastCachePeriod);
                if (cache != null) {
                    for (int i = 0; i < cache.length; i++) {
                        cache[i] = null;// old cache entry eligible to garbage collection
                    }
                }
            }
            lastCachePeriod = className;
        }

        CacheTimePeriodEntry[] cache = caches.get(className);
        if (cache == null) {
            cache = new CacheTimePeriodEntry[size];
            caches.put(className, cache);
            Map<Long, Integer> cacheIndex;
            cacheIndex = new TreeMap<Long, Integer>();
            this.cachesIndexes.put(className, cacheIndex);
        }

        return cache;
    }

    public Integer getIndex(String className, long pos) {
        return cachesIndexes.get(className).get(pos);
    }
}
