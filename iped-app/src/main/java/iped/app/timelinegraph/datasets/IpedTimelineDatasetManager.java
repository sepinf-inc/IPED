package iped.app.timelinegraph.datasets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jfree.data.time.Day;
import org.jfree.data.time.Hour;
import org.jfree.data.time.Month;
import org.jfree.data.time.Quarter;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimePeriod;
import org.jfree.data.time.Week;
import org.jfree.data.time.Year;
import org.jfree.data.xy.AbstractIntervalXYDataset;

import iped.app.timelinegraph.IpedChartsPanel;
import iped.app.timelinegraph.TimeEventGroup;
import iped.app.timelinegraph.cache.IndexTimeStampCache;
import iped.app.timelinegraph.cache.TimeStampCache;
import iped.jfextensions.model.Minute;

/*
 * Implements the method to choose timeline dataset object that represents. 
 * 
 * Obs.: Currently it checks if there is an available cache. If not use a dataset with direct access to lucene resultset.
 */
public class IpedTimelineDatasetManager {
    IpedChartsPanel ipedChartsPanel;

    private static final Logger logger = LogManager.getLogger(IpedTimelineDatasetManager.class);

    List<TimeStampCache> timeStampCaches = new ArrayList<>();
    volatile boolean isCacheLoaded = false;
    ArrayList<TimeStampCache> selectedTimeStampCaches = new ArrayList<TimeStampCache>();

    public IpedTimelineDatasetManager(IpedChartsPanel ipedChartsPanel) {
        this.ipedChartsPanel = ipedChartsPanel;

        List<Class<? extends TimePeriod>> periods = Arrays.asList(Day.class, Hour.class, Year.class, Month.class, Quarter.class, Week.class, Minute.class, Second.class);
        
        // includes basic properties time event group first
        for (Class<? extends TimePeriod> period : periods) {
            TimeStampCache timeStampCache = new IndexTimeStampCache(ipedChartsPanel,
                    ipedChartsPanel.getResultsProvider());
            timeStampCache.setTimeEventGroup(TimeEventGroup.BASIC_EVENTS);
            timeStampCache.addTimePeriodClassToCache(period);
            timeStampCaches.add(timeStampCache);
        }

        for (TimeEventGroup teGroup : getTimeEventGroupsFromMetadataPrefix()) {
            if (!teGroup.equals(TimeEventGroup.BASIC_EVENTS)) {
                for (Class<? extends TimePeriod> period : periods) {
                    TimeStampCache timeStampCache = new IndexTimeStampCache(ipedChartsPanel,
                            ipedChartsPanel.getResultsProvider());
                    timeStampCache.setTimeEventGroup(teGroup);
                    timeStampCache.addTimePeriodClassToCache(period);
                    timeStampCaches.add(timeStampCache);
                }
            }
        }

    }

    public AbstractIntervalXYDataset getBestDataset(Class<? extends TimePeriod> timePeriodClass,
            ArrayList<TimeEventGroup> selectedTeGroups,
            String splitValue) {
        selectedTimeStampCaches.clear();
        try {
            for (TimeStampCache timeStampCache : timeStampCaches) {
                if (timeStampCache.hasTimePeriodClassToCache(timePeriodClass)
                        && timeStampCache.isFromEventGroup(selectedTeGroups)) {
                    selectedTimeStampCaches.add(timeStampCache);
                }
            }
            IpedTimelineDataset result = new IpedTimelineDataset(this, ipedChartsPanel.getResultsProvider(),
                    splitValue);
            return result; 
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /*
     * Start the creation of cache for timeline chart
     */
    public void startCacheCreation() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        int poolSize = 1;
        int totalItems = ipedChartsPanel.getResultsProvider().getIPEDSource().getTotalItems();
        if (getAvailableMemory() > totalItems * 100) {
            poolSize = (int) Math.ceil((float) Runtime.getRuntime().availableProcessors() / 2f);
        } else {
            logger.info("Only {}MB of free memory for {} total items. Timeline index creation will occur sequentially. ", Runtime.getRuntime().freeMemory(), totalItems);
        }
        ExecutorService threadPool = Executors.newFixedThreadPool(poolSize);
        boolean first = true;
        for (TimeStampCache timeStampCache : timeStampCaches) {
            Future<?> future = threadPool.submit(timeStampCache);
            // first loads the Day cache alone to speed up it, then run others in parallel
            if (first) {
                first = false;
                try {
                    future.get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }
        }
        threadPool.shutdown();
        try {
            threadPool.awaitTermination(12, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public Collection<TimeStampCache> getCaches() {
        return selectedTimeStampCaches;
    }

    public IpedChartsPanel getIpedChartsPanel() {
        return ipedChartsPanel;
    }

    public void waitMemory() throws InterruptedException {
        if (getAvailableMemory() < Runtime.getRuntime().maxMemory() / 2) {
            while (!isCacheLoaded) {
                Thread.sleep(100);
            }
            int tries = 0;
            while (getAvailableMemory() < 40000000) {
                Thread.sleep(1000);
                if (++tries > 30) {
                    throw new OutOfMemoryError();
                }
                System.gc();
            }
        }
    }

    public boolean isCacheLoaded() {
        return isCacheLoaded;
    }

    public void setCacheLoaded(boolean isCacheLoaded) {
        this.isCacheLoaded = isCacheLoaded;
    }

    public static long getAvailableMemory() {
        return Runtime.getRuntime().freeMemory() + Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory();
    }

    HashMap<String, TimeEventGroup> groupNames = null;

    public Collection<TimeEventGroup> getTimeEventGroupsFromMetadataPrefix() {
        if (groupNames == null) {
            groupNames = new HashMap<String, TimeEventGroup>();
            String[] cachedEventNames = ipedChartsPanel.getOrdToEventName();

            int ord = 0;
            for (String eventName : cachedEventNames) {
                String groupName;
                TimeEventGroup teGroup = TimeEventGroup.BASIC_EVENTS;
                groupName = teGroup.getName();

                // changes the group if there is a prefix
                int sepIndex = eventName.indexOf(":");
                if (sepIndex != -1) {
                    groupName = eventName.substring(0, eventName.indexOf(":"));
                    teGroup = groupNames.get(groupName);
                    if (teGroup == null) {
                        teGroup = new TimeEventGroup(groupName);
                        groupNames.put(groupName, teGroup);
                    }
                }


                teGroup.addEvent(eventName, ord);
                ord++;
            }
        }
        return groupNames.values();
    }

}
