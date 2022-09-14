package iped.parsers.python;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

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
import iped.utils.IOUtil;

public class PythonParserTest {
    private static String userDir = System.getProperty("user.dir");
    private static String osName = System.getProperty("os.name").toLowerCase();
    private static String testRoot;
    private static String PYTHON_PARSERS_FOLDER = userDir + "/src/test/resources/test-files/python-parsers-test";

    @BeforeClass
    public static void setUpTestRoot() throws IOException {
        if (osName.startsWith("windows")) {
            String targetReleasePath = userDir + "/../../target/release/";
            assumeTrue(new File(targetReleasePath).exists());
            String ipedName = new File(targetReleasePath).listFiles()[0].getName(); 
            testRoot = targetReleasePath + ipedName;

            System.setProperty(IConfigurationDirectory.IPED_ROOT, testRoot);
            assumeTrue(new File(testRoot + "/python").exists());
        }
        System.setProperty(PythonParser.PYTHON_PARSERS_FOLDER, PYTHON_PARSERS_FOLDER);
    }

    @AfterClass
    public static void cleanUp() {
        File cacheDir = new File(PYTHON_PARSERS_FOLDER, "__pycache__");
        IOUtil.deleteDirectory(cacheDir);
    }

    @Test
    public void testPythonParserExample() throws IOException, SAXException, TikaException {
        PythonParser parser = new PythonParser();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        Metadata metadata = new Metadata();
        metadata.set(StandardParser.INDEXER_CONTENT_TYPE, "text/plain");
        assumeFalse(parser.getSupportedTypes(context).isEmpty());

        try (InputStream stream = this.getClass().getResourceAsStream("/test-files/test_utf8")) {
            parser.parse(stream, handler, metadata, context);

            String hts = handler.toString();

            assertTrue(hts.contains("issO é OUTR4 stRin8888 codificada em UTF8"));
            assertTrue(hts.contains("Essa stRin8888G esta´ sendÖOO utilizada n0 P4RSER"));
            assertTrue(hts.contains("do 1P3D para R4W STR1N85...!!111"));
            assertEquals("propertyValueExample", metadata.get("propertyNameExample"));
            assertEquals("text/plain; charset=UTF-8", metadata.get(Metadata.CONTENT_TYPE));
        }
    }

}
