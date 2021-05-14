package dpf.mg.udi.gpinf.bittorrent;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import junit.framework.TestCase;

public class BitTorrentResumeDatParserTest extends TestCase{
   
    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
    
    @Test
    public void testBitTorrentResumeParsing() throws IOException, SAXException, TikaException{

        BitTorrentResumeDatParser parser = new BitTorrentResumeDatParser();
        Metadata metadata = new Metadata();
        metadata.add(Metadata.CONTENT_TYPE,
                MediaType.application("x-bittorrent-resume-dat").toString());
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_resume.dat");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        
        String hts = handler.toString();
        
        assertTrue(hts.contains("Sintel.torrent"));
        assertTrue(hts.contains("63831927"));
        assertTrue(hts.contains("Tears of Steel.torrent"));
        assertTrue(hts.contains("29736960"));
        assertTrue(hts.contains("NovaROFull_19112020.exe.torrent"));
        assertTrue(hts.contains("3259111196"));     
        assertTrue(hts.contains("Cosmos Laundromat.torrent"));
        assertTrue(hts.contains("23551574"));     
        assertTrue(hts.contains("Big Buck Bunny.torrent"));
        assertTrue(hts.contains("56228123"));     
   
    }
}
