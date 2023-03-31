package iped.app.timelinegraph.cache;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import iped.app.timelinegraph.cache.persistance.CachePersistance;
import iped.utils.SeekableFileInputStream;

public class TimeIndexedMap extends HashMap<String, List<CacheTimePeriodEntry>> {
    HashMap<String, TreeMap<Date, Long>> monthIndex = new HashMap<String, TreeMap<Date, Long>>();
    HashMap<String, Date> startDates = new HashMap<String, Date>();
    HashMap<String, Date> endDates = new HashMap<String, Date>();
    HashMap<String, File> cacheFiles = new HashMap<String, File>();
    HashMap<String, File> monthIndexCacheFiles = new HashMap<String, File>();
    private HashMap<String, CacheDataInputStream> cacheDis = new HashMap<String, CacheDataInputStream>();
    private HashMap<String, SeekableFileInputStream> cacheSfis = new HashMap<String, SeekableFileInputStream>();

    TimelineCache timelineCache = TimelineCache.get();

    @Override
    public List<CacheTimePeriodEntry> put(String key, List<CacheTimePeriodEntry> value) {
        List<CacheTimePeriodEntry> result = super.put(key, value);

        processIndex(key, value);

        return result;
    }

    public Date getStartDate(String className) {
        return startDates.get(className);
    }

    public Date getEndDate(String className) {
        return endDates.get(className);
    }

    protected void processIndex(String key, List<CacheTimePeriodEntry> value) {
        if (!key.equals("Year") && !key.equals("Month") && !key.equals("Quarter")) {
            Calendar c = (Calendar) Calendar.getInstance().clone();
            Date minDate = startDates.get(key);
            if (minDate == null) {
                minDate = new Date(Long.MAX_VALUE);
            }
            Date maxDate = endDates.get(key);
            if (maxDate == null) {
                maxDate = new Date(0);
            }

            Collections.sort(value);

            long i = 0;
            for (Iterator iterator = value.iterator(); iterator.hasNext();) {
                CacheTimePeriodEntry cacheTimePeriodEntry = (CacheTimePeriodEntry) iterator.next();
                c.clear();
                c.set(Calendar.YEAR, 1900 + cacheTimePeriodEntry.date.getYear());
                c.set(Calendar.MONTH, cacheTimePeriodEntry.date.getMonth());
                Date month = c.getTime();
                TreeMap<Date, Long> months = monthIndex.get(key);
                if (months == null) {
                    months = new TreeMap<Date, Long>();
                    monthIndex.put(key, months);
                }
                Long firstIndex = months.get(month);
                if (firstIndex == null) {
                    months.put(month, i);
                }

                if (!minDate.before(cacheTimePeriodEntry.date)) {
                    minDate = cacheTimePeriodEntry.date;
                }
                if (!maxDate.after(cacheTimePeriodEntry.date)) {
                    maxDate = cacheTimePeriodEntry.date;
                }

                i++;
            }
            startDates.put(key, minDate);
            endDates.put(key, minDate);
        }
    }

    @Override
    public void putAll(Map<? extends String, ? extends List<CacheTimePeriodEntry>> m) {
        super.putAll(m);
        for (Iterator iterator = m.entrySet().iterator(); iterator.hasNext();) {
            Entry e = (Entry) iterator.next();
            processIndex((String) e.getKey(), (List<CacheTimePeriodEntry>) e.getValue());
        }
    }

    public void setIndexFile(String string, File f) throws IOException {
        File cacheFile = new File(new File(f, string), "0");
        if (!cacheFile.exists() || cacheFile.length() == 0) {
            throw new IOException("File content does not exists:" + f.getName());
        }

        this.cacheFiles.put(string, cacheFile);
        this.monthIndexCacheFiles.put(string, new File(new File(f, string), "1"));

        SeekableFileInputStream lcacheSfis = new SeekableFileInputStream(cacheFile);
        cacheSfis.put(string, lcacheSfis);
        CacheDataInputStream lcacheDis = new CacheDataInputStream(lcacheSfis) {
        };
        cacheDis.put(string, lcacheDis);

        try {
            int committed = lcacheDis.readShort();
            if (committed != 1) {
                throw new IOException("File not committed:" + f.getName());
            }
        } finally {
        }
    }

    public static class CacheDataInputStream extends DataInputStream {

        public InputStream wrapped;

        public CacheDataInputStream(InputStream in) {
            super(in);
            wrapped = in;
        }

        public int readInt2() throws IOException {
            byte[] chs = new byte[4];
            int i = in.read(chs);
            if (i < 0) {
                throw new EOFException();
            }
            int ch1 = chs[0] & 0xFF;
            int ch2 = chs[1] & 0xFF;
            int ch3 = chs[2] & 0xFF;
            int ch4 = chs[3] & 0xFF;

            if (ch1 == -1 && ch1 == -1 && ch2 == -1 && ch3 == -1) {
                return -1;
            }
            return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
        }
    }

    Date lastStartDate = null;
    Date lastEndDate = null;

    public Iterator<CacheTimePeriodEntry> iterator(String className, Date startDate, Date endDate) {
        try {
            SeekableFileInputStream tmpCacheSifs = cacheSfis.get(className);
            CacheDataInputStream tmpCacheDis = null;

            if (tmpCacheSifs == null) {
                tmpCacheSifs = new SeekableFileInputStream(cacheFiles.get(className));
                cacheSfis.put(className, tmpCacheSifs);
                tmpCacheDis = new CacheDataInputStream(tmpCacheSifs);
                cacheDis.put(className, tmpCacheDis);
            } else {
                tmpCacheDis = cacheDis.get(className);
            }

            SeekableFileInputStream lcacheSfis = tmpCacheSifs;
            CacheDataInputStream lcacheDis = tmpCacheDis;

            lcacheSfis.seek(2l);
            String timezoneID = lcacheDis.readUTF();
            int entries = lcacheDis.readInt2();

            CacheTimePeriodEntry[] cache = timelineCache.get(className, entries);

            long startpos = lcacheSfis.position();
            if (startDate != null) {
                Long num = null;
                try {
                    Entry<Date, Long> entry = monthIndex.get(className).floorEntry(startDate);
                    if (entry != null) {
                        num = entry.getValue();
                    } else {
                        num = startpos;
                    }
                } catch (Exception e) {
                }
                if (num == null) {
                    num = startpos;
                }
                startpos = num.longValue();
            }

            final long pos = startpos;

            try {
                lcacheSfis.seek(pos);
                Integer index = null;
                if (cache != null) {
                    index = timelineCache.getIndex(className, pos);

                    if (lastStartDate != null && startDate != null) {
                        long startinterval = startDate.getTime() - lastStartDate.getTime();
                        if (startinterval > 0) {
                            timelineCache.clean(className, lastStartDate, startDate);
                        }
                        long endinterval = lastEndDate.getTime() - endDate.getTime();
                        if (endinterval > 0) {
                            timelineCache.clean(className, endDate, lastEndDate);
                        }
                    }
                }
                lastStartDate = startDate;
                lastEndDate = endDate;

                return new ResultIterator(index, timelineCache, lcacheSfis, lcacheDis, endDate, className);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        return null;
    }

    class ResultIterator implements Iterator<CacheTimePeriodEntry> {
        CacheTimePeriodEntry lastHasNext = null;
        int i = 0;
        int j;
        private Integer index;
        private CacheTimePeriodEntry[] lcache;
        private SeekableFileInputStream lcacheSfis;
        private CacheDataInputStream lcacheDis;
        private Map<Long, Integer> lcacheIndexes;
        private Date endDate;
        private String className;
        boolean useCache = false;
        private TimelineCache timelineCache;
        CachePersistance cp = new CachePersistance();
        Date startIterationDate = new Date();

        public ResultIterator(Integer index, TimelineCache timelineCache, SeekableFileInputStream lcacheSfis, CacheDataInputStream lcacheDis, Date endDate, String className) {
            this.index = index;
            j = index != null ? index : 0;
            this.lcache = timelineCache.caches.get(className);
            this.lcacheIndexes = timelineCache.cachesIndexes.get(className);
            this.timelineCache = timelineCache;
            this.lcacheSfis = lcacheSfis;
            this.lcacheDis = lcacheDis;
            this.endDate = endDate;
            this.className = className;
            this.useCache = this.lcache != null;
        }

        public boolean finish() {
            lastHasNext = null;
            lcache = null;
            // System.out.println(new Date().getTime()-startIterationDate.getTime());
            return false;
        }

        @Override
        public boolean hasNext() {
            try {
                if (index != null && i >= 0 && useCache) {
                    if (index + i < lcache.length) {
                        lastHasNext = lcache[index + i];
                        if (lastHasNext == null) {
                            i = -1;// end of cache
                        } else {
                            i++;
                        }
                    } else {
                        return finish();
                    }
                }
                if (lastHasNext == null) {// not in cache so load from file
                    long curpos = lcacheSfis.position();
                    lastHasNext = cp.loadNextEntry(lcacheDis);
                    if (lcache != null) {
                        lcache[j + i] = lastHasNext;
                        lcacheIndexes.put(curpos, j + i);
                    }
                    i++;
                }
            } catch (EOFException e) {
                return finish();
            } catch (IOException e) {
                return finish();
            } catch (Exception e) {
                return finish();
            }

            if (endDate == null || lastHasNext.date.before(endDate)) {
                return true;
            } else {
                return finish();
            }
        }

        @Override
        public CacheTimePeriodEntry next() {
            CacheTimePeriodEntry result = lastHasNext;
            lastHasNext = null;
            return result;
        }
    };

    public void createOrLoadDayIndex(IndexTimeStampCache indexTimeStampCache) {
        CachePersistance cp = new CachePersistance();
        if (monthIndex.size() == 0) {
            for (Iterator iterator = indexTimeStampCache.getPeriodClassesToCache().iterator(); iterator.hasNext();) {
                String ev = (String) ((Class) iterator.next()).getSimpleName();
                File f = monthIndexCacheFiles.get(ev);
                TreeMap<Date, Long> datesPos = new TreeMap<Date, Long>();
                if (f.exists()) {
                    cp.loadMonthIndex(ev, datesPos);
                } else {
                    Date lastMonth = null;
                    int internalCount = 0;
                    long lastPos;
                    Calendar c = (Calendar) Calendar.getInstance().clone();

                    Iterator<CacheTimePeriodEntry> i = iterator(ev, null, null);
                    try {
                        lastPos = cacheSfis.get(ev).position();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                        return;
                    }
                    while (i.hasNext()) {
                        CacheTimePeriodEntry ct = i.next();
                        c.clear();
                        c.set(Calendar.YEAR, 1900 + ct.date.getYear());
                        c.set(Calendar.MONTH, ct.date.getMonth());
                        c.set(Calendar.DAY_OF_MONTH, ct.date.getDate());
                        if (ev.equals("Minute") || ev.equals("Second")) {
                            c.set(Calendar.HOUR_OF_DAY, ct.date.getHours());
                        }

                        internalCount += ct.events.size();
                        Date month = c.getTime();
                        if (!month.equals(lastMonth) || internalCount > 4000) {
                            lastMonth = month;
                            internalCount = 0;
                            datesPos.put(month, lastPos);
                        }

                        try {
                            lastPos = cacheSfis.get(ev).position();
                        } catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }
                monthIndex.put(ev, datesPos);
                cp.saveMonthIndex(monthIndex, ev);
            }
        }
    }

    long cacheStartPos = 0;
    long cacheEndPos = 0;

    public HashMap<String, TreeMap<Date, Long>> getMonthIndex() {
        return monthIndex;
    }

    public void clearCache() {
    }

}
