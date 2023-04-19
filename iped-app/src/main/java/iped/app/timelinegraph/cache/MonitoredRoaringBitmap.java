package iped.app.timelinegraph.cache;

import org.roaringbitmap.RoaringBitmap;

public class MonitoredRoaringBitmap extends RoaringBitmap {

    PersistedArrayList containerList;
    CacheTimePeriodEntry selectedCt;
    
    public MonitoredRoaringBitmap(PersistedArrayList persistedArrayList) {
        this.containerList = persistedArrayList;
        this.selectedCt = selectedCt;
    }

    @Override
    public void add(int x) {
        super.add(x);
        containerList.notifyAdd();
    }

}
