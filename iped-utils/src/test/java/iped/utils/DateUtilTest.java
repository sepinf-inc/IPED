package iped.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import org.junit.Test;

public class DateUtilTest {

    private static void assertInstant(String expectedInstant, String value) {
        Date date = DateUtil.tryToParseDate(value);
        assertEquals("parsing " + value, Instant.parse(expectedInstant), date == null ? null : date.toInstant());
    }

    /** The value has no offset, so it is read in the default timezone (long-standing behavior). */
    private static void assertLocal(String expectedLocalDateTime, String value) {
        Date date = DateUtil.tryToParseDate(value);
        Instant expected = LocalDateTime.parse(expectedLocalDateTime).atZone(ZoneId.systemDefault()).toInstant();
        assertEquals("parsing " + value, expected, date == null ? null : date.toInstant());
    }

    // ── fraction of second combined with an offset ─────────────────────────────

    @Test
    public void testMicrosecondsWithOffsetAndSpaceSeparator() {
        // Python's str(datetime); used to lose the offset and be read as local time
        assertInstant("2023-05-12T14:33:12.123Z", "2023-05-12 14:33:12.123456+00:00");
    }

    @Test
    public void testMicrosecondsWithOffsetAndIsoSeparator() {
        // used to be read as 123456 MILLIseconds, shifting the instant by ~2 minutes
        assertInstant("2023-05-12T14:33:12.123Z", "2023-05-12T14:33:12.123456+00:00");
        assertInstant("2023-05-12T14:33:12.123Z", "2023-05-12T14:33:12.123456Z");
    }

    @Test
    public void testSingleDigitFractionWithNegativeOffset() {
        assertInstant("2023-05-12T14:33:12.900Z", "2023-05-12 09:33:12.9-05:00");
    }

    @Test
    public void testMillisecondsKeepPrecision() {
        assertInstant("2023-05-12T14:33:12.123Z", "2023-05-12T14:33:12.123+00:00");
        assertInstant("2023-05-12T14:33:12.123Z", "2023-05-12T14:33:12.123Z");
    }

    // ── formats that already worked must not regress ───────────────────────────

    @Test
    public void testOffsetWithoutFraction() {
        assertInstant("2023-05-12T14:33:12Z", "2023-05-12 14:33:12+00:00");
        assertInstant("2023-05-12T14:33:12Z", "2023-05-12T14:33:12+00:00");
        assertInstant("2023-05-12T14:33:12Z", "2023-05-12T14:33:12Z");
        assertInstant("2023-05-12T14:33:12Z", "2023-05-12 14:33:12Z");
        assertInstant("2023-05-12T17:33:12Z", "2023-05-12 14:33:12-0300");
    }

    @Test
    public void testMidnightIsParsed() {
        // a zeroed time must not be mistaken for a missing one
        assertInstant("2023-05-12T00:00:00Z", "2023-05-12 00:00:00+00:00");
    }

    @Test
    public void testNamedZoneStillHandledByLegacyFormats() {
        assertInstant("2023-05-12T14:33:12Z", "2023-05-12 14:33:12 UTC");
    }

    @Test
    public void testColonSeparatedDateStillHandledByLegacyFormats() {
        assertLocal("2023-05-12T14:33:12", "2023:05:12 14:33:12");
    }

    @Test
    public void testWithoutOffsetUsesDefaultTimezone() {
        assertLocal("2023-05-12T14:33:12", "2023-05-12 14:33:12");
        assertLocal("2023-05-12T14:33:12", "2023-05-12T14:33:12");
        // the fraction is now kept instead of being discarded
        assertLocal("2023-05-12T14:33:12.123", "2023-05-12 14:33:12.123456");
    }

    @Test
    public void testNonDatesReturnNull() {
        assertNull(DateUtil.tryToParseDate("not a date"));
        assertNull(DateUtil.tryToParseDate(""));
    }

    @Test
    public void testDateWithoutTimeReturnsNull() {
        // datePattern requires a time component, so a date alone is not recognized
        assertNull(DateUtil.tryToParseDate("2023-05-12"));
        assertNull(DateUtil.tryToParseDate("2023:05:12"));
    }

    @Test
    public void testUnsupportedRepresentationsReturnNull() {
        assertNull(DateUtil.tryToParseDate("15/05/2023 14:33:12")); // day-first, non ISO
        assertNull(DateUtil.tryToParseDate("Fri May 12 14:33:12 UTC 2023")); // java.util.Date.toString()
        assertNull(DateUtil.tryToParseDate("1683901992")); // raw epoch seconds
    }
}
