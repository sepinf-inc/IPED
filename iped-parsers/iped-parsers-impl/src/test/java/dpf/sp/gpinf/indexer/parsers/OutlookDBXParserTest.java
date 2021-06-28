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

public class OutlookDBXParserTest  extends TestCase{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
   
    @Test
    public void testOutlookDBXParser() throws IOException, SAXException, TikaException{

        OutlookDBXParser parser = new OutlookDBXParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_entryBox.dbx");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
//        parser.parse(stream, handler, metadata, context);
        
        String hts = handler.toString(); 
        String mts = metadata.toString();
        System.out.println(hts + mts);
    }
    
}