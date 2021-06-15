package dpf.inc.sepinf.UsnJrnl;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.sax.ToTextContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.inc.sepinf.UsnJrnl.UsnJrnlParser.ReportType;

public class UsnJrnlParserTest extends AbstractPkgTest {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testUsnJrnlParsingHTML() throws IOException, SAXException, TikaException {

        UsnJrnlParser parser = new UsnJrnlParser();
        InputStream stream = getStream("test-files/test_UsnJrnl.bin");
        ContentHandler handler = new ToTextContentHandler();
        parser.setExtractEntries(true);
        parser.getSupportedTypes(usnContext);
        parser.findNextEntry(stream);
        parser.setReportType(ReportType.HTML);
        parser.parse(stream, handler, metadata, usnContext);
        stream.close();

        assertEquals(3, usntracker.contenttype.size());
        assertEquals(3085, usntracker.title.size());
        assertEquals(3084, usntracker.created.size());

        assertEquals("application/x-usnjournal-report-html", usntracker.contenttype.get(0));
        assertEquals("application/x-usnjournal-registry", usntracker.contenttype.get(2));

        assertEquals("2021-05-20", usntracker.created.get(0).substring(0, 10));
        // assertEquals("2021-05-20 14:52:07.632 -03:00", usntracker.created.get(0));
        // assertEquals("2021-05-20 14:52:07.633 -03:00", usntracker.created.get(1));
        // assertEquals("2021-05-20 14:52:07.633 -03:00", usntracker.created.get(2));
        // assertEquals("2021-05-20 14:52:07.633 -03:00", usntracker.created.get(3));
        // assertEquals("2021-05-20 14:55:03.014 -03:00", usntracker.created.get(3082));
        // assertEquals("2021-05-20 14:55:03.014 -03:00", usntracker.created.get(3083));

        assertEquals("USN Journal Report 1", usntracker.title.get(0));
        assertEquals("USN journal Entry 6098518016", usntracker.title.get(1));
        assertEquals("USN journal Entry 6098518192", usntracker.title.get(2));
        assertEquals("USN journal Entry 6098518360", usntracker.title.get(3));
        assertEquals("USN journal Entry 6098963040", usntracker.title.get(3083));
        assertEquals("USN journal Entry 6098963120", usntracker.title.get(3084));

    }

    @Test
    public void testUsnJrnlParsingCSV() throws IOException, SAXException, TikaException {

        UsnJrnlParser parser = new UsnJrnlParser();
        InputStream stream = getStream("test-files/test_UsnJrnl.bin");
        ContentHandler handler = new ToTextContentHandler();
        parser.setExtractEntries(true);
        parser.getSupportedTypes(usnContext);
        parser.findNextEntry(stream);
        parser.setReportType(ReportType.CSV);
        parser.parse(stream, handler, metadata, usnContext);
        stream.close();

        assertEquals(3, usntracker.contenttype.size());
        assertEquals(3085, usntracker.title.size());
        assertEquals(3084, usntracker.created.size());

        assertEquals("application/x-usnjournal-report-csv", usntracker.contenttype.get(0));
        assertEquals("application/x-usnjournal-registry", usntracker.contenttype.get(2));

        assertEquals("2021-05-20", usntracker.created.get(0).substring(0, 10));
        // assertEquals("2021-05-20 14:52:07.632 -03:00", usntracker.created.get(0));
        // assertEquals("2021-05-20 14:52:07.633 -03:00", usntracker.created.get(1));
        // assertEquals("2021-05-20 14:52:07.633 -03:00", usntracker.created.get(2));
        // assertEquals("2021-05-20 14:52:07.633 -03:00", usntracker.created.get(3));
        // assertEquals("2021-05-20 14:55:03.014 -03:00", usntracker.created.get(3082));
        // assertEquals("2021-05-20 14:55:03.014 -03:00", usntracker.created.get(3083));

        assertEquals("USN Journal Report", usntracker.title.get(0));
        assertEquals("USN journal Entry 6098518016", usntracker.title.get(1));
        assertEquals("USN journal Entry 6098518192", usntracker.title.get(2));
        assertEquals("USN journal Entry 6098518360", usntracker.title.get(3));
        assertEquals("USN journal Entry 6098963040", usntracker.title.get(3083));
        assertEquals("USN journal Entry 6098963120", usntracker.title.get(3084));
    }

}
