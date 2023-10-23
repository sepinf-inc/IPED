package iped.parsers.browsers.ie;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.FileUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToTextContentHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.util.RepoToolDownloader;

public class IndexDatParserTest {
    private static String testRoot = System.getProperty("user.dir") + "/src/test";
    private static String osName = System.getProperty("os.name").toLowerCase();

    @BeforeClass
    public static void setUpTool() throws IOException {
        if (osName.startsWith("windows")) {
            String repoPath = "libyal/libmsiecf/20160421.1/libmsiecf-20160421.1.zip";
            RepoToolDownloader.unzipFromUrl(repoPath, testRoot + "/tmp_tools/");
            System.setProperty(IndexDatParser.TOOL_PATH_PROP, testRoot + "/tmp_tools/msiecfexport/");
        }
    }

    @AfterClass
    public static void tearDownTool() throws IOException {
        if (osName.startsWith("windows")) {
            File tool_path = new File(System.clearProperty(IndexDatParser.TOOL_PATH_PROP));
            FileUtils.deleteDirectory(tool_path.getParentFile());
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
