package iped.parsers.zoomdpapi;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;

import org.junit.Test;

/**
 * Unit tests for ZoomDetector.
 *
 * @author Calil Khalil (Hakal)
 */
public class ZoomDetectorTest {

    private final ZoomDetector detector = new ZoomDetector();

    @Test
    public void testDetectZoomIni() throws IOException {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "Zoom.us.ini");
        MediaType result = detector.detect(new ByteArrayInputStream(new byte[0]), metadata);
        assertEquals(ZoomDpapiParser.ZOOM_INI, result);
    }

    @Test
    public void testDetectZoomIniCaseInsensitive() throws IOException {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "zoom.us.INI");
        MediaType result = detector.detect(new ByteArrayInputStream(new byte[0]), metadata);
        assertEquals(ZoomDpapiParser.ZOOM_INI, result);
    }

    @Test
    public void testDetectNonZoomFile() throws IOException {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "config.ini");
        MediaType result = detector.detect(new ByteArrayInputStream(new byte[0]), metadata);
        assertEquals(MediaType.OCTET_STREAM, result);
    }

    @Test
    public void testDetectNoName() throws IOException {
        Metadata metadata = new Metadata();
        MediaType result = detector.detect(new ByteArrayInputStream(new byte[0]), metadata);
        assertEquals(MediaType.OCTET_STREAM, result);
    }

    @Test
    public void testDetectFallbackResourceName() throws IOException {
        Metadata metadata = new Metadata();
        metadata.set("resourceName", "Zoom.us.ini");
        MediaType result = detector.detect(new ByteArrayInputStream(new byte[0]), metadata);
        assertEquals(ZoomDpapiParser.ZOOM_INI, result);
    }
}
