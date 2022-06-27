package iped.viewers.timelinegraph;

import java.util.Calendar;
import java.util.Date;

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

}