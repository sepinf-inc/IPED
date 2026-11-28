package iped.parsers.evtx;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.sax.ToTextContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.properties.ExtraProperties;

public class EvtxParserTest extends AbstractPkgTest {

    private static final String TEST_FILE = "test-files/test_evtxLog.evtx";

    // Fixture: Microsoft-Windows-Servicing log from hosts ARES05 and ARES01
    private static final String EXPECTED_PROVIDER = "Microsoft-Windows-Servicing";
    private static final String EXPECTED_GUID     = "BD12F3B8-FC40-4A61-A307-B7A013A069C1";
    private static final int    EXPECTED_FOLDERS  = 1;
    private static final int    EXPECTED_GROUPS   = 16;
    private static final int    EXPECTED_TOTAL    = EXPECTED_FOLDERS + EXPECTED_GROUPS;

    private void parse(int maxEventPerItem) throws IOException, SAXException, TikaException {
        evtxContext = getContext(TEST_FILE);
        EvtxParser parser = new EvtxParser();
        // same groupBy as iped-app/resources/config/conf/ParserConfig.xml
        parser.setGroupBy("Event/System/EventID;Event/System/Computer");
        parser.setMaxEventPerItem(maxEventPerItem);
        ContentHandler handler = new ToTextContentHandler();
        try (InputStream stream = getStream(TEST_FILE)) {
            parser.parse(stream, handler, new Metadata(), evtxContext);
        }
    }

    // ── output structure ─────────────────────────────────────────────────────

    @Test
    public void testTotalDocumentCount() throws IOException, SAXException, TikaException {
        parse(100);
        assertEquals(EXPECTED_TOTAL, evtxTracker.metadataList.size());
    }

    @Test
    public void testAllEmbeddedDocsHaveCorrectContentType() throws IOException, SAXException, TikaException {
        parse(100);
        for (String ct : evtxTracker.contentTypes) {
            assertEquals("application/x-elf-record", ct);
        }
    }

    @Test
    public void testFolderCount() throws IOException, SAXException, TikaException {
        parse(100);
        long folders = evtxTracker.metadataList.stream()
                .filter(m -> "true".equals(m.get(ExtraProperties.EMBEDDED_FOLDER)))
                .count();
        assertEquals(EXPECTED_FOLDERS, folders);
    }

    @Test
    public void testRecordGroupCount() throws IOException, SAXException, TikaException {
        parse(100);
        long groups = evtxTracker.metadataList.stream()
                .filter(m -> !"true".equals(m.get(ExtraProperties.EMBEDDED_FOLDER)))
                .count();
        assertEquals(EXPECTED_GROUPS, groups);
    }

    // ── provider folder ───────────────────────────────────────────────────────

    @Test
    public void testProviderFolderTitle() throws IOException, SAXException, TikaException {
        parse(100);
        String title = evtxTracker.metadataList.stream()
                .filter(m -> "true".equals(m.get(ExtraProperties.EMBEDDED_FOLDER)))
                .map(m -> m.get(TikaCoreProperties.TITLE))
                .findFirst().orElse(null);
        assertEquals(EXPECTED_PROVIDER, title);
    }

    @Test
    public void testProviderFolderGUID() throws IOException, SAXException, TikaException {
        parse(100);
        String guid = evtxTracker.metadataList.stream()
                .filter(m -> "true".equals(m.get(ExtraProperties.EMBEDDED_FOLDER)))
                .map(m -> m.get(EvtxParser.EVTX_METADATA_PREFIX + "ProviderGUID"))
                .findFirst().orElse(null);
        assertEquals(EXPECTED_GUID, guid);
    }

    // ── record groups ─────────────────────────────────────────────────────────

    @Test
    public void testRecordGroupTitlesContainEventIDAndComputer() throws IOException, SAXException, TikaException {
        parse(100);
        evtxTracker.metadataList.stream()
                .filter(m -> !"true".equals(m.get(ExtraProperties.EMBEDDED_FOLDER)))
                .forEach(m -> {
                    String title = m.get(TikaCoreProperties.TITLE);
                    assertNotNull(title);
                    assertTrue(title.contains("Event/System/EventID:"));
                    assertTrue(title.contains("Event/System/Computer:"));
                });
    }

    @Test
    public void testRecordGroupsHaveRecordCount() throws IOException, SAXException, TikaException {
        parse(100);
        evtxTracker.metadataList.stream()
                .filter(m -> !"true".equals(m.get(ExtraProperties.EMBEDDED_FOLDER)))
                .forEach(m -> assertNotNull(m.get(EvtxParser.EVTX_METADATA_PREFIX + "recordCount")));
    }

    @Test
    public void testRecordCountDoesNotExceedMaxEventPerItem() throws IOException, SAXException, TikaException {
        parse(100);
        evtxTracker.metadataList.stream()
                .filter(m -> !"true".equals(m.get(ExtraProperties.EMBEDDED_FOLDER)))
                .forEach(m -> assertTrue(
                        Integer.parseInt(m.get(EvtxParser.EVTX_METADATA_PREFIX + "recordCount")) <= 100));
    }

    @Test
    public void testAllRecordGroupsHaveEventRecordIDs() throws IOException, SAXException, TikaException {
        parse(100);
        evtxTracker.metadataList.stream()
                .filter(m -> !"true".equals(m.get(ExtraProperties.EMBEDDED_FOLDER)))
                .forEach(m -> assertNotNull(m.get(EvtxParser.EVTX_METADATA_PREFIX + "eventRecordID")));
    }

    @Test
    public void testAllDocsShareSameProviderGUID() throws IOException, SAXException, TikaException {
        parse(100);
        evtxTracker.metadataList.forEach(m ->
                assertEquals(EXPECTED_GUID, m.get(EvtxParser.EVTX_METADATA_PREFIX + "ProviderGUID")));
    }

    // ── configuration ─────────────────────────────────────────────────────────

    @Test
    public void testSupportedTypes() {
        EvtxParser parser = new EvtxParser();
        assertEquals(1, parser.getSupportedTypes(null).size());
        assertEquals("application/x-elf-file",
                parser.getSupportedTypes(null).iterator().next().toString());
    }

    @Test
    public void testMaxEventPerItemIsRespected() throws IOException, SAXException, TikaException {
        parse(10);
        evtxTracker.metadataList.stream()
                .filter(m -> !"true".equals(m.get(ExtraProperties.EMBEDDED_FOLDER)))
                .forEach(m -> assertTrue(
                        Integer.parseInt(m.get(EvtxParser.EVTX_METADATA_PREFIX + "recordCount")) <= 10));
    }
}
