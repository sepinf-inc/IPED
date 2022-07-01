package iped.viewers.timelinegraph;

import java.lang.reflect.InvocationTargetException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

import org.jfree.chart.axis.DateTickUnitType;
import org.jfree.data.time.TimePeriod;

public class DateUtil {
    static SimpleDateFormat ISO8601DATEFORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");

    public static Date removeFromDatePart(Date date, int fromDatePart) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        
        if(fromDatePart==Calendar.DAY_OF_MONTH) {
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
        }
        return cal.getTime();
    }

    public static Date lastdateFromDatePart(Date date, int fromDatePart) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        
        if(fromDatePart==Calendar.DAY_OF_MONTH) {
            cal.set(Calendar.HOUR_OF_DAY, 23);
            cal.set(Calendar.MINUTE, 59);
            cal.set(Calendar.SECOND, 59);
            cal.set(Calendar.MILLISECOND, 999);
        }
        return cal.getTime();
    }

	static public TimePeriod getDateOnConfiguredTimePeriod(Class<? extends TimePeriod> timePeriodClass, Date date) {
		Class[] cArg = new Class[1];
        cArg[0] = Date.class;
		try {
			TimePeriod t = timePeriodClass.getDeclaredConstructor(cArg).newInstance(date);
			return t;
		}catch(InvocationTargetException e) {
			try {
				TimePeriod t = null;
		        Calendar cal = Calendar.getInstance();
		        cal.set(1900, 0, 1, 0, 0, 0);
				if(date.before(cal.getTime())){
					t = timePeriodClass.getDeclaredConstructor(cArg).newInstance(cal.getTime());
				}
		        cal.set(9999, 12, 31, 23, 59, 59);
				if(date.after(cal.getTime())){
					t = timePeriodClass.getDeclaredConstructor(cArg).newInstance(cal.getTime());
				}
				return t;
			}catch(InvocationTargetException | InstantiationException | IllegalAccessException | IllegalArgumentException | NoSuchMethodException | SecurityException e2) {
				e2.printStackTrace();
				return null;
			}
		}catch( InstantiationException | IllegalAccessException | IllegalArgumentException | NoSuchMethodException | SecurityException e){
			e.printStackTrace();
			return null;
		}
	}
	
	static HashMap<DateTickUnitType, DateFormat> dateFormaters = new HashMap<DateTickUnitType, DateFormat>();
	static {
		dateFormaters.put(DateTickUnitType.YEAR, new SimpleDateFormat("yyyy"));
		dateFormaters.put(DateTickUnitType.MONTH, new SimpleDateFormat("MMM-yyyy"));
		dateFormaters.put(DateTickUnitType.DAY, new SimpleDateFormat("d-MMM'\n'yyyy"));
		dateFormaters.put(DateTickUnitType.HOUR, new SimpleDateFormat("HH:'00'\nd-MMM-yyyy"));
		dateFormaters.put(DateTickUnitType.MINUTE, new SimpleDateFormat("HH:mm\nd-MMM-yyyy"));
		dateFormaters.put(DateTickUnitType.SECOND, new SimpleDateFormat(" HH:mm:ss\nd-MMM-yyyy"));
		dateFormaters.put(DateTickUnitType.MILLISECOND, new SimpleDateFormat("HH:mm:ss.SSS\nd-MMM-yyyy"));
	}
	
	static public DateFormat getLongDateFormaterTickUnit(DateTickUnitType tickUnitType) {
		return dateFormaters.get(tickUnitType);
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

	public static Date ISO8601DateParse(String strDate) {
		try {
			return ISO8601DATEFORMAT.parse(strDate);
		} catch (ParseException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static String ISO8601DateFormat(Date date) {
		return ISO8601DATEFORMAT.format(date);
	}
}