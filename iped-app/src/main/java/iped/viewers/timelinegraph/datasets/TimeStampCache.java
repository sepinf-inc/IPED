package iped.viewers.timelinegraph.datasets;

import java.util.ArrayList;
import java.util.Map;

import org.jfree.data.time.TimePeriod;

public interface TimeStampCache extends Runnable{
	public Map<TimePeriod, ArrayList<Integer>> getCachedEventTimeStamps(String eventField);
	public void addTimePeriodClassToCache(Class<? extends TimePeriod> timePeriodClass);	
	public boolean hasTimePeriodClassToCache(Class<? extends TimePeriod> timePeriodClass);	
}
