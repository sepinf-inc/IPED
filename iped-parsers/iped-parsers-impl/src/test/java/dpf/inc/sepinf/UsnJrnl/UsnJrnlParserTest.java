package dpf.inc.sepinf.UsnJrnl;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.sax.ToTextContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import dpf.inc.sepinf.UsnJrnl.UsnJrnlParser.ReportType;

public class UsnJrnlParserTest extends AbstractPkgTest {

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
    public void testUsnJrnlParsingHTML() throws IOException, SAXException, TikaException, ParseException {

        UsnJrnlParser parser = new UsnJrnlParser();
        InputStream stream = getStream("test-files/test_UsnJrnl.bin");
        ContentHandler handler = new ToTextContentHandler();
        parser.setExtractEntries(true);
        parser.getSupportedTypes(usnContext);
        parser.parse(stream, handler, metadata, usnContext);
        stream.close();
        
        int style = DateFormat.MEDIUM;
        DateFormat df;
        df = DateFormat.getDateTimeInstance(style, style, new Locale("pt", "BR"));
        

        assertEquals(3, usntracker.contenttype.size());
        assertEquals(3085, usntracker.title.size());
        assertEquals(3084, usntracker.created.size());

        assertEquals("application/x-usnjournal-report-html", usntracker.contenttype.get(0));
        assertEquals("application/x-usnjournal-registry", usntracker.contenttype.get(2));

        Date date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(usntracker.created.get(0));
        if(getVersion() < 9)
            assertEquals("20/05/2021 14:52:07", df.format(date));
        if(getVersion() >= 9  && getVersion() < 12)
            assertEquals("20 de mai de 2021 14:52:07", df.format(date));
        if(getVersion() >= 12)
            assertEquals("20 de mai. de 2021 14:52:07", df.format(date));
        date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(usntracker.created.get(1));
        if(getVersion() < 9)
            assertEquals("20/05/2021 14:52:07", df.format(date));
        if(getVersion() >= 9  && getVersion() < 12)
            assertEquals("20 de mai de 2021 14:52:07", df.format(date));
        if(getVersion() >= 12)

        date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(usntracker.created.get(2));
        if(getVersion() < 9)
            assertEquals("20/05/2021 14:52:07", df.format(date));
        if(getVersion() >= 9  && getVersion() < 12)
            assertEquals("20 de mai de 2021 14:52:07", df.format(date));
        if(getVersion() >= 12)
            assertEquals("20 de mai. de 2021 14:52:07", df.format(date));

        date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(usntracker.created.get(3));
        if(getVersion() < 9)
            assertEquals("20/05/2021 14:52:07", df.format(date));
        if(getVersion() >= 9  && getVersion() < 12)
            assertEquals("20 de mai de 2021 14:52:07", df.format(date));
        if(getVersion() >= 12)
            assertEquals("20 de mai. de 2021 14:52:07", df.format(date));

        date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(usntracker.created.get(3082));
        if(getVersion() < 9)
            assertEquals("20/05/2021 14:55:03", df.format(date));
        if(getVersion() >= 9  && getVersion() < 12)
            assertEquals("20 de mai de 2021 14:55:03", df.format(date));
        if(getVersion() >= 12)
            assertEquals("20 de mai. de 2021 14:55:03", df.format(date));

        date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(usntracker.created.get(3083));
        if(getVersion() < 9)
            assertEquals("20/05/2021 14:55:03", df.format(date));
        if(getVersion() >= 9  && getVersion() < 12)
            assertEquals("20 de mai de 2021 14:55:03", df.format(date));
        if(getVersion() >= 12)
            assertEquals("20 de mai. de 2021 14:55:03", df.format(date));


        assertEquals("USN Journal Report 1", usntracker.title.get(0));
        assertEquals("USN journal Entry 6098518016", usntracker.title.get(1));
        assertEquals("USN journal Entry 6098518192", usntracker.title.get(2));
        assertEquals("USN journal Entry 6098518360", usntracker.title.get(3));
        assertEquals("USN journal Entry 6098963040", usntracker.title.get(3083));
        assertEquals("USN journal Entry 6098963120", usntracker.title.get(3084));

    }

    @Test
    public void testUsnJrnlParsingCSV() throws IOException, SAXException, TikaException, ParseException {

        UsnJrnlParser parser = new UsnJrnlParser();
        InputStream stream = getStream("test-files/test_UsnJrnl.bin");
        ContentHandler handler = new ToTextContentHandler();
        parser.setExtractEntries(true);
        parser.getSupportedTypes(usnContext);
        parser.parse(stream, handler, metadata, usnContext);
        stream.close();
        
        int style = DateFormat.MEDIUM;
        DateFormat df;
        df = DateFormat.getDateTimeInstance(style, style, new Locale("pt", "BR"));
        

        assertEquals(3, usntracker.contenttype.size());
        assertEquals(3085, usntracker.title.size());
        assertEquals(3084, usntracker.created.size());

        assertEquals("application/x-usnjournal-report-csv", usntracker.contenttype.get(0));
        assertEquals("application/x-usnjournal-registry", usntracker.contenttype.get(2));

        Date date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(usntracker.created.get(0));
        if(getVersion() < 9)
            assertEquals("20/05/2021 14:52:07", df.format(date));
        if(getVersion() >= 9  && getVersion() < 12)
            assertEquals("20 de mai de 2021 14:52:07", df.format(date));
        if(getVersion() >= 12)
            assertEquals("20 de mai. de 2021 14:52:07", df.format(date));

        date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(usntracker.created.get(1));
        if(getVersion() < 9)
            assertEquals("20/05/2021 14:52:07", df.format(date));
        if(getVersion() >= 9  && getVersion() < 12)
            assertEquals("20 de mai de 2021 14:52:07", df.format(date));
        if(getVersion() >= 12)
            assertEquals("20 de mai. de 2021 14:52:07", df.format(date));

        date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(usntracker.created.get(2));
        if(getVersion() < 9)
            assertEquals("20/05/2021 14:52:07", df.format(date));
        if(getVersion() >= 9  && getVersion() < 12)
            assertEquals("20 de mai de 2021 14:52:07", df.format(date));
        if(getVersion() >= 12)
            assertEquals("20 de mai. de 2021 14:52:07", df.format(date));

        date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(usntracker.created.get(3));
        if(getVersion() < 9)
            assertEquals("20/05/2021 14:52:07", df.format(date));
        if(getVersion() >= 9  && getVersion() < 12)
            assertEquals("20 de mai de 2021 14:52:07", df.format(date));
        if(getVersion() >= 12)
            assertEquals("20 de mai. de 2021 14:52:07", df.format(date));

        date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(usntracker.created.get(3082));
        if(getVersion() < 9)
            assertEquals("20/05/2021 14:55:03", df.format(date));
        if(getVersion() >= 9  && getVersion() < 12)
            assertEquals("20 de mai de 2021 14:55:03", df.format(date));
        if(getVersion() >= 12)
            assertEquals("20 de mai. de 2021 14:55:03", df.format(date));

        date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(usntracker.created.get(3083));
        if(getVersion() < 9)
            assertEquals("20/05/2021 14:55:03", df.format(date));
        if(getVersion() >= 9  && getVersion() < 12)
            assertEquals("20 de mai de 2021 14:55:03", df.format(date));
        if(getVersion() >= 12)
            assertEquals("20 de mai. de 2021 14:55:03", df.format(date));

        assertEquals("USN Journal Report", usntracker.title.get(0));
        assertEquals("USN journal Entry 6098518016", usntracker.title.get(1));
        assertEquals("USN journal Entry 6098518192", usntracker.title.get(2));
        assertEquals("USN journal Entry 6098518360", usntracker.title.get(3));
        assertEquals("USN journal Entry 6098963040", usntracker.title.get(3083));
        assertEquals("USN journal Entry 6098963120", usntracker.title.get(3084));
    }

}
