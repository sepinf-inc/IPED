package dpf.sp.gpinf.indexer.parsers;





import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;



import java.io.InputStream;


import junit.framework.TestCase;


public class SevenZipParserTest extends TestCase {
    

    @Test
    public void testSevenZipParsing()  throws Exception {
       //stream receiving null???
        Parser parser = new AutoDetectParser(); 
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        InputStream stream = SevenZipParserTest.class.getResourceAsStream(
                "/test-files/mockrar.zip");
        try {
            parser.parse(stream, handler, metadata, context);
        } finally {
            stream.close();
        }
    }
}


