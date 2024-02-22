package iped.app.timelinegraph.cache;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jfree.data.time.TimePeriod;
import org.roaringbitmap.RoaringBitmap;

import iped.app.timelinegraph.IpedChartsPanel;
import iped.app.timelinegraph.TimeEventGroup;
import iped.app.timelinegraph.cache.persistance.CachePersistence;
import iped.engine.core.Manager;
import iped.viewers.api.IMultiSearchResultProvider;

public class IndexTimeStampCache implements TimeStampCache {

    private static final Logger logger = LogManager.getLogger(IndexTimeStampCache.class);

    ArrayList<Class<? extends TimePeriod>> periodClassesToCache = new ArrayList<Class<? extends TimePeriod>>();

    AtomicInteger running = new AtomicInteger();
    Semaphore timeStampCacheSemaphore = new Semaphore(1);
    IMultiSearchResultProvider resultsProvider;
    IpedChartsPanel ipedChartsPanel;
    TimeZone timezone;
    TimeEventGroup teGroup;// default TimeEventGroup

    TimeIndexedMap newCache = new TimeIndexedMap();
    AtomicBoolean loading = new AtomicBoolean(false);

    public IndexTimeStampCache(IpedChartsPanel ipedChartsPanel, IMultiSearchResultProvider resultsProvider) {
        this.resultsProvider = resultsProvider;
        this.ipedChartsPanel = ipedChartsPanel;
        this.timezone = ipedChartsPanel.getTimeZone();
        try {
            timeStampCacheSemaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    SortedSet<String> eventTypes = new TreeSet<String>();
    final Object monitor = new Object();

    @Override
    public void run() {
        loading.set(true);
        int oldPriority = Thread.currentThread().getPriority();
        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
        try {

            if (newCache.size() > 0) {
                return;
            }

            boolean cacheExists = false;

            if (periodClassesToCache.size() == 0) {
                periodClassesToCache.add(ipedChartsPanel.getTimePeriodClass());
            }

            for (Class periodClasses : periodClassesToCache) {
                CachePersistence cp = CachePersistence.getInstance();
                try {
                    TimeIndexedMap c = cp.loadNewCache(teGroup, periodClasses);
                    if (c != null) {
                        cacheExists = true;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (!cacheExists) {
                Date d1 = new Date();
                logger.info("Starting to build time cache of [{}]...", periodClassesToCache.toString());

                ArrayList<EventTimestampCache> cacheLoaders = new ArrayList<EventTimestampCache>();

                String[] cachedEventNames = ipedChartsPanel.getOrdToEventName();

                int ord = 0;
                while (ord < cachedEventNames.length) {
                    String eventType = cachedEventNames[ord];
                    if (eventType != null && !eventType.isEmpty() && teGroup.hasEvent(eventType)) {
                        cacheLoaders.add(new EventTimestampCache(ipedChartsPanel, resultsProvider, this, cachedEventNames[ord], ord));
                    }
                    ord++;
                }
                running.set(cacheLoaders.size());

                ExecutorService threadPool = Executors.newFixedThreadPool(1);
                for (EventTimestampCache cacheLoader : cacheLoaders) {
                    threadPool.execute(cacheLoader);
                }

                try {
                    synchronized (monitor) {
                        monitor.wait();

                        if (Manager.getInstance() != null && Manager.getInstance().isProcessingFinished()) {
                        }
                        CachePersistence cp = CachePersistence.getInstance();
                        cp.saveNewCache(this);

                        cacheLoaders.clear();

                        newCache = null;// liberates data used to create indexes for garbage collection

                        newCache = new TimeIndexedMap();
                        for (Class periodClasses : periodClassesToCache) {
                            newCache.setIndexFile(teGroup, periodClasses.getSimpleName(), cp.getBaseDir());
                            LinkedHashSet<CacheTimePeriodEntry> times = new LinkedHashSet<CacheTimePeriodEntry>();
                            newCache.put(periodClasses.getSimpleName(), times);
                        }

                        newCache.createOrLoadUpperPeriodIndex(this);

                        Date d2 = new Date();
                        logger.info("Time to build timeline index of [{}]: {}ms", periodClassesToCache.toString(), (d2.getTime() - d1.getTime()));
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            } else {
                CachePersistence cp = CachePersistence.getInstance();
                for (Class periodClasses : periodClassesToCache) {
                    newCache.setIndexFile(teGroup, periodClasses.getSimpleName(), cp.getBaseDir());
                }
                newCache.createOrLoadUpperPeriodIndex(this);
            }
            ipedChartsPanel.getIpedTimelineDatasetManager().setCacheLoaded(true);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Thread.currentThread().setPriority(oldPriority);
            timeStampCacheSemaphore.release();
        }
    }

    public void addTimePeriodClassToCache(Class<? extends TimePeriod> timePeriodClass) {
        periodClassesToCache.add(timePeriodClass);
    }

    public boolean hasTimePeriodClassToCache(Class<? extends TimePeriod> timePeriodClass) {
        return periodClassesToCache.contains(timePeriodClass);
    }

    @Override
    public ArrayList<Class<? extends TimePeriod>> getPeriodClassesToCache() {
        return periodClassesToCache;
    }

    @Override
    public Map<String, List<CacheTimePeriodEntry>> getCachedList() {
        // TODO Auto-generated method stub
        return null;
    }

    public RoaringBitmap add(Class<? extends TimePeriod> timePeriodClass, Date t, String eventType, RoaringBitmap docs) {
        PersistedArrayList l = (PersistedArrayList) newCache.get(timePeriodClass.getSimpleName());
        if (l == null) {
            l = new PersistedArrayList(timePeriodClass);
            newCache.put(timePeriodClass.getSimpleName(), l);
        }

        if (docs == null) {
            docs = new RoaringBitmap();
        }

        CacheTimePeriodEntry selectedCt = null;
        CacheEventEntry selectedCe = null;

        selectedCt = l.get(t.getTime());

        if (selectedCt == null) {
            selectedCt = new CacheTimePeriodEntry();
            selectedCt.date = t.getTime();
            l.add(selectedCt);
        }

        selectedCt.addEventEntry(eventType, docs);

        return docs;
    }

    public RoaringBitmap add(Class<? extends TimePeriod> timePeriodClass, Date t, int eventInternalOrd, RoaringBitmap docs) {
        PersistedArrayList l = (PersistedArrayList) newCache.get(timePeriodClass.getSimpleName());
        if (l == null) {
            l = new PersistedArrayList(timePeriodClass);
            newCache.put(timePeriodClass.getSimpleName(), l);
        }

        if (docs == null) {
            docs = new RoaringBitmap();
        }

        CacheTimePeriodEntry selectedCt = null;
        CacheEventEntry selectedCe = null;

        selectedCt = l.get(t.getTime());

        if (selectedCt == null) {
            selectedCt = new CacheTimePeriodEntry();
            selectedCt.date = t.getTime();
            l.add(selectedCt);
        }

        selectedCt.addEventEntry(eventInternalOrd, docs);

        return docs;
    }

    public RoaringBitmap get(Class<? extends TimePeriod> timePeriodClass, Date t, Integer eventInternalOrd) {
        PersistedArrayList l = (PersistedArrayList) newCache.get(timePeriodClass.getSimpleName());
        if (l == null) {
            l = new PersistedArrayList(timePeriodClass);
            newCache.put(timePeriodClass.getSimpleName(), l);
        }
        CacheTimePeriodEntry selectedCt = l.get(t.getTime());

        if (selectedCt == null) {
            return null;
        }

        RoaringBitmap result = selectedCt.getEventDocIds(eventInternalOrd);
        if (result == null) {
            return null;
        }

        return result;
    }

    public RoaringBitmap get(Class<? extends TimePeriod> timePeriodClass, Date t, String eventType) {
        PersistedArrayList l = (PersistedArrayList) newCache.get(timePeriodClass.getSimpleName());
        if (l == null) {
            l = new PersistedArrayList(timePeriodClass);
            newCache.put(timePeriodClass.getSimpleName(), l);
        }
        CacheTimePeriodEntry selectedCt = l.get(t.getTime());

        if (selectedCt == null) {
            return null;
        }

        RoaringBitmap result = selectedCt.getEventDocIds(eventType);
        if (result == null) {
            return null;
        }

        return result;
    }

    @Override
    public Map<String, Set<CacheTimePeriodEntry>> getNewCache() {
        return newCache;
    }

    @Override
    public TimeZone getCacheTimeZone() {
        return this.timezone;
    }

    public void add(Class<? extends TimePeriod> timePeriodClass, Date t, Integer eventInternalOrd, int doc) {
        PersistedArrayList l = (PersistedArrayList) newCache.get(timePeriodClass.getSimpleName());
        if (l == null) {
            l = new PersistedArrayList(timePeriodClass);
            synchronized (this) {
                newCache.put(timePeriodClass.getSimpleName(), l);
            }
        }

        CacheTimePeriodEntry selectedCt = null;
        CacheEventEntry selectedCe = null;

        selectedCt = l.get(t.getTime());

        if (selectedCt == null) {
            selectedCt = new CacheTimePeriodEntry();
            selectedCt.date = t.getTime();
            l.add(selectedCt);
        }

        selectedCt.addEventEntry(eventInternalOrd, doc);
    }

    @Override
    public boolean isFromEventGroup(TimeEventGroup teGroup) {
        return this.teGroup.equals(teGroup);
    }

    @Override
    public void setTimeEventGroup(TimeEventGroup teGroup) {
        this.teGroup = teGroup;
    }

    @Override
    public TimeEventGroup getTimeEventGroup() {
        return this.teGroup;
    }

    @Override
    public boolean isFromEventGroup(ArrayList<TimeEventGroup> selectedTeGroups) {
        for (TimeEventGroup teGroup : selectedTeGroups) {
            if (isFromEventGroup(teGroup)) {
                return true;
            }
        }
        return false;
    }

    public boolean isLoading() {
        return loading.get();
    }

}