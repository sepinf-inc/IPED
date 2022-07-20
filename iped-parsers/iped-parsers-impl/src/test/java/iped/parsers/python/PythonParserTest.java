package iped.parsers.python;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.FileUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.configuration.IConfigurationDirectory;
import iped.parsers.standard.StandardParser;
import iped.parsers.util.RepoToolDownloader;

public class PythonParserTest {
    private static String testRoot = System.getProperty("user.dir") + "/src/test";
    private static String osName = System.getProperty("os.name").toLowerCase();
    private static String PYTHON_PARSERS_FOLDER = testRoot + "/resources/test-files/python-parsers-test";

    @BeforeClass
    public static void setUpPython() throws IOException {
        if (osName.startsWith("windows")) {
            String repoPath = "org/python/python-jep-dlib/3.9.12-4.0.3-19.23.1/python-jep-dlib-3.9.12-4.0.3-19.23.1.zip";
            RepoToolDownloader.unzipFromUrl(repoPath, testRoot + "/tmp_tools/");
            System.setProperty(IConfigurationDirectory.IPED_ROOT, testRoot + "/tmp_tools");
        }
    }

    @AfterClass
    public static void tearDownPython() throws IOException {
        if (osName.startsWith("windows")) {
            File python_path = new File(System.clearProperty(IConfigurationDirectory.IPED_ROOT));
            // FileUtils.deleteDirectory(python_path);  // can't delete because library still loaded
            // have to delete iped/iped-parsers/iped-parsers-impl/src/test/tmp_tools manually after
        }
    }
    

    @Test
    public void testPythonParserExample() throws IOException, SAXException, TikaException {
        System.setProperty(PythonParser.PYTHON_PARSERS_FOLDER, PYTHON_PARSERS_FOLDER);

        PythonParser parser = new PythonParser();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        Metadata metadata = new Metadata();
        metadata.set(StandardParser.INDEXER_CONTENT_TYPE, "text/plain");
        try (InputStream stream = this.getClass().getResourceAsStream("/test-files/test_utf8")) {
            parser.parse(stream, handler, metadata, context);

            String hts = handler.toString();
            String mts = metadata.toString();

            assertTrue(hts.contains("issO é OUTR4 stRin8888 codificada em UTF8"));
            assertTrue(hts.contains("Essa stRin8888G esta´ sendÖOO utilizada n0 P4RSER"));
            assertTrue(hts.contains("do 1P3D para R4W STR1N85...!!111"));
            assertTrue(mts.contains("propertyNameExample"));
            assertTrue(mts.contains("propertyValueExample"));
            assertTrue(mts.contains("UTF-8"));
        }
    }

}
