package iped.parsers.eventtranscript;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.standard.StandardParser;
import iped.properties.ExtraProperties;

public class EventTranscriptParserTest {

    private static EventTranscriptParser parser;
    private static Metadata metadata;
    private static ContentHandler handler;
    private static ParseContext context;
    private static EmbeddedEventTranscriptParser tracker;

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @BeforeClass
    public static void parse() throws IOException, SAXException, TikaException {
        parser = new EventTranscriptParser();
        metadata = new Metadata();
        handler = new BodyContentHandler(1 << 20);
        tracker = new EmbeddedEventTranscriptParser();
        context = new ParseContext();

        parser.setExtractEntries(true);
        context.set(Parser.class, tracker);

        try (InputStream stream = getStream("test-files/test_EventTranscript.db")) {
            parser.parse(stream, handler, metadata, context);
        }
    }

    @Test
    public void testEventranscriptHistory() throws IOException, SAXException, TikaException {
        assertEquals(105, tracker.pageTitles.size());
        assertEquals(105, tracker.urls.size());
        assertEquals(105, tracker.visitDates.size());
        assertEquals(105, tracker.histEventNames.size());

        assertTrue(tracker.histEventNames.contains("HJ_HistoryAddUrl") || tracker.histEventNames.contains("HJ_HistoryAddUrlEx"));
        assertTrue(tracker.pageTitles.contains("Search · \"eventtranscript.db\" \"query\""));
        assertTrue(tracker.urls.contains("https://github.com/search?q=%22eventtranscript.db%22&type=issues"));
        
        assertEquals("https://slo-tech.com/forum/t743895", tracker.urls.get(12));
        assertEquals("Windows Diagnostics Data Viewer @ Slo-Tech", tracker.pageTitles.get(12));
        assertEquals("2022-08-25T17:15:39Z", tracker.visitDates.get(12));
        assertTrue(tracker.urls.get(97).contains("https://www.google.com/search?q=%22inventoryapplicationadd%22&oq=%22inventoryapplicationadd"));
        assertEquals("\"inventoryapplicationadd\" - Pesquisa Google", tracker.pageTitles.get(97));
        assertEquals("2022-08-24T16:01:08Z", tracker.visitDates.get(97));
    }

    @Test
    public void testEventTranscriptInventoryApps() throws IOException, SAXException, TikaException {
        assertEquals(4, tracker.invAppNames.size());
        assertEquals(4, tracker.installDates.size());
        assertEquals(4, tracker.rootDirPaths.size());
        assertEquals(4, tracker.versions.size());
        assertEquals(4, tracker.invEventNames.size());

        assertTrue(tracker.invEventNames.contains("ApplicationAdd"));

        assertEquals("2022-08-23T00:00:00Z", tracker.installDates.get(0));
        assertEquals("Microsoft Edge WebView2 Runtime", tracker.invAppNames.get(0));
        assertEquals("%ProgramFiles% (x86)\\microsoft\\edgewebview\\application", tracker.rootDirPaths.get(0));
        assertEquals("104.0.1293.63", tracker.versions.get(0));

        assertEquals("2022-08-23T00:00:00Z", tracker.installDates.get(1));
        assertEquals("Microsoft Edge", tracker.invAppNames.get(1));
        assertEquals("%ProgramFiles% (x86)\\microsoft\\edge\\application", tracker.rootDirPaths.get(1));

        assertEquals("%ProgramFiles%\\windowsapps\\microsoft.microsoftedge.stable_104.0.1293.63_neutral__8wekyb3d8bbwe", tracker.rootDirPaths.get(2));
        assertEquals("Microsoft.MicrosoftEdge.Stable", tracker.invAppNames.get(2));

        assertEquals("2022-08-24T15:59:34Z", tracker.installDates.get(3));
        assertEquals("Docker Desktop", tracker.invAppNames.get(3));
        assertEquals("%ProgramFiles%\\docker\\docker", tracker.rootDirPaths.get(3));
        assertEquals("4.11.1", tracker.versions.get(3));
    }

    @Test
    public void testEventTranscriptAppInteract() throws IOException, SAXException, TikaException {
        assertEquals(59, tracker.intAppNames.size());
        assertEquals(59, tracker.intTimestamps.size());
        assertEquals(59, tracker.inFocusDuration.size());

        assertEquals("explorer.exe", tracker.intAppNames.get(0));
        assertEquals("2022-08-25T17:00:52Z", tracker.intTimestamps.get(0));
        assertEquals("5532", tracker.inFocusDuration.get(0));

        assertEquals("teams.exe", tracker.intAppNames.get(15));
        assertEquals("2022-08-25T16:39:52Z", tracker.intTimestamps.get(15));
        assertEquals("55625", tracker.inFocusDuration.get(15));

        assertEquals("Microsoft.DiagnosticDataViewer_4.2007.21653.0_x64__8wekyb3d8bbwe!App", tracker.intAppNames.get(58));
        assertEquals("2022-08-24T15:56:57Z", tracker.intTimestamps.get(58));
        assertEquals("17141", tracker.inFocusDuration.get(58));
    }

    @Test
    public void testEventTranscriptDevicePNPs() throws IOException, SAXException, TikaException {
        assertEquals(4, tracker.models.size());
        assertEquals(4, tracker.pnpEventNames.size());
        assertEquals(4, tracker.pnpInstallDates.size());
        assertEquals(4, tracker.manufacturer.size());

        assertTrue(tracker.pnpEventNames.contains("DevicePnpAdd"));

        assertEquals("Generic software device", tracker.models.get(0));
        assertEquals("2022-05-12T00:00:00Z", tracker.pnpInstallDates.get(0));
        assertEquals("Microsoft", tracker.manufacturer.get(0));

        assertEquals("Generic software device", tracker.models.get(1));
        assertEquals("2022-06-21T00:00:00Z", tracker.pnpInstallDates.get(1));

        assertEquals("Hyper-V Virtual Ethernet Adapter", tracker.models.get(2));
        assertEquals("2022-08-24T00:00:00Z", tracker.pnpInstallDates.get(2));

        assertEquals("Hyper-V Virtual Switch Extension Adapter", tracker.models.get(3));
        assertEquals("2022-08-24T00:00:00Z", tracker.pnpInstallDates.get(3));
    }

    @Test
    public void testEventTranscriptCensus() throws IOException, SAXException, TikaException {
        assertEquals(1, tracker.censusEventNames.size());
        assertEquals(1, tracker.censusTimesTamp.size());
        assertEquals(1, tracker.censusJSONPayload.size());

        assertEquals("App", tracker.censusEventNames.get(0));
        assertEquals("2022-08-25T16:20:19Z", tracker.censusTimesTamp.get(0));
        assertTrue(tracker.censusJSONPayload.get(0).contains("\"devModel\":\"HP Z8 G4 Workstation\""));
    }


    @SuppressWarnings("serial")
    private static class EmbeddedEventTranscriptParser extends AbstractParser {

        // history
        public List<String> pageTitles = new ArrayList<String>();
        public List<String> urls = new ArrayList<String>();
        public List<String> visitDates = new ArrayList<String>();
        public List<String> histEventNames = new ArrayList<String>();
        // inventory applications
        public List<String> invAppNames = new ArrayList<String>();
        public List<String> installDates = new ArrayList<String>();
        public List<String> rootDirPaths = new ArrayList<String>();
        public List<String> versions = new ArrayList<String>();
        public List<String> invEventNames = new ArrayList<String>();
        // device pnp
        public List<String> models = new ArrayList<String>();
        public List<String> pnpEventNames = new ArrayList<String>();
        public List<String> pnpInstallDates = new ArrayList<String>();
        public List<String> manufacturer = new ArrayList<String>();
        // app interactions
        public List<String> intAppNames = new ArrayList<String>();
        public List<String> intEventNames = new ArrayList<String>();
        public List<String> intTimestamps = new ArrayList<String>();
        public List<String> inFocusDuration = new ArrayList<String>();
        // census
        public List<String> censusEventNames = new ArrayList<String>();
        public List<String> censusTimesTamp = new ArrayList<String>();
        public List<String> censusJSONPayload = new ArrayList<String>();

        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return (new AutoDetectParser()).getSupportedTypes(context);
        }

        public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

            if (metadata.get(StandardParser.INDEXER_CONTENT_TYPE).equals(EventTranscriptParser.EVENT_TRANSCRIPT_HIST_REG.toString())) {
                pageTitles.add(metadata.get(TikaCoreProperties.TITLE));
                urls.add(metadata.get(ExtraProperties.URL));
                visitDates.add(metadata.get(ExtraProperties.VISIT_DATE));
                histEventNames.add(metadata.get("eventName"));
            }
            if (metadata.get(StandardParser.INDEXER_CONTENT_TYPE).equals(EventTranscriptParser.EVENT_TRANSCRIPT_INVENTORY_APP_REG.toString())) {
                invAppNames.add(metadata.get(TikaCoreProperties.TITLE));
                rootDirPaths.add(metadata.get(TikaCoreProperties.SOURCE_PATH));
                installDates.add(metadata.get("installDate"));
                versions.add(metadata.get("version"));
                invEventNames.add(metadata.get("eventName"));
            }
            if (metadata.get(StandardParser.INDEXER_CONTENT_TYPE).equals(EventTranscriptParser.EVENT_TRANSCRIPT_APP_INTERACT_REG.toString())) {
                intAppNames.add(metadata.get(TikaCoreProperties.TITLE));
                intEventNames.add(metadata.get("eventName"));
                intTimestamps.add(metadata.get(intEventNames.get(intEventNames.size()-1) + "Event"));
                inFocusDuration.add(metadata.get("inFocusDurationMS"));
            }
            if (metadata.get(StandardParser.INDEXER_CONTENT_TYPE).equals(EventTranscriptParser.EVENT_TRANSCRIPT_DEVICES_REG.toString())) {
                models.add(metadata.get(TikaCoreProperties.TITLE));
                pnpEventNames.add(metadata.get("eventName"));
                manufacturer.add(metadata.get("manufacturer"));
                pnpInstallDates.add(metadata.get("installDate"));
            }
            if (metadata.get(StandardParser.INDEXER_CONTENT_TYPE).equals(EventTranscriptParser.EVENT_TRANSCRIPT_CENSUS_REG.toString())) {
                censusEventNames.add(metadata.get("eventName"));
                censusTimesTamp.add(metadata.get(censusEventNames.get(censusEventNames.size()-1) + "Event"));
                censusJSONPayload.add(metadata.get("originalPayload"));
            }
        }
    }

}
