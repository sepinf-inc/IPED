package iped.viewers.timelinegraph.datasets;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jfree.data.time.Day;
import org.jfree.data.time.Hour;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.Month;
import org.jfree.data.time.Quarter;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimePeriod;
import org.jfree.data.time.Week;
import org.jfree.data.time.Year;
import org.jfree.data.xy.AbstractIntervalXYDataset;

import iped.viewers.timelinegraph.IpedChartsPanel;
import iped.viewers.timelinegraph.cache.IndexTimeStampCache;
import iped.viewers.timelinegraph.cache.TimeStampCache;
import iped.viewers.timelinegraph.model.Minute;

/*
 * Implements the method to choose timeline dataset object that represents. 
 * 
 * Obs.: Currently it checks if there is an available cache. If not use a dataset with direct access to lucene resultset.
 */
public class IpedTimelineDatasetManager {
    IpedChartsPanel ipedChartsPanel;

    Thread timeStampCacheThread;
    TimeStampCache timeStampCache;
    Thread timeStampCacheThread2;
    TimeStampCache timeStampCache2;
    Thread timeStampCacheThread3;
    TimeStampCache timeStampCache3;
    Thread timeStampCacheThread4;
    TimeStampCache timeStampCache4;
    Thread timeStampCacheThread5;
    TimeStampCache timeStampCache5;
    Thread timeStampCacheThread6;
    TimeStampCache timeStampCache6;

    TimeStampCache selectedTimeStampCache;

    CaseSearchFilterListenerFactory cacheFLFactory;
    CaseSearchFilterListenerFactory luceneFLFactory;

    public IpedTimelineDatasetManager(IpedChartsPanel ipedChartsPanel) {
        this.ipedChartsPanel = ipedChartsPanel;

        luceneFLFactory = new CaseSearchFilterListenerFactory(LuceneFilterListener.class);

        timeStampCache = new IndexTimeStampCache(ipedChartsPanel, ipedChartsPanel.getResultsProvider());
        timeStampCache.addTimePeriodClassToCache(Day.class);
        timeStampCacheThread = new Thread(timeStampCache);

        timeStampCache2 = new IndexTimeStampCache(ipedChartsPanel, ipedChartsPanel.getResultsProvider());
        timeStampCache2.addTimePeriodClassToCache(Hour.class);
        timeStampCacheThread2 = new Thread(timeStampCache2);

        timeStampCache3 = new IndexTimeStampCache(ipedChartsPanel, ipedChartsPanel.getResultsProvider());
        timeStampCache3.addTimePeriodClassToCache(Year.class);
        timeStampCache3.addTimePeriodClassToCache(Quarter.class);
        timeStampCache3.addTimePeriodClassToCache(Week.class);
        timeStampCache3.addTimePeriodClassToCache(Month.class);
        timeStampCacheThread3 = new Thread(timeStampCache3);

        timeStampCache4 = new IndexTimeStampCache(ipedChartsPanel, ipedChartsPanel.getResultsProvider());
        timeStampCache4.addTimePeriodClassToCache(Minute.class);
        timeStampCacheThread4 = new Thread(timeStampCache4);

        timeStampCache5 = new IndexTimeStampCache(ipedChartsPanel, ipedChartsPanel.getResultsProvider());
        timeStampCache5.addTimePeriodClassToCache(Second.class);
        timeStampCacheThread5 = new Thread(timeStampCache5);

        timeStampCache6 = new IndexTimeStampCache(ipedChartsPanel, ipedChartsPanel.getResultsProvider());
        timeStampCache6.addTimePeriodClassToCache(Millisecond.class);
        timeStampCacheThread6 = new Thread(timeStampCache6);
    }

    public AbstractIntervalXYDataset getBestDataset(Class<? extends TimePeriod> timePeriodClass, String splitValue) {
        try {
            if (timeStampCache.hasTimePeriodClassToCache(timePeriodClass)) {
                selectedTimeStampCache = timeStampCache;
                return new IpedTimelineDataset(this, ipedChartsPanel.getResultsProvider(), null, splitValue);
            }
            if (timeStampCache2.hasTimePeriodClassToCache(timePeriodClass)) {
                selectedTimeStampCache = timeStampCache2;
                return new IpedTimelineDataset(this, ipedChartsPanel.getResultsProvider(), null, splitValue);
            }
            if (timeStampCache3.hasTimePeriodClassToCache(timePeriodClass)) {
                selectedTimeStampCache = timeStampCache3;
                return new IpedTimelineDataset(this, ipedChartsPanel.getResultsProvider(), null, splitValue);
            }
            if (timeStampCache4.hasTimePeriodClassToCache(timePeriodClass)) {
                selectedTimeStampCache = timeStampCache4;
                return new IpedTimelineDataset(this, ipedChartsPanel.getResultsProvider(), null, splitValue);
            }
            if (timeStampCache5.hasTimePeriodClassToCache(timePeriodClass)) {
                selectedTimeStampCache = timeStampCache5;
                return new IpedTimelineDataset(this, ipedChartsPanel.getResultsProvider(), null, splitValue);
            }
            if (timeStampCache6.hasTimePeriodClassToCache(timePeriodClass)) {
                selectedTimeStampCache = timeStampCache6;
                return new IpedTimelineDataset(this, ipedChartsPanel.getResultsProvider(), null, splitValue);
            }
            AbstractIntervalXYDataset res = new IpedTimelineDataset(this, ipedChartsPanel.getResultsProvider(), luceneFLFactory, splitValue);
            return res;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /*
     * Start the creation of cache for timeline chart
     */
    public void startBackgroundCacheCreation() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        ExecutorService threadPool = Executors.newFixedThreadPool(1);
        threadPool.execute(timeStampCacheThread);// first loads the day cache
        threadPool.execute(timeStampCacheThread2);// loads the other timeperiodCaches
        threadPool.execute(timeStampCacheThread3);// loads the other timeperiodCaches
        threadPool.execute(timeStampCacheThread4);// loads the other timeperiodCaches
        threadPool.execute(timeStampCacheThread5);// loads the other timeperiodCaches
        threadPool.execute(timeStampCacheThread6);// loads the other timeperiodCaches
    }

    public TimeStampCache getCache() {
        return selectedTimeStampCache;
    }

    public IpedChartsPanel getIpedChartsPanel() {
        return ipedChartsPanel;
    }

}
