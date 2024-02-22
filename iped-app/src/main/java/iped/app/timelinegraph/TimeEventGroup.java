package iped.app.timelinegraph;

import java.util.Collection;
import java.util.HashSet;

import org.roaringbitmap.RoaringBitmap;

/**
 * Class that represents a collection of event to use as a filter to graph
 * events viewing. Also, the cache will be managed/persisted based on this event
 * groups
 * 
 * @author Patrick Dalla Bernardina patrick.dalla@gmail.com
 */
public class TimeEventGroup {
    public static final TimeEventGroup ALL_EVENTS = new TimeEventGroup();
    public static final TimeEventGroup BASIC_EVENTS = new TimeEventGroup("BasicProperties");

    HashSet eventNames = new HashSet<String>();
    RoaringBitmap eventOrds = new RoaringBitmap();

    String name;

    private TimeEventGroup() {
        this.name = "ALL";
    }

    public TimeEventGroup(String name) {
        this.name = name;
    }

    public boolean hasEvent(String eventType) {
        if (this == ALL_EVENTS) {
            return true;
        }
        return eventNames.contains(eventType);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TimeEventGroup) {
            return name.equals(((TimeEventGroup) obj).name);
        } else {
            return true;
        }
    }

    public String getName() {
        return name;
    }

    public void addEvent(String eventName, int ord) {
        eventNames.add(eventName);
        eventOrds.add(ord);
    }

    @Override
    public String toString() {
        return name;
    }

    public Collection<? extends String> getEventNames() {
        return eventNames;
    }

    public RoaringBitmap getEventOrds() {
        return eventOrds;
    }
}