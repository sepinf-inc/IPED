package iped.app.timelinegraph.cache;

import java.util.Date;

import org.roaringbitmap.RoaringBitmap;

import iped.app.timelinegraph.IpedChartsPanel;
import scala.Array;

/*
 * Represent a cache timeperiod entry on cache persistance
 */
public class CacheTimePeriodEntry implements Comparable<CacheTimePeriodEntry> {
    public volatile long date;
    RoaringBitmap eventOrds = new RoaringBitmap();
    RoaringBitmap[] docids;

    public CacheTimePeriodEntry() {
    }

    @Override
    public int compareTo(CacheTimePeriodEntry entry) {
        return date < entry.date ? -1 : date > entry.date ? 1 : 0;
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

    public RoaringBitmap getEventsOrds() {
        return eventOrds;
    }

    synchronized public CacheEventEntry[] getEvents() {
        CacheEventEntry[] e = new CacheEventEntry[eventOrds.getCardinality()];
        int i = 0;
        for (int ord : eventOrds) {
            CacheEventEntry ce = new CacheEventEntry(ord);
            ce.docIds = docids[i];
            e[i] = ce;
            i++;
        }
        return e;

    }

    public void addEventEntry(CacheEventEntry ce) {
        addEventEntry(ce.eventOrd, ce.docIds);
    }

    synchronized public RoaringBitmap getEventDocIds(int ord) {
        if (docids == null) {
            return null;
        }
        if (eventOrds.getCardinality() == 0) {
            return null;
        }
        if (!eventOrds.contains(ord)) {
            return null;
        }
        int pos = (int) eventOrds.rangeCardinality(0, ord);
        if (pos >= docids.length) {
            return null;
        }
        return docids[pos];
    }

    synchronized public RoaringBitmap getEventDocIds(String event) {
        if (docids == null) {
            return null;
        }
        if (eventOrds.getCardinality() == 0) {
            return null;
        }
        Integer ord = IpedChartsPanel.getEventOrd(event);
        if (ord == null) {
            return null;
        }
        if (!eventOrds.contains(ord)) {
            return null;
        }
        int pos = (int) eventOrds.rangeCardinality(0, ord - 1);
        if (pos >= docids.length) {
            return null;
        }
        return docids[pos];
    }

    synchronized public void addEventEntry(int ord, RoaringBitmap pdocids) {
        eventOrds.add(ord);
        int pos = 0;
        if (docids != null) {
            pos = (int) eventOrds.rangeCardinality(0, ord);
            RoaringBitmap[] ldocids = new RoaringBitmap[docids.length + 1];
            Array.copy(docids, 0, ldocids, 0, pos);
            Array.copy(docids, pos, ldocids, pos + 1, docids.length - pos);
            docids = ldocids;
        } else {
            docids = new RoaringBitmap[1];
        }
        docids[pos] = pdocids;
    }

    synchronized public void addEventEntry(String event, RoaringBitmap pdocids) {
        int ord = IpedChartsPanel.getEventOrd(event);
        addEventEntry(ord, pdocids);
    }

    synchronized public void addEventEntry(int ord, int doc) {
        eventOrds.add(ord);
        int pos = 0;
        if (docids != null) {
            pos = (int) eventOrds.rangeCardinality(0, ord);
            if (pos >= docids.length) {
                RoaringBitmap[] ldocids = new RoaringBitmap[docids.length + 1];
                Array.copy(docids, 0, ldocids, 0, pos);
                Array.copy(docids, pos, ldocids, pos + 1, docids.length - pos);
                docids = ldocids;
            }
        } else {
            docids = new RoaringBitmap[1];
        }
        if (docids[pos] == null) {
            docids[pos] = new RoaringBitmap();
        }
        docids[pos].add(doc);
    }

}
