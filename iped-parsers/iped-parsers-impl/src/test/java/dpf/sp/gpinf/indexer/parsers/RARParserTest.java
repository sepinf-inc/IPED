package dpf.sp.gpinf.indexer.parsers;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RARParserTest  extends AbstractPkgTest {
    
    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }
    
    private static int getVersion() {
        String version = System.getProperty("java.version");
        if(version.startsWith("1.")) {
            version = version.substring(2, 3);
        } else {
            int dot = version.indexOf(".");
            if(dot != -1) { version = version.substring(0, dot); }
        } return Integer.parseInt(version);
    }
    
    @Test
    public void testSevenZipRAR4Parsing() throws Exception {
        RARParser parser = new RARParser();
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        InputStream stream = getStream("test-files/test_mockRar4.rar");
        parser.getSupportedTypes(recursingContext);
        parser.parse(stream, handler, metadata, recursingContext);
        stream.close();
            
        String hts = handler.toString();
        assertTrue(hts.contains("mockdoc3.docx"));
        assertTrue(hts.contains("Mockdoc3:"));
        
        assertTrue(hts.contains("mockdoc4.docx"));
        assertTrue(hts.contains("Mockdoc4:"));
        
        assertTrue(hts.contains("mockdoc5.docx"));
        assertTrue(hts.contains("Mockdoc5:"));
        
        assertTrue(hts.contains("mocksheets1.xls"));
        assertTrue(hts.contains("mocksheets1:"));
        
        assertTrue(hts.contains("mocksheets2.xls"));
        assertTrue(hts.contains("mocksheets2:"));
        
        assertTrue(hts.contains("mocksheets3.xls"));
        assertTrue(hts.contains("mocksheets3:"));
        
        assertTrue(hts.contains("mocksheets4.xls"));
        assertTrue(hts.contains("mocksheets4:"));
        
        assertTrue(hts.contains("mocksheets5.xls"));
        assertTrue(hts.contains("mocksheets5:"));
        
        assertTrue(hts.contains("mocktext1.txt"));
        assertTrue(hts.contains("MOCKTEXT1:"));
        
        assertTrue(hts.contains("mocktext2.txt"));
        assertTrue(hts.contains("MOCKTEXT2:"));
        
        assertTrue(hts.contains("mocktext3.txt"));
        assertTrue(hts.contains("MOCKTEXT3:"));
        
        assertTrue(hts.contains("mocktext4.txt"));
        assertTrue(hts.contains("MOCKTEXT4:"));
        
        assertTrue(hts.contains("mocktext5.txt"));
        assertTrue(hts.contains("MOCKTEXT5:"));
        
        assertTrue(hts.contains("mockdoc1.docx"));
        assertTrue(hts.contains("Mockdoc1:"));
        
        assertTrue(hts.contains("mockdoc2.docx"));
        assertTrue(hts.contains("Mockdoc2:"));
        
        assertTrue(hts.contains("mockfolder/mocktext5.txt"));
        assertTrue(hts.contains("Mocktext5/Folder:"));
        
        assertTrue(hts.contains("mockfolder/mockdoc5.docx"));
        assertTrue(hts.contains("Mockdoc5/folder"));
        
        assertTrue(hts.contains("mockfolder/mocksheets5.xlsx"));
        assertTrue(hts.contains("Mocksheets5/Folder"));
        
    }
    
    @Test
    public void testSevenZipRAR4Embedded() throws Exception {
        RARParser parser = new RARParser();
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        InputStream stream = getStream("test-files/test_mockRar4.rar");
        parser.parse(stream, handler, metadata, trackingContext);
        stream.close();
        
        int style = DateFormat.MEDIUM;
        DateFormat df;
        df = DateFormat.getDateTimeInstance(style, style, new Locale("pt", "BR"));

        
        assertEquals(19, tracker.filenames.size());
        assertEquals(19, tracker.modifieddate.size());
        assertEquals(19, tracker.itensmd5.size());
        
        assertEquals("mockfolder", tracker.filenames.get(0));
        Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(0));
        if(getVersion() < 9)
            assertEquals("09/04/2021 11:26:10", df.format(date));
        if(getVersion() >= 9  && getVersion() < 12)
            assertEquals("9 de abr de 2021 11:26:10", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 11:26:10", df.format(date));
        //assertEquals("2021-04-09", tracker.modifieddate.get(0).substring(0,10));
        //assertEquals("11:26:10", tracker.modifieddate.get(0).substring(11,19));
        assertEquals("D41D8CD98F00B204E9800998ECF8427E", tracker.itensmd5.get(0));
        assertEquals("true", tracker.isfolder.get(0));
        
        assertEquals("mockdoc3.docx", tracker.filenames.get(1));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(1));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:25:20", df.format(date));
        if(getVersion() >= 9  && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:25:20", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:25:20", df.format(date));
            
        //assertEquals("2021-04-09", tracker.modifieddate.get(1).substring(0,10));
        //assertEquals("12:25:20", tracker.modifieddate.get(1).substring(11,19));
        assertEquals("11C3ADA476E30311B49CBF32A5425E7D", tracker.itensmd5.get(1));
        assertEquals("false", tracker.isfolder.get(1));
        
        assertEquals("mockdoc4.docx", tracker.filenames.get(2));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(2));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:25:10", df.format(date));
        if(getVersion() >= 9  && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:25:10", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:25:10", df.format(date));
            
        //assertEquals("2021-04-09", tracker.modifieddate.get(2).substring(0,10));
        //assertEquals("12:25:10", tracker.modifieddate.get(2).substring(11,19));
        assertEquals("CC54EC74F7F6E3BD945A3142D5A99F4C", tracker.itensmd5.get(2));
        assertEquals("false", tracker.isfolder.get(2));
        
        assertEquals("mockdoc5.docx", tracker.filenames.get(3));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(3));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:24:58", df.format(date));
        if(getVersion() >= 9  && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:24:58", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:24:58", df.format(date));
            
        //assertEquals("2021-04-09", tracker.modifieddate.get(3).substring(0,10));
        //assertEquals("12:24:58", tracker.modifieddate.get(3).substring(11,19));
        assertEquals("86ECED558D2D9423E4017930D6664B5D", tracker.itensmd5.get(3));
        assertEquals("false", tracker.isfolder.get(3));
        
        assertEquals("mocksheets1.xlsx", tracker.filenames.get(4));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(4));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:24:22", df.format(date));
        if(getVersion() >= 9  && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:24:22", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:24:22", df.format(date));
            
        //assertEquals("2021-04-09", tracker.modifieddate.get(4).substring(0,10));
        //assertEquals("12:24:22", tracker.modifieddate.get(4).substring(11,19));
        assertEquals("03C14E0C639186329CAB1B65054E0AA8", tracker.itensmd5.get(4));
        assertEquals("false", tracker.isfolder.get(4));
        
        assertEquals("mocksheets2.xlsx", tracker.filenames.get(5));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(5));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:24:12", df.format(date));
        if(getVersion() >= 9  && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:24:12", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:24:12", df.format(date));
            
        //assertEquals("2021-04-09", tracker.modifieddate.get(5).substring(0,10));
        //assertEquals("12:24:12", tracker.modifieddate.get(5).substring(11,19));
        assertEquals("0D6FC269C63F593B1F232D20F6E30462", tracker.itensmd5.get(5));
        assertEquals("false", tracker.isfolder.get(5));
        
        assertEquals("mocksheets3.xlsx", tracker.filenames.get(6));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(6));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:24:00", df.format(date));
        if(getVersion() >= 9  && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:24:00", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:24:00", df.format(date));
            
        //assertEquals("2021-04-09", tracker.modifieddate.get(6).substring(0,10));
        //assertEquals("12:24:00", tracker.modifieddate.get(6).substring(11,19));
        assertEquals("70699181ACE6063C5565C1655E5F8661", tracker.itensmd5.get(6));
        assertEquals("false", tracker.isfolder.get(6));
        
        assertEquals("mocksheets4.xlsx", tracker.filenames.get(7));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(7));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:23:48", df.format(date));
        if(getVersion() >= 9  && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:23:48", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:23:48", df.format(date));
            
        //assertEquals("2021-04-09", tracker.modifieddate.get(7).substring(0,10));
        //assertEquals("12:23:48", tracker.modifieddate.get(7).substring(11,19));
        assertEquals("5EB65E9DE8B5C7756101AE7D81CA4A50", tracker.itensmd5.get(7));
        assertEquals("false", tracker.isfolder.get(7));
        
        assertEquals("mocksheets5.xlsx", tracker.filenames.get(8));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(8));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:23:30", df.format(date));
        if(getVersion() >= 9  && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:23:30", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:23:30", df.format(date));
            
        //assertEquals("2021-04-09", tracker.modifieddate.get(8).substring(0,10));
        //assertEquals("12:23:30", tracker.modifieddate.get(8).substring(11,19));
        assertEquals("76EB76CCA2457917186DCF10CC1A2F88", tracker.itensmd5.get(8));
        assertEquals("false", tracker.isfolder.get(8));
        
        assertEquals("mocktext1.txt", tracker.filenames.get(9));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(9));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:23:02", df.format(date));
        if(getVersion() >= 9  && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:23:02", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:23:02", df.format(date));
            
        //assertEquals("2021-04-09", tracker.modifieddate.get(9).substring(0,10));
        //assertEquals("12:23:02", tracker.modifieddate.get(9).substring(11,19));
        assertEquals("F45BC2E86A3CC307776E851072003559", tracker.itensmd5.get(9));
        assertEquals("false", tracker.isfolder.get(9));
        
        assertEquals("mocktext2.txt", tracker.filenames.get(10));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(10));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:22:54", df.format(date));
        if(getVersion() >= 9  && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:22:54", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:22:54", df.format(date));
            
        //assertEquals("2021-04-09", tracker.modifieddate.get(10).substring(0,10));
        //assertEquals("12:22:54", tracker.modifieddate.get(10).substring(11,19));
        assertEquals("C922B74878BA73C51904E75A79B3DF5B", tracker.itensmd5.get(10));
        assertEquals("false", tracker.isfolder.get(10));
        
        assertEquals("mocktext3.txt", tracker.filenames.get(11));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(11));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:22:40", df.format(date));
        if(getVersion() >= 9  && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:22:40", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:22:40", df.format(date));
            
        //assertEquals("2021-04-09", tracker.modifieddate.get(11).substring(0,10));
        //assertEquals("12:22:40", tracker.modifieddate.get(11).substring(11,19));
        assertEquals("C03511CB57B7B5D71D0B0848D26EB6FE", tracker.itensmd5.get(11));
        assertEquals("false", tracker.isfolder.get(11));
        
        assertEquals("mocktext4.txt", tracker.filenames.get(12));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(12));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:22:46", df.format(date));
        if(getVersion() >= 9  && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:22:46", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:22:46", df.format(date));
            
        //assertEquals("2021-04-09", tracker.modifieddate.get(12).substring(0,10));
        //assertEquals("12:22:46", tracker.modifieddate.get(12).substring(11,19));
        assertEquals("C2186D0182395690DC449FB589423050", tracker.itensmd5.get(12));
        assertEquals("false", tracker.isfolder.get(12));
        
        assertEquals("mocktext5.txt", tracker.filenames.get(13));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(13));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:22:06", df.format(date));
        if(getVersion() >= 9  && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:22:06", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:22:06", df.format(date));
            
        //assertEquals("2021-04-09", tracker.modifieddate.get(13).substring(0,10));
        //assertEquals("12:22:06", tracker.modifieddate.get(13).substring(11,19));
        assertEquals("5A16DBA8C69887BE2E0CBA896A0933A2", tracker.itensmd5.get(13));
        assertEquals("false", tracker.isfolder.get(13));
        
        assertEquals("mockdoc1.docx", tracker.filenames.get(14));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(14));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:25:42", df.format(date));
        if(getVersion() >= 9  && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:25:42", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:25:42", df.format(date));
            
        //assertEquals("2021-04-09", tracker.modifieddate.get(14).substring(0,10));
        //assertEquals("12:25:42", tracker.modifieddate.get(14).substring(11,19));
        assertEquals("A8D50FC70640100E628B73138FB8F9BB", tracker.itensmd5.get(14));
        assertEquals("false", tracker.isfolder.get(14));
                
        assertEquals("mockdoc2.docx", tracker.filenames.get(15));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(15));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:25:30", df.format(date));
        if(getVersion() >= 9  && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:25:30", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:25:30", df.format(date));
            
        //assertEquals("2021-04-09", tracker.modifieddate.get(15).substring(0,10));
        //assertEquals("12:25:30", tracker.modifieddate.get(15).substring(11,19));
        assertEquals("62032D28BB6587132BA4ABE786636429", tracker.itensmd5.get(15));
        assertEquals("false", tracker.isfolder.get(15));
        
        assertEquals("mockfolder/mocktext5.txt", tracker.filenames.get(16));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(16));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:26:00", df.format(date));
        if(getVersion() >= 9  && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:26:00", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:26:00", df.format(date));
            
        //assertEquals("2021-04-09", tracker.modifieddate.get(16).substring(0,10));
        //assertEquals("12:26:00", tracker.modifieddate.get(16).substring(11,19));
        assertEquals("6212E7FBA5A8FE2FFF5EEA267D4009BE", tracker.itensmd5.get(16));
        assertEquals("false", tracker.isfolder.get(16));
        
        assertEquals("mockfolder/mockdoc5.docx", tracker.filenames.get(17));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(17));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:26:28", df.format(date));
        if(getVersion() >= 9  && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:26:28", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:26:28", df.format(date));
            
        //assertEquals("2021-04-09", tracker.modifieddate.get(17).substring(0,10));
        //assertEquals("12:26:28", tracker.modifieddate.get(17).substring(11,19));
        assertEquals("6E2B84163C6352908AC4B612B0FDDF1E", tracker.itensmd5.get(17));
        assertEquals("false", tracker.isfolder.get(17));
        
        assertEquals("mockfolder/mocksheets5.xlsx", tracker.filenames.get(18));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(18));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:26:16", df.format(date));
        if(getVersion() >= 9  && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:26:16", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:26:16", df.format(date));
            
        //assertEquals("2021-04-09", tracker.modifieddate.get(18).substring(0,10));
        //assertEquals("12:26:16", tracker.modifieddate.get(18).substring(11,19));
        assertEquals("0E3FD8870A4F85F0975DEBD0C8E24ECF", tracker.itensmd5.get(18));
        assertEquals("false", tracker.isfolder.get(18));
        
    }
    
}


    



