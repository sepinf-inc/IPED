package iped.app.timelinegraph.cache;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.UnicodeUtil;
import org.jfree.data.time.Day;
import org.jfree.data.time.Hour;
import org.jfree.data.time.Month;
import org.jfree.data.time.Quarter;
import org.jfree.data.time.TimePeriod;
import org.jfree.data.time.Week;
import org.jfree.data.time.Year;
import org.roaringbitmap.RoaringBitmap;

import iped.app.timelinegraph.DateUtil;
import iped.app.timelinegraph.IpedChartsPanel;
import iped.viewers.api.IMultiSearchResultProvider;

public class EventTimestampCache implements Runnable {
    String eventType;

    IMultiSearchResultProvider resultsProvider;
    TimeStampCache timeStampCache;
    IpedChartsPanel ipedChartsPanel;
    
    public EventTimestampCache(IpedChartsPanel ipedChartsPanel, IMultiSearchResultProvider resultsProvider, TimeStampCache timeStampCache, String eventType) {
        this.eventType = eventType;
        this.resultsProvider = resultsProvider;
        this.timeStampCache = timeStampCache;
        this.ipedChartsPanel = ipedChartsPanel;
    }

    public void run() {
        LeafReader reader = resultsProvider.getIPEDSource().getLeafReader();

        IndexTimeStampCache timeStampCache = (IndexTimeStampCache) this.timeStampCache;

        DocIdSetIterator timeStampValues;
        try {
            String eventField = ipedChartsPanel.getTimeEventColumnName(eventType);
            if (eventField != null) {
                timeStampValues = reader.getSortedDocValues(eventField);
                ArrayList<HashMap<String, Date>> parsedDateCache = new ArrayList<HashMap<String, Date>>();
                if (timeStampValues == null) {
                    SortedSetDocValues values = reader.getSortedSetDocValues(eventField);
                    int emptyValueOrd = -1;
                    int doc = values.nextDoc();
                    while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                        int ord = (int) values.nextOrd();
                        while (ord != SortedSetDocValues.NO_MORE_ORDS) {
                            if (ord != emptyValueOrd) {
                                HashMap<String, Date> dates = null;

                                if (ord < parsedDateCache.size()) {
                                    dates = parsedDateCache.get(ord);
                                }
                                if (dates == null) {
                                    String timeStr = cloneBr(values.lookupOrd(ord));
                                    if (timeStr.isEmpty()) {
                                        emptyValueOrd = ord;
                                        continue;
                                    }
                                    
                                    dates = new HashMap<String, Date> ();                                     
                                    for (Class<? extends TimePeriod> timePeriodClass : timeStampCache.getPeriodClassesToCache()) {
                                        Date date = DateUtil.ISO8601DateParse(timePeriodClass, timeStr);
                                        dates.put(timePeriodClass.getSimpleName(), date);
                                    }

                                    while (ord >= parsedDateCache.size()) {
                                        parsedDateCache.add(null);
                                    }
                                    parsedDateCache.set(ord, dates);
                                }
                                for (Class<? extends TimePeriod> timePeriodClass : timeStampCache.getPeriodClassesToCache()) {
                                    Date date = dates.get(timePeriodClass.getSimpleName());
                                    if (date != null) {
                                        RoaringBitmap docs2 = timeStampCache.get(timePeriodClass, date, eventType);
                                        if (docs2 == null) {
                                            docs2 = new RoaringBitmap();
                                            synchronized (timeStampCache) {
                                                timeStampCache.add(timePeriodClass, date, eventType, docs2);
                                            }
                                        }
                                        synchronized (docs2) {
                                            docs2.add(doc);
                                        }
                                    }
                                }
                            }
                            ord = (int) values.nextOrd();
                        }
                        doc = values.nextDoc();
                    }
                } else {
                    SortedDocValues values = (SortedDocValues) timeStampValues;
                    int emptyValueOrd = -1;
                    int doc = values.nextDoc();
                    TreeMap<Date, TimePeriod> periodCache = new TreeMap<>();
                    while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                        int ord = values.ordValue();
                        if (ord != emptyValueOrd) {
                            HashMap<String, Date> dates = null;

                            if (ord < parsedDateCache.size()) {
                                dates = parsedDateCache.get(ord);
                            }
                            if (dates == null) {
                                dates = new HashMap<String, Date>();
                                
                                String timeStr = cloneBr(values.lookupOrd(ord));
                                if (timeStr.isEmpty()) {
                                    emptyValueOrd = ord;
                                    continue;
                                }

                                for (Class<? extends TimePeriod> timePeriodClass : timeStampCache.getPeriodClassesToCache()) {
                                    Date date = DateUtil.ISO8601DateParse(timePeriodClass, timeStr);
                                    dates.put(timePeriodClass.getSimpleName(), date);
                                }

                                while (ord >= parsedDateCache.size()) {
                                    parsedDateCache.add(null);
                                }
                                while (ord >= parsedDateCache.size()) {
                                    parsedDateCache.add(null);
                                }
                                parsedDateCache.set(ord, dates);
                            }
                            for (Class<? extends TimePeriod> timePeriodClass : timeStampCache.getPeriodClassesToCache()) {
                                Date date = dates.get(timePeriodClass.getSimpleName());
                                if (date != null) {
                                    RoaringBitmap docs2 = timeStampCache.get(timePeriodClass, date, eventType);
                                    if (docs2 == null) {
                                        docs2 = new RoaringBitmap();
                                        synchronized (timeStampCache) {
                                            timeStampCache.add(timePeriodClass, date, eventType, docs2);
                                        }
                                    }
                                    synchronized (docs2) {
                                        docs2.add(doc);
                                    }
                                }
                            }
                        }
                        doc = values.nextDoc();
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (timeStampCache.running.decrementAndGet() == 0) {
                synchronized (timeStampCache.monitor) {
                    timeStampCache.monitor.notifyAll();
                }
            }
        }
    }

    private static final boolean isPeriodToCache(Class<? extends TimePeriod> timePeriodClass) {
        return timePeriodClass == Year.class || timePeriodClass == Quarter.class || timePeriodClass == Month.class || timePeriodClass == Week.class || timePeriodClass == Day.class || timePeriodClass == Hour.class;
    }

    static public synchronized String cloneBr(BytesRef br) {
        char[] saida = new char[br.length];
        final int len = UnicodeUtil.UTF8toUTF16(br.bytes, br.offset, br.length, saida);
        return new String(saida, 0, len);
    }
}
