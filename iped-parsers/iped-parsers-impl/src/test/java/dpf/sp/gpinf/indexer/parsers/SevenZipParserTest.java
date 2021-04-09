package dpf.sp.gpinf.indexer.parsers;





import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MimeType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import static org.mockito.Mockito.*;




import java.io.InputStream;



public class SevenZipParserTest  extends AbstractPkgTest {
    

    @Test
    public void testSevenZipRAR4Parsing() throws Exception {
        
        Parser parser = new AutoDetectParser();
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        InputStream stream = SevenZipParserTest.class.getResourceAsStream("/test-files/mockrar4.rar");
        parser.parse(stream, handler, metadata, recursingContext);
        stream.close();

            
        assertEquals("application/x-rar-compressed", metadata.get(Metadata.CONTENT_TYPE));
        String content = handler.toString();
        assertTrue(content.contains("mockdoc3.docx"));
        assertTrue(content.contains("Mockdoc3:"));
        assertTrue(content.contains("mockdoc4.docx"));
        assertTrue(content.contains("Mockdoc4:"));
        assertTrue(content.contains("mockdoc5.docx"));
        assertTrue(content.contains("Mockdoc5:"));
        assertTrue(content.contains("mocksheets1.xls"));
        assertTrue(content.contains("mocksheets1:"));
        assertTrue(content.contains("mocksheets2.xls"));
        assertTrue(content.contains("mocksheets2:"));
        assertTrue(content.contains("mocksheets3.xls"));
        assertTrue(content.contains("mocksheets3:"));
        assertTrue(content.contains("mocksheets4.xls"));
        assertTrue(content.contains("mocksheets4:"));
        assertTrue(content.contains("mocksheets5.xls"));
        assertTrue(content.contains("mocksheets5:"));
        assertTrue(content.contains("mocktext1.txt"));
        assertTrue(content.contains("MOCKTEXT1:"));
        assertTrue(content.contains("mocktext2.txt"));
        assertTrue(content.contains("MOCKTEXT2:"));
        assertTrue(content.contains("mocktext3.txt"));
        assertTrue(content.contains("MOCKTEXT3:"));
        assertTrue(content.contains("mocktext4.txt"));
        assertTrue(content.contains("MOCKTEXT4:"));
        assertTrue(content.contains("mocktext5.txt"));
        assertTrue(content.contains("MOCKTEXT5:"));
        assertTrue(content.contains("mockdoc1.docx"));
        assertTrue(content.contains("Mockdoc1:"));
        assertTrue(content.contains("mockdoc2.docx"));
        assertTrue(content.contains("Mockdoc2:"));
        assertTrue(content.contains("mockfolder/mocktext5.txt"));
        assertTrue(content.contains("Mocktext5/Folder:"));
        assertTrue(content.contains("mockfolder/mockdoc5.docx"));
        assertTrue(content.contains("Mockdoc5/folder"));
        assertTrue(content.contains("mockfolder/mocksheets5.xlsx"));
        assertTrue(content.contains("Mocksheets5/Folder"));
            
            
            }
    
    @Test
    public void testSevenZipRAR4Embedded() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        InputStream stream = SevenZipParserTest.class.getResourceAsStream("/test-files/mockrar4.rar");
        parser.parse(stream, handler, metadata, trackingContext);
        stream.close();
        
        assertEquals(18, tracker.filenames.size());
        assertEquals(18, tracker.mediatypes.size());
        assertEquals("mockdoc3.docx", tracker.filenames.get(0));
        assertEquals("mockdoc4.docx", tracker.filenames.get(1));
        assertEquals("mockdoc5.docx", tracker.filenames.get(2));
        assertEquals("mocksheets1.xlsx", tracker.filenames.get(3));
        assertEquals("mocksheets2.xlsx", tracker.filenames.get(4));
        assertEquals("mocksheets3.xlsx", tracker.filenames.get(5));
        assertEquals("mocksheets4.xlsx", tracker.filenames.get(6));
        assertEquals("mocksheets5.xlsx", tracker.filenames.get(7));
        assertEquals("mocktext1.txt", tracker.filenames.get(8));
        assertEquals("mocktext2.txt", tracker.filenames.get(9));
        assertEquals("mocktext3.txt", tracker.filenames.get(10));
        assertEquals("mocktext4.txt", tracker.filenames.get(11));
        assertEquals("mocktext5.txt", tracker.filenames.get(12));
        assertEquals("mockdoc1.docx", tracker.filenames.get(13));
        assertEquals("mockdoc2.docx", tracker.filenames.get(14));
        assertEquals("mockfolder/mocktext5.txt", tracker.filenames.get(15));
        assertEquals("mockfolder/mockdoc5.docx", tracker.filenames.get(16));
        assertEquals("mockfolder/mocksheets5.xlsx", tracker.filenames.get(17));

        
        for(String type : tracker.mediatypes) {
            assertNull(type);
        }
    }
    


    @Test
    public void testSevenZipRAR5Parsing() throws Exception {
        Parser parser = new SevenZipParser();
        ContentHandler handler = new BodyContentHandler();
        Metadata mockedMetadata = spy(new Metadata());
        InputStream stream = SevenZipParserTest.class.getResourceAsStream("/test-files/mockrar5.rar");
        String mimetype = "application/x-rar-compressed";
        when(mockedMetadata.get(Metadata.CONTENT_TYPE)).thenReturn(mimetype);
        parser.parse(stream, handler, mockedMetadata, recursingContext);
        stream.close();
  
        
        String content = handler.toString();
        assertTrue(content.contains("mockdoc3.docx"));
        assertTrue(content.contains("Mockdoc3:"));
        assertTrue(content.contains("Mockdoc3"));
        assertTrue(content.contains("mockdoc4.docx"));
        assertTrue(content.contains("Mockdoc4:"));
        assertTrue(content.contains("mockdoc5.docx"));
        assertTrue(content.contains("Mockdoc5:"));
        assertTrue(content.contains("mocksheets1.xls"));
        assertTrue(content.contains("mocksheets1:"));
        assertTrue(content.contains("mocksheets2.xls"));
        assertTrue(content.contains("mocksheets2:"));
        assertTrue(content.contains("mocksheets3.xls"));
        assertTrue(content.contains("mocksheets3:"));
        assertTrue(content.contains("mocksheets4.xls"));
        assertTrue(content.contains("mocksheets4:"));
        assertTrue(content.contains("mocksheets5.xls"));
        assertTrue(content.contains("mocksheets5:"));
        assertTrue(content.contains("mocktext1.txt"));
        assertTrue(content.contains("MOCKTEXT1:"));
        assertTrue(content.contains("mocktext2.txt"));
        assertTrue(content.contains("MOCKTEXT2:"));
        assertTrue(content.contains("mocktext3.txt"));
        assertTrue(content.contains("MOCKTEXT3:"));
        assertTrue(content.contains("mocktext4.txt"));
        assertTrue(content.contains("MOCKTEXT4:"));
        assertTrue(content.contains("mocktext5.txt"));
        assertTrue(content.contains("MOCKTEXT5:"));
        assertTrue(content.contains("mockdoc1.docx"));
        assertTrue(content.contains("Mockdoc1:"));
        assertTrue(content.contains("mockdoc2.docx"));
        assertTrue(content.contains("Mockdoc2:"));
        assertTrue(content.contains("mockfolder/mocktext5.txt"));
        assertTrue(content.contains("Mocktext5/Folder:"));
        assertTrue(content.contains("mockfolder/mockdoc5.docx"));
        assertTrue(content.contains("Mockdoc5/folder"));
        assertTrue(content.contains("mockfolder/mocksheets5.xlsx"));
        assertTrue(content.contains("Mocksheets5/Folder"));
        
            
            
        }
    
//    @Test
//    public void testSevenZipRAR5Parsing() throws Exception {
//        Parser parser = new SevenZipParser();
//        ContentHandler handler = new BodyContentHandler();
//        Metadata mockedMetadata = spy(new Metadata());
//        InputStream stream = SevenZipParserTest.class.getResourceAsStream("/test-files/mockrar5.rar");
//        String mimetype = "application/x-rar-compressed";
//        when(mockedMetadata.get(Metadata.CONTENT_TYPE)).thenReturn(mimetype);
//        parser.parse(stream, handler, mockedMetadata, recursingContext);
//        stream.close();
//    }
}


    



