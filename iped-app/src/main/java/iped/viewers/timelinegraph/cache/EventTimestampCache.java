package iped.viewers.timelinegraph.cache;

import java.util.ArrayList;
import java.util.Date;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.UnicodeUtil;
import org.jfree.data.time.TimePeriod;

import iped.viewers.api.IMultiSearchResultProvider;
import iped.viewers.timelinegraph.DateUtil;
import iped.viewers.timelinegraph.IpedChartsPanel;

class EventTimestampCache implements Runnable {
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
                if (timeStampValues == null) {
                    SortedSetDocValues values = reader.getSortedSetDocValues(eventField);
                    ArrayList<Date> parsedDateCache = new ArrayList<>();
                    int emptyValueOrd = -1;
                    int doc = values.nextDoc();
                    while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                        int ord = (int) values.nextOrd();
                        outer: while (ord != SortedSetDocValues.NO_MORE_ORDS) {
                            if (ord != emptyValueOrd) {
                                for (Class<? extends TimePeriod> timePeriodClass : timeStampCache.getPeriodClassesToCache()) {
                                    Date date = null;
                                    if (ord < parsedDateCache.size()) {
                                        date = parsedDateCache.get(ord);
                                    }
                                    if (date == null) {
                                        String timeStr = cloneBr(values.lookupOrd(ord));
                                        if (timeStr.isEmpty()) {
                                            emptyValueOrd = ord;
                                            continue outer;
                                        }
                                        date = DateUtil.ISO8601DateParse(timeStr);
                                        while (ord >= parsedDateCache.size()) {
                                            parsedDateCache.add(null);
                                        }
                                        parsedDateCache.set(ord, date);
                                    }
                                    TimePeriod t = ipedChartsPanel.getDomainAxis().getDateOnConfiguredTimePeriod(timePeriodClass, date);
                                    if (t != null) {
                                        ArrayList<Integer> docs2 = timeStampCache.get(timePeriodClass, t, eventType);
                                        if (docs2 == null) {
                                            docs2 = new ArrayList<Integer>();
                                            synchronized (timeStampCache) {
                                                timeStampCache.add(timePeriodClass, t, eventType, docs2);
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
                    ArrayList<Date> parsedDateCache = new ArrayList<>();
                    int emptyValueOrd = -1;
                    int doc = values.nextDoc();
                    outer: while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                        int ord = values.ordValue();
                        if (ord != emptyValueOrd) {
                            for (Class<? extends TimePeriod> timePeriodClass : timeStampCache.getPeriodClassesToCache()) {
                                Date date = null;
                                if (ord < parsedDateCache.size()) {
                                    date = parsedDateCache.get(ord);
                                }
                                if (date == null) {
                                    String timeStr = cloneBr(values.lookupOrd(ord));
                                    if (timeStr.isEmpty()) {
                                        emptyValueOrd = ord;
                                        continue outer;
                                    }
                                    date = DateUtil.ISO8601DateParse(timeStr);
                                    while (ord >= parsedDateCache.size()) {
                                        parsedDateCache.add(null);
                                    }
                                    parsedDateCache.set(ord, date);
                                }
                                TimePeriod t = ipedChartsPanel.getDomainAxis().getDateOnConfiguredTimePeriod(timePeriodClass, date);
                                if (t != null) {
                                    ArrayList<Integer> docs2 = timeStampCache.get(timePeriodClass, t, eventType);
                                    if (docs2 == null) {
                                        docs2 = new ArrayList<Integer>();
                                        synchronized (timeStampCache) {
                                            timeStampCache.add(timePeriodClass, t, eventType, docs2);
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
            timeStampCache.running--;
            if (timeStampCache.running == 0) {
                synchronized (timeStampCache.monitor) {
                    timeStampCache.monitor.notifyAll();
                }
            }
        }
    }

    synchronized String cloneBr(BytesRef br) {
        char[] saida = new char[br.length];
        final int len = UnicodeUtil.UTF8toUTF16(br.bytes, br.offset, br.length, saida);
        return new String(saida, 0, len);
    }
}
