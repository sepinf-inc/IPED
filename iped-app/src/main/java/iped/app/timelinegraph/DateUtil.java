package iped.app.timelinegraph;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.jfree.data.time.TimePeriod;

import iped.app.ui.Messages;

public class DateUtil {
    static public String getTimezoneOffsetInformation(TimeZone tz) {
        String timezoneofssetformat = String.format("%02d", (int) tz.getRawOffset() / (int) 3600000);
        timezoneofssetformat += ":";
        timezoneofssetformat += String.format("%02d", (tz.getRawOffset() % 3600000) / 60000);
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

    private static final ThreadLocal<SimpleDateFormat> localDateFormat = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");
        }
    };

    static long computerTimezoneOffset = TimeZone.getDefault().getRawOffset();

    public static Date ISO8601DateParse(String timeStr) {
        byte b[] = timeStr.getBytes();
        if (b.length < 18)
            return null;
        int year = (b[0] - 48) * 1000 + (b[1] - 48) * 100 + (b[2] - 48) * 10 + (b[3] - 48) - 1900;
        int month = (b[5] - 48) * 10 + (b[6] - 48);
        int day = (b[8] - 48) * 10 + (b[9] - 48);
        long time = ((b[11] - 48) * 10 + (b[12] - 48)) * 3600000 + ((b[14] - 48) * 10 + (b[15] - 48)) * 60000 + ((b[17] - 48) * 10 + (b[18] - 48)) * 1000 + computerTimezoneOffset;
        b = null;
        return new Date(new Date(year, month, day).getTime() + time);
    }

    static public String getTimePeriodName(Class<? extends TimePeriod> tpclass) {
        return Messages.getString("TimeLineGraph." + tpclass.getSimpleName());
    }

}