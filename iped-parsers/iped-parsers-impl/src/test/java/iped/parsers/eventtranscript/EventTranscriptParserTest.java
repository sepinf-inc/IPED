package iped.parsers.eventtranscript;

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

        try (InputStream stream = getStream("test-files/eventtranscript/test_eventTranscript_big_big.db")) {
            //parser.parse(stream, handler, metadata, context);
        }
    }

    @Test
    public void testEventranscriptHistoryParser() throws IOException, SAXException, TikaException {

        // assertTrue(tracker.pageTitles.size() > 0);
        // assertTrue(tracker.urls.size() > 0);
        // assertTrue(tracker.visitDates.size() > 0);
        // assertTrue(tracker.histEventNames.size() > 0);
    }

    @Test
    public void testEventTranscriptInventoryAppsParser() throws IOException, SAXException, TikaException {

        // assertTrue(tracker.appNames.size() > 0);
        // assertTrue(tracker.installDates.size() > 0);
        // assertTrue(tracker.rootDirPaths.size() > 0);
        // assertTrue(tracker.versions.size() > 0);
        // assertTrue(tracker.invEventNames.size() > 0);
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
        public List<String> intTimestamps = new ArrayList<String>();
        public List<String> inFocusDuration = new ArrayList<String>();
        // networking
        public List<String> netEventSources = new ArrayList<String>();
        public List<String> netEventReasons = new ArrayList<String>();


        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return (new AutoDetectParser()).getSupportedTypes(context);
        }

        public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
                throws IOException, SAXException, TikaException {

            if (metadata.get(StandardParser.INDEXER_CONTENT_TYPE).equals(EventTranscriptParser.EVENT_TRANSCRIPT_HIST_REG.toString())) {
                pageTitles.add(metadata.get(TikaCoreProperties.TITLE));
                urls.add(metadata.get(ExtraProperties.URL));
                visitDates.add(metadata.get(ExtraProperties.VISIT_DATE));
                histEventNames.add(metadata.get("eventNames"));
            }
            if (metadata.get(StandardParser.INDEXER_CONTENT_TYPE).equals(EventTranscriptParser.EVENT_TRANSCRIPT_INVENTORY_APP_REG.toString())) {
                invAppNames.add(metadata.get(TikaCoreProperties.TITLE));
                rootDirPaths.add(metadata.get(TikaCoreProperties.SOURCE_PATH));
                installDates.add(metadata.get(ExtraProperties.DOWNLOAD_DATE));
                versions.add(metadata.get("version"));
                invEventNames.add(metadata.get("eventName"));
            }
            if (metadata.get(StandardParser.INDEXER_CONTENT_TYPE).equals(EventTranscriptParser.EVENT_TRANSCRIPT_APP_INTERACT_REG.toString())) {
                intAppNames.add(metadata.get(TikaCoreProperties.TITLE));
                intTimestamps.add(metadata.get(TikaCoreProperties.CREATED));
                inFocusDuration.add(metadata.get("inFocusDurationMS"));
            }
            if (metadata.get(StandardParser.INDEXER_CONTENT_TYPE).equals(EventTranscriptParser.EVENT_TRANSCRIPT_DEVICES_REG.toString())) {
                models.add(metadata.get(TikaCoreProperties.TITLE));
                pnpEventNames.add(metadata.get("eventName"));
                manufacturer.add(metadata.get("manufacturer"));
                pnpInstallDates.add(metadata.get(ExtraProperties.DOWNLOAD_DATE));
            }
            if (metadata.get(StandardParser.INDEXER_CONTENT_TYPE).equals(EventTranscriptParser.EVENT_TRANSCRIPT_NETWORKING_REG.toString())) {
                netEventSources.add(metadata.get("eventSource"));
                netEventReasons.add(metadata.get("eventReasons"));
            }
        }
    }

}
