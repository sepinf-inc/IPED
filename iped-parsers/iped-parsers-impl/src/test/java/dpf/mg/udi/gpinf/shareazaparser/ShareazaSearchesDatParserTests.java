package dpf.mg.udi.gpinf.shareazaparser;

import java.io.IOException;
import java.io.InputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToTextContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import junit.framework.TestCase;

public class ShareazaSearchesDatParserTests extends TestCase{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
    
    @Test
    public void testShareazaSearchesDatParser() throws IOException, SAXException, TikaException{

        ShareazaSearchesDatParser parser = new ShareazaSearchesDatParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new ToTextContentHandler();
        InputStream stream = getStream("test-files/test_shareazaSearches.dat");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        
        String hts = handler.toString();
        String mts = metadata.toString();
        
        assertTrue(hts.contains("http://www.limewire.com/schemas/audio.xsd"));
        assertTrue(hts.contains("Musique Classique Bethoven - Sonate Au Clair De Lune.mp3"));
        assertTrue(hts.contains("90401ec0241f9000edaf53da4ef1b542"));
        assertTrue(hts.contains("Symphony No. 5 In C Minor Opus 67. Bethoven - Paul Mauriat.mp3"));
        assertTrue(hts.contains("ed2kftp://7191240@91.208.184.143:4232/4d29a8f82cfca1f5f987de6f16714a61/4753906/"));
        
        assertTrue(mts.contains("Content-Type=application/x-shareaza-searches-dat"));
        
    }

}
