package iped.app.timelinegraph.cache;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

public class CombinedIterators implements Iterator<CacheTimePeriodEntry> {
    Iterator<CacheTimePeriodEntry>[] iterators;
    CacheTimePeriodEntry[] nextValues;
    private CacheTimePeriodEntry minValue;
    private CacheTimePeriodEntry nextMinValue;

    public CombinedIterators(Iterator<CacheTimePeriodEntry>[] iterators) {
        this.iterators = iterators;
        nextValues=new CacheTimePeriodEntry[iterators.length];
        minValue = getNextMinValue();
    }
    
    CacheTimePeriodEntry getNextMinValue() {
        CacheTimePeriodEntry lminValue = null;
        int imin = -1;
        for (int i = 0; i < nextValues.length; i++) {
            if(nextValues[i]==null) {
                if(iterators[i].hasNext()) {
                    nextValues[i]=iterators[i].next();
                }else {
                    nextValues[i]=null;
                }
            }
            if(lminValue==null || (nextValues[i]!=null && nextValues[i].date < lminValue.date )) {
                lminValue = nextValues[i];
                imin = i;
            }            
        }
        if(imin!=-1) {
            nextValues[imin]=null;//selected minvalue position is nullified, so it will loaded with next iterator value
        }
        return lminValue;
    }

    @Override
    public boolean hasNext() {
        if(minValue == null) {
            return false;
        }
        nextMinValue = getNextMinValue();
        while(nextMinValue!=null && nextMinValue.date == minValue.date) {
            merge(minValue, nextMinValue);
            nextMinValue = getNextMinValue();
        }
        return true;
    }

    private void merge(CacheTimePeriodEntry value1, CacheTimePeriodEntry value2) {
        HashSet<String> included = new HashSet();
        for(CacheEventEntry cee1: value1.events) {
            for(CacheEventEntry cee2: value2.events) {
                if(cee2.event.equals(cee1.event)) {
                    cee1.docIds.or(cee2.docIds);//merge
                    included.add(cee1.event);
                }
            }
        }
        for(CacheEventEntry cee2: value2.events) {
            if(!included.contains(cee2.event)) {
                value1.events.add(cee2);
            }
        }
    }

    @Override
    public CacheTimePeriodEntry next() {
        CacheTimePeriodEntry result = minValue;
        minValue = nextMinValue;
        return result;
    }

}
