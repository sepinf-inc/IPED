package dpf.sp.gpinf.indexer.parsers;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.InputStream;


public class SevenZipParserTest  extends AbstractPkgTest {

    @Test
    public void testSevenZipRAR5Parsing() throws Exception {
        SevenZipParser parser = new SevenZipParser();
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
    public void testSevenZipRAR5Embedded() throws Exception {
        SevenZipParser parser = new SevenZipParser();
        ContentHandler handler = new BodyContentHandler();
        Metadata mockedMetadata = spy(new Metadata());
        InputStream stream = SevenZipParserTest.class.getResourceAsStream("/test-files/mockrar5.rar");
        String mimetype = "application/x-rar-compressed";
        when(mockedMetadata.get(Metadata.CONTENT_TYPE)).thenReturn(mimetype);
        parser.parse(stream, handler, mockedMetadata, trackingContext);
        stream.close();
        
        assertEquals(19, tracker.filenames.size());
        assertEquals(19, tracker.modifieddate.size());
        assertEquals(19, tracker.itensmd5.size());
        
        assertEquals("mockfolder", tracker.filenames.get(0));
        assertEquals("2021-04-09", tracker.modifieddate.get(0).substring(0,10));
        assertEquals("13:19:21", tracker.modifieddate.get(0).substring(11,19));
        assertEquals("0", tracker.itensmd5.get(0));
        
        assertEquals("mockdoc3.docx", tracker.filenames.get(1));
        assertEquals("2021-04-09", tracker.modifieddate.get(1).substring(0,10));
        assertEquals("12:25:20", tracker.modifieddate.get(1).substring(11,19));
        assertEquals("e6e28cf4", tracker.itensmd5.get(1));
        
        assertEquals("mockdoc4.docx", tracker.filenames.get(2));
        assertEquals("2021-04-09", tracker.modifieddate.get(2).substring(0,10));
        assertEquals("12:25:10", tracker.modifieddate.get(2).substring(11,19));
        assertEquals("d91c45ae", tracker.itensmd5.get(2));
        
        assertEquals("mockdoc5.docx", tracker.filenames.get(3));
        assertEquals("2021-04-09", tracker.modifieddate.get(3).substring(0,10));
        assertEquals("12:24:58", tracker.modifieddate.get(3).substring(11,19));
        assertEquals("db2394d4", tracker.itensmd5.get(3));
        
        assertEquals("mocksheets1.xlsx", tracker.filenames.get(4));
        assertEquals("2021-04-09", tracker.modifieddate.get(4).substring(0,10));
        assertEquals("12:24:22", tracker.modifieddate.get(4).substring(11,19));
        assertEquals("4c0168d7", tracker.itensmd5.get(4));
        
        assertEquals("mocksheets2.xlsx", tracker.filenames.get(5));
        assertEquals("2021-04-09", tracker.modifieddate.get(5).substring(0,10));
        assertEquals("12:24:12", tracker.modifieddate.get(5).substring(11,19));
        assertEquals("c25ae1a9", tracker.itensmd5.get(5));
        
        assertEquals("mocksheets3.xlsx", tracker.filenames.get(6));
        assertEquals("2021-04-09", tracker.modifieddate.get(6).substring(0,10));
        assertEquals("12:24:01", tracker.modifieddate.get(6).substring(11,19));
        assertEquals("1f932abc", tracker.itensmd5.get(6));
        
        assertEquals("mocksheets4.xlsx", tracker.filenames.get(7));
        assertEquals("2021-04-09", tracker.modifieddate.get(7).substring(0,10));
        assertEquals("12:23:49", tracker.modifieddate.get(7).substring(11,19));
        assertEquals("a56f169c", tracker.itensmd5.get(7));

        assertEquals("mocksheets5.xlsx", tracker.filenames.get(8));
        assertEquals("2021-04-09", tracker.modifieddate.get(8).substring(0,10));
        assertEquals("12:23:30", tracker.modifieddate.get(8).substring(11,19));
        assertEquals("8cc6de1", tracker.itensmd5.get(8));
        
        assertEquals("mocktext1.txt", tracker.filenames.get(9));
        assertEquals("2021-04-09", tracker.modifieddate.get(9).substring(0,10));
        assertEquals("12:23:02", tracker.modifieddate.get(9).substring(11,19));
        assertEquals("ff8688ea", tracker.itensmd5.get(9));

        assertEquals("mocktext2.txt", tracker.filenames.get(10));
        assertEquals("2021-04-09", tracker.modifieddate.get(10).substring(0,10));
        assertEquals("12:22:54", tracker.modifieddate.get(10).substring(11,19));
        assertEquals("fae81ade", tracker.itensmd5.get(10));
        
        assertEquals("mocktext3.txt", tracker.filenames.get(11));
        assertEquals("2021-04-09", tracker.modifieddate.get(11).substring(0,10));
        assertEquals("12:22:41", tracker.modifieddate.get(11).substring(11,19));
        assertEquals("b01112cd", tracker.itensmd5.get(11));

        assertEquals("mocktext4.txt", tracker.filenames.get(12));
        assertEquals("2021-04-09", tracker.modifieddate.get(12).substring(0,10));
        assertEquals("12:22:47", tracker.modifieddate.get(12).substring(11,19));
        assertEquals("31f39960", tracker.itensmd5.get(12));
        
        assertEquals("mocktext5.txt", tracker.filenames.get(13));
        assertEquals("2021-04-09", tracker.modifieddate.get(13).substring(0,10));
        assertEquals("12:22:06", tracker.modifieddate.get(13).substring(11,19));
        assertEquals("498cc66c", tracker.itensmd5.get(13));

        assertEquals("mockfolder/mockdoc5.docx", tracker.filenames.get(14));
        assertEquals("2021-04-09", tracker.modifieddate.get(14).substring(0,10));
        assertEquals("12:26:28", tracker.modifieddate.get(14).substring(11,19));
        assertEquals("9ac0054b", tracker.itensmd5.get(14));

        assertEquals("mockfolder/mocksheets5.xlsx", tracker.filenames.get(15));
        assertEquals("2021-04-09", tracker.modifieddate.get(15).substring(0,10));
        assertEquals("12:26:17", tracker.modifieddate.get(15).substring(11,19));
        assertEquals("4c1a44e1", tracker.itensmd5.get(15));

        assertEquals("mockfolder/mocktext5.txt", tracker.filenames.get(16));
        assertEquals("2021-04-09", tracker.modifieddate.get(16).substring(0,10));
        assertEquals("12:26:01", tracker.modifieddate.get(16).substring(11,19));
        assertEquals("79d4fb4f", tracker.itensmd5.get(16));

        assertEquals("mockdoc1.docx", tracker.filenames.get(17));
        assertEquals("2021-04-09", tracker.modifieddate.get(17).substring(0,10));
        assertEquals("12:25:42", tracker.modifieddate.get(17).substring(11,19));
        assertEquals("2411fabd", tracker.itensmd5.get(17));

        assertEquals("mockdoc2.docx", tracker.filenames.get(18));
        assertEquals("2021-04-09", tracker.modifieddate.get(18).substring(0,10));
        assertEquals("12:25:30", tracker.modifieddate.get(18).substring(11,19));
        assertEquals("14dd8ace", tracker.itensmd5.get(18));
    }
}


    



