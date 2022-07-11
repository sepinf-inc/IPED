package iped.viewers.timelinegraph.cache;

import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.SortedSet;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;
import org.jfree.data.time.TimePeriod;

import iped.properties.ExtraProperties;
import iped.viewers.api.IMultiSearchResultProvider;
import iped.viewers.timelinegraph.IpedChartsPanel;

public class TimeStampCache implements Runnable{
    TreeMap<String, Map<String, Map<TimePeriod,ArrayList<Integer>>>> timePeriodClassTimeCacheTree;
    Map<String, Map<TimePeriod,ArrayList<Integer>>> timeStampCacheTree;
	ArrayList<Class<? extends TimePeriod>> periodClassesToCache = new ArrayList<Class<? extends TimePeriod>>();

    volatile int running=0;
	Semaphore timeStampCacheSemaphore = new Semaphore(1);
	IMultiSearchResultProvider resultsProvider;
	IpedChartsPanel ipedChartsPanel;
	
	public TimeStampCache(IpedChartsPanel ipedChartsPanel, IMultiSearchResultProvider resultsProvider) {
		this.resultsProvider = resultsProvider;		
		this.ipedChartsPanel=ipedChartsPanel;
		timePeriodClassTimeCacheTree = new TreeMap<String, Map<String, Map<TimePeriod,ArrayList<Integer>>>>();
	}
    
    SortedSet<String> eventTypes = new TreeSet<String>();
	final Object monitor = new Object();
	
	@Override
	public void run() {
		try {
			timeStampCacheSemaphore.acquire();
			Date d1 = new Date();
			
			if(periodClassesToCache.size()==0) {
				periodClassesToCache.add(ipedChartsPanel.getTimePeriodClass());
			}
			
	        LeafReader reader = resultsProvider.getIPEDSource().getLeafReader();

	        SortedSetDocValues timeEventGroupValues = reader.getSortedSetDocValues(ExtraProperties.TIME_EVENT_GROUPS);

			ArrayList<EventTimestampCache> cacheLoaders = new ArrayList<EventTimestampCache>();
			
	        TermsEnum te = timeEventGroupValues.termsEnum();
			BytesRef br = te.next();
			while(br!=null) {
	       		StringTokenizer st = new StringTokenizer(br.utf8ToString(), "|");
	       		while(st.hasMoreTokens()) {
	       			String eventType = st.nextToken().trim();
	       			if(eventTypes.add(eventType)) {
	       				cacheLoaders.add(new EventTimestampCache(ipedChartsPanel,resultsProvider,this, eventType));
	       			}
	       		}
				br = te.next();
			}
			running=cacheLoaders.size();
			
			ExecutorService threadPool = Executors.newFixedThreadPool(1);
			for(EventTimestampCache cacheLoader:cacheLoaders) {
				threadPool.execute(cacheLoader);
			}
			
			try {
				synchronized (monitor) {
					monitor.wait();
					Date d2 = new Date();
					System.out.println("Tempo para montar o cache:"+(d2.getTime()-d1.getTime()));
					timeStampCacheSemaphore.release();
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public Map<TimePeriod, ArrayList<Integer>> getCachedEventTimeStamps(String eventField) {
		try {
			timeStampCacheSemaphore.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}finally {
			timeStampCacheSemaphore.release();
		}

		timeStampCacheTree = timePeriodClassTimeCacheTree.get(ipedChartsPanel.getTimePeriodClass().getSimpleName());

		return timeStampCacheTree.get(eventField);
	}
	
	public void addTimePeriodClassToCache(Class<? extends TimePeriod> timePeriodClass) {
		periodClassesToCache.add(timePeriodClass);
	}
	
	public boolean hasTimePeriodClassToCache(Class<? extends TimePeriod> timePeriodClass) {
		return periodClassesToCache.contains(timePeriodClass);
	}

}