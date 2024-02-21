package iped.app.timelinegraph.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.jfree.data.time.TimePeriod;

import iped.app.timelinegraph.TimeEventGroup;


public interface TimeStampCache extends Runnable {
    public void addTimePeriodClassToCache(Class<? extends TimePeriod> timePeriodClass);

    public boolean hasTimePeriodClassToCache(Class<? extends TimePeriod> timePeriodClass);

    public ArrayList<Class<? extends TimePeriod>> getPeriodClassesToCache();

    public Map<String, List<CacheTimePeriodEntry>> getCachedList();

    public Map<String, Set<CacheTimePeriodEntry>> getNewCache();

    public TimeZone getCacheTimeZone();

    public boolean isFromEventGroup(TimeEventGroup teGroup);

    public void setTimeEventGroup(TimeEventGroup teGroup);

    public TimeEventGroup getTimeEventGroup();
}
