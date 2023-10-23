package iped.app.timelinegraph.cache;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.pdfbox.io.RandomAccessBufferedFileInputStream;

import iped.app.timelinegraph.cache.persistance.CachePersistance;

public class TimeIndexedMap extends HashMap<String, Set<CacheTimePeriodEntry>> {
    HashMap<String, TreeMap<Long, Long>> upperPeriodIndex = new HashMap<String, TreeMap<Long, Long>>();
    HashMap<String, File> cacheFiles = new HashMap<String, File>();
    HashMap<String, File> monthIndexCacheFiles = new HashMap<String, File>();

    TimelineCache timelineCache = TimelineCache.get();

    @Override
    public Set<CacheTimePeriodEntry> put(String key, Set<CacheTimePeriodEntry> value) {
        Set<CacheTimePeriodEntry> result = super.put(key, value);

        return result;
    }

    public void setIndexFile(String string, File f) throws IOException {
        File cacheFile = new File(new File(f, string), "0");
        if (!cacheFile.exists() || cacheFile.length() == 0) {
            throw new IOException("File content does not exists:" + f.getName());
        }

        this.cacheFiles.put(string, cacheFile);
        this.monthIndexCacheFiles.put(string, new File(new File(f, string), "1"));

        int committed = 0;
        try (RandomAccessBufferedFileInputStream lcacheSfis = new RandomAccessBufferedFileInputStream(cacheFile); DataInputStream lcacheDis = new DataInputStream(lcacheSfis)) {
            committed = lcacheDis.readShort();
        }
        if (committed != 1) {
            String msg = "File not committed:" + f.getName();
            throw new IOException(msg);
        }
    }

    Date lastStartDate = null;
    Date lastEndDate = null;

    public RandomAccessBufferedFileInputStream getTmpCacheSfis(String className) throws IOException {
        File f = cacheFiles.get(className);
        if (f != null) {
            return new RandomAccessBufferedFileInputStream(f);
        } else {
            return null;
        }
    }

    public ResultIterator iterator(String className, RandomAccessBufferedFileInputStream lcacheSfis, Date startDate, Date endDate) {
        try {
            DataInputStream lcacheDis = new DataInputStream(lcacheSfis);

            // skips header
            lcacheSfis.seek(2l);
            String timezoneID = lcacheDis.readUTF();
            int entries = lcacheDis.readInt();

            CacheTimePeriodEntry[] cache = timelineCache.get(className, entries);

            long startpos = lcacheSfis.getPosition();// position of first entry in cache
            if (startDate != null) {
                // if start date is given, search for the position of correspondent first entry
                // throught month index
                Long num = null;
                try {
                    Entry<Long, Long> entry = upperPeriodIndex.get(className).floorEntry(startDate.getTime());
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

            final long pos = startpos;// start position in the index to iterate

            try {
                lcacheSfis.seek(pos);
                Integer index = null;
                if (cache != null) {
                    index = timelineCache.getIndex(className, pos);// finds the correspondent index position in cache array for the startpos

                    if (lastStartDate != null && startDate != null) { // liberate some memory of entries in cache out of window of iteration
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

                if (endDate == null) {
                    return new ResultIterator(pos, index, timelineCache, lcacheSfis, lcacheDis, Long.MAX_VALUE, className);
                } else {
                    return new ResultIterator(pos, index, timelineCache, lcacheSfis, lcacheDis, endDate.getTime(), className);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        return null;
    }

    class ResultIterator implements Iterator<CacheTimePeriodEntry> {
        CacheTimePeriodEntry lastHasNext = null;// found entry if existent when iterating
        int cacheCurrentIndex = 0;// current index being iterated (sum with startIndex to identify correspondent
                                  // cache array index)
        private CacheTimePeriodEntry[] lcache;// cache array to iterate
        private RandomAccessBufferedFileInputStream lcacheSfis;// seekable stream to index file (to read from when entry not in cache)
        private DataInputStream lcacheDis;// data parser stream to same above index file
        private TreeMap<Long, Integer> lcacheIndexes;
        private long startDate = 0;
        private long endDate;
        private String className;
        boolean useCache = false;
        private TimelineCache timelineCache;
        CachePersistance cp = CachePersistance.getInstance();
        int countRead = 0;// counters to log number of cache reads
        int countCache = 0;// counters to log number of disk reads
        private Reference<CacheTimePeriodEntry>[] lsoftcache;// soft reference cache (a more complete cache but with SoftReferences)
        boolean hasSoftCache = false;
        Long nextSeekPos = 0l;
        private Integer startIndex;// start index in cache array in file to iterate (if not in cache array load
                                   // from position in file)
        private long startPos;// start position in file to iterate

        public ResultIterator(long pos, Integer startIndex, TimelineCache timelineCache, RandomAccessBufferedFileInputStream lcacheSfis, DataInputStream lcacheDis, long endDate, String className) {
            this.startPos = pos;
            this.startIndex = startIndex;
            this.lcache = timelineCache.caches.get(className);
            this.lsoftcache = timelineCache.softCaches.get(className);
            this.lcacheIndexes = (TreeMap) timelineCache.getCachesIndexes(className);
            this.timelineCache = timelineCache;
            this.lcacheSfis = lcacheSfis;
            this.lcacheDis = lcacheDis;
            this.endDate = endDate;
            this.className = className;
            this.useCache = this.lcache != null;
            this.hasSoftCache = timelineCache.hasSoftCacheFor(className);
            nextSeekPos = startPos;
        }

        @Override
        public boolean hasNext() {
            try {
                if (useCache) {
                    if (startIndex == null) {
                        startIndex = 0;
                    }
                    if (startIndex + cacheCurrentIndex < lcache.length) {
                        lastHasNext = lcache[startIndex + cacheCurrentIndex];
                        if (lastHasNext == null && lsoftcache != null) {
                            // try the soft reference cache
                            Reference<CacheTimePeriodEntry> softRef = lsoftcache[startIndex + cacheCurrentIndex];
                            if (softRef != null) {
                                lastHasNext = softRef.get();
                                if (lastHasNext != null) {
                                    lcache[startIndex + cacheCurrentIndex] = lastHasNext;
                                }
                            }
                        }
                        if (lastHasNext != null) {
                            nextSeekPos = lcacheIndexes.ceilingKey(nextSeekPos + 1);
                        }
                    } else {
                        return false;
                    }
                }
                if (lastHasNext == null) {// not in cache so load from file
                    long curpos = nextSeekPos;
                    synchronized (lcacheSfis) {
                        if (nextSeekPos != lcacheSfis.getPosition()) {
                            lcacheSfis.seek(nextSeekPos);
                        }
                        lastHasNext = cp.loadNextEntry(lcacheDis, cp.isBitstreamSerialize());
                        nextSeekPos = lcacheSfis.getPosition();
                    }
                    countRead++;
                    if (lcache != null && useCache) {
                        lcache[startIndex + cacheCurrentIndex] = lastHasNext;
                        if (hasSoftCache) {
                            lsoftcache[startIndex + cacheCurrentIndex] = new SoftReference<CacheTimePeriodEntry>(lastHasNext);
                        }
                        lcacheIndexes.put(nextSeekPos, startIndex + cacheCurrentIndex);
                        cacheCurrentIndex++;
                    }
                } else {
                    countCache++;
                    cacheCurrentIndex++;
                }
                if (startDate == 0) {
                    startDate = lastHasNext.date;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }

            if (lastHasNext.date < endDate) {
                return true;
            } else {
                return false;
            }
        }

        @Override
        public CacheTimePeriodEntry next() {
            CacheTimePeriodEntry result = lastHasNext;
            // lastHasNext = null;
            return result;
        }
    };

    public void createOrLoadUpperPeriodIndex(IndexTimeStampCache indexTimeStampCache) {
        CachePersistance cp = CachePersistance.getInstance();
        if (upperPeriodIndex.size() == 0) {
            for (Iterator iterator = indexTimeStampCache.getPeriodClassesToCache().iterator(); iterator.hasNext();) {
                String ev = (String) ((Class) iterator.next()).getSimpleName();
                File f = monthIndexCacheFiles.get(ev);
                TreeMap<Long, Long> datesPos = new TreeMap<Long, Long>();
                Map<Long, Integer> positionsIndexes = timelineCache.getCachesIndexes(ev);
                try {
                    if (f.exists()) {
                        cp.loadUpperPeriodIndex(ev, datesPos, positionsIndexes);
                        upperPeriodIndex.put(ev, datesPos);
                    }
                } finally {
                    timelineCache.liberateCachesIndexes(ev);
                }

            }
        }
    }

    long cacheStartPos = 0;
    long cacheEndPos = 0;

    public HashMap<String, TreeMap<Long, Long>> getMonthIndex() {
        return upperPeriodIndex;
    }

}
