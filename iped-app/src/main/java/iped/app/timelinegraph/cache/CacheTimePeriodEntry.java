package iped.app.timelinegraph.cache;

import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

/*
 * Represent a cache timeperiod entry on cache persistance
 */
public class CacheTimePeriodEntry implements Comparable<CacheTimePeriodEntry> {
    public volatile long date;
    public ArrayList<CacheEventEntry> events;
    
    
    public CacheTimePeriodEntry() {
    }

    @Override
    public int compareTo(CacheTimePeriodEntry entry) {
        return (int)(this.date-entry.date);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CacheTimePeriodEntry) {
            return this.date == ((CacheTimePeriodEntry) obj).date;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (int) date;
    }

    public Date getDate() {
        return new Date(date);
    }

}
