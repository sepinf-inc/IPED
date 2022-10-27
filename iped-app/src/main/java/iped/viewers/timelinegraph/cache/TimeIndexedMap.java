package iped.viewers.timelinegraph.cache;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class TimeIndexedMap extends HashMap<String, List<CacheTimePeriodEntry>> {
    HashMap<String,HashMap<Date, Integer>> monthIndex = new HashMap<String,HashMap<Date, Integer>>();
    HashMap<String,Date> startDates = new HashMap<String,Date>();
    HashMap<String,Date> endDates = new HashMap<String,Date>();
    
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
    
    public Iterator<CacheTimePeriodEntry> iterator(String className, Date startDate, Date endDate) {
        if(!className.equals("Year") && !className.equals("Month") && !className.equals("Quarter")) {
            Calendar c = (Calendar) Calendar.getInstance().clone();
            c.clear();
            c.set(Calendar.YEAR, 1900+startDate.getYear());
            c.set(Calendar.MONTH, startDate.getMonth());
            Integer num = null;
            try{
                num = monthIndex.get(className).get(c.getTime());
            }catch (Exception e) {
            }
            if(num==null) {
                num=0;
            }
            final int i=num.intValue();
            List<CacheTimePeriodEntry> classList = this.get(className);
            return new Iterator<CacheTimePeriodEntry>() {
                int currentIndex = i;
                CacheTimePeriodEntry lastHasNext; 
                
                @Override
                public boolean hasNext() {
                    boolean result = currentIndex<classList.size();
                    if(!result) {
                        return result;
                    }
                    lastHasNext = classList.get(currentIndex);
                    return lastHasNext.date.before(endDate);
                }

                @Override
                public CacheTimePeriodEntry next() {
                    if(lastHasNext==null) {
                        return classList.get(currentIndex++);
                    }else {
                        currentIndex++;
                        CacheTimePeriodEntry result = lastHasNext;
                        lastHasNext=null;
                        return result;
                    }
                }
            };        
        }else {
            return this.get(className).iterator();
        }
    }

    protected void processIndex(String key, List<CacheTimePeriodEntry> value) {
        if(!key.equals("Year") && !key.equals("Month") && !key.equals("Quarter")) {
            Calendar c = (Calendar) Calendar.getInstance().clone();
            Date minDate = startDates.get(key);
            if(minDate==null) {
                minDate = new Date(Long.MAX_VALUE); 
            }
            Date maxDate = endDates.get(key);
            if(maxDate==null) {
                maxDate = new Date(0); 
            }
            int i=0;
            for (Iterator iterator = value.iterator(); iterator.hasNext();) {
                CacheTimePeriodEntry cacheTimePeriodEntry = (CacheTimePeriodEntry) iterator.next();
                c.clear();
                c.set(Calendar.YEAR, 1900+cacheTimePeriodEntry.date.getYear());
                c.set(Calendar.MONTH, cacheTimePeriodEntry.date.getMonth());            
                Date month = c.getTime();
                HashMap<Date, Integer> months = monthIndex.get(key);
                if(months==null) {
                    months = new HashMap<Date, Integer>();
                    monthIndex.put(key, months);
                }
                Integer firstIndex = months.get(month);
                if(firstIndex==null) {
                    months.put(month, i);
                }
                
                if(!minDate.before(cacheTimePeriodEntry.date)) {
                    minDate=cacheTimePeriodEntry.date;
                }
                if(!maxDate.after(cacheTimePeriodEntry.date)) {
                    maxDate=cacheTimePeriodEntry.date;
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
            processIndex((String)e.getKey(), (List<CacheTimePeriodEntry>)e.getValue());            
        }
    }
    
}
