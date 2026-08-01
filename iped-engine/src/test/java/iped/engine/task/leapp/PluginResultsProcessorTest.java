package iped.engine.task.leapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import iped.engine.data.Item;
import iped.engine.tika.SyncMetadata;
import iped.properties.ExtraProperties;

public class PluginResultsProcessorTest {

    private Item newItem() {
        Item item = new Item();
        item.setMetadata(new SyncMetadata());
        return item;
    }

    private static final Class<?> HEADER;
    private static final Class<?> STANDARD_FIELD;
    static {
        try {
            HEADER = Class.forName("iped.engine.task.leapp.PluginResultsProcessor$Header");
            STANDARD_FIELD = Class.forName("iped.engine.task.leapp.PluginResultsProcessor$StandardField");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private Object newHeader(String name, String type) throws Exception {
        Constructor<?> c = HEADER.getDeclaredConstructor(String.class, String.class);
        c.setAccessible(true);
        return c.newInstance(name, type);
    }

    private Object invokeClassify(String name, String type) throws Exception {
        Method classify = PluginResultsProcessor.class.getDeclaredMethod("classifyHeader", HEADER);
        classify.setAccessible(true);
        return classify.invoke(null, newHeader(name, type));
    }

    private void invokeMapStandard(Item item, String name, String type, String value) throws Exception {
        Object field = invokeClassify(name, type);
        Method apply = PluginResultsProcessor.class
                .getDeclaredMethod("applyStandardField", Item.class, STANDARD_FIELD, String.class);
        apply.setAccessible(true);
        apply.invoke(null, item, field, value);
    }

    private Object invokeCellValue(List<Object> data, int i) throws Exception {
        Method m = PluginResultsProcessor.class.getDeclaredMethod("cellValue", List.class, int.class);
        m.setAccessible(true);
        return m.invoke(null, data, i);
    }

    // ── Typed headers (lava (name, type) tuples) ───────────────────────────────

    @Test
    public void testDatetimeTypeMapsToDate() throws Exception {
        Item item = newItem();
        invokeMapStandard(item, "Whatever Name", "datetime", "2024-01-15 10:00:00");
        assertEquals("2024-01-15 10:00:00", item.getMetadata().get(ExtraProperties.MESSAGE_DATE));
    }

    @Test
    public void testDateTypeMapsToDate() throws Exception {
        Item item = newItem();
        invokeMapStandard(item, "Whatever Name", "date", "2024-01-15");
        assertEquals("2024-01-15", item.getMetadata().get(ExtraProperties.MESSAGE_DATE));
    }

    @Test
    public void testMediaTypeClassifiesAsMedia() throws Exception {
        assertEquals("MEDIA", invokeClassify("Image", "media").toString());
    }

    @Test
    public void testTypedNonDateHeaderDoesNotUseDateNameHeuristics() throws Exception {
        // typed headers must not fall back to the name heuristics for dates:
        // e.g. a TEXT column that happens to end with " time"
        Item item = newItem();
        invokeMapStandard(item, "Prayer Time", "str", "value");
        assertNull(item.getMetadata().get(ExtraProperties.MESSAGE_DATE));
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

    // ── Untyped headers: name-based fallback ───────────────────────────────────

    @Test
    public void testTimestampMapsToMessageDate() throws Exception {
        Item item = newItem();
        invokeMapStandard(item, "Timestamp", null, "2024-01-15T10:00:00Z");
        assertEquals("2024-01-15T10:00:00Z", item.getMetadata().get(ExtraProperties.MESSAGE_DATE));
    }

    @Test
    public void testDateOfBirthDoesNotMapToDate() throws Exception {
        // A personal date must never become the record's event/timeline date
        Item item = newItem();
        invokeMapStandard(item, "Date of Birth", null, "1985-03-10");
        assertNull(item.getMetadata().get(ExtraProperties.MESSAGE_DATE));
    }

    @Test
    public void testSenderMapsToCommunicationFrom() throws Exception {
        Item item = newItem();
        invokeMapStandard(item, "Sender", null, "Alice");
        assertEquals("Alice", item.getMetadata().get(ExtraProperties.COMMUNICATION_FROM));
    }

    @Test
    public void testAmbiguousAccountDoesNotMapToCommunicationFrom() throws Exception {
        // "Account" usually denotes the device owner, who is the RECEIVER of
        // incoming records — mapping it to COMMUNICATION_FROM would mislabel senders.
        Item item = newItem();
        invokeMapStandard(item, "Account", null, "user@example.com");
        assertNull(item.getMetadata().get(ExtraProperties.COMMUNICATION_FROM));
    }

    @Test
    public void testDateHeaderVariantsMap() throws Exception {
        for (String header : Arrays.asList("Start Time", "Message Timestamp",
                "Created Timestamp", "Last Updated Timestamp", "Visit Date",
                "Date Added", "End Time",
                // sibling of the untyped "Date Received"/"Date Sent" columns of FairEmail
                "Date Stored",
                // no separator, so the " time" suffix rule does not apply (wifiConfigstore2)
                "CreationTime")) {
            Item item = newItem();
            invokeMapStandard(item, header, null, "2024-01-15 10:00:00");
            assertEquals("header must map to MESSAGE_DATE: " + header,
                    "2024-01-15 10:00:00", item.getMetadata().get(ExtraProperties.MESSAGE_DATE));
        }
    }

    @Test
    public void testNonEventDateHeadersDoNotMap() throws Exception {
        for (String header :  Arrays.asList("Timezone", "Birthdate", "Duration",
                "Birthday", "Birthdate (MM-DD)", "Date of Sleep", "Date of Birth")) {
            Item item = newItem();
            invokeMapStandard(item, header, null, "some value");
            assertNull("header must NOT map to MESSAGE_DATE: " + header,
                    item.getMetadata().get(ExtraProperties.MESSAGE_DATE));
        }
    }

    @Test
    public void testRecipientMapsToCommunicationTo() throws Exception {
        Item item = newItem();
        invokeMapStandard(item, "Recipient", null, "Carol");
        assertEquals("Carol", item.getMetadata().get(ExtraProperties.COMMUNICATION_TO));
    }

    @Test
    public void testMessageMapsToBody() throws Exception {
        Item item = newItem();
        invokeMapStandard(item, "Message", null, "Hello world");
        assertEquals("Hello world", item.getMetadata().get(ExtraProperties.MESSAGE_BODY));
    }

    @Test
    public void testBlankValueDoesNotBlockLaterColumn() throws Exception {
        Item item = newItem();
        invokeMapStandard(item, "Timestamp", null, "");
        invokeMapStandard(item, "Date", null, "2024-01-15 10:00:00");
        assertEquals("2024-01-15 10:00:00", item.getMetadata().get(ExtraProperties.MESSAGE_DATE));
    }

    @Test
    public void testFirstValueWinsForDuplicateHeaders() throws Exception {
        Item item = newItem();
        invokeMapStandard(item, "Timestamp", null, "first");
        invokeMapStandard(item, "Timestamp", null, "second");
        assertEquals("first", item.getMetadata().get(ExtraProperties.MESSAGE_DATE));
    }

    @Test
    public void testCaseInsensitiveHeader() throws Exception {
        Item item = newItem();
        invokeMapStandard(item, "TIMESTAMP", null, "2024-01-15T10:00:00Z");
        assertEquals("2024-01-15T10:00:00Z", item.getMetadata().get(ExtraProperties.MESSAGE_DATE));
    }

    @Test
    public void testUnknownHeaderDoesNotMapStandard() throws Exception {
        Item item = newItem();
        invokeMapStandard(item, "CustomField", null, "value");
        assertNull(item.getMetadata().get(ExtraProperties.MESSAGE_DATE));
        assertNull(item.getMetadata().get(ExtraProperties.COMMUNICATION_FROM));
        assertNull(item.getMetadata().get(ExtraProperties.COMMUNICATION_TO));
        assertNull(item.getMetadata().get(ExtraProperties.MESSAGE_BODY));
    }

    // ── Untyped file-path columns ──────────────────────────────────────────────

    @Test
    public void testFilePathColumnsClassifyAsFilePath() throws Exception {
        // representative "* Path" / *filepath / file_path columns across ALEAPP plugins
        for (String header : Arrays.asList("File Path", "Local Path To Media", "Download Path", "Full Path",
                "Original Path", "Source File Path", "Screenshot Path", "Code Path", "Save Path", "Item Path",
                "Target File Path", "Path", "file_path", "Original Filepath", "vault_filepath")) {
            assertEquals("header must classify as FILE_PATH: " + header, "FILE_PATH",
                    invokeClassify(header, null).toString());
        }
    }

    private boolean invokeIsNonFilePathColumn(String moduleName, String headerName) throws Exception {
        Method m = PluginResultsProcessor.class
                .getDeclaredMethod("isNonFilePathColumn", String.class, String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, moduleName, headerName);
    }

    @Test
    public void testNonFilePathColumnsAreExcludedPerPlugin() throws Exception {
        // known per-plugin exceptions: the name looks like a file path but is not one in that plugin
        assertTrue(invokeIsNonFilePathColumn("chromeCookies", "Path"));
        assertTrue(invokeIsNonFilePathColumn("firefoxCookies", "Path"));
        assertTrue(invokeIsNonFilePathColumn("FairEmail", "Return Path"));
        assertTrue(invokeIsNonFilePathColumn("DuckDuckGo", "Folder Path"));
    }

    @Test
    public void testSameColumnIsFilePathInOtherPlugins() throws Exception {
        // "Path" is a real file path in these plugins, so it must NOT be excluded there
        assertFalse(invokeIsNonFilePathColumn("emulatedSmeta", "Path"));
        assertFalse(invokeIsNonFilePathColumn("Zapya", "path"));
        // and on its own the name still classifies as FILE_PATH — the plugin decides the exception
        assertEquals("FILE_PATH", invokeClassify("Path", null).toString());
    }

    @Test
    public void testTypedColumnIsNotClassifiedAsFilePath() throws Exception {
        // a column carrying an explicit type must not fall back to the file-path name heuristic
        assertEquals("NONE", invokeClassify("File Path", "str").toString());
    }

    @Test
    public void testMediaTypedPathColumnStaysMedia() throws Exception {
        // ('Attachment File', 'media') and similar must remain MEDIA, not FILE_PATH
        assertEquals("MEDIA", invokeClassify("Attachment File Path", "media").toString());
    }

    @Test
    public void testNonPathColumnsAreNotFilePath() throws Exception {
        for (String header : Arrays.asList("Title", "URL", "Account", "File Size", "File Name")) {
            assertEquals("header must NOT classify as FILE_PATH: " + header, "NONE",
                    invokeClassify(header, null).toString());
        }
    }

    // ── Geolocation mapping ────────────────────────────────────────────────────

    private void invokeSetLocation(Item item, String lat, String lon) throws Exception {
        Method m = PluginResultsProcessor.class
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
        Item item = newItem();
        invokeSetLocation(item, "15.50", "-0.0005");
        assertEquals("15.50;-0.0005", item.getMetadata().get(ExtraProperties.LOCATIONS));
    }

    @Test
    public void testCommaDecimalCoordinatesAreNormalized() throws Exception {
        Item item = newItem();
        invokeSetLocation(item, "-15,7942", "-47,8822");
        assertEquals("-15.7942;-47.8822", item.getMetadata().get(ExtraProperties.LOCATIONS));
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
