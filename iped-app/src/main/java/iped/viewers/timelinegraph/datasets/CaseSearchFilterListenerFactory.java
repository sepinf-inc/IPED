package iped.viewers.timelinegraph.datasets;

import java.lang.reflect.InvocationTargetException;

import iped.app.ui.CaseSearchFilterListener;
import iped.app.ui.CaseSearcherFilter;

public class CaseSearchFilterListenerFactory {
    Class<? extends CaseSearchFilterListener> csflClass;

    public CaseSearchFilterListenerFactory(Class<? extends CaseSearchFilterListener> csflClass) {
        this.csflClass = csflClass;
    }

    public CaseSearchFilterListener getCaseSearchFilterListener(String eventType, CaseSearcherFilter csf, IpedTimelineDataset ipedTimelineDataset, String bookmark) {
        Class[] cArg = new Class[4];
        cArg[0] = String.class;
        cArg[1] = CaseSearcherFilter.class;
        cArg[2] = IpedTimelineDataset.class;
        cArg[3] = String.class;
        CaseSearchFilterListener t = null;
        try {
            t = csflClass.getDeclaredConstructor(cArg).newInstance(eventType, csf, ipedTimelineDataset, bookmark);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
            e.printStackTrace();
        }

        return t;
    }
}
