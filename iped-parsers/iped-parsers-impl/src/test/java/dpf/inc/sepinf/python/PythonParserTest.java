package dpf.inc.sepinf.python;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import junit.framework.TestCase;

public class PythonParserTest extends TestCase {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testPythonParser() throws IOException, SAXException, TikaException, URISyntaxException {

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("test-files/test_setup.py").toURI());
        PythonParser parser = new PythonParser(file);
        Metadata metadata = new Metadata();
        metadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, "application/x-sh");
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_setup.py")){
            parser.parse(stream, handler, metadata, context);
            String hts = handler.toString();
            String mts = metadata.toString();
            System.out.println(hts + "\n" + mts);
            
        } catch (Exception e) {
            System.out.println(e);
        }

    }

}
