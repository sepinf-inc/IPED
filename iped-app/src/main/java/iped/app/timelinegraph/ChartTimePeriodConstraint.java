package iped.app.timelinegraph;

import org.jfree.data.time.Day;
import org.jfree.data.time.Hour;
import org.jfree.data.time.Month;
import org.jfree.data.time.Quarter;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimePeriod;
import org.jfree.data.time.Week;
import org.jfree.data.time.Year;

import iped.jfextensions.model.Minute;

public class ChartTimePeriodConstraint {
    static double YEAR_UNIT_RANGE_SIZE = 365l * 24l * 60l * 60l * 1000l;// a week
    static double QUARTER_UNIT_RANGE_SIZE = 90l * 24l * 60l * 60l * 1000l;// a week
    static double MONTH_UNIT_RANGE_SIZE = 31l * 24l * 60l * 60l * 1000l;// a week
    static double WEEK_UNIT_RANGE_SIZE = 7l * 24l * 60l * 60l * 1000l;// a week
    static double DAY_UNIT_RANGE_SIZE = 24l * 60l * 60l * 1000l;// a week
    static double HOUR_UNIT_RANGE_SIZE = 60l * 60l * 1000l;// a week
    static double MINUTE_UNIT_RANGE_SIZE = 60l * 1000l;// a week
    static double SECOND_UNIT_RANGE_SIZE = 1000;// 3 hours
    static double MILLISECOND_UNIT_RANGE_SIZE = 1;// ten minutes

    static double HOUR_MAX_RANGE_SIZE = 30l * 24l * 60l * 60l * 1000l;// a week
    static double MINUTE_MAX_RANGE_SIZE = 24l * 60l * 60l * 1000l;// a week
    static double SECOND_MAX_RANGE_SIZE = 3l * 60l * 60l * 1000l;// 3 hours
    static double MILLISECOND_MAX_RANGE_SIZE = 60 * 1000;// ten minutes

    static double HOUR_MIN_RANGE_SIZE = 3 * HOUR_UNIT_RANGE_SIZE;// a week
    static double MINUTE_MIN_RANGE_SIZE = 3 * MINUTE_UNIT_RANGE_SIZE;// a week
    static double SECOND_MIN_RANGE_SIZE = 3 * SECOND_UNIT_RANGE_SIZE;// 3 hours
    static double MILLISECOND_MIN_RANGE_SIZE = 3;

    double maxZoomoutRangeSize;
    double minZoominRangeSize;
    public double minBarSizeInPixels = 2;
    static private double minBarWidth = 5;

    static public double getTimePeriodUnit(Class<? extends TimePeriod> t) {
        if (t.equals(Year.class)) {
            return YEAR_UNIT_RANGE_SIZE;
        }
        if (t.equals(Quarter.class)) {
            return QUARTER_UNIT_RANGE_SIZE;
        }
        if (t.equals(Month.class)) {
            return MONTH_UNIT_RANGE_SIZE;
        }
        if (t.equals(Week.class)) {
            return WEEK_UNIT_RANGE_SIZE;
        }
        if (t.equals(Day.class)) {
            return DAY_UNIT_RANGE_SIZE;
        }
        if (t.equals(Hour.class)) {
            return HOUR_UNIT_RANGE_SIZE;
        }
        if (t.equals(Minute.class)) {
            return MINUTE_UNIT_RANGE_SIZE;
        }
        if (t.equals(Second.class)) {
            return SECOND_UNIT_RANGE_SIZE;
        }
        return 5;
    }

    public double getMaxZoomoutRangeSize() {
        return maxZoomoutRangeSize;
    }

    public double getMinZoominRangeSize() {
        return minZoominRangeSize;
    }
}
