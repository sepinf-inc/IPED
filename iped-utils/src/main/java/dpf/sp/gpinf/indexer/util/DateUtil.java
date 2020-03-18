package dpf.sp.gpinf.indexer.util;

import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

import org.apache.tika.utils.DateUtils;

public class DateUtil {
    
    public static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    
    public static final TimeZone MIDDAY = TimeZone.getTimeZone("GMT-12:00");
    
    private static final Pattern datePattern = Pattern.compile("\\d{4}([-:]\\d{2}){2}[T ](\\d{2}:){2}\\d{2}");
    
    private static final DateUtil INSTANCE = new DateUtil();

    private static DateFormat createDateFormat(String format, TimeZone timezone) {
        final SimpleDateFormat sdf =
                new SimpleDateFormat(format, new DateFormatSymbols(Locale.US));
        if (timezone != null) {
            sdf.setTimeZone(timezone);
        }
        return sdf;
    }

    /**
     * So we can return Date objects for these, this is the
     *  list (in preference order) of the various ISO-8601
     *  variants that we try when processing a date based
     *  property.
     */
    private final List<DateFormat> iso8601InputFormats = loadDateFormats();

    private List<DateFormat> loadDateFormats() {
        List<DateFormat> dateFormats = new ArrayList<>();
        // yyyy-mm-ddThh...
        dateFormats.add(createDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", UTC));   // UTC/Zulu
        dateFormats.add(createDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", null));    // With timezone
        dateFormats.add(createDateFormat("yyyy-MM-dd'T'HH:mm:ss", null));     // Without timezone
        // yyyy-mm-dd hh...
        dateFormats.add(createDateFormat("yyyy-MM-dd' 'HH:mm:ss'Z'", UTC));   // UTC/Zulu
        dateFormats.add(createDateFormat("yyyy-MM-dd' 'HH:mm:ssZ", null));    // With timezone
        dateFormats.add(createDateFormat("yyyy-MM-dd' 'HH:mm:ss", null));     // Without timezone
        dateFormats.add(createDateFormat("yyyy:MM:dd' 'HH:mm:ss", null));     // Without timezone
        // Date without time, set to Midday UTC
        dateFormats.add(createDateFormat("yyyy-MM-dd", MIDDAY));       // Normal date format
        dateFormats.add(createDateFormat("yyyy:MM:dd", MIDDAY));              // Image (IPTC/EXIF) format

        return dateFormats;
    }
    
    /**
     * Tries to parse the date string; returns null if no parse was possible.
     *
     * This is not thread safe!  Wrap in synchronized or create new {@link DateUtils}
     * for each class.
     *
     * @param dateString
     * @return
     */
    public Date tryToParse(String dateString) {
        // Java doesn't like timezones in the form ss+hh:mm
        // It only likes the hhmm form, without the colon
        int n = dateString.length();
        if (dateString.charAt(n - 3) == ':'
                && (dateString.charAt(n - 6) == '+' || dateString.charAt(n - 6) == '-')) {
            dateString = dateString.substring(0, n - 3) + dateString.substring(n - 2);
        }

        for (DateFormat df : iso8601InputFormats) {
            try {
                return df.parse(dateString);
            } catch (java.text.ParseException e){

            }
        }
        return null;
    }
    
    /**
     * Thread-safe method internally synchronized
     * 
     * @param val
     * @return
     */
    public static Date tryToParseDate(String val) {
        if(datePattern.matcher(val).find()) {
            synchronized(INSTANCE) {
                return INSTANCE.tryToParse(val);
            }
        }else
            return null;
    }

    // Thread local variable
    private static final ThreadLocal<DateFormat> threadLocal = new ThreadLocal<DateFormat>() {
        @Override
        protected DateFormat initialValue() {
            SimpleDateFormat df = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss"); //$NON-NLS-1$
            df.setTimeZone(TimeZone.getTimeZone("GMT")); //$NON-NLS-1$
            return df;
        }
    };

    public static String dateToString(Date date) {
        return threadLocal.get().format(date);
    }

    public static Date stringToDate(String date) throws ParseException {
        return threadLocal.get().parse(date);
    }
}
