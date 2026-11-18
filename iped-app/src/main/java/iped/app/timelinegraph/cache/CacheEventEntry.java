package iped.app.timelinegraph.cache;

import org.roaringbitmap.RoaringBitmap;

import iped.app.timelinegraph.IpedChartsPanel;

/*
 * Represent a cache event entry on cache persistance
 */
public class CacheEventEntry {
    public String event = null;
    int eventOrd = -1;
    public RoaringBitmap docIds;

    public CacheEventEntry(int eventOrd) {
        this.eventOrd = eventOrd;
    }

    public int getEventOrd() {
        if (eventOrd == -1) {
            eventOrd = IpedChartsPanel.getEventOrd(event);
        }
        return eventOrd;
    }

    public String getEventName() {
        if (event == null) {
            event = IpedChartsPanel.getEventName(eventOrd);
        }
        return event;
    }
}