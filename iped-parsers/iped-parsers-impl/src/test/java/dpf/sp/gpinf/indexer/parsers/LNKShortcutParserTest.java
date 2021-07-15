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

public class LNKShortcutParserTest extends TestCase{
    
    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }
        
        @Test
        public void testLNKShortcutParserParsingLink() throws IOException, SAXException, TikaException{

            LNKShortcutParser parser = new LNKShortcutParser();
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            InputStream stream = getStream("test-files/test_lnk.lnk");
            ParseContext context = new ParseContext();
            parser.getSupportedTypes(context);
            parser.parse(stream, handler, metadata, context);
            
            String hts = handler.toString();
            
            String actualLocalPath = "C:\\Users\\guilh\\AppData\\Roaming\\Telegram Desktop\\Telegram.exe";
            assertTrue(hts.contains(actualLocalPath));
       
            String actualPrimaryName = "Telegram.exe";
            assertTrue(hts.contains(actualPrimaryName));
            
            String actualSecundaryName = "Telegram Desktop";
            assertTrue(hts.contains(actualSecundaryName));
 
           }
        
        
        @Test
        public void testLNKShortcutParserParsingLinkTracker() throws IOException, SAXException, TikaException{

            LNKShortcutParser parser = new LNKShortcutParser();
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            InputStream stream = getStream("test-files/test_lnkLinkTracker.lnk");
            ParseContext context = new ParseContext();
            parser.getSupportedTypes(context);
            parser.parse(stream, handler, metadata, context);
            
            String hts = handler.toString();
            
            String actualLocalPath = "C:\\Program Files (x86)\\PokerStars\\PokerStarsUpdate.exe";
            assertTrue(hts.contains(actualLocalPath));
            
            String actualPrimaryName = "PokerStarsUpdate.exe";
            assertTrue(hts.contains(actualPrimaryName));
            
            String actualSecundaryName = "PokerStars";
            assertTrue(hts.contains(actualSecundaryName));

           }


}