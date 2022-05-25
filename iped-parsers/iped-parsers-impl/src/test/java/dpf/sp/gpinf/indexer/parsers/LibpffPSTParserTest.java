package dpf.sp.gpinf.indexer.parsers;

import static org.junit.Assume.assumeFalse;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.FileUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import dpf.sp.gpinf.indexer.parsers.util.RepoToolDownloader;

public class LibpffPSTParserTest extends AbstractPkgTest {
    private static String testRoot = System.getProperty("user.dir") + "/src/test";
    private static String osName = System.getProperty("os.name").toLowerCase();

    
    private void setUpTool() throws IOException {
        if (osName.startsWith("windows")) {
            String repoPath = "libyal/libpff/20131028/libpff-20131028.zip";
            RepoToolDownloader.unzipFromUrl(repoPath, testRoot + "/tmp_tools/");
            System.setProperty(LibpffPSTParser.TOOL_PATH_PROP, testRoot + "/tmp_tools/pffexport/");
        }

    }

    private void removeTempToolsFolder() throws IOException {
        if (osName.startsWith("windows")) {
            File tool_path = new File(System.clearProperty(LibpffPSTParser.TOOL_PATH_PROP));
            FileUtils.deleteDirectory(tool_path.getParentFile());
        }
    }

    @Test
    public void testLibpffPSTParser() throws IOException, SAXException, TikaException {
        setUpTool();

        LibpffPSTParser parser = new LibpffPSTParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new DefaultHandler();
        parser.setExtractOnlyActive(true);
        parser.getSupportedTypes(pstContext);
        metadata.set(Metadata.RESOURCE_NAME_KEY, "pst_sample");
        try (InputStream stream = this.getClass().getResourceAsStream("/test-files/test_sample.pst")) {
            if (parser.getSupportedTypes(pstContext).isEmpty()) throw new IOException();
            parser.parse(stream, handler, metadata, pstContext);

            String hts = handler.toString();
            String mts = metadata.toString();

            // String test = psttracker.messagebody.get(0);
        } catch (IOException e) {
            // skip test
        } finally {
            removeTempToolsFolder();
        }
    }

}
