package iped.parsers.browsers;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToTextContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.browsers.IndexDatParser;

public class IndexDatParserTest {

    @Test
    public void testIndexDatParser() throws IOException, SAXException, TikaException {

        IndexDatParser parser = new IndexDatParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new ToTextContentHandler();
        ParseContext context = new ParseContext();
        assumeFalse(parser.getSupportedTypes(context).isEmpty());

        try (InputStream stream = this.getClass().getResourceAsStream("/test-files/test_index.dat")) {
            parser.parse(stream, handler, metadata, context);
            String hts = handler.toString();
            assertTrue(hts.contains("Record type"));
            assertTrue(hts.contains("URL"));
            assertTrue(hts.contains("Offset range"));
            assertTrue(hts.contains("20480 - 20736 (256)"));
            assertTrue(hts.contains("Location"));
            assertTrue(hts.contains("Cookie:guileb@google.com.br/"));
            assertTrue(hts.contains("Filename"));
            assertTrue(hts.contains("guileb@google.com[2].txt"));
            assertTrue(hts.contains("Cookie:guileb@incredimail.com/"));
            assertTrue(hts.contains("Cookie:guileb@google.com.br/search"));
            assertTrue(hts.contains("Cookie:guileb@incredibarvuz1.com/"));
            assertTrue(hts.contains("Cookie:guileb@google.com.br/complete/search"));
            assertTrue(hts.contains("Cookie:guileb@www.incredibarvuz1.com/"));
            assertTrue(hts.contains("Export completed."));

        }

    }
}
