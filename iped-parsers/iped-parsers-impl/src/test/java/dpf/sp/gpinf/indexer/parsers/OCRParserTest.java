package dpf.sp.gpinf.indexer.parsers;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import dpf.sp.gpinf.indexer.parsers.util.OCROutputFolder;
import dpf.sp.gpinf.indexer.parsers.util.PDFToImage;
import dpf.sp.gpinf.indexer.parsers.util.RepoToolDownloader;

public class OCRParserTest {
    private static String testRoot = System.getProperty("user.dir") + "/src/test";
    private static String osName = System.getProperty("os.name").toLowerCase();
    private static String OCR_OUTPUT_FOLDER_NAME = "ocr_output";
    private static Logger LOGGER = LoggerFactory.getLogger(OCRParser.class);

    /**
     * Checks if tesseract is present to then enable OCR parsing property
     */
    private static void enableOCR() {
        try {
            String tesseractPath = System.getProperty(OCRParser.TOOL_PATH_PROP, "") + "tesseract";
            OCRParser.checkVersionInfo(tesseractPath, "-v");
            System.setProperty(OCRParser.ENABLE_PROP, "true");
        } catch (IOException | InterruptedException e) {
            LOGGER.error("Error testing tesseract");
        }
    }

    @BeforeClass
    public static void setUpTool() throws IOException {
        // Setting properties with default OCRConfig.txt values
        System.setProperty(OCRParser.LANGUAGE_PROP, "por");
        System.setProperty(OCRParser.MIN_SIZE_PROP, "1000");
        System.setProperty(OCRParser.MAX_SIZE_PROP, "200000000");
        System.setProperty(OCRParser.PAGE_SEGMODE_PROP, "1");
        System.setProperty(PDFToImage.RESOLUTION_PROP, "250");
        System.setProperty(PDFToImage.PDFLIB_PROP, "icepdf");
        System.setProperty(PDFToImage.EXTERNAL_CONV_PROP, "true");
        System.setProperty(PDFToImage.EXTERNAL_CONV_MAXMEM_PROP, "512M");
        System.setProperty(PDFOCRTextParser.MAX_CHARS_TO_OCR, "100");
        System.setProperty(OCRParser.PROCESS_NON_STANDARD_FORMATS_PROP, "true");
        System.setProperty(OCRParser.MAX_CONV_IMAGE_SIZE_PROP, "3000");
        
        if (osName.startsWith("windows")) {
            String repoPath = "tesseract/tesseract-zip/5.0.0-alpha/tesseract-zip-5.0.0-alpha.zip";
            RepoToolDownloader.unzipFromUrl(repoPath, testRoot + "/tmp_tools/");
            System.setProperty(OCRParser.TOOL_PATH_PROP, testRoot + "/tmp_tools/tesseract/");
        }
        enableOCR();
    }

    @AfterClass
    public static void removeTempToolsFolder() throws IOException {
        System.clearProperty(OCRParser.ENABLE_PROP);
        System.clearProperty(OCRParser.LANGUAGE_PROP);
        System.clearProperty(OCRParser.MIN_SIZE_PROP);
        System.clearProperty(OCRParser.MAX_SIZE_PROP);
        System.clearProperty(OCRParser.PAGE_SEGMODE_PROP);
        System.clearProperty(PDFToImage.RESOLUTION_PROP);
        System.clearProperty(PDFToImage.PDFLIB_PROP);
        System.clearProperty(PDFToImage.EXTERNAL_CONV_PROP);
        System.clearProperty(PDFToImage.EXTERNAL_CONV_MAXMEM_PROP);
        System.clearProperty(PDFOCRTextParser.MAX_CHARS_TO_OCR);
        System.clearProperty(OCRParser.PROCESS_NON_STANDARD_FORMATS_PROP);
        System.clearProperty(OCRParser.MAX_CONV_IMAGE_SIZE_PROP);

        if (osName.startsWith("windows")) {
            File tool_path = new File(System.clearProperty(OCRParser.TOOL_PATH_PROP));
            FileUtils.deleteDirectory(tool_path.getParentFile());
        }
        FileUtils.deleteDirectory(new File(OCR_OUTPUT_FOLDER_NAME));
    }

    @Test
    public void testOCRParserPNG() throws IOException, SAXException, TikaException, SQLException {
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        ItemInfo itemInfo = new ItemInfo(0, "test_hash", null, null, "test_path", false);
        context.set(ItemInfo.class, itemInfo);
        metadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, "image/png");
        context.set(OCROutputFolder.class, new OCROutputFolder(new File(OCR_OUTPUT_FOLDER_NAME)));
        
        try (OCRParser parser = new OCRParser();
            InputStream stream = this.getClass().getResourceAsStream("/test-files/test_OCR.png")) {
            assumeTrue(parser.isEnabled());
            
            parser.parse(stream, handler, metadata, context);
            String mts = metadata.toString();
            String hts = handler.toString();

            System.out.println(hts);
            assertTrue(mts.contains("Content-Type=image/png"));
            assertTrue(hts.contains("Lena"));
            assertTrue(hts.contains("online"));
            assertTrue(hts.contains("57%"));
            assertTrue(hts.contains("10:04 am"));
            assertTrue(hts.contains("Oi, tudo bem?"));
            assertTrue(hts.contains("Tudo certo, o que estamos fazendo\naqui?"));
            assertTrue(hts.contains("Isso é um print para testar o\nOCRParser"));
            assertTrue(hts.contains("Boa sOrte GALeRa"));
        }
    }
}
