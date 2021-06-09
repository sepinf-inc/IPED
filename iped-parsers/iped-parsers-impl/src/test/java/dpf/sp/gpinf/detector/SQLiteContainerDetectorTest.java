package dpf.sp.gpinf.detector;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import junit.framework.TestCase;


public class SQLiteContainerDetectorTest extends TestCase{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
   

    @Test
    public void testSQLiteContainerDetectorGlobalDB() throws IOException, SAXException, TikaException{

       SQLiteContainerDetector detector = new SQLiteContainerDetector();
       Metadata metadata = new Metadata();
       InputStream stream = getStream("test-files/test_global.db");
       TikaInputStream tis = TikaInputStream.get(stream);
       MediaType assertion = detector.detect(tis, metadata);
       assertEquals(assertion.toString(), "application/x-gdrive-account-info");
    }
    
    @Test
    public void testSQLiteContainerDetectorActivitiesCache() throws IOException, SAXException, TikaException{

       SQLiteContainerDetector detector = new SQLiteContainerDetector();
       Metadata metadata = new Metadata();
       InputStream stream = getStream("test-files/test_activitiesCache.db");
       TikaInputStream tis = TikaInputStream.get(stream);
       MediaType assertion = detector.detect(tis, metadata);
       assertEquals(assertion.toString(), "application/x-win10-timeline");
    }
    
    @Test
    public void testSQLiteContainerDetectorSkypeMain() throws IOException, SAXException, TikaException{

       SQLiteContainerDetector detector = new SQLiteContainerDetector();
       Metadata metadata = new Metadata();
       InputStream stream = getStream("test-files/test_skypeMain.db");
       TikaInputStream tis = TikaInputStream.get(stream);
       MediaType assertion = detector.detect(tis, metadata);
       assertEquals(assertion.toString(), "application/sqlite-skype");
    }
    
    @Test
    public void testSQLiteContainerDetectorSkype() throws IOException, SAXException, TikaException{

       SQLiteContainerDetector detector = new SQLiteContainerDetector();
       Metadata metadata = new Metadata();
       InputStream stream = getStream("test-files/test_skypeS4lStreeguil1.db");
       TikaInputStream tis = TikaInputStream.get(stream);
       MediaType assertion = detector.detect(tis, metadata);
       assertEquals(assertion.toString(), "application/sqlite-skype-v12");
    }
    
    @Test
    public void testSQLiteContainerDetectorCloudGraph() throws IOException, SAXException, TikaException{

       SQLiteContainerDetector detector = new SQLiteContainerDetector();
       Metadata metadata = new Metadata();
       InputStream stream = getStream("test-files/test_cloudGraph.db");
       TikaInputStream tis = TikaInputStream.get(stream);
       MediaType assertion = detector.detect(tis, metadata);
       assertEquals(assertion.toString(), "application/x-gdrive-cloud-graph");
    }
    
    @Test
    public void testSQLiteContainerDetectorChrome() throws IOException, SAXException, TikaException{

       SQLiteContainerDetector detector = new SQLiteContainerDetector();
       Metadata metadata = new Metadata();
       InputStream stream = getStream("test-files/test_historyChrome");
       TikaInputStream tis = TikaInputStream.get(stream);
       MediaType assertion = detector.detect(tis, metadata);
       assertEquals(assertion.toString(), "application/x-chrome-sqlite");
    }
}
