package iped.viewers.timelinegraph;

import java.util.Calendar;
import java.util.TimeZone;

public class DateUtil {
	static public String getTimezoneOffsetInformation(TimeZone tz) {
		String timezoneofssetformat = String.format("%02d", (int)tz.getRawOffset()/(int)3600000);
		timezoneofssetformat+=":";
		timezoneofssetformat+=String.format("%02d", (tz.getRawOffset()%3600000)/60000);
		return timezoneofssetformat;
	}

	static public int getUpperCalendarField(int calendarField) {
		switch (calendarField) {
		case Calendar.DAY_OF_MONTH:
			return Calendar.MONTH;
		case Calendar.HOUR:
		case Calendar.HOUR_OF_DAY:
			return Calendar.DAY_OF_MONTH;
		case Calendar.MINUTE:
			return Calendar.HOUR;
		case Calendar.SECOND:
			return Calendar.MINUTE;
		case Calendar.MILLISECOND:
			return Calendar.SECOND;
		case Calendar.MONTH:
			return Calendar.YEAR;
		default:
			return calendarField;
		}
	}
}