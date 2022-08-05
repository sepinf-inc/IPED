package iped.parsers.sqlite.detector;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.junit.Test;
import org.xml.sax.SAXException;

import iped.parsers.browsers.chrome.ChromeSqliteParser;
import iped.parsers.gdrive.GDriveCloudGraphParser;
import iped.parsers.gdrive.GDriveMainParser;
import iped.parsers.skype.SkypeParser;
import iped.parsers.whatsapp.WhatsAppParser;
import iped.parsers.winx.WinXTimelineParser;
import junit.framework.TestCase;

public class SQLiteContainerDetectorTest extends TestCase {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testSQLiteContainerDetectorGlobalDB() throws IOException, SAXException, TikaException {

        SQLiteContainerDetector detector = new SQLiteContainerDetector();
        Metadata metadata = new Metadata();
        try (InputStream stream = getStream("test-files/test_global.db")) {
            TikaInputStream tis = TikaInputStream.get(stream);
            MediaType assertion = detector.detect(tis, metadata);
            assertEquals(assertion.toString(), GDriveMainParser.GDRIVE_ACCOUNT_INFO.toString());

        }
    }

    @Test
    public void testSQLiteContainerDetectorActivitiesCache() throws IOException, SAXException, TikaException {

        SQLiteContainerDetector detector = new SQLiteContainerDetector();
        Metadata metadata = new Metadata();
        try (InputStream stream = getStream("test-files/test_activitiesCache.db")) {
            TikaInputStream tis = TikaInputStream.get(stream);
            MediaType assertion = detector.detect(tis, metadata);
            assertEquals(assertion.toString(), WinXTimelineParser.WIN10_TIMELINE.toString());

        }
    }

    @Test
    public void testSQLiteContainerDetectorSkypeMain() throws IOException, SAXException, TikaException {

        SQLiteContainerDetector detector = new SQLiteContainerDetector();
        Metadata metadata = new Metadata();
        try (InputStream stream = getStream("test-files/test_skypeMain.db")) {

            TikaInputStream tis = TikaInputStream.get(stream);
            MediaType assertion = detector.detect(tis, metadata);
            assertEquals(assertion.toString(), SkypeParser.SKYPE_MIME.toString());

        }
    }

    @Test
    public void testSQLiteContainerDetectorSkype() throws IOException, SAXException, TikaException {

        SQLiteContainerDetector detector = new SQLiteContainerDetector();
        Metadata metadata = new Metadata();
        try (InputStream stream = getStream("test-files/test_skypeS4lStreeguil1.db")) {

            TikaInputStream tis = TikaInputStream.get(stream);
            MediaType assertion = detector.detect(tis, metadata);
            assertEquals(assertion.toString(), SkypeParser.SKYPE_MIME_V12.toString());

        }
    }

    @Test
    public void testSQLiteContainerDetectorCloudGraph() throws IOException, SAXException, TikaException {

        SQLiteContainerDetector detector = new SQLiteContainerDetector();
        Metadata metadata = new Metadata();
        try (InputStream stream = getStream("test-files/test_cloudGraph.db")) {
            TikaInputStream tis = TikaInputStream.get(stream);
            MediaType assertion = detector.detect(tis, metadata);
            assertEquals(assertion.toString(), GDriveCloudGraphParser.GDRIVE_CLOUD_GRAPH.toString());

        }
    }

    @Test
    public void testSQLiteContainerDetectorChrome() throws IOException, SAXException, TikaException {

        SQLiteContainerDetector detector = new SQLiteContainerDetector();
        Metadata metadata = new Metadata();
        try (InputStream stream = getStream("test-files/test_historyChrome")) {
            TikaInputStream tis = TikaInputStream.get(stream);
            MediaType assertion = detector.detect(tis, metadata);
            assertEquals(assertion.toString(), ChromeSqliteParser.CHROME_SQLITE.toString());

        }
    }

    @Test
    public void testSQLiteContainerDetectorGDriveSnapshot() throws IOException, SAXException, TikaException {

        SQLiteContainerDetector detector = new SQLiteContainerDetector();
        Metadata metadata = new Metadata();
        try (InputStream stream = getStream("test-files/test_snapshot.db")) {
            TikaInputStream tis = TikaInputStream.get(stream);
            MediaType assertion = detector.detect(tis, metadata);
            assertEquals(assertion.toString(), GDriveMainParser.GDRIVE_SNAPSHOT.toString());
        }
    }

    @Test
    public void testSQLiteContainerDetectorWhatsappMsgStore() throws IOException, SAXException, TikaException {

        SQLiteContainerDetector detector = new SQLiteContainerDetector();
        Metadata metadata = new Metadata();
        try (InputStream stream = getStream("test-files/test_whatsAppMsgStore.db")) {
            TikaInputStream tis = TikaInputStream.get(stream);
            MediaType assertion = detector.detect(tis, metadata);
            assertEquals(assertion.toString(), WhatsAppParser.MSG_STORE.toString());
        }
    }
}
