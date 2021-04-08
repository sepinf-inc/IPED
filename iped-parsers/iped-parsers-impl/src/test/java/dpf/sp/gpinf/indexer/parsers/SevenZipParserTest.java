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


public class SevenZipParserTest  extends AbstractPkgTest {
    

    @Test

        public void testSevenZipParsing() throws Exception {
            Parser parser = new AutoDetectParser();
            ContentHandler handler = new BodyContentHandler();
            Metadata metadata = new Metadata();

            InputStream stream = SevenZipParserTest.class.getResourceAsStream(
                    "/test-files/mockrar.rar");
            try {
                parser.parse(stream, handler, metadata, recursingContext);
            } finally {
                stream.close();
            }
            
            assertEquals("application/x-rar-compressed", metadata.get(Metadata.CONTENT_TYPE));
            String content = handler.toString();
            assertTrue(content.contains("mocktext1.txt"));
            assertTrue(content.contains("mocktext2.txt"));
            assertTrue(content.contains("mocktext3.txt"));
            }
}


    



