package dpf.mt.gpinf.registro;



import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToTextContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import junit.framework.TestCase;

public class RegistroParserTest extends TestCase{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
    
    @Test
    public void testRegistroParserSecurity() throws IOException, SAXException, TikaException{

        RegistroParser parser = new RegistroParser();
        Metadata metadata = new Metadata();
        metadata.set(TikaMetadataKeys.RESOURCE_NAME_KEY, "SECURITY");
        ContentHandler handler = new ToTextContentHandler();
        InputStream stream = getStream("test-files/test_security");
        ParseContext context = new ParseContext();
        ItemInfo itemInfo = new ItemInfo(0, getName(), null, null, "system32/config", false);
        context.set(ItemInfo.class, itemInfo);
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        
        String hts = handler.toString();
        String mts = metadata.toString();
        System.out.println(hts + mts);
    }

    @Test
    public void testRegistroParserSAM() throws IOException, SAXException, TikaException{

        RegistroParser parser = new RegistroParser();
        Metadata metadata = new Metadata();
        metadata.set(TikaMetadataKeys.RESOURCE_NAME_KEY, "SAM");
        ContentHandler handler = new ToTextContentHandler();
        InputStream stream = getStream("test-files/test_sam");
        ParseContext context = new ParseContext();
        ItemInfo itemInfo = new ItemInfo(0, getName(), null, null, "system32/config", false);
        context.set(ItemInfo.class, itemInfo);
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        
        String hts = handler.toString();
        String mts = metadata.toString();
        System.out.println(hts + mts);
    }
}
