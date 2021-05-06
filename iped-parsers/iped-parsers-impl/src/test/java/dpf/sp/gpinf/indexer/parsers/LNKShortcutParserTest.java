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
        
    //it seems the builder in github uses english encoding when building. Not only for names but for date format as well...
    //ALSO whenever the encoding changes, the location of some chars changes... changing assert method again! this time should work.
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
            
//            String creationDate = "Data de Criação";
//            assertEquals(creationDate, hts.substring(22,37));
            
//            String creationDateNumber = "05/09/2020 02:04:27";
//            assertEquals(creationDateNumber, hts.substring(38,57));
            
//            String localPath = "Caminho Local";
//            assertEquals(localPath, hts.substring(609,622));
            
            String actualLocalPath = "C:\\Users\\guilh\\AppData\\Roaming\\Telegram Desktop\\Telegram.exe";
            assertTrue(hts.contains(actualLocalPath));
            
//            String primaryName = "Nome Primário";
//            assertEquals(primaryName, hts.substring(1480, 1493));
            
            String actualPrimaryName = "Telegram.exe";
            assertTrue(hts.contains(actualPrimaryName));
            
//            String secundaryName = "Nome Secundário";
//            assertEquals(secundaryName, hts.substring(1310, 1325));
            
            String actualSecundaryName = "Telegram Desktop";
            assertTrue(hts.contains(actualSecundaryName));
            
//            String lastAccessDate = "Data do Último Acesso";
//            assertEquals(lastAccessDate, hts.substring(72, 93));
            
//            String lastAccessDateNumber = "05/09/2020 02:04:33";
//            assertEquals(lastAccessDateNumber, hts.substring(94, 113)); 

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
            
//            String creationDate = "Data de Criação";
//            assertEquals(creationDate, hts.substring(22, 37));
            
//            String creationDateNumber = "31/03/2021 01:54:53";
//            assertEquals(creationDateNumber, hts.substring(38, 57));
            
//            String localPath = "Caminho Local";
//            assertEquals(localPath, hts.substring(637, 650));
            
            String actualLocalPath = "C:\\Program Files (x86)\\PokerStars\\PokerStarsUpdate.exe";
            assertTrue(hts.contains(actualLocalPath));
            
//            String primaryName = "Nome Primário";
//            assertEquals(primaryName, hts.substring(1315, 1328));
            
            String actualPrimaryName = "PokerStarsUpdate.exe";
            assertTrue(hts.contains(actualPrimaryName));
            
//            String secundaryName = "Nome Secundário";
//            assertEquals(secundaryName, hts.substring(1530, 1545));
            
            String actualSecundaryName = "PokerStars";
            assertTrue(hts.contains(actualSecundaryName));
            
//            String lastAccessDate = "Data do Último Acesso";
//            assertEquals(lastAccessDate, hts.substring(72, 93));
            
//            String lastAccessDateNumber = "31/03/2021 01:54:53";
//            assertEquals(lastAccessDateNumber, hts.substring(94, 113));

           }


}
