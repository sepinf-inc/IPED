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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import dpf.sp.gpinf.indexer.parsers.util.OCROutputFolder;
import dpf.sp.gpinf.indexer.parsers.util.RepoToolDownloader;

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
    public static void removeTempToolsFolder() throws IOException {
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
    
}
