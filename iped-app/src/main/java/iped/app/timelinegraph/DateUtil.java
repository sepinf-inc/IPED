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

    static public Date ISO8601DateParse(String strDate) {
        try {
            return localDateFormat.get().parse(strDate);

        } catch (ParseException | RuntimeException e) {
            e.printStackTrace();
            return null;
        }
    }

    static public String getTimePeriodName(Class<? extends TimePeriod> tpclass) {
        return Messages.getString("TimeLineGraph." + tpclass.getSimpleName());
    }

}