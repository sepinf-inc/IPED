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
        assertEquals("D41D8CD98F00B204E9800998ECF8427E", tracker.itensmd5.get(0));
        assertEquals("true", tracker.isfolder.get(0));
        
        assertEquals("mockdoc3.docx", tracker.filenames.get(1));
        assertEquals("2021-04-09", tracker.modifieddate.get(1).substring(0,10));
        assertEquals("12:25:20", tracker.modifieddate.get(1).substring(11,19));
        assertEquals("11C3ADA476E30311B49CBF32A5425E7D", tracker.itensmd5.get(1));
        assertEquals("false", tracker.isfolder.get(1));
        
        assertEquals("mockdoc4.docx", tracker.filenames.get(2));
        assertEquals("2021-04-09", tracker.modifieddate.get(2).substring(0,10));
        assertEquals("12:25:10", tracker.modifieddate.get(2).substring(11,19));
        assertEquals("CC54EC74F7F6E3BD945A3142D5A99F4C", tracker.itensmd5.get(2));
        assertEquals("false", tracker.isfolder.get(2));
        
        assertEquals("mockdoc5.docx", tracker.filenames.get(3));
        assertEquals("2021-04-09", tracker.modifieddate.get(3).substring(0,10));
        assertEquals("12:24:58", tracker.modifieddate.get(3).substring(11,19));
        assertEquals("86ECED558D2D9423E4017930D6664B5D", tracker.itensmd5.get(3));
        assertEquals("false", tracker.isfolder.get(3));
        
        assertEquals("mocksheets1.xlsx", tracker.filenames.get(4));
        assertEquals("2021-04-09", tracker.modifieddate.get(4).substring(0,10));
        assertEquals("12:24:22", tracker.modifieddate.get(4).substring(11,19));
        assertEquals("03C14E0C639186329CAB1B65054E0AA8", tracker.itensmd5.get(4));
        assertEquals("false", tracker.isfolder.get(4));
        
        assertEquals("mocksheets2.xlsx", tracker.filenames.get(5));
        assertEquals("2021-04-09", tracker.modifieddate.get(5).substring(0,10));
        assertEquals("12:24:12", tracker.modifieddate.get(5).substring(11,19));
        assertEquals("0D6FC269C63F593B1F232D20F6E30462", tracker.itensmd5.get(5));
        assertEquals("false", tracker.isfolder.get(5));
        
        assertEquals("mocksheets3.xlsx", tracker.filenames.get(6));
        assertEquals("2021-04-09", tracker.modifieddate.get(6).substring(0,10));
        assertEquals("12:24:01", tracker.modifieddate.get(6).substring(11,19));
        assertEquals("70699181ACE6063C5565C1655E5F8661", tracker.itensmd5.get(6));
        assertEquals("false", tracker.isfolder.get(6));
        
        assertEquals("mocksheets4.xlsx", tracker.filenames.get(7));
        assertEquals("2021-04-09", tracker.modifieddate.get(7).substring(0,10));
        assertEquals("12:23:49", tracker.modifieddate.get(7).substring(11,19));
        assertEquals("5EB65E9DE8B5C7756101AE7D81CA4A50", tracker.itensmd5.get(7));
        assertEquals("false", tracker.isfolder.get(7));
        
        assertEquals("mocksheets5.xlsx", tracker.filenames.get(8));
        assertEquals("2021-04-09", tracker.modifieddate.get(8).substring(0,10));
        assertEquals("12:23:30", tracker.modifieddate.get(8).substring(11,19));
        assertEquals("76EB76CCA2457917186DCF10CC1A2F88", tracker.itensmd5.get(8));
        assertEquals("false", tracker.isfolder.get(8));
        
        assertEquals("mocktext1.txt", tracker.filenames.get(9));
        assertEquals("2021-04-09", tracker.modifieddate.get(9).substring(0,10));
        assertEquals("12:23:02", tracker.modifieddate.get(9).substring(11,19));
        assertEquals("F45BC2E86A3CC307776E851072003559", tracker.itensmd5.get(9));
        assertEquals("false", tracker.isfolder.get(9));

        assertEquals("mocktext2.txt", tracker.filenames.get(10));
        assertEquals("2021-04-09", tracker.modifieddate.get(10).substring(0,10));
        assertEquals("12:22:54", tracker.modifieddate.get(10).substring(11,19));
        assertEquals("C922B74878BA73C51904E75A79B3DF5B", tracker.itensmd5.get(10));
        assertEquals("false", tracker.isfolder.get(10));
        
        assertEquals("mocktext3.txt", tracker.filenames.get(11));
        assertEquals("2021-04-09", tracker.modifieddate.get(11).substring(0,10));
        assertEquals("12:22:41", tracker.modifieddate.get(11).substring(11,19));
        assertEquals("C03511CB57B7B5D71D0B0848D26EB6FE", tracker.itensmd5.get(11));
        assertEquals("false", tracker.isfolder.get(11));

        assertEquals("mocktext4.txt", tracker.filenames.get(12));
        assertEquals("2021-04-09", tracker.modifieddate.get(12).substring(0,10));
        assertEquals("12:22:47", tracker.modifieddate.get(12).substring(11,19));
        assertEquals("C2186D0182395690DC449FB589423050", tracker.itensmd5.get(12));
        assertEquals("false", tracker.isfolder.get(12));
        
        assertEquals("mocktext5.txt", tracker.filenames.get(13));
        assertEquals("2021-04-09", tracker.modifieddate.get(13).substring(0,10));
        assertEquals("12:22:06", tracker.modifieddate.get(13).substring(11,19));
        assertEquals("5A16DBA8C69887BE2E0CBA896A0933A2", tracker.itensmd5.get(13));
        assertEquals("false", tracker.isfolder.get(13));

        assertEquals("mockfolder/mockdoc5.docx", tracker.filenames.get(14));
        assertEquals("2021-04-09", tracker.modifieddate.get(14).substring(0,10));
        assertEquals("12:26:28", tracker.modifieddate.get(14).substring(11,19));
        assertEquals("6E2B84163C6352908AC4B612B0FDDF1E", tracker.itensmd5.get(14));
        assertEquals("false", tracker.isfolder.get(14));

        assertEquals("mockfolder/mocksheets5.xlsx", tracker.filenames.get(15));
        assertEquals("2021-04-09", tracker.modifieddate.get(15).substring(0,10));
        assertEquals("12:26:17", tracker.modifieddate.get(15).substring(11,19));
        assertEquals("0E3FD8870A4F85F0975DEBD0C8E24ECF", tracker.itensmd5.get(15));
        assertEquals("false", tracker.isfolder.get(15));

        assertEquals("mockfolder/mocktext5.txt", tracker.filenames.get(16));
        assertEquals("2021-04-09", tracker.modifieddate.get(16).substring(0,10));
        assertEquals("12:26:01", tracker.modifieddate.get(16).substring(11,19));
        assertEquals("6212E7FBA5A8FE2FFF5EEA267D4009BE", tracker.itensmd5.get(16));
        assertEquals("false", tracker.isfolder.get(16));

        assertEquals("mockdoc1.docx", tracker.filenames.get(17));
        assertEquals("2021-04-09", tracker.modifieddate.get(17).substring(0,10));
        assertEquals("12:25:42", tracker.modifieddate.get(17).substring(11,19));
        assertEquals("A8D50FC70640100E628B73138FB8F9BB", tracker.itensmd5.get(17));
        assertEquals("false", tracker.isfolder.get(17));

        assertEquals("mockdoc2.docx", tracker.filenames.get(18));
        assertEquals("2021-04-09", tracker.modifieddate.get(18).substring(0,10));
        assertEquals("12:25:30", tracker.modifieddate.get(18).substring(11,19));
        assertEquals("62032D28BB6587132BA4ABE786636429", tracker.itensmd5.get(18));
        assertEquals("false", tracker.isfolder.get(18));
    }
}


    



