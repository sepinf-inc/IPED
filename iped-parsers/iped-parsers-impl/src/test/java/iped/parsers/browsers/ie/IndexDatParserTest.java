package iped.parsers.browsers.ie;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.lang3.SystemUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToTextContentHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.util.TestTools;

public class IndexDatParserTest {

    @BeforeClass
    public static void setUpTool() throws IOException {
        if (SystemUtils.IS_OS_WINDOWS) {
            System.setProperty(IndexDatParser.TOOL_PATH_PROP, TestTools.dir("msiecfexport"));
        }
    }

    @AfterClass
    public static void tearDownTool() throws IOException {
        if (SystemUtils.IS_OS_WINDOWS) {
            System.clearProperty(IndexDatParser.TOOL_PATH_PROP);
        }
    }


    @Test
    public void testIndexDatParser() throws IOException, SAXException, TikaException {
        IndexDatParser parser = new IndexDatParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new ToTextContentHandler();
        ParseContext context = new ParseContext();
        assumeFalse(parser.getSupportedTypes(context).isEmpty());

        try (InputStream stream = this.getClass().getResourceAsStream("/test-files/test_index.dat")) {
            parser.parse(stream, handler, metadata, context);
            String hts = handler.toString();

            assertTrue(hts.contains("Record type"));
            assertTrue(hts.contains("URL"));
            assertTrue(hts.contains("Offset range"));
            assertTrue(hts.contains("20480 - 20736 (256)"));
            assertTrue(hts.contains("Location"));
            assertTrue(hts.contains("Cookie:guileb@google.com.br/"));
            assertTrue(hts.contains("Filename"));
            assertTrue(hts.contains("guileb@google.com[2].txt"));
            assertTrue(hts.contains("Cookie:guileb@incredimail.com/"));
            assertTrue(hts.contains("Cookie:guileb@google.com.br/search"));
            assertTrue(hts.contains("Cookie:guileb@incredibarvuz1.com/"));
            assertTrue(hts.contains("Cookie:guileb@google.com.br/complete/search"));
            assertTrue(hts.contains("Cookie:guileb@www.incredibarvuz1.com/"));
            assertTrue(hts.contains("Export completed."));

        }

    }
}
