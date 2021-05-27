package dpf.inc.sepinf.python;

import java.io.File;
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

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.LibpffPSTParser;
import junit.framework.TestCase;

public class PythonParserTest extends TestCase{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
    
    @Test
    public void testPythonParser() throws IOException, SAXException, TikaException{
        
        File file = new File("/test-files/test_setup.py/");
        PythonParser parser = new PythonParser(file);
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_setup.py");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
//        parser.parse(stream, handler, metadata, context);
        String hts = handler.toString();
        String mts = metadata.toString();
        System.out.println(hts + "\n" + mts);
           
    }

}
