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
import junit.framework.TestCase;

public class FLVParserWrapperTest extends TestCase{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 

    @Test
    public void testFLVParserWrapperParsing() throws IOException, SAXException, TikaException{

        FLVParserWrapper parser = new FLVParserWrapper();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_videoFlv.flv");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);

    }
    
    
    @Test
    public void testFLVParserWrapperEmbedded() throws IOException, SAXException, TikaException{

        FLVParserWrapper parser = new FLVParserWrapper();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_videoFlv.flv");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        String mts = metadata.toString();
        assertEquals("video:videocodecid=2.0", mts.substring(0, 22));
        assertEquals("video:width=256.0", mts.substring(23, 40));
        assertEquals("video:minor_version=512", mts.substring(41, 64));
        assertEquals("video:height=144.0", mts.substring(65, 83));
        assertEquals("video:duration=36.931", mts.substring(84, 105));
        assertEquals("video:major_brand=isom", mts.substring(106, 128));
        assertEquals("video:stereo=true", mts.substring(129, 146));
        assertEquals("video:Content-Type=video/x-flv", mts.substring(147, 177));
        assertEquals("video:encoder=Lavf58.45.100", mts.substring(178, 205));
        assertEquals("video:videodatarate=195.3125", mts.substring(206, 234));
        assertEquals("video:audiodatarate=0.0", mts.substring(235, 258));
        assertEquals("video:hasVideo=true", mts.substring(259, 278));
        assertEquals("video:hasAudio=true", mts.substring(279, 298));
        assertEquals("video:framerate=29.97002997002997", mts.substring(299, 332));
        assertEquals("video:audiocodecid=2.0", mts.substring(333, 355));
        assertEquals("video:filesize=2317483.0", mts.substring(356, 380));
        assertEquals("video:audiosamplerate=44100.0", mts.substring(381, 410));
        assertEquals("video:compatible_brands=isomiso2avc1iso6mp41", mts.substring(411, 455));
        assertEquals("video:audiosamplesize=16.0", mts.substring(456, 482));
           
    }

}
