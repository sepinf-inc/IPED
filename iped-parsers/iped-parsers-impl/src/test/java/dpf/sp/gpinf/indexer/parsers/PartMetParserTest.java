package dpf.sp.gpinf.indexer.parsers;

import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.util.BaseItemSearchContext;

public class PartMetParserTest extends BaseItemSearchContext {
    
    @Test
    public void testPartMetParsing() throws IOException, SAXException, TikaException {

        String file = "test-files/test_partMet.part.met";
        ParseContext context = getContext(file);
        PartMetParser parser = new PartMetParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream(file)) {
            parser.parse(stream, handler, metadata, context);
            String hts = handler.toString();

            assertTrue(hts.contains("10/05/2022"));    // latest date
            assertTrue(hts.contains("The Strokes - Last Nite.mp3"));    // file name
            assertTrue(hts.contains("baa73b1bb93704b0225e6fc2e4b8e16d")); // hash
            assertTrue(hts.contains("001.part"));      // tempfile
        }
    }

    @Test
    public void testInvalidPartMetVersion() throws IOException {
        String file = "test-files/test_partMetInvalid.part.met";
        ParseContext context = getContext(file);
        PartMetParser parser = new PartMetParser();
        ContentHandler handler = new BodyContentHandler();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream(file)) {
            Exception exception = assertThrows(TikaException.class, () -> {
                parser.parse(stream, handler, new Metadata(), context);
            });
            String expectedExceptionMessage = "Detected part.met file format version not supported";
            assertTrue(exception.getMessage().contains(expectedExceptionMessage));
        }
    }
}
