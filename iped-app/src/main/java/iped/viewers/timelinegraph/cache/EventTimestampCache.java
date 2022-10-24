package iped.viewers.timelinegraph.cache;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.index.TermsEnum;
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
                    TermsEnum te = values.termsEnum();
                    String timeStr = syncNext(te);
                    while (timeStr != null) {
                        if (!"".equals(timeStr)) {
                            int i = 0;
                            for (Class<? extends TimePeriod> timePeriodClass : timeStampCache.getPeriodClassesToCache()) {
                                TimePeriod t = ipedChartsPanel.getDomainAxis().getDateOnConfiguredTimePeriod(timePeriodClass, DateUtil.ISO8601DateParse(timeStr));
                                if (t != null) {
                                    ArrayList<Integer> docs = new ArrayList<Integer>();
                                    synchronized (timeStampCache) {
                                        timeStampCache.add(timePeriodClass, t, eventType, docs);
                                    }
                                }
                                i++;
                            }
                        }
                        timeStr = syncNext(te);
                    }
                    int doc = values.nextDoc();
                    while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                        long ord = values.nextOrd();
                        while (ord != SortedSetDocValues.NO_MORE_ORDS) {
                            timeStr = cloneBr(values.lookupOrd(ord));
                            if (!"".equals(timeStr)) {
                                int i = 0;
                                for (Class<? extends TimePeriod> timePeriodClass : timeStampCache.getPeriodClassesToCache()) {
                                    TimePeriod t = ipedChartsPanel.getDomainAxis().getDateOnConfiguredTimePeriod(timePeriodClass, DateUtil.ISO8601DateParse(timeStr));

                                    if (t != null) {
                                        ArrayList<Integer> docs2 = timeStampCache.get(timePeriodClass, t, eventType);
                                        synchronized (docs2) {
                                            docs2.add(doc);
                                        }
                                    }
                                    i++;
                                }
                            }
                            ord = values.nextOrd();
                        }
                        doc = values.nextDoc();
                    }
                } else {
                    SortedDocValues values = (SortedDocValues) timeStampValues;
                    TermsEnum te = values.termsEnum();
                    String timeStr = syncNext(te);
                    while (timeStr != null) {
                        if (!"".equals(timeStr)) {
                            int i = 0;
                            for (Class<? extends TimePeriod> timePeriodClass : timeStampCache.getPeriodClassesToCache()) {
                                TimePeriod t = ipedChartsPanel.getDomainAxis().getDateOnConfiguredTimePeriod(timePeriodClass, DateUtil.ISO8601DateParse(timeStr));
                                if (t != null) {
                                    ArrayList<Integer> docs = new ArrayList<Integer>();
                                    synchronized (timeStampCache) {
                                        timeStampCache.add(timePeriodClass, t, eventType, docs);
                                    }
                                }
                                i++;
                            }
                        }
                        timeStr = syncNext(te);
                    }
                    int doc = values.nextDoc();
                    while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                        timeStr = cloneBr(values.lookupOrd(values.ordValue()));
                        if (!"".equals(timeStr)) {
                            int i = 0;
                            for (Class<? extends TimePeriod> timePeriodClass : timeStampCache.getPeriodClassesToCache()) {
                                TimePeriod t = ipedChartsPanel.getDomainAxis().getDateOnConfiguredTimePeriod(timePeriodClass, DateUtil.ISO8601DateParse(timeStr));

                                if (t != null) {
                                    ArrayList<Integer> docs2 = timeStampCache.get(timePeriodClass, t, eventType);
                                    synchronized (docs2) {
                                        docs2.add(doc);
                                    }

                                }
                                i++;
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

    synchronized private String syncNext(TermsEnum te) {
        BytesRef br;
        try {
            br = te.next();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        if (br == null) {
            return null;
        } else {
            return cloneBr(br);
        }
    }

    synchronized String cloneBr(BytesRef br) {
        char[] saida = new char[br.length];
        final int len = UnicodeUtil.UTF8toUTF16(br.bytes, br.offset, br.length, saida);
        return new String(saida, 0, len);
    }
}
