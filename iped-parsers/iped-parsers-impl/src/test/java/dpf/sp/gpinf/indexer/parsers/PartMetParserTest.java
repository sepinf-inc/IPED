package dpf.sp.gpinf.indexer.parsers;

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

        String file = "test-files/test_emule.part.met";
        ParseContext context = getContext(file);
        PartMetParser parser = new PartMetParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream(file)) {
            parser.parse(stream, handler, metadata, context);
            String hts = handler.toString();

            assertTrue(hts.contains("arq_0_000_010kb_000"));    // file name
            assertTrue(hts.contains("71A3550F7C8621475DA1CB5FC333CAC0".toLowerCase())); // file id (hash edonkey)

            //

        }

    }
}
