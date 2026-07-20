package iped.engine.task.aleapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.tika.metadata.Message;
import org.junit.Test;

import iped.engine.data.Item;
import iped.engine.task.aleapp.interceptors.IlapfuncsTsvInterceptor;
import iped.engine.tika.SyncMetadata;
import iped.properties.ExtraProperties;

public class IlapfuncsTsvInterceptorTest {

    private IlapfuncsTsvInterceptor interceptor = new IlapfuncsTsvInterceptor();

    private Item newItem() {
        Item item = new Item();
        item.setMetadata(new SyncMetadata());
        return item;
    }

    private static final Class<?> STANDARD_FIELD;
    static {
        try {
            STANDARD_FIELD = Class.forName(
                    "iped.engine.task.aleapp.interceptors.IlapfuncsTsvInterceptor$StandardField");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private void invokeMapStandard(Item item, String header, String value) throws Exception {
        Method classify = IlapfuncsTsvInterceptor.class.getDeclaredMethod("classifyHeader", String.class);
        classify.setAccessible(true);
        Object field = classify.invoke(null, header);
        Method apply = IlapfuncsTsvInterceptor.class
                .getDeclaredMethod("applyStandardField", Item.class, STANDARD_FIELD, String.class);
        apply.setAccessible(true);
        apply.invoke(null, item, field, value);
    }

    private Object invokeCellValue(List<Object> data, int i) throws Exception {
        Method m = IlapfuncsTsvInterceptor.class.getDeclaredMethod("cellValue", List.class, int.class);
        m.setAccessible(true);
        return m.invoke(null, data, i);
    }

    // ── Bounds safety ──────────────────────────────────────────────────────────

    @Test
    public void testShortRowYieldsNullForMissingColumns() throws Exception {
        // Row has 2 values but headers expect 4 — must not throw IndexOutOfBoundsException
        List<Object> shortRow = Arrays.asList("2024-01-15T10:00:00Z", "Alice");

        assertEquals("Alice", invokeCellValue(shortRow, 1));
        assertNull("Missing column must be null, not an exception", invokeCellValue(shortRow, 2));
        assertNull("Missing column must be null, not an exception", invokeCellValue(shortRow, 3));
    }

    @Test
    public void testEmptyRowYieldsNull() throws Exception {
        assertNull(invokeCellValue(Collections.emptyList(), 0));
        assertNull(invokeCellValue(Collections.emptyList(), 1));
    }

    // ── Standard metadata mapping ──────────────────────────────────────────────

    @Test
    public void testTimestampMapsToMessageDate() throws Exception {
        Item item = newItem();
        invokeMapStandard(item, "Timestamp", "2024-01-15T10:00:00Z");
        assertEquals("2024-01-15T10:00:00Z", item.getMetadata().get(ExtraProperties.MESSAGE_DATE));
    }

    @Test
    public void testDateMapsToMessageDate() throws Exception {
        Item item = newItem();
        invokeMapStandard(item, "Date", "2024-01-15");
        assertEquals("2024-01-15", item.getMetadata().get(ExtraProperties.MESSAGE_DATE));
    }

    @Test
    public void testDateTimeMapsToMessageDate() throws Exception {
        Item item = newItem();
        invokeMapStandard(item, "Date/Time", "2024-01-15 10:00:00");
        assertEquals("2024-01-15 10:00:00", item.getMetadata().get(ExtraProperties.MESSAGE_DATE));
    }

    @Test
    public void testDateOfBirthDoesNotMapToDate() throws Exception {
        // A personal date must never become the record's event/timeline date
        Item item = newItem();
        invokeMapStandard(item, "Date of Birth", "1985-03-10");
        assertNull(item.getMetadata().get(ExtraProperties.MESSAGE_DATE));
    }

    @Test
    public void testSenderMapsToMessageFrom() throws Exception {
        Item item = newItem();
        invokeMapStandard(item, "Sender", "Alice");
        assertEquals("Alice", item.getMetadata().get(Message.MESSAGE_FROM));
    }

    @Test
    public void testAmbiguousAccountDoesNotMapToMessageFrom() throws Exception {
        // "Account" usually denotes the device owner, who is the RECEIVER of
        // incoming records — mapping it to MESSAGE_FROM would mislabel senders.
        Item item = newItem();
        invokeMapStandard(item, "Account", "user@example.com");
        assertNull(item.getMetadata().get(Message.MESSAGE_FROM));
    }

    @Test
    public void testDateHeaderVariantsMap() throws Exception {
        // Real ALeapp v2026.1.0 plugins use many datetime column names; the
        // suffix family must capture the common ones ("Start Time", "Message
        // Timestamp", "Created Timestamp", "Last Updated Timestamp", ...)
        for (String header : Arrays.asList("Start Time", "Message Timestamp",
                "Created Timestamp", "Last Updated Timestamp", "Visit Date",
                "Date Added", "End Time")) {
            Item item = newItem();
            invokeMapStandard(item, header, "2024-01-15 10:00:00");
            assertEquals("header must map to MESSAGE_DATE: " + header,
                    "2024-01-15 10:00:00", item.getMetadata().get(ExtraProperties.MESSAGE_DATE));
        }
    }

    @Test
    public void testNonEventDateHeadersDoNotMap() throws Exception {
        // Not event times: durations, zones and personal dates must not become
        // the record's timeline date
        for (String header : Arrays.asList("Timezone", "Birthdate", "Duration")) {
            Item item = newItem();
            invokeMapStandard(item, header, "some value");
            assertNull("header must NOT map to MESSAGE_DATE: " + header,
                    item.getMetadata().get(ExtraProperties.MESSAGE_DATE));
        }
    }

    @Test
    public void testTimeOnlyHeaderDoesNotMapToDate() throws Exception {
        // A bare "Time" column may hold a time without a date — not usable as MESSAGE_DATE
        Item item = newItem();
        invokeMapStandard(item, "Time", "10:00:00");
        assertNull(item.getMetadata().get(ExtraProperties.MESSAGE_DATE));
    }

    @Test
    public void testSentDateMapsToMessageDate() throws Exception {
        Item item = newItem();
        invokeMapStandard(item, "Sent Date", "2024-01-15 10:00:00");
        assertEquals("2024-01-15 10:00:00", item.getMetadata().get(ExtraProperties.MESSAGE_DATE));
    }

    @Test
    public void testBlankValueDoesNotBlockLaterColumn() throws Exception {
        // A blank cell must not claim the standard field, or the real value is lost
        Item item = newItem();
        invokeMapStandard(item, "Timestamp", "");
        invokeMapStandard(item, "Date", "2024-01-15 10:00:00");
        assertEquals("2024-01-15 10:00:00", item.getMetadata().get(ExtraProperties.MESSAGE_DATE));
    }

    @Test
    public void testFromMapsToMessageFrom() throws Exception {
        Item item = newItem();
        invokeMapStandard(item, "From", "Bob");
        assertEquals("Bob", item.getMetadata().get(Message.MESSAGE_FROM));
    }

    @Test
    public void testRecipientMapsToMessageTo() throws Exception {
        Item item = newItem();
        invokeMapStandard(item, "Recipient", "Carol");
        assertEquals("Carol", item.getMetadata().get(Message.MESSAGE_TO));
    }

    @Test
    public void testToMapsToMessageTo() throws Exception {
        Item item = newItem();
        invokeMapStandard(item, "To", "Dave");
        assertEquals("Dave", item.getMetadata().get(Message.MESSAGE_TO));
    }

    @Test
    public void testMessageMapsToBody() throws Exception {
        Item item = newItem();
        invokeMapStandard(item, "Message", "Hello world");
        assertEquals("Hello world", item.getMetadata().get(ExtraProperties.MESSAGE_BODY));
    }

    @Test
    public void testContentMapsToBody() throws Exception {
        Item item = newItem();
        invokeMapStandard(item, "Content", "Some text");
        assertEquals("Some text", item.getMetadata().get(ExtraProperties.MESSAGE_BODY));
    }

    @Test
    public void testBodyMapsToMessageBody() throws Exception {
        Item item = newItem();
        invokeMapStandard(item, "Body", "Body text");
        assertEquals("Body text", item.getMetadata().get(ExtraProperties.MESSAGE_BODY));
    }

    @Test
    public void testCaseInsensitiveHeader() throws Exception {
        Item item = newItem();
        invokeMapStandard(item, "TIMESTAMP", "2024-01-15T10:00:00Z");
        assertEquals("2024-01-15T10:00:00Z", item.getMetadata().get(ExtraProperties.MESSAGE_DATE));
    }

    @Test
    public void testFirstValueWinsForDuplicateHeaders() throws Exception {
        Item item = newItem();
        invokeMapStandard(item, "Timestamp", "first");
        invokeMapStandard(item, "Timestamp", "second");
        assertEquals("first", item.getMetadata().get(ExtraProperties.MESSAGE_DATE));
    }

    @Test
    public void testUnknownHeaderDoesNotMapStandard() throws Exception {
        Item item = newItem();
        invokeMapStandard(item, "CustomField", "value");
        assertNull(item.getMetadata().get(ExtraProperties.MESSAGE_DATE));
        assertNull(item.getMetadata().get(Message.MESSAGE_FROM));
        assertNull(item.getMetadata().get(Message.MESSAGE_TO));
        assertNull(item.getMetadata().get(ExtraProperties.MESSAGE_BODY));
    }

    // ── Geolocation mapping ────────────────────────────────────────────────────

    private void invokeSetLocation(Item item, String lat, String lon) throws Exception {
        Method m = IlapfuncsTsvInterceptor.class
                .getDeclaredMethod("setLocationIfValid", Item.class, String.class, String.class);
        m.setAccessible(true);
        m.invoke(null, item, lat, lon);
    }

    @Test
    public void testValidCoordinatesMapToLocations() throws Exception {
        Item item = newItem();
        invokeSetLocation(item, "-15.7942", "-47.8822");
        assertEquals("-15.7942;-47.8822", item.getMetadata().get(ExtraProperties.LOCATIONS));
    }

    @Test
    public void testOriginalStringIsStoredNotReparsedDouble() throws Exception {
        // The original text must be preserved: Double.toString would turn
        // "15.50" into "15.5" and small values into scientific notation
        Item item = newItem();
        invokeSetLocation(item, "15.50", "-0.0005");
        assertEquals("15.50;-0.0005", item.getMetadata().get(ExtraProperties.LOCATIONS));
    }

    @Test
    public void testCommaDecimalCoordinatesAreNormalized() throws Exception {
        // Locale-formatted devices emit comma decimals
        Item item = newItem();
        invokeSetLocation(item, "-15,7942", "-47,8822");
        assertEquals("-15.7942;-47.8822", item.getMetadata().get(ExtraProperties.LOCATIONS));
    }

    @Test
    public void testCoordinatesAreTrimmed() throws Exception {
        Item item = newItem();
        invokeSetLocation(item, " -15.7942 ", " -47.8822 ");
        assertEquals("-15.7942;-47.8822", item.getMetadata().get(ExtraProperties.LOCATIONS));
    }

    @Test
    public void testNaNCoordinatesDoNotMap() throws Exception {
        Item item = newItem();
        invokeSetLocation(item, "NaN", "NaN");
        assertNull(item.getMetadata().get(ExtraProperties.LOCATIONS));
    }

    @Test
    public void testZeroZeroCoordinatesDoNotMap() throws Exception {
        // 0;0 is the classic "no fix" placeholder — must not plot at Null Island
        Item item = newItem();
        invokeSetLocation(item, "0", "0");
        assertNull(item.getMetadata().get(ExtraProperties.LOCATIONS));
    }

    @Test
    public void testOutOfRangeCoordinatesDoNotMap() throws Exception {
        Item item = newItem();
        invokeSetLocation(item, "91.0", "10.0");
        assertNull(item.getMetadata().get(ExtraProperties.LOCATIONS));
    }

    @Test
    public void testNonNumericCoordinatesDoNotMap() throws Exception {
        Item item = newItem();
        invokeSetLocation(item, "n/a", "n/a");
        assertNull(item.getMetadata().get(ExtraProperties.LOCATIONS));
    }

    @Test
    public void testMissingCoordinateDoesNotMap() throws Exception {
        Item item = newItem();
        invokeSetLocation(item, "-15.7942", null);
        assertNull(item.getMetadata().get(ExtraProperties.LOCATIONS));
    }
}
