package iped.app.timelinegraph.cache;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;
import org.jfree.data.time.TimePeriod;

import com.zaxxer.sparsebits.SparseBitSet;

import iped.app.timelinegraph.IpedChartsPanel;
import iped.app.timelinegraph.cache.persistance.CachePersistance;
import iped.engine.core.Manager;
import iped.properties.ExtraProperties;
import iped.viewers.api.IMultiSearchResultProvider;

public class IndexTimeStampCache implements TimeStampCache {

    private static final Logger logger = LogManager.getLogger(IndexTimeStampCache.class);

    ArrayList<Class<? extends TimePeriod>> periodClassesToCache = new ArrayList<Class<? extends TimePeriod>>();

    AtomicInteger running = new AtomicInteger();
    Semaphore timeStampCacheSemaphore = new Semaphore(1);
    IMultiSearchResultProvider resultsProvider;
    IpedChartsPanel ipedChartsPanel;
    TimeZone timezone;

    TimeIndexedMap newCache = new TimeIndexedMap();
    Map<String, Map<TimePeriod, CacheTimePeriodEntry>> timePeriodEntryIndex = new HashMap<String, Map<TimePeriod, CacheTimePeriodEntry>>();

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
        try {

            if (newCache.size() > 0) {
                return;
            }

            Date d1 = new Date();
            logger.info("Starting to load/build time cache of [{}]...", periodClassesToCache.toString());

            boolean cacheExists = false;

            if (periodClassesToCache.size() == 0) {
                periodClassesToCache.add(ipedChartsPanel.getTimePeriodClass());
            }
            for (Class periodClasses : periodClassesToCache) {
                CachePersistance cp = new CachePersistance();
                try {
                    TimeIndexedMap c = cp.loadNewCache(periodClasses);
                    if (c.size() > 0) {
                        cacheExists = true;
                        newCache.putAll(c);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (!cacheExists) {
                LeafReader reader = resultsProvider.getIPEDSource().getLeafReader();

                SortedSetDocValues timeEventGroupValues = reader.getSortedSetDocValues(ExtraProperties.TIME_EVENT_GROUPS);

                ArrayList<EventTimestampCache> cacheLoaders = new ArrayList<EventTimestampCache>();

                TermsEnum te = timeEventGroupValues.termsEnum();
                BytesRef br = te.next();
                while (br != null) {
                    StringTokenizer st = new StringTokenizer(br.utf8ToString(), "|");
                    while (st.hasMoreTokens()) {
                        String eventType = st.nextToken().trim();
                        if (eventTypes.add(eventType)) {
                            cacheLoaders.add(new EventTimestampCache(ipedChartsPanel, resultsProvider, this, eventType));
                        }
                    }
                    br = te.next();
                }
                running.set(cacheLoaders.size());

                ExecutorService threadPool = Executors.newFixedThreadPool(1);
                for (EventTimestampCache cacheLoader : cacheLoaders) {
                    threadPool.execute(cacheLoader);
                }

                try {
                    synchronized (monitor) {
                        monitor.wait();

                        Date d2 = new Date();
                        logger.info("Time to build time cache of [{}]: {}ms", periodClassesToCache.toString(), (d2.getTime() - d1.getTime()));
                        if (Manager.getInstance() != null && Manager.getInstance().isProcessingFinished()) {
                        }
                        CachePersistance cp = new CachePersistance();
                        cp.saveNewCache(this);

                        newCache.clearCache();
                        timePeriodEntryIndex.clear();
                        cacheLoaders.clear();
                        TimelineCache.get().clear();//clear old empty timeline entries to be reloaded with created cache data

                        for (Class periodClasses : periodClassesToCache) {
                            newCache.setIndexFile(periodClasses.getSimpleName(), cp.getBaseDir());
                            ArrayList<CacheTimePeriodEntry> times = new ArrayList<CacheTimePeriodEntry>();
                            newCache.put(periodClasses.getSimpleName(), times);
                        }
                        newCache.createOrLoadDayIndex(this);

                        newCache = null;//liberates data used to create indexes for garbage collection

                        newCache = new TimeIndexedMap();
                        for (Class periodClasses : periodClassesToCache) {
                            newCache.setIndexFile(periodClasses.getSimpleName(), cp.getBaseDir());
                            ArrayList<CacheTimePeriodEntry> times = new ArrayList<CacheTimePeriodEntry>();
                            newCache.put(periodClasses.getSimpleName(), times);
                        }

                        newCache.createOrLoadDayIndex(this);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            } else {
                Date d2 = new Date();

                logger.info("Time to load time cache of [{}]: {}ms", periodClassesToCache.toString(), (d2.getTime() - d1.getTime()));
                CachePersistance cp = new CachePersistance();
                for (Class periodClasses : periodClassesToCache) {
                    newCache.setIndexFile(periodClasses.getSimpleName(), cp.getBaseDir());
                }
                newCache.createOrLoadDayIndex(this);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            timeStampCacheSemaphore.release();
        }
    }

    public void addTimePeriodClassToCache(Class<? extends TimePeriod> timePeriodClass) {
        periodClassesToCache.add(timePeriodClass);
    }

    public boolean hasTimePeriodClassToCache(Class<? extends TimePeriod> timePeriodClass) {
        try {
            timeStampCacheSemaphore.acquire();// pause until cache is populated
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            timeStampCacheSemaphore.release();
        }
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

    public void add(Class<? extends TimePeriod> timePeriodClass, TimePeriod t, String eventType, SparseBitSet docs) {
        List<CacheTimePeriodEntry> l = newCache.get(timePeriodClass.getSimpleName());
        if (l == null) {
            l = new ArrayList<CacheTimePeriodEntry>();
            newCache.put(timePeriodClass.getSimpleName(), l);
        }

        Map<TimePeriod, CacheTimePeriodEntry> timePeriodIndexEntry = timePeriodEntryIndex.get(timePeriodClass.getSimpleName());
        if (timePeriodIndexEntry == null) {
            timePeriodIndexEntry = new HashMap<TimePeriod, CacheTimePeriodEntry>();
            timePeriodEntryIndex.put(timePeriodClass.getSimpleName(), timePeriodIndexEntry);
        }

        CacheTimePeriodEntry selectedCt = null;
        CacheEventEntry selectedCe = null;

        selectedCt = timePeriodIndexEntry.get(t);

        if (selectedCt == null) {
            selectedCt = new CacheTimePeriodEntry();
            selectedCt.events = new ArrayList<CacheEventEntry>();
            selectedCt.date = t.getStart();
            l.add(selectedCt);
            timePeriodIndexEntry.put(t, selectedCt);
        }

        for (int i = 0; i < selectedCt.events.size(); i++) {
            CacheEventEntry ce = selectedCt.events.get(i);
            if (ce.event.equals(eventType)) {
                selectedCe = ce;
                break;
            }
        }
        if (selectedCe == null) {
            selectedCe = new CacheEventEntry();
            selectedCt.events.add(selectedCe);
            selectedCe.event = eventType;
        }
        selectedCe.docIds = docs;
    }

    public SparseBitSet get(Class<? extends TimePeriod> timePeriodClass, TimePeriod t, String eventType) {
        Map<TimePeriod, CacheTimePeriodEntry> timePeriodIndexEntry = timePeriodEntryIndex.get(timePeriodClass.getSimpleName());

        if (timePeriodIndexEntry == null) {
            return null;
        }

        CacheTimePeriodEntry selectedCt = timePeriodIndexEntry.get(t);

        if (selectedCt == null) {
            return null;
        }

        CacheEventEntry selectedCe = null;
        for (int i = 0; i < selectedCt.events.size(); i++) {
            CacheEventEntry ce = selectedCt.events.get(i);
            if (ce.event.equals(eventType)) {
                selectedCe = ce;
                break;
            }
        }

        if (selectedCe == null) {
            return null;
        }

        return selectedCe.docIds;
    }

    @Override
    public Map<String, List<CacheTimePeriodEntry>> getNewCache() {
        return newCache;
    }

    @Override
    public TimeZone getCacheTimeZone() {
        return this.timezone;
    }

}