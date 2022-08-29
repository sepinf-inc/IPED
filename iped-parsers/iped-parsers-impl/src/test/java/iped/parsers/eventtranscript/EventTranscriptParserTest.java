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
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.properties.ExtraProperties;


public class EventTranscriptParserTest {
    public List<String> pageTitles = new ArrayList<String>();
    public List<String> urls = new ArrayList<String>();
    public List<String> visitDates = new ArrayList<String>();

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testEventranscriptHistoryParser() throws IOException, SAXException, TikaException {

        EventTranscriptParser parser = new EventTranscriptParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler(1 << 20);
        parser.setExtractEntries(true);

        ParseContext historyContext = new ParseContext();
        historyContext.set(Parser.class, new EmbeddedEventTranscriptHistoryParser());

        // try (InputStream stream = getStream("test-files/eventtranscript/test_eventTranscript.db")) {
        //     parser.parse(stream, handler, metadata, new ParseContext());

        //     // assertTrue(pageTitles.size() > 0);
        //     // assertTrue(urls.size() > 0);
        //     // assertTrue(visitDates.size() > 0);
        // }
    }

    @SuppressWarnings("serial")
    public class EmbeddedEventTranscriptHistoryParser extends AbstractParser {

        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return (new AutoDetectParser()).getSupportedTypes(context);
        }

        public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
                throws IOException, SAXException, TikaException {

            if (metadata.get(TikaCoreProperties.TITLE) != null)
                pageTitles.add(metadata.get(TikaCoreProperties.TITLE));

            if (metadata.get(ExtraProperties.URL) != null)
                urls.add(metadata.get(ExtraProperties.URL));

            if (metadata.get(ExtraProperties.VISIT_DATE) != null)
                visitDates.add(metadata.get(ExtraProperties.VISIT_DATE));

        }
    }

}
