package dpf.sp.gpinf.indexer.parsers;

import java.io.IOException;
import java.io.InputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import junit.framework.TestCase;

public class IndexerDefaultParserTest extends TestCase{
    
    //This class will test some files such as: Image, Video, Document, Text, Package, PDF and an Unknown file type.
    
    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }
    
    @Test
    public void testIndexerDefaultParserParsing() throws IOException, SAXException, TikaException{

        IndexerDefaultParser parser = new IndexerDefaultParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_utf8");
        ParseContext context = new ParseContext();
        parser.getBestParser(metadata);
        parser.setPrintMetadata(true);
        parser.setIgnoreStyle(true);
        parser.hasSpecificParser(metadata);
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);


       }

    @Test
    public void testIndexerDefaultParserParsingImage() throws IOException, SAXException, TikaException{

        IndexerDefaultParser parser = new IndexerDefaultParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_lenaPng.png");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        
        String hts = handler.toString();
        String mts = metadata.toString();
        
        assertTrue(hts.contains("Indexer-Content-Type: image/png"));
        assertTrue(hts.contains("X-Parsed-By: org.apache.tika.parser.image.ImageParser"));
        assertTrue(hts.contains("image:IHDR: width=512, height=512, bitDepth=8, colorType=RGB, compressionMethod=deflate, filterMethod=adaptive"));
        assertTrue(hts.contains("image:tiff:BitsPerSample: 8 8 8"));
        assertTrue(hts.contains("image:Height: 512"));
        assertTrue(hts.contains("image:Width: 512"));

        assertTrue(mts.contains("Content-Type=image/png"));
        assertTrue(mts.contains("image:Height=512"));
        assertTrue(mts.contains("image:Width=512"));
        assertTrue(mts.contains("image:tiff:BitsPerSample=8 8 8"));

       }
    
    @Test
    public void testIndexerDefaultParserParsingVideo() throws IOException, SAXException, TikaException{

        IndexerDefaultParser parser = new IndexerDefaultParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_videoFlv.flv");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        
        String hts = handler.toString();
        String mts = metadata.toString();
        
        assertTrue(hts.contains("Indexer-Content-Type: video/x-flv"));
        assertTrue(hts.contains("X-Parsed-By: org.apache.tika.parser.video.FLVParser"));
        assertTrue(hts.contains("video:audiocodecid: 2.0"));
        assertTrue(hts.contains("video:audiodatarate: 0.0"));
        assertTrue(hts.contains("video:audiosamplerate: 44100.0"));
        assertTrue(hts.contains("video:audiosamplesize: 16.0"));
        assertTrue(hts.contains("video:compatible_brands: isomiso2avc1iso6mp41"));
        assertTrue(hts.contains("video:duration: 36.931"));
        assertTrue(hts.contains("video:encoder: Lavf58.45.100"));
        assertTrue(hts.contains("video:filesize: 2317483.0"));
        assertTrue(hts.contains("video:framerate: 29.97002997002997"));
        assertTrue(hts.contains("video:hasAudio: true"));
        assertTrue(hts.contains("video:hasVideo: true"));
        assertTrue(hts.contains("video:Height: 144.0"));
        assertTrue(hts.contains("video:major_brand: isom"));
        assertTrue(hts.contains("video:minor_version: 512"));
        assertTrue(hts.contains("video:stereo: true"));
        assertTrue(hts.contains("video:videocodecid: 2.0"));
        assertTrue(hts.contains("video:videodatarate: 195.3125"));
        assertTrue(hts.contains("video:Width: 256.0"));

        assertTrue(mts.contains("Content-Type=video/x-flv"));
        assertTrue(mts.contains("video:Height=144.0"));
        assertTrue(mts.contains("video:Width=256.0"));
        assertTrue(mts.contains("video:framerate=29.97002997002997"));

       }
    
    @Test
    public void testIndexerDefaultParserParsingDocument() throws IOException, SAXException, TikaException{

        IndexerDefaultParser parser = new IndexerDefaultParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_mockDoc1.docx");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        
        String hts = handler.toString();
        String mts = metadata.toString();
        
        assertTrue(hts.contains("Mockdoc1:"));
        assertTrue(hts.contains("· Lorem ipsum dolor sit amet, consectetur adipiscing elit."));
        assertTrue(hts.contains("· Suspendisse id erat maximus, iaculis turpis ut, dignissim nibh."));
        
        assertTrue(hts.contains("Indexer-Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        assertTrue(hts.contains("X-Parsed-By: org.apache.tika.parser.microsoft.ooxml.OOXMLParser"));
        assertTrue(hts.contains("office:Application-Name: Microsoft Office Word"));
        assertTrue(hts.contains("office:Application-Version: 12.0000"));
        assertTrue(hts.contains("office:Character Count: 2724"));
        assertTrue(hts.contains("office:Character-Count-With-Spaces: 3222"));
        assertTrue(hts.contains("office:Line-Count: 22"));
        assertTrue(hts.contains("office:Page-Count: 1"));
        assertTrue(hts.contains("office:Paragraph-Count: 6"));
        assertTrue(hts.contains("office:Revision-Number: 5"));
        assertTrue(hts.contains("office:Template: Normal.dotm"));
        assertTrue(hts.contains("office:Word-Count: 504"));
        assertTrue(hts.contains("office:cp:revision: 5"));
        assertTrue(hts.contains("office:dc:creator: Guilherme Andreúce Sobreira Monteiro"));
        assertTrue(hts.contains("office:dcterms:created: 2021-04-08T17:03:00Z"));
        assertTrue(hts.contains("office:dcterms:modified: 2021-04-09T12:25:00Z"));
        assertTrue(hts.contains("office:extended-properties:AppVersion: 12.0000"));
        assertTrue(hts.contains("office:extended-properties:Application: Microsoft Office Word"));
        assertTrue(hts.contains("office:extended-properties:DocSecurityString: None"));
        assertTrue(hts.contains("office:extended-properties:Template: Normal.dotm"));
        assertTrue(hts.contains("office:meta:character-count: 2724"));
        assertTrue(hts.contains("office:meta:character-count-with-spaces: 3222"));
        assertTrue(hts.contains("office:meta:last-author: Guilherme Andreúce Sobreira Monteiro"));
        assertTrue(hts.contains("office:meta:line-count: 22"));
        assertTrue(hts.contains("office:meta:page-count: 1"));
        assertTrue(hts.contains("office:meta:paragraph-count: 6"));
        assertTrue(hts.contains("office:meta:word-count: 504"));
        assertTrue(hts.contains("office:xmpTPg:NPages: 1"));
        
        assertTrue(mts.contains("Content-Type=application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        assertTrue(mts.contains("office:meta:character-count=2724"));
        assertTrue(mts.contains("office:dc:creator=Guilherme Andreúce Sobreira Monteiro"));

       }
    
    @Test
    public void testIndexerDefaultParserParsingText() throws IOException, SAXException, TikaException{

        IndexerDefaultParser parser = new IndexerDefaultParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_utf8");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        
        String hts = handler.toString();
        String mts = metadata.toString();
        
        assertTrue(hts.contains("issO é OUTR4 stRin8888"));
        assertTrue(hts.contains("codificada em UTF8"));
        assertTrue(hts.contains("Essa part3 está em UTF8."));
        
        assertTrue(hts.contains("Content-Encoding: UTF-8"));
        assertTrue(hts.contains("Indexer-Content-Type: text/plain"));
        assertTrue(hts.contains("X-Parsed-By: org.apache.tika.parser.csv.TextAndCSVParser"));
        
        assertTrue(mts.contains("Content-Type=text/plain"));
        assertTrue(mts.contains("X-Parsed-By=org.apache.tika.parser.csv.TextAndCSVParser"));
        assertTrue(mts.contains("charset=UTF-8"));

       }
    
    @Test
    public void testIndexerDefaultParserParsingPackage() throws IOException, SAXException, TikaException{

        IndexerDefaultParser parser = new IndexerDefaultParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_mockRar4.rar");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        
        String hts = handler.toString();
        String mts = metadata.toString();
        

        assertTrue(hts.contains("mocktext1.txt"));
        assertTrue(hts.contains("mocktext2.txt"));
        assertTrue(hts.contains("mocktext3.txt"));
        assertTrue(hts.contains("mocktext4.txt"));
        assertTrue(hts.contains("mocktext5.txt"));
        assertTrue(hts.contains("mocksheets1.xlsx"));
        assertTrue(hts.contains("mocksheets2.xlsx"));
        assertTrue(hts.contains("mocksheets3.xlsx"));
        assertTrue(hts.contains("mocksheets4.xlsx"));
        assertTrue(hts.contains("mocksheets5.xlsx"));
        assertTrue(hts.contains("mockfolder/mockdoc5.docx"));
        assertTrue(hts.contains("mockfolder/mocksheets5.xlsx"));
        assertTrue(hts.contains("mockfolder/mocktext5.txt"));
        assertTrue(hts.contains("mockdoc1.docx"));
        assertTrue(hts.contains("mockdoc2.docx"));
        assertTrue(hts.contains("mockdoc3.docx"));
        assertTrue(hts.contains("mockdoc4.docx"));
        assertTrue(hts.contains("mockdoc5.docx"));
        
        assertTrue(hts.contains("Indexer-Content-Type: application/x-rar-compressed; version=4"));
        assertTrue(hts.contains("X-Parsed-By: org.apache.tika.parser.pkg.RarParser"));
        
        assertTrue(mts.contains("Indexer-Content-Type=application/x-rar-compressed; version=4"));
        assertTrue(mts.contains("X-Parsed-By=org.apache.tika.parser.pkg.RarParser"));
        assertTrue(mts.contains("Content-Type=application/x-rar-compressed; version=4"));

       }
    
    @Test
    public void testIndexerDefaultParserParsingPDF() throws IOException, SAXException, TikaException{

        IndexerDefaultParser parser = new IndexerDefaultParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_pdfResumes.pdf");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        
        String hts = handler.toString();
        String mts = metadata.toString();
        
        assertTrue(hts.contains("Freshman Resume"));
        assertTrue(hts.contains("Education Massachusetts Institute of Technology (MIT) Cambridge, MA"));
        assertTrue(hts.contains("UROP-Diabetes Management Project February 2016-Present"));
        
        assertTrue(hts.contains("Indexer-Content-Type: application/pdf"));
        assertTrue(hts.contains("X-Parsed-By: org.apache.tika.parser.pdf.PDFParser"));
        assertTrue(hts.contains("pdf:PDFExtensionVersion: 1.7 Adobe Extension Level 8"));
        assertTrue(hts.contains("pdf:PDFVersion: 1.7"));
        assertTrue(hts.contains("pdf:access_permission:assemble_document: true"));
        assertTrue(hts.contains("pdf:access_permission:can_modify: true"));
        assertTrue(hts.contains("pdf:access_permission:can_print: true"));
        assertTrue(hts.contains("pdf:access_permission:can_print_degraded: true"));
        assertTrue(hts.contains("pdf:access_permission:extract_content: true"));
        assertTrue(hts.contains("pdf:access_permission:extract_for_accessibility: true"));
        assertTrue(hts.contains("pdf:access_permission:fill_in_form: true"));
        assertTrue(hts.contains("pdf:access_permission:modify_annotations: true"));
        assertTrue(hts.contains("pdf:charsPerPage: 2767 2772 2990 3056 3204 3614 3627 3796 3971 3988 4242 4462 4683 64"));
        assertTrue(hts.contains("pdf:created: 2017-08-29T19:12:20Z"));
        assertTrue(hts.contains("pdf:dc:format: application/pdf; version=\"1.7 Adobe Extension Level 8\" application/pdf; version=1.7"));
        assertTrue(hts.contains("pdf:dc:language: en-US"));
        assertTrue(hts.contains("pdf:dcterms:created: 2017-08-29T19:12:20Z"));
        assertTrue(hts.contains("pdf:dcterms:modified: 2017-09-12T19:12:44Z"));
        assertTrue(hts.contains("pdf:docinfo:created: 2017-08-29T19:12:20Z"));
        assertTrue(hts.contains("pdf:docinfo:creator_tool: Adobe InDesign CC 2017 (Macintosh)"));
        assertTrue(hts.contains("pdf:docinfo:modified: 2017-09-12T19:12:44Z"));
        assertTrue(hts.contains("pdf:docinfo:producer: Adobe PDF Library 15.0"));
        assertTrue(hts.contains("pdf:docinfo:trapped: False"));
        assertTrue(hts.contains("pdf:encrypted: false"));
        assertTrue(hts.contains("pdf:hasMarkedContent: true"));
        assertTrue(hts.contains("pdf:hasXFA: false"));
        assertTrue(hts.contains("pdf:hasXMP: true"));
        assertTrue(hts.contains("pdf:producer: Adobe PDF Library 15.0"));
        assertTrue(hts.contains("pdf:trapped: False"));
        assertTrue(hts.contains("pdf:unmappedUnicodeCharsPerPage: 0"));
        assertTrue(hts.contains("pdf:xmp:CreateDate: 2017-08-29T14:12:20Z"));
        assertTrue(hts.contains("pdf:xmp:CreatorTool: Adobe InDesign CC 2017 (Macintosh)"));
        assertTrue(hts.contains("pdf:xmp:MetadataDate: 2017-09-12T15:12:44Z"));
        assertTrue(hts.contains("pdf:xmp:ModifyDate: 2017-09-12T15:12:44Z"));
        assertTrue(hts.contains("pdf:xmpMM:DerivedFrom:DocumentID: xmp.did:f3bb53b2-ad53-4b82-8274-0773472726fc"));
        assertTrue(hts.contains("pdf:xmpMM:DerivedFrom:InstanceID: xmp.iid:367905db-1695-4b2f-853d-67618bdd58a9"));
        assertTrue(hts.contains("pdf:xmpMM:DocumentID: xmp.id:53055f1a-dc31-4555-871e-832d1d70ed0e"));
        assertTrue(hts.contains("pdf:xmpTPg:NPages: 14"));

        assertTrue(mts.contains("Content-Type=application/pdf"));
        assertTrue(mts.contains("X-Parsed-By=org.apache.tika.parser.pdf.PDFParser"));
        assertTrue(mts.contains("pdf:PDFVersion=1.7"));
        
       }
    
    @Test
    public void testIndexerDefaultParserParsingUnknownFileLua() throws IOException, SAXException, TikaException{

        IndexerDefaultParser parser = new IndexerDefaultParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_unknownFileLua");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        
        String hts = handler.toString();
        String mts = metadata.toString();
        
        assertTrue(hts.contains("function"));
        assertTrue(hts.contains("OnATTACK_OBJECT_CMD (id)"));
        
        assertTrue(mts.contains("Content-Type=text/plain; charset=EUC-KR"));
        assertTrue(mts.contains("X-Parsed-By=org.apache.tika.parser.csv.TextAndCSVParser"));
        assertTrue(mts.contains("Content-Encoding=EUC-KR"));

       }
    
    @Test
    public void testIndexerDefaultParserParsingUnknownFile() throws IOException, SAXException, TikaException{

        //When in doubt, tries to parse with RawStringParser.
        IndexerDefaultParser parser = new IndexerDefaultParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_unknownFile");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        
        String mts = metadata.toString();
        
        assertTrue(mts.contains("X-Parsed-By=dpf.sp.gpinf.indexer.parsers.RawStringParser"));
        assertTrue(mts.contains("compressRatioLZ4=0.5720935240387917"));
        assertTrue(mts.contains("Content-Type=application/octet-stream"));

       }
    
    @Test
    public void testIndexerDefaultParserParsingEncryptedDoc() throws IOException, SAXException, TikaException{

        IndexerDefaultParser parser = new IndexerDefaultParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_cryptoDoc.docx");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        
        String mts = metadata.toString();
        assertTrue(mts.contains("X-Parsed-By=org.apache.tika.parser.microsoft.OfficeParser"));
        assertTrue(mts.contains("Indexer-Content-Type=application/x-tika-ooxml-protected"));
        assertTrue(mts.contains("encryptedDocument=true"));
        assertTrue(mts.contains("Content-Type=application/x-tika-ooxml-protected"));
        

       }
    
    @Test
    public void testIndexerDefaultParserParsingPython() throws IOException, SAXException, TikaException{

        IndexerDefaultParser parser = new IndexerDefaultParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_setup.py");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        
        String mts = metadata.toString();
        assertTrue(mts.contains("X-Parsed-By=org.apache.tika.parser.csv.TextAndCSVParser"));
        assertTrue(mts.contains("Indexer-Content-Type=application/x-sh"));
        
       }

}
