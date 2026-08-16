package iped.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

import org.apache.tika.utils.DateUtils;

import iped.data.IItemReader;

public class DateUtil {

    public static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    public static final TimeZone MIDDAY = TimeZone.getTimeZone("GMT-12:00");

    private static final Pattern datePattern = Pattern.compile("\\d{4}([-:]\\d{2}){2}[T ](\\d{2}:){2}\\d{2}");

    private static final DateUtil INSTANCE = new DateUtil();

    /**
     * ISO-8601 dates with either separator ('T' or space), an optional fraction of second of any precision and an
     * optional offset (+HH:MM, +HHMM or Z).
     *
     * SimpleDateFormat cannot express this: it needs one pattern per fraction/offset/separator combination and, worse,
     * its 'SSS' is a plain number field, so a value with microseconds ("...12.123456") is read as 123456 MILLIseconds
     * and the instant is silently shifted by about two minutes. Values whose fraction is not exactly 3 digits used to
     * fall back to a pattern without the offset, which dropped the timezone and read the date as local time.
     */
    private static final DateTimeFormatter ISO_WITH_OPTIONAL_FRACTION_AND_OFFSET = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd[['T'][' ']]HH:mm:ss")
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .optionalStart().appendOffset("+HH:MM", "Z").optionalEnd()
            .optionalStart().appendOffset("+HHMM", "Z").optionalEnd()
            .toFormatter(Locale.US);

    private static DateFormat createDateFormat(String format, TimeZone timezone) {
        final SimpleDateFormat sdf = new SimpleDateFormat(format, new DateFormatSymbols(Locale.US));
        if (timezone != null) {
            sdf.setTimeZone(timezone);
        }
        return sdf;
    }

    /**
     * So we can return Date objects for these, this is the list (in preference
     * order) of the various ISO-8601 variants that we try when processing a date
     * based property.
     */
    private final List<DateFormat> iso8601InputFormats = loadDateFormats();

    private List<DateFormat> loadDateFormats() {
        List<DateFormat> dateFormats = new ArrayList<>();
        // yyyy-mm-ddThh...
        dateFormats.add(createDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", UTC)); // UTC/Zulu
        dateFormats.add(createDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", null)); // With timezone
        dateFormats.add(createDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", null));// With timezone
        dateFormats.add(createDateFormat("yyyy-MM-dd'T'HH:mm:ss", null)); // Without timezone
        // yyyy-mm-dd hh...
        dateFormats.add(createDateFormat("yyyy-MM-dd' 'HH:mm:ss'Z'", UTC)); // UTC/Zulu
        dateFormats.add(createDateFormat("yyyy-MM-dd' 'HH:mm:ssZ", null)); // With timezone
        dateFormats.add(createDateFormat("yyyy-MM-dd' 'HH:mm:ss", null)); // Without timezone
        dateFormats.add(createDateFormat("yyyy:MM:dd' 'HH:mm:ss", null)); // Without timezone
        // Date without time, set to Midday UTC
        dateFormats.add(createDateFormat("yyyy-MM-dd", MIDDAY)); // Normal date format
        dateFormats.add(createDateFormat("yyyy:MM:dd", MIDDAY)); // Image (IPTC/EXIF) format

        return dateFormats;
    }

    /**
     * Tries to parse the date string; returns null if no parse was possible.
     *
     * This is not thread safe! Wrap in synchronized or create new {@link DateUtils}
     * for each class.
     *
     * @param dateString
     * @return
     */
    public Date tryToParse(String dateString) {
        // Java doesn't like timezones in the form ss+hh:mm
        // It only likes the hhmm form, without the colon
        int n = dateString.length();
        if (dateString.charAt(n - 3) == ':' && (dateString.charAt(n - 6) == '+' || dateString.charAt(n - 6) == '-')) {
            dateString = dateString.substring(0, n - 3) + dateString.substring(n - 2);
        }

        for (DateFormat df : iso8601InputFormats) {
            try {
                return df.parse(dateString);
            } catch (java.text.ParseException e) {

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
        if (!datePattern.matcher(val).find()) {
            return null;
        }
        // DateTimeFormatter is immutable and thread safe, so this runs unsynchronized
        Date date = parseIsoDate(val);
        if (date != null) {
            return date;
        }
        // formats the builder above does not cover (e.g. "yyyy:MM:dd HH:mm:ss", named zones like " UTC")
        synchronized (INSTANCE) {
            return INSTANCE.tryToParse(val);
        }
    }

    /**
     * Parses the ISO-8601 variants of {@link #ISO_WITH_OPTIONAL_FRACTION_AND_OFFSET}, or returns null when the value is
     * not one of them. Values carrying an offset are resolved to that offset; values without one keep the previous
     * behavior of being interpreted in the default timezone.
     */
    private static Date parseIsoDate(String value) {
        try {
            TemporalAccessor parsed = ISO_WITH_OPTIONAL_FRACTION_AND_OFFSET.parse(value);
            try {
                return Date.from(Instant.from(parsed));
            } catch (DateTimeException e) {
                return Date.from(LocalDateTime.from(parsed).atZone(ZoneId.systemDefault()).toInstant());
            }
        } catch (DateTimeException e) {
            return null;
        }
    }

    // Thread local variable
    private static final ThreadLocal<DateFormat> threadLocal = new ThreadLocal<DateFormat>() {
        @Override
        protected DateFormat initialValue() {
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'"); //$NON-NLS-1$
            df.setTimeZone(UTC);
            return df;
        }
    };

    public static String dateToString(Date date) {
        return threadLocal.get().format(date);
    }

    public static Date stringToDate(String date) throws ParseException {
        return threadLocal.get().parse(date);
    }

    /**
     * Updates the file times (creation, modified, access) of the specified path.
     */
    public static void updatePathTimes(Path path, IItemReader item) throws IOException {

        // Get the view wrapper for modifying attributes
        @SuppressWarnings("null")
        BasicFileAttributeView view = Files.getFileAttributeView(path, BasicFileAttributeView.class);

        // Define your new dates using java.time.Instant
        FileTime creationTime = item.getCreationDate() != null ? FileTime.from(item.getCreationDate().toInstant()) : null;
        FileTime modifiedTime = item.getModDate() != null ? FileTime.from(item.getModDate().toInstant()) : null;
        FileTime accessTime = item.getAccessDate() != null ? FileTime.from(item.getAccessDate().toInstant()) : null;

        // Apply changes (Pass null to ignore a specific property)
        view.setTimes(modifiedTime, accessTime, creationTime);
    }
}
