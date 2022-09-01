package iped.viewers.timelinegraph.cache;

import java.util.ArrayList;
import java.util.Date;

/*
 * Represent a cache timeperiod entry on cache persistance
 */
public class CacheTimePeriodEntry implements Comparable<CacheTimePeriodEntry>{
	public Date date;
	public ArrayList<CacheEventEntry> events;
	
	@Override
	public int compareTo(CacheTimePeriodEntry entry) {
		return this.date.compareTo(entry.date);
	}
	
}
