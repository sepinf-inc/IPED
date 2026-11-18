package iped.app.timelinegraph.cache;

import java.util.HashSet;
import java.util.Iterator;

public class CombinedIterators implements Iterator<CacheTimePeriodEntry> {
    Iterator<CacheTimePeriodEntry>[] iterators;
    CacheTimePeriodEntry[] nextValues;
    private CacheTimePeriodEntry minValue;
    private CacheTimePeriodEntry nextMinValue;

    public CombinedIterators(Iterator<CacheTimePeriodEntry>[] iterators) {
        this.iterators = iterators;
        nextValues = new CacheTimePeriodEntry[iterators.length];
        minValue = getNextMinValue();
    }

    CacheTimePeriodEntry getNextMinValue() {
        CacheTimePeriodEntry lminValue = null;
        int imin = -1;
        int itrunc = -1;
        int isToTrucate = 0;
        for (int i = 0; i < nextValues.length; i++) {
            if (nextValues[i] == null) {
                if (iterators[i] != null && iterators[i].hasNext()) {
                    nextValues[i] = iterators[i].next();
                } else {
                    nextValues[i] = null;
                    itrunc = i;
                }
            }
            if (lminValue == null || (nextValues[i] != null && nextValues[i].date < lminValue.date)) {
                lminValue = nextValues[i];
                imin = i;
            }
        }
        if (imin != -1) {
            if (itrunc > -1 && nextValues.length - 1 > 0) {
                CacheTimePeriodEntry[] nextValuesTmp = new CacheTimePeriodEntry[nextValues.length - 1];
                Iterator<CacheTimePeriodEntry>[] iteratorsTmp = new Iterator[nextValues.length - 1];
                int j = 0;
                for (int i = 0; i < nextValues.length; i++) {
                    if (nextValues[i] != null) {
                        nextValuesTmp[j] = nextValues[i];
                        iteratorsTmp[j] = iterators[i];
                        j++;
                    }
                }
                if (imin > itrunc) {
                    imin--;
                }
                nextValues = nextValuesTmp;
                iterators = iteratorsTmp;
            }
            nextValues[imin] = null;// selected minvalue position is nullified, so it will loaded with next iterator
                                    // value
        }
        return lminValue;
    }

    @Override
    public boolean hasNext() {
        if (minValue == null) {
            return false;
        }
        nextMinValue = getNextMinValue();
        while (nextMinValue != null && nextMinValue.date == minValue.date) {
            merge(minValue, nextMinValue);
            nextMinValue = getNextMinValue();
        }
        return true;
    }

    private void merge(CacheTimePeriodEntry value1, CacheTimePeriodEntry value2) {
        HashSet<Integer> included = new HashSet();
        for (CacheEventEntry cee1 : value1.getEvents()) {
            for (CacheEventEntry cee2 : value2.getEvents()) {
                if (cee2.eventOrd == cee1.eventOrd) {
                    cee1.docIds.or(cee2.docIds);// merge
                    included.add(cee1.eventOrd);
                }
            }
        }
        for (CacheEventEntry cee2 : value2.getEvents()) {
            if (!included.contains(cee2.eventOrd)) {
                value1.addEventEntry(cee2);
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
