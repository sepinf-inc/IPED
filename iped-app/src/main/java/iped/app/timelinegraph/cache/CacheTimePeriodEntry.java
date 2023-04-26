package iped.app.timelinegraph.cache;

import java.util.ArrayList;
import java.util.Date;

import org.roaringbitmap.RoaringBitmap;

/*
 * Represent a cache timeperiod entry on cache persistance
 */
public class CacheTimePeriodEntry implements Comparable<CacheTimePeriodEntry> {
    public volatile long date;
    public ArrayList<CacheEventEntry> events = new ArrayList<CacheEventEntry>();
    RoaringBitmap eventOrds;
    RoaringBitmap[] docids;
    
    
    public CacheTimePeriodEntry() {
    }

    @Override
    public int compareTo(CacheTimePeriodEntry entry) {
        return date<entry.date?-1:date>entry.date?1:0;
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
    
    public CacheEventEntry[] getEvents() {
        return events.toArray(new CacheEventEntry[0]);
        
    }

    public void addEventEntry(CacheEventEntry ce) {
        events.add(ce);
        
    }

    public void addEventEntry(String eventType, RoaringBitmap docs) {
        CacheEventEntry[] events = this.getEvents();
        CacheEventEntry selectedCe = null;
        for (int i = 0; i < events.length; i++) {
            CacheEventEntry ce = events[i];
            if (ce.event.equals(eventType)) {
                selectedCe = ce;
                break;
            }
        }
        if (selectedCe == null) {
            selectedCe = new CacheEventEntry();
            selectedCe.event = eventType;
            selectedCe.docIds = docs;
            this.addEventEntry(selectedCe);
        }else {
            selectedCe.docIds = docs;
        }
    }

}
