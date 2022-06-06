package dpf.sp.gpinf.indexer.parsers;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

import org.apache.commons.io.FileUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import dpf.sp.gpinf.indexer.parsers.util.OCROutputFolder;
import dpf.sp.gpinf.indexer.parsers.util.RepoToolDownloader;
import dpf.sp.gpinf.indexer.util.ExternalImageConverter;

public class OCRParserTest {
    private static String testRoot = System.getProperty("user.dir") + "/src/test";
    private static String osName = System.getProperty("os.name").toLowerCase();
    private static String OCR_OUTPUT_FOLDER_NAME = "ocr_output";
    private static Logger LOGGER = LoggerFactory.getLogger(OCRParser.class);

    @Rule
    public TestName testName = new TestName();

    @BeforeClass
    public static void setUpTool() throws IOException {
        if (osName.startsWith("windows")) {
            String repoPath = "tesseract/tesseract-zip/5.0.0-alpha/tesseract-zip-5.0.0-alpha.zip";
            RepoToolDownloader.unzipFromUrl(repoPath, testRoot + "/tmp_tools/");
            System.setProperty(OCRParser.TOOL_PATH_PROP, testRoot + "/tmp_tools/tesseract/");
        }

        // Checks if tesseract is present to then enable OCR parsing property
        try {
            String tesseractPath = System.getProperty(OCRParser.TOOL_PATH_PROP, "") + "tesseract";
            OCRParser.checkVersionInfo(tesseractPath, "-v");
            System.setProperty(OCRParser.ENABLE_PROP, "true");
        } catch (IOException | InterruptedException e) {
            LOGGER.error("Error testing tesseract");
        }
    }

    @AfterClass
    public static void tearDownTool() throws IOException {
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
        ItemInfo itemInfo = new ItemInfo(0, testName.getMethodName(), null, null, testName.getMethodName(), false);
        context.set(ItemInfo.class, itemInfo);
        metadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, "image/png");
        context.set(OCROutputFolder.class, new OCROutputFolder(new File(OCR_OUTPUT_FOLDER_NAME)));
        System.setProperty(OCRParser.LANGUAGE_PROP, "por");
        
        try (OCRParser parser = new OCRParser();
            InputStream stream = this.getClass().getResourceAsStream("/test-files/test_OCR.png")) {
            assumeTrue(parser.isEnabled());
            
            parser.parse(stream, handler, metadata, context);
            String mts = metadata.toString();
            String hts = handler.toString();

            assertTrue(mts.contains("Content-Type=image/png"));
            assertTrue(hts.contains("57%"));
            assertTrue(hts.contains("10:04 am"));
            assertTrue(hts.contains("Oi, tudo bem?"));
            assertTrue(hts.contains("Tudo certo, o que estamos fazendo"));
            assertTrue(hts.contains("aqui?"));
            assertTrue(hts.contains("Isso é um print para testar o"));
            assertTrue(hts.contains("OCRParser"));
            assertTrue(hts.contains("Boa sOrte GALeRa"));
        }
    }

    @Test
    public void testOCRParserPDF() throws IOException, SAXException, TikaException, SQLException {
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        ItemInfo itemInfo = new ItemInfo(0, testName.getMethodName(), null, null, testName.getMethodName(), false);
        context.set(ItemInfo.class, itemInfo);
        metadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, "application/pdf");
        context.set(OCROutputFolder.class, new OCROutputFolder(new File(OCR_OUTPUT_FOLDER_NAME)));
        System.setProperty(OCRParser.LANGUAGE_PROP, "por");
        
        try (OCRParser parser = new OCRParser();
            InputStream stream = this.getClass().getResourceAsStream("/test-files/test_OCR.pdf")) {
            assumeTrue(parser.isEnabled());
            
            parser.parse(stream, handler, metadata, context);
            String mts = metadata.toString();
            String hts = handler.toString();

            assertTrue(mts.contains("Content-Type=application/pdf"));
            assertTrue(hts.contains("RISC-V UNICICLO"));
            assertTrue(hts.contains("Instruction [31-0]"));
            assertTrue(hts.contains("MemtoReg"));
            assertTrue(hts.contains("Lógico-Aritméticas com imediato: ADDi, ANDi, ORi, XORi, SLLi, SRLi"));
            assertTrue(hts.contains("process (ALUop, funct3, funct7)"));
            assertTrue(hts.contains("and s6, s5, s4"));
            assertTrue(hts.contains("00000048 005324b3"));
            assertTrue(hts.contains("as memórias de instruções e dados."));
        }
    }
    
    @Test
    public void testOCRParserTIFF() throws IOException, SAXException, TikaException, SQLException {
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        ItemInfo itemInfo = new ItemInfo(0, testName.getMethodName(), null, null, testName.getMethodName(), false);
        context.set(ItemInfo.class, itemInfo);
        metadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, "image/tiff");
        context.set(OCROutputFolder.class, new OCROutputFolder(new File(OCR_OUTPUT_FOLDER_NAME)));
        System.setProperty(OCRParser.LANGUAGE_PROP, "eng");

        try (OCRParser parser = new OCRParser();
            InputStream stream = this.getClass().getResourceAsStream("/test-files/test_OCR.tiff")) {
            assumeTrue(parser.isEnabled());
            
            parser.parse(stream, handler, metadata, context);
            String mts = metadata.toString();
            String hts = handler.toString();

            assertTrue(mts.contains("Content-Type=image/tiff"));
            assertTrue(hts.contains("Literature must rest always on a principle"));
            assertTrue(hts.contains("times and places are one; the stuff he deals with"));
            assertTrue(hts.contains("extract from THE ENGLISH RENAISSANCE"));
            assertTrue(hts.contains("The Quick Brown"));
            assertTrue(hts.contains("Fox Jumps Over"));
            assertTrue(hts.contains("The Lazy Dog."));
            assertTrue(hts.contains("abcdefghijklmnopq"));
            assertTrue(hts.contains("01234567890 01234567890"));
        }
    }

    @Test
    public void testOCRParserPSD() throws IOException, SAXException, TikaException, SQLException {
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        ItemInfo itemInfo = new ItemInfo(0, testName.getMethodName(), null, null, testName.getMethodName(), false);
        context.set(ItemInfo.class, itemInfo);
        metadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, "image/vnd.adobe.photoshop");
        context.set(OCROutputFolder.class, new OCROutputFolder(new File(OCR_OUTPUT_FOLDER_NAME)));
        System.setProperty(OCRParser.LANGUAGE_PROP, "eng");

        try (OCRParser parser = new OCRParser();
            InputStream stream = this.getClass().getResourceAsStream("/test-files/test_OCR.psd")) {
            assumeTrue(parser.isEnabled());

            setUpImageMagick();
            String magickDir = System.getProperty(ExternalImageConverter.winToolPathPrefixProp, "");
            assumeTrue(isImageMagickInstalled(magickDir));
            
            parser.parse(stream, handler, metadata, context);
            String mts = metadata.toString();
            String hts = handler.toString();

            assertTrue(mts.contains("Content-Type=image/vnd.adobe.photoshop"));
            assertTrue(hts.contains("Parsing non-standard file format"));
            assertTrue(hts.contains("SAMPLE TEXT"));
            assertTrue(hts.contains("Centered Text"));
            assertTrue(hts.contains("sample .psd file"));
        }
    }

    @Test
    public void testOCRParserSVG() throws IOException, SAXException, TikaException, SQLException, InterruptedException {
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        ItemInfo itemInfo = new ItemInfo(0, testName.getMethodName(), null, null, testName.getMethodName(), false);
        context.set(ItemInfo.class, itemInfo);
        metadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, "image/svg+xml");
        context.set(OCROutputFolder.class, new OCROutputFolder(new File(OCR_OUTPUT_FOLDER_NAME)));
        System.setProperty(OCRParser.LANGUAGE_PROP, "eng");

        try (OCRParser parser = new OCRParser();
            InputStream stream = this.getClass().getResourceAsStream("/test-files/test_OCR.svg")) {
            assumeTrue(parser.isEnabled());

            setUpImageMagick();
            String magickDir = System.getProperty(ExternalImageConverter.winToolPathPrefixProp, "");
            assumeTrue(isImageMagickInstalled(magickDir));
            
            parser.parse(stream, handler, metadata, context);
            String mts = metadata.toString();
            String hts = handler.toString();

            assertTrue(mts.contains("Content-Type=image/svg+xml"));
            assertTrue(hts.contains("Transport"));
            assertTrue(hts.contains("Aa Ee Qq"));
            assertTrue(hts.contains("Rr Ss Tt"));
            assertTrue(hts.contains("Manchester"));
            assertTrue(hts.contains("abcdefghijklm"));
            assertTrue(hts.contains("0123456789"));
        }
    }


    private void setUpImageMagick() throws IOException {
        if (System.getProperty(ExternalImageConverter.enabledProp, "").isEmpty()) {
            System.setProperty(ExternalImageConverter.enabledProp, "true");
            if (osName.startsWith("windows")) {
                String repoPath = "org/imagemagick/imagemagick-zip/7.1.0-q8-x64/imagemagick-zip-7.1.0-q8-x64.zip";
                RepoToolDownloader.unzipFromUrl(repoPath, testRoot + "/tmp_tools/tools");
                System.setProperty(ExternalImageConverter.winToolPathPrefixProp, testRoot + "/tmp_tools");
            }
        }
    }

    private boolean isImageMagickInstalled(String magickDir) {
        magickDir += osName.startsWith("windows") ? "/tools/imagemagick/magick" : "convert";
        try {
            Process process = Runtime.getRuntime().exec(magickDir + " -version");
            int result = process.waitFor();
            if (result != 0) {
                throw new IOException("Returned error code " + result);
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.error("Error testing imagemagick: " + e.toString());
            return false;
        }
        return true;
    }
}
