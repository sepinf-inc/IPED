package gpinf.emule;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.KnownMetParser;
import iped3.util.ExtraProperties;

public class KnownMetParserTest extends AbstractPkgTest{
    
    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
   
    @Test
    public void testKnownMetParsing() throws IOException, SAXException, TikaException{

        KnownMetParser parser = new KnownMetParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/test_known.met");
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        String hts = handler.toString();
        
        assertTrue(hts.contains("Radiohead - High and Dry.mp3"));
        assertTrue(hts.contains("77481ddd95730681"));
        assertTrue(hts.contains("cba7686a8fa7e613"));
        assertTrue(hts.contains("Michael Jackson - Bad.mp3"));
        assertTrue(hts.contains("2b871d30675d0815"));
        assertTrue(hts.contains("d2a4dfb995ad7220"));
        
    }

    @Test
    public void testKnownMetEmbedded() throws IOException, SAXException, TikaException{

        KnownMetParser parser = new KnownMetParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_known.met");
        parser.parse(stream, handler, metadata, knownmetContext);
        String[] sharedhashes;
        String[] p2pregistrycount;
        sharedhashes = metadata.getValues(ExtraProperties.SHARED_HASHES);
        p2pregistrycount = metadata.getValues(ExtraProperties.P2P_REGISTRY_COUNT);
        
        assertEquals("77481ddd95730681cba7686a8fa7e613", sharedhashes[0]);
        assertEquals("2b871d30675d0815d2a4dfb995ad7220", sharedhashes[1]);
        assertEquals("2", p2pregistrycount[0]);
    }
}
