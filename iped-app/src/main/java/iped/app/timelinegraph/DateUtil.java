package iped.app.timelinegraph;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.jfree.data.time.Day;
import org.jfree.data.time.FixedMillisecond;
import org.jfree.data.time.Hour;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.Month;
import org.jfree.data.time.Quarter;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimePeriod;
import org.jfree.data.time.Week;
import org.jfree.data.time.Year;

import iped.app.ui.Messages;
import iped.jfextensions.model.Minute;

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
        return new Date(new Date(year, month - 1, day).getTime() + time);
    }

    static public String getTimePeriodName(Class<? extends TimePeriod> tpclass) {
        return Messages.getString("TimeLineGraph." + tpclass.getSimpleName());
    }

    public static Date ISO8601DateParse(Class<? extends TimePeriod> timePeriodClass, byte[] b) {
        try {
            if (b.length < 18)
                return null;
            int year = (b[0] - 48) * 1000 + (b[1] - 48) * 100 + (b[2] - 48) * 10 + (b[3] - 48) - 1900;
            int month = ((b[5] - 48) * 10 + (b[6] - 48)) - 1;
            int day = (b[8] - 48) * 10 + (b[9] - 48);
            long time = ((b[11] - 48) * 10 + (b[12] - 48)) * 3600000 + ((b[14] - 48) * 10 + (b[15] - 48)) * 60000 + ((b[17] - 48) * 10 + (b[18] - 48)) * 1000 + computerTimezoneOffset;
            if (time >= 24 * 3600000) {
                day += 1;
                time = time % 24 * 3600000;
            } else {
                if (time < 0) {
                    day -= 1;
                    time = 24 * 3600000 + time;
                }
            }

            if (timePeriodClass == Day.class) {
                return new Date(year, month, day);
            } else if (timePeriodClass == Hour.class) {
                int hour = (int) Math.floorDiv(time, 1000 * 60 * 60);
                return new Date(year, month, day, hour, 0, 0);
            } else if (timePeriodClass == Year.class) {
                Date d = new Date(year, month, day);
                return new Date(d.getYear(), 0, 1);
            } else if (timePeriodClass == Quarter.class) {
                Date d = new Date(year, month, day);
                return new Date(d.getYear(), Math.floorDiv(d.getMonth(), 3) * 3, 1);
            } else if (timePeriodClass == Month.class) {
                Date d = new Date(year, month, day);
                return new Date(d.getYear(), d.getMonth(), 1);
            } else if (timePeriodClass == Week.class) {
                Calendar calendar = Calendar.getInstance();
                Date d = new Date(year, month, day);
                calendar.setTime(d);
                int week;
                // sometimes the last few days of the year are considered to fall in
                // the *first* week of the following year. Refer to the Javadocs for
                // GregorianCalendar.
                int tempWeek = calendar.get(Calendar.WEEK_OF_YEAR);
                if (tempWeek == 1 && calendar.get(Calendar.MONTH) == Calendar.DECEMBER) {
                    week = 1;
                    year = (short) (calendar.get(Calendar.YEAR) + 1);
                } else {
                    week = (byte) Math.min(tempWeek, Week.LAST_WEEK_IN_YEAR);
                    int yyyy = calendar.get(Calendar.YEAR);
                    // alternatively, sometimes the first few days of the year are
                    // considered to fall in the *last* week of the previous year...
                    if (calendar.get(Calendar.MONTH) == Calendar.JANUARY && week >= 52) {
                        yyyy--;
                    }
                    year = (short) yyyy;
                }
                Calendar c = (Calendar) calendar.clone();
                c.clear();
                c.set(Calendar.YEAR, year);
                c.set(Calendar.WEEK_OF_YEAR, week);
                c.set(Calendar.DAY_OF_WEEK, c.getFirstDayOfWeek());
                c.set(Calendar.HOUR, 0);
                c.set(Calendar.MINUTE, 0);
                c.set(Calendar.SECOND, 0);
                c.set(Calendar.MILLISECOND, 0);

                return c.getTime();
            } else if (timePeriodClass == Minute.class) {
                int hour = (int) Math.floorDiv(time, 1000 * 60 * 60);
                int minute = (int) Math.floorDiv(time, 1000 * 60) - hour * 60;
                return new Date(year, month, day, hour, minute, 0);
            } else if (timePeriodClass == iped.jfextensions.model.Minute.class) {
                int hour = (int) Math.floorDiv(time, 1000 * 60 * 60);
                int minute = (int) Math.floorDiv(time, 1000 * 60) - hour * 60;
                return new Date(year, month, day, hour, minute, 0);
            } else if (timePeriodClass == Second.class) {
                int hour = (int) Math.floorDiv(time, 1000 * 60 * 60);
                int minute = (int) Math.floorDiv(time, 1000 * 60) - hour * 60;
                int second = (int) Math.floorDiv(time, 1000) - hour * 60 * 60 - minute * 60;
                return new Date(year, month, day, hour, minute, second);
            } else if (timePeriodClass == Millisecond.class) {
                return new Date(new Date(year, month, day).getTime() + time);
            } else if (timePeriodClass == FixedMillisecond.class) {
                return new Date(new Date(year, month, day).getTime() + time);
            }
        } catch (Exception e) {
            return null;

        }
        throw new RuntimeException(timePeriodClass.getName() + " not handled!");
    }

    public static Date ISO8601DateParse(Class<? extends TimePeriod> timePeriodClass, String timeStr) {
        TimePeriod t = null;
        byte b[] = timeStr.getBytes();
        return ISO8601DateParse(timePeriodClass, b);
    }

}