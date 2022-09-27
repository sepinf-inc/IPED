package iped.viewers.timelinegraph;

import org.jfree.data.time.Hour;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimePeriod;

import iped.viewers.timelinegraph.model.Minute;

public class ChartTimePeriodConstraint {
	static double HOUR_UNIT_RANGE_SIZE = 60*60*1000;//a week
	static double MINUTE_UNIT_RANGE_SIZE = 60*1000;//a week
	static double SECOND_UNIT_RANGE_SIZE = 1000;//3 hours 
	static double MILLISECOND_UNIT_RANGE_SIZE = 1;//ten minutes
	
	static double HOUR_MAX_RANGE_SIZE = 30l*24l*60l*60l*1000l;//a week
	static double MINUTE_MAX_RANGE_SIZE = 24*60*60*1000;//a week
	static double SECOND_MAX_RANGE_SIZE = 3*60*60*1000;//3 hours 
	static double MILLISECOND_MAX_RANGE_SIZE = 60*1000;//ten minutes

	static double HOUR_MIN_RANGE_SIZE = 3*HOUR_UNIT_RANGE_SIZE;//a week
	static double MINUTE_MIN_RANGE_SIZE = 3*MINUTE_UNIT_RANGE_SIZE;//a week
	static double SECOND_MIN_RANGE_SIZE = 3*SECOND_UNIT_RANGE_SIZE;//3 hours 
	static double MILLISECOND_MIN_RANGE_SIZE = 3;

	double maxZoomoutRangeSize;
	double minZoominRangeSize;
	public double minBarSizeInPixels=2;
	
	static private double minBarWidth = 5;
	public double getTimePeriodUnit(Class<? extends TimePeriod> t) {
		if(t.equals(Hour.class)) {
			return HOUR_UNIT_RANGE_SIZE;
		}
		if(t.equals(Minute.class)) {
			return MINUTE_UNIT_RANGE_SIZE;
		}
		if(t.equals(Second.class)) {
			return SECOND_UNIT_RANGE_SIZE;
		}
		return 5;
	}
}
