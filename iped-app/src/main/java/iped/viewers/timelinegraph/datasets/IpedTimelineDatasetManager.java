package iped.viewers.timelinegraph.datasets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jfree.data.time.Day;
import org.jfree.data.time.Hour;
import org.jfree.data.time.Minute;
import org.jfree.data.time.Month;
import org.jfree.data.time.Quarter;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimePeriod;
import org.jfree.data.time.Week;
import org.jfree.data.time.Year;

import iped.data.IItemId;
import iped.viewers.timelinegraph.IpedChartsPanel;
import iped.viewers.timelinegraph.cache.TimeStampCache;

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
        
    TimeStampCache selectedTimeStampCache;
    
    CaseSearchFilterListenerFactory cacheFLFactory;
    CaseSearchFilterListenerFactory luceneFLFactory;
	
	public IpedTimelineDatasetManager(IpedChartsPanel ipedChartsPanel){
		this.ipedChartsPanel = ipedChartsPanel;

		cacheFLFactory=new CaseSearchFilterListenerFactory(CachedFilterListener.class);
		luceneFLFactory=new CaseSearchFilterListenerFactory(LuceneFilterListener.class);

		timeStampCache = new TimeStampCache(ipedChartsPanel, ipedChartsPanel.getResultsProvider());
		timeStampCache.addTimePeriodClassToCache(Day.class);
		timeStampCacheThread = new Thread(timeStampCache);
		
		timeStampCache2 = new TimeStampCache(ipedChartsPanel, ipedChartsPanel.getResultsProvider());
		timeStampCache2.addTimePeriodClassToCache(Hour.class);
		timeStampCacheThread2 = new Thread(timeStampCache2);

		timeStampCache3 = new TimeStampCache(ipedChartsPanel, ipedChartsPanel.getResultsProvider());
		timeStampCache3.addTimePeriodClassToCache(Year.class);
		timeStampCache3.addTimePeriodClassToCache(Quarter.class);
		timeStampCache3.addTimePeriodClassToCache(Week.class);
		timeStampCache3.addTimePeriodClassToCache(Month.class);
		timeStampCacheThread3 = new Thread(timeStampCache3);

		timeStampCache4 = new TimeStampCache(ipedChartsPanel, ipedChartsPanel.getResultsProvider());
		timeStampCache4.addTimePeriodClassToCache(Minute.class);
		timeStampCacheThread4 = new Thread(timeStampCache4);

		timeStampCache5 = new TimeStampCache(ipedChartsPanel, ipedChartsPanel.getResultsProvider());
		timeStampCache5.addTimePeriodClassToCache(Second.class);
		timeStampCacheThread5 = new Thread(timeStampCache5);
    }

	public IpedTimelineDataset getBestDataset(Class<? extends TimePeriod> timePeriodClass, String splitValue){
		try {/*
			if(timeStampCache.hasTimePeriodClassToCache(timePeriodClass)) {
				selectedTimeStampCache=timeStampCache;
				return new IpedTimelineDataset(this, ipedChartsPanel.getResultsProvider(), cacheFLFactory,splitValue);
			}
			if(timeStampCache2.hasTimePeriodClassToCache(timePeriodClass)) {
				selectedTimeStampCache=timeStampCache2;
				return new IpedTimelineDataset(this, ipedChartsPanel.getResultsProvider(), cacheFLFactory,splitValue);
			}
			if(timeStampCache3.hasTimePeriodClassToCache(timePeriodClass)) {
				selectedTimeStampCache=timeStampCache3;
				return new IpedTimelineDataset(this, ipedChartsPanel.getResultsProvider(), cacheFLFactory,splitValue);
			}
			if(timeStampCache4.hasTimePeriodClassToCache(timePeriodClass)) {
				selectedTimeStampCache=timeStampCache4;
				return new IpedTimelineDataset(this, ipedChartsPanel.getResultsProvider(), cacheFLFactory,splitValue);
			}
			if(timeStampCache5.hasTimePeriodClassToCache(timePeriodClass)) {
				selectedTimeStampCache=timeStampCache5;
				return new IpedTimelineDataset(this, ipedChartsPanel.getResultsProvider(), cacheFLFactory,splitValue);
			}*/
			return new IpedTimelineDataset(this, ipedChartsPanel.getResultsProvider(), luceneFLFactory,splitValue);
			
		}catch(Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public void startBackgroundCaching(){
		/*
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		ExecutorService threadPool = Executors.newFixedThreadPool(1);
		threadPool.execute(timeStampCacheThread);//first loads the day cache
		threadPool.execute(timeStampCacheThread2);//loads the other timeperiodCaches			
		threadPool.execute(timeStampCacheThread3);//loads the other timeperiodCaches			
		threadPool.execute(timeStampCacheThread4);//loads the other timeperiodCaches			
		threadPool.execute(timeStampCacheThread5);//loads the other timeperiodCaches
		*/			
	}

	public Map<TimePeriod, ArrayList<Integer>> getCachedEventTimeStamps(String eventType) {
		return selectedTimeStampCache.getCachedEventTimeStamps(eventType);
	}
	
}
