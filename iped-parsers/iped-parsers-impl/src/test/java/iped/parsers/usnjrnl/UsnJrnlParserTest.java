package iped.parsers.usnjrnl;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.ToTextContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class UsnJrnlParserTest extends AbstractPkgTest {

    private static int getVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            version = version.substring(2, 3);
        } else {
            int dot = version.indexOf(".");
            if (dot != -1) {
                version = version.substring(0, dot);
            }
        }
        return Integer.parseInt(version);
    }

    @Test
    public void testUsnJrnlParsingHTML() throws IOException, SAXException, TikaException, ParseException {

        String testPath = "test-files/test_UsnJrnl.bin";
        usnContext = getContext(testPath);
        UsnJrnlParser parser = new UsnJrnlParser();
        ContentHandler handler = new ToTextContentHandler();
        parser.setExtractEntries(true);
        parser.getSupportedTypes(usnContext);
        try (InputStream stream = getStream(testPath)) {
            parser.parse(stream, handler, new Metadata(), usnContext);

            int style = DateFormat.MEDIUM;
            DateFormat df;
            df = DateFormat.getDateTimeInstance(style, style, new Locale("pt", "BR"));
            df.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));

            assertEquals(3, usntracker.contenttype.size());
            assertEquals(3085, usntracker.title.size());
            // assertEquals(3084, usntracker.created.size());

            assertEquals("application/x-usnjournal-report-csv", usntracker.contenttype.get(0));
            assertEquals("application/x-usnjournal-registry", usntracker.contenttype.get(2));

            Date date;
            Metadata metadata;
            String[] reasons;

            for (int i = 1; i <= 4; i++) {
                metadata = usntracker.metadata.get(i);
                reasons = metadata.getValues("Reasons");
                for (String reason : reasons) {
                    date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                            .parse(metadata.get(UsnJrnlParser.USN_REASON_PREFIX + ":" + reason));
                    if (getVersion() < 9)
                        assertEquals("20/05/2021 14:52:07", df.format(date));
                    if (getVersion() >= 9 && getVersion() < 12)
                        assertEquals("20 de mai de 2021 14:52:07", df.format(date));
                    if (getVersion() >= 12)
                        assertEquals("20 de mai. de 2021 14:52:07", df.format(date));
                }
            }

            for (int i = 3082; i <= 3083; i++) {
                metadata = usntracker.metadata.get(i);
                reasons = metadata.getValues("Reasons");
                for (String reason : reasons) {
                    date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                            .parse(metadata.get(UsnJrnlParser.USN_REASON_PREFIX + ":" + reason));
                    if (getVersion() < 9)
                        assertEquals("20/05/2021 14:55:03", df.format(date));
                    if (getVersion() >= 9 && getVersion() < 12)
                        assertEquals("20 de mai de 2021 14:55:03", df.format(date));
                    if (getVersion() >= 12)
                        assertEquals("20 de mai. de 2021 14:55:03", df.format(date));
                }
            }

            assertEquals("USN Journal Report", usntracker.title.get(0));
            assertEquals("USN journal Entry 6098518016", usntracker.title.get(1));
            assertEquals("USN journal Entry 6098518192", usntracker.title.get(2));
            assertEquals("USN journal Entry 6098518360", usntracker.title.get(3));
            assertEquals("USN journal Entry 6098963040", usntracker.title.get(3083));
            assertEquals("USN journal Entry 6098963120", usntracker.title.get(3084));

        }

    }

    @Test
    public void testUsnJrnlParsingCSV() throws IOException, SAXException, TikaException, ParseException {

        String testPath = "test-files/test_UsnJrnl.bin";
        usnContext = getContext(testPath);
        UsnJrnlParser parser = new UsnJrnlParser();
        ContentHandler handler = new ToTextContentHandler();
        parser.setExtractEntries(true);
        parser.getSupportedTypes(usnContext);
        try (InputStream stream = getStream(testPath)) {
            parser.parse(stream, handler, new Metadata(), usnContext);

            int style = DateFormat.MEDIUM;
            DateFormat df;
            df = DateFormat.getDateTimeInstance(style, style, new Locale("pt", "BR"));
            df.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));

            assertEquals(3, usntracker.contenttype.size());
            assertEquals(3085, usntracker.title.size());

            Date date;
            Metadata metadata;
            String[] reasons;

            for (int i = 1; i <= 4; i++) {
                metadata = usntracker.metadata.get(i);
                reasons = metadata.getValues("Reasons");
                for (String reason : reasons) {
                    date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                            .parse(metadata.get(UsnJrnlParser.USN_REASON_PREFIX + ":" + reason));
                    if (getVersion() < 9)
                        assertEquals("20/05/2021 14:52:07", df.format(date));
                    if (getVersion() >= 9 && getVersion() < 12)
                        assertEquals("20 de mai de 2021 14:52:07", df.format(date));
                    if (getVersion() >= 12)
                        assertEquals("20 de mai. de 2021 14:52:07", df.format(date));
                }
            }

            for (int i = 3082; i <= 3083; i++) {
                metadata = usntracker.metadata.get(i);
                reasons = metadata.getValues("Reasons");
                for (String reason : reasons) {
                    date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                            .parse(metadata.get(UsnJrnlParser.USN_REASON_PREFIX + ":" + reason));
                    if (getVersion() < 9)
                        assertEquals("20/05/2021 14:55:03", df.format(date));
                    if (getVersion() >= 9 && getVersion() < 12)
                        assertEquals("20 de mai de 2021 14:55:03", df.format(date));
                    if (getVersion() >= 12)
                        assertEquals("20 de mai. de 2021 14:55:03", df.format(date));
                }
            }

            assertEquals("USN Journal Report", usntracker.title.get(0));
            assertEquals("USN journal Entry 6098518016", usntracker.title.get(1));
            assertEquals("USN journal Entry 6098518192", usntracker.title.get(2));
            assertEquals("USN journal Entry 6098518360", usntracker.title.get(3));
            assertEquals("USN journal Entry 6098963040", usntracker.title.get(3083));
            assertEquals("USN journal Entry 6098963120", usntracker.title.get(3084));

        }
    }

}
