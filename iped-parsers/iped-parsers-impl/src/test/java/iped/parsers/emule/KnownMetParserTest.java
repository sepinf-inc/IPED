package iped.parsers.emule;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.util.BaseItemSearchContext;
import iped.properties.ExtraProperties;

public class KnownMetParserTest extends BaseItemSearchContext {

    @Test
    public void testKnownMetParsing() throws IOException, SAXException, TikaException {

        String file = "test-files/test_known.met";
        ParseContext context = getContext(file);
        KnownMetParser parser = new KnownMetParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream(file)) {
            parser.parse(stream, handler, metadata, context);
            String hts = handler.toString();

            assertTrue(hts.contains("Radiohead - High and Dry.mp3"));
            assertTrue(hts.contains("77481ddd95730681"));
            assertTrue(hts.contains("cba7686a8fa7e613"));
            assertTrue(hts.contains("Michael Jackson - Bad.mp3"));
            assertTrue(hts.contains("2b871d30675d0815"));
            assertTrue(hts.contains("d2a4dfb995ad7220"));

            String[] sharedhashes;
            String[] p2pregistrycount;
            sharedhashes = metadata.getValues(ExtraProperties.SHARED_HASHES);
            p2pregistrycount = metadata.getValues(ExtraProperties.P2P_REGISTRY_COUNT);

            assertEquals("77481ddd95730681cba7686a8fa7e613", sharedhashes[0]);
            assertEquals("2b871d30675d0815d2a4dfb995ad7220", sharedhashes[1]);
            assertEquals("2", p2pregistrycount[0]);
        }

    }

}
