package iped.parsers.eventtranscript;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;


public class EventTranscriptParserTest {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testEventranscriptParser() throws IOException, SAXException, TikaException {

        EventTranscriptParser parser = new EventTranscriptParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler(1 << 20);
        ParseContext context = new ParseContext();
        // parser.setExtractEntries(true);
        // parser.getSupportedTypes(context);
        // try (InputStream stream = getStream("test-files/test_EventTranscript.db")) {
        //     parser.parse(stream, handler, metadata, context);
        //     String hts = handler.toString();

        // }
    }

}
