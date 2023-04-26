package iped.app.timelinegraph.cache;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.roaringbitmap.RoaringBitmap;

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

            boolean cacheExists = false;

            if (periodClassesToCache.size() == 0) {
                periodClassesToCache.add(ipedChartsPanel.getTimePeriodClass());
            }
            for (Class periodClasses : periodClassesToCache) {
                CachePersistance cp = CachePersistance.getInstance();
                try {
                    TimeIndexedMap c = cp.loadNewCache(periodClasses);
                    if (c!=null) {
                        cacheExists = true;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (!cacheExists) {
                Date d1 = new Date();
                logger.info("Starting to load/build time cache of [{}]...", periodClassesToCache.toString());
                
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

                        if (Manager.getInstance() != null && Manager.getInstance().isProcessingFinished()) {
                        }
                        CachePersistance cp = new CachePersistance();
                        cp.saveNewCache(this);

                        newCache.clearCache();
                        cacheLoaders.clear();
                        TimelineCache.get().clear();// clear old empty timeline entries to be reloaded with created cache data

                        Date d2 = new Date();
                        logger.info("Time to build timeline index of [{}]: {}ms", periodClassesToCache.toString(), (d2.getTime() - d1.getTime()));

                        newCache = null;// liberates data used to create indexes for garbage collection

                        newCache = new TimeIndexedMap();
                        for (Class periodClasses : periodClassesToCache) {
                            newCache.setIndexFile(periodClasses.getSimpleName(), cp.getBaseDir());
                            LinkedHashSet<CacheTimePeriodEntry> times = new LinkedHashSet<CacheTimePeriodEntry>();
                            newCache.put(periodClasses.getSimpleName(), times);
                        }

                        newCache.createOrLoadDayIndex(this);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            } else {
                CachePersistance cp = new CachePersistance();
                for (Class periodClasses : periodClassesToCache) {
                    newCache.setIndexFile(periodClasses.getSimpleName(), cp.getBaseDir());
                }
                newCache.createOrLoadDayIndex(this);
            }
            ipedChartsPanel.getIpedTimelineDatasetManager().setCacheLoaded(true);
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
    
    public RoaringBitmap add(Class<? extends TimePeriod> timePeriodClass, Date t, String eventType, RoaringBitmap docs) {
        PersistedArrayList l = (PersistedArrayList) newCache.get(timePeriodClass.getSimpleName());
        if (l == null) {
            l = new PersistedArrayList(timePeriodClass);
            newCache.put(timePeriodClass.getSimpleName(), l);
        }
        
        if(docs==null) {
            docs = new RoaringBitmap();
        }


        CacheTimePeriodEntry selectedCt = null;
        CacheEventEntry selectedCe = null;

        selectedCt = l.get(t.getTime());

        if (selectedCt == null) {
            selectedCt = new CacheTimePeriodEntry();
            selectedCt.date=t.getTime();
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
        
        if(docs==null) {
            docs = new RoaringBitmap();
        }


        CacheTimePeriodEntry selectedCt = null;
        CacheEventEntry selectedCe = null;

        selectedCt = l.get(t.getTime());

        if (selectedCt == null) {
            selectedCt = new CacheTimePeriodEntry();
            selectedCt.date=t.getTime();
            l.add(selectedCt);
        }

        selectedCt.addEventEntry(eventInternalOrd, docs);
        
        return docs;
    }

    public RoaringBitmap get(Class<? extends TimePeriod> timePeriodClass, Date t, Integer eventInternalOrd) {
        PersistedArrayList l = (PersistedArrayList) newCache.get(timePeriodClass.getSimpleName());
        if(l==null) {
            l = new PersistedArrayList(timePeriodClass);
            newCache.put(timePeriodClass.getSimpleName(), l);
        }
        CacheTimePeriodEntry selectedCt = l.get(t.getTime());

        if (selectedCt == null) {
            return null;
        }

        RoaringBitmap result = selectedCt.getEventDocIds(eventInternalOrd);
        if(result==null) {
            return null;
        }

        return result;
    }
    
    public RoaringBitmap get(Class<? extends TimePeriod> timePeriodClass, Date t, String eventType) {
        PersistedArrayList l = (PersistedArrayList) newCache.get(timePeriodClass.getSimpleName());
        if(l==null) {
            l = new PersistedArrayList(timePeriodClass);
            newCache.put(timePeriodClass.getSimpleName(), l);
        }
        CacheTimePeriodEntry selectedCt = l.get(t.getTime());

        if (selectedCt == null) {
            return null;
        }

        RoaringBitmap result = selectedCt.getEventDocIds(eventType);
        if(result==null) {
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

}