package iped.app.timelinegraph.cache;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
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
    private HashMap<String, DataInputStream> cacheDis = new HashMap<String, DataInputStream>();
    private HashMap<String, SeekableFileInputStream> cacheSfis = new HashMap<String, SeekableFileInputStream>();

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
        File cacheFile = new File(new File(f,string), "0");
        if (!cacheFile.exists() || cacheFile.length() == 0) {
            throw new IOException("File content does not exists:" + f.getName());
        }

        this.cacheFiles.put(string, cacheFile);
        this.monthIndexCacheFiles.put(string, new File(new File(f,string), "1"));

        SeekableFileInputStream lcacheSfis = new SeekableFileInputStream(cacheFile);
        cacheSfis.put(string, lcacheSfis);
        DataInputStream lcacheDis = new DataInputStream(lcacheSfis);
        cacheDis.put(string, lcacheDis);

        try {
            int committed = lcacheDis.readShort();
            if (committed != 1) {
                throw new IOException("File not committed:" + f.getName());
            }
        }finally {
        }
    }

    Date d1;
    long countIO;
    public Iterator<CacheTimePeriodEntry> iterator(String className, Date startDate, Date endDate) {
        try {
            SeekableFileInputStream tmpCacheSifs = cacheSfis.get(className);
            DataInputStream tmpCacheDis = null;
            
            d1=new Date();
            countIO=0;
            
            if(tmpCacheSifs==null) {
                tmpCacheSifs = new SeekableFileInputStream(cacheFiles.get(className));
                cacheSfis.put(className, tmpCacheSifs);
                tmpCacheDis = new DataInputStream(tmpCacheSifs);
                cacheDis.put(className, tmpCacheDis);
            }else {
                tmpCacheDis = cacheDis.get(className);            
            }
            
            SeekableFileInputStream lcacheSfis = tmpCacheSifs;
            DataInputStream lcacheDis = tmpCacheDis;            

            lcacheSfis.seek(2l);
            String timezoneID = lcacheDis.readUTF();
            int entries = lcacheDis.readInt();
            
            CacheTimePeriodEntry[] cache = caches.get(className);
            Map<Long, Integer> cacheIndexes = null;
            if(cache==null) {
                cache = new CacheTimePeriodEntry[entries];
                caches.put(className, cache);
                cacheIndexes = new TreeMap<Long, Integer>();
                cachesIndexes.put(className, cacheIndexes);
            }else {
                cacheIndexes = cachesIndexes.get(className);
            }

            long startpos = lcacheSfis.position();
            if(startDate!=null) {
                Long num = null;
                try {
                    Entry<Date, Long> entry = monthIndex.get(className).floorEntry(startDate);
                    if(entry!=null) {
                        num = entry.getValue();
                    }else{
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
            final Map<Long, Integer> lcacheIndexes = cacheIndexes;
            final CacheTimePeriodEntry[] lcache = cache;
            final Integer index = lcacheIndexes.get(pos);
            
            try {
                lcacheSfis.seek(pos);
                
                return new Iterator<CacheTimePeriodEntry>() {
                    CacheTimePeriodEntry lastHasNext=null;
                    int i=0;
                    int j = index!=null?index:0;

                    @Override
                    public boolean hasNext() {
                        try {                            
                            if(index!=null && i>=0) {
                                if(index+i<lcache.length) {
                                    lastHasNext = lcache[index+i];
                                    if(lastHasNext==null) {
                                        i=-1;//end of cache
                                    }else {
                                        i++;
                                    }
                                }else {
                                    lastHasNext = null;//no more entries
                                    return false;
                                }
                            }
                            if(lastHasNext==null){//not in cache so load from file
                                long curpos = lcacheSfis.position();
                                lastHasNext = loadNextEntry(lcacheDis);
                                lcache[j+i] = lastHasNext;
                                lcacheIndexes.put(curpos, j+i);
                                i++;
                            }
                        } catch (EOFException e) {
                            lastHasNext = null;
                            e.printStackTrace();
                            return false;
                        }catch (IOException e) {
                            e.printStackTrace();
                            return false;
                        }catch(Exception e) {
                            e.printStackTrace();
                            return false;
                        }
                        
                        if(endDate==null || lastHasNext.date.before(endDate)) {
                            return true;
                        }else {
                            lastHasNext = null;
                            return false;
                        }
                    }

                    @Override
                    public CacheTimePeriodEntry next() {
                        CacheTimePeriodEntry result = lastHasNext;
                        lastHasNext=null;
                        return result;
                    }
                };
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        return null;
    }

    public void createMonthIndex(IndexTimeStampCache indexTimeStampCache) {
        CachePersistance cp = new CachePersistance();
        if(monthIndex.size()==0) {
            for (Iterator iterator = indexTimeStampCache.getPeriodClassesToCache().iterator(); iterator.hasNext();) {
                String ev = (String) ((Class)iterator.next()).getSimpleName();
                File f = monthIndexCacheFiles.get(ev);
                TreeMap<Date, Long> datesPos = new TreeMap<Date, Long>();
                if(f.exists()) {
                    cp.loadMonthIndex(ev, datesPos);
                }else {
                    Date lastMonth=null;
                    long lastPos;
                    Calendar c = (Calendar) Calendar.getInstance().clone();
                    
                    Iterator<CacheTimePeriodEntry> i = iterator(ev, null, null);
                    try {
                        lastPos = cacheSfis.get(ev).position();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                        return;
                    }
                    while(i.hasNext()) {
                        CacheTimePeriodEntry ct = i.next();
                        c.clear();
                        c.set(Calendar.YEAR, 1900 + ct.date.getYear());                        
                        c.set(Calendar.MONTH, ct.date.getMonth());
                        c.set(Calendar.DAY_OF_MONTH, ct.date.getDate());

                        Date month = c.getTime();
                        if(!month.equals(lastMonth)) {
                            lastMonth=month;
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

    private long calcSize(CacheTimePeriodEntry ct) {
        long size = 8l;

        for (Iterator<CacheEventEntry> iterator = ct.events.iterator(); iterator.hasNext();) {
            CacheEventEntry cee = iterator.next();
            size+=cee.event.getBytes(Charset.forName("UTF-8")).length+2;
            size+=cee.docIds.size()*4 + 4;
        }

        return size;
    }

    long cacheStartPos=0;
    long cacheEndPos=0;
    HashMap<String, Map<Long, Integer>> cachesIndexes = new HashMap<String, Map<Long, Integer>>();
    HashMap<String, CacheTimePeriodEntry[]> caches = new HashMap<String,CacheTimePeriodEntry[]>();

    private CacheTimePeriodEntry loadNextEntry(DataInputStream dis) throws IOException {
        Date ld1 = new Date();
        Date d = new Date(dis.readLong());
        CacheTimePeriodEntry ct = new CacheTimePeriodEntry();
        ct.events = new ArrayList<CacheEventEntry>();
        ct.date = d;
        String eventName = dis.readUTF();
        while (!eventName.equals("!!")) {
            CacheEventEntry ce = new CacheEventEntry();
            ce.event = eventName;
            ce.docIds = new ArrayList<Integer>();
            int docId = dis.readInt();
            while (docId != -1) {
                ce.docIds.add(docId);
                docId = dis.readInt();
            }
            ct.events.add(ce);
            eventName = dis.readUTF();
        }
        Date ld2 = new Date();
        countIO+=(ld2.getTime()-ld1.getTime());
        return ct;
    }

    public HashMap<String, TreeMap<Date, Long>> getMonthIndex() {
        return monthIndex;
    }

}
