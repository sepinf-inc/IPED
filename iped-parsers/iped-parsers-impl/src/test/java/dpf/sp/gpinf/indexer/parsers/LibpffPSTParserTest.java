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

public class LibpffPSTParserTest  extends TestCase{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
    
    //same thing that is happening with registry parser. Need to set up the path manually. Using: C:\Users\guilh\Downloads\libpff-main\libpff-main\vs2019\Release\Win32
    
    @Test
    public void testLibpffPSTParser() throws IOException, SAXException, TikaException{

        LibpffPSTParser parser = new LibpffPSTParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_sample.pst");
        ParseContext context = new ParseContext();
        parser.setExtractOnlyActive(true);
        parser.setExtractOnlyDeleted(false);
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        parser.safeparse(stream, handler, metadata, context);
        String hts = handler.toString();
        String mts = metadata.toString();
        System.out.println(hts + "\n" + mts);
           
    }

}
