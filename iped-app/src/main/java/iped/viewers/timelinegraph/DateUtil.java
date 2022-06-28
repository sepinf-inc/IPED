package iped.viewers.timelinegraph;

import java.lang.reflect.InvocationTargetException;
import java.util.Calendar;
import java.util.Date;

import org.jfree.data.time.TimePeriod;

public class DateUtil {

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
}