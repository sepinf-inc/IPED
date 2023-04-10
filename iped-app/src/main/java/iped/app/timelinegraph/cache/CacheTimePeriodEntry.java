package iped.app.timelinegraph.cache;

import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

/*
 * Represent a cache timeperiod entry on cache persistance
 */
public class CacheTimePeriodEntry implements Comparable<CacheTimePeriodEntry> {
    public AtomicLong date;
    public ArrayList<CacheEventEntry> events;
    
    
    public CacheTimePeriodEntry() {
        date = new AtomicLong();
    }

    @Override
    public int compareTo(CacheTimePeriodEntry entry) {
        return (int)(this.date.get()-entry.date.get());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CacheTimePeriodEntry) {
            return this.date.equals(((CacheTimePeriodEntry) obj).date);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return date.hashCode();
    }

    public Date getDate() {
        return new Date(date.get());
    }

}
