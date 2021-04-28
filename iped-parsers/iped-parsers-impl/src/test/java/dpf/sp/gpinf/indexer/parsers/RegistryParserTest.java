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

public class RegistryParserTest extends TestCase{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
    @Test
    public void testRegistryParserSOFTWARE() throws IOException, SAXException, TikaException{

        RegistryParser parser = new RegistryParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler(28000000);
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/SOFTWARE");
        metadata.set(Metadata.RESOURCE_NAME_KEY, "software");
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
//        String hts = handler.toString();
//        assertTrue(hts.contains("*.wpc.azureedge.net")); 
//        assertTrue(hts.contains("California1")); 
//        assertTrue(hts.contains("App.AppXyk8e485yjpp3qmb8j9cw21eqy1snr07m.mca")); 
//        assertTrue(hts.contains("microsoftvideo"));
         
    }
    
    @Test
    public void testRegistryParserSAM() throws IOException, SAXException, TikaException{

        RegistryParser parser = new RegistryParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/SAM");
        parser.getSupportedTypes(context);
        metadata.set(Metadata.RESOURCE_NAME_KEY, "sam");
        parser.parse(stream, handler, metadata, context);
//        String hts = handler.toString();
//        assertTrue(hts.contains("ROOT")); 
//        assertTrue(hts.contains("Members")); 
//        assertTrue(hts.contains("guilh")); 
//        assertTrue(hts.contains("ServerDomainUpdates")); 
         
    }
    
    @Test
    public void testRegistryParserSECURITY() throws IOException, SAXException, TikaException{

        RegistryParser parser = new RegistryParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/SECURITY");
        metadata.set(Metadata.RESOURCE_NAME_KEY, "security");
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
//        String hts = handler.toString();
//        assertTrue(hts.contains("DefaultPassword")); 
//        assertTrue(hts.contains("S-1-5-80-3139157870-2983391045-3678747466-658725712-1809340420t")); 
//        assertTrue(hts.contains("MINWINPC")); 
//        assertTrue(hts.contains("WORKGROUP")); 

    }
    
    @Test
    public void testRegistryParserSYSTEM() throws IOException, SAXException, TikaException{

        RegistryParser parser = new RegistryParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler(6000000);
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/SYSTEM");
        metadata.set(Metadata.RESOURCE_NAME_KEY, "system");
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
//        String hts = handler.toString();
//        assertTrue(hts.contains("C:\\Program Files\\Avast Software\\Avast\\defs\\21012716\\engsup.exe")); 
//        assertTrue(hts.contains("RC:\\Windows\\system32\\MusNotificationUx.exe")); 
//        assertTrue(hts.contains("C:\\Users\\guilh\\AppData\\Local\\NVIDIA\\NvBackend\\DAO\\29549383\\0.dat")); 
//        assertTrue(hts.contains("C:\\Program Files\\NVIDIA Corporation\\NVIDIA GeForce Experience\\NVIDIA GeForce Experience.exe")); 
    }

}
