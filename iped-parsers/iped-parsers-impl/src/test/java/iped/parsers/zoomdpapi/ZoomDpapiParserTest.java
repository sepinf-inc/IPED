package iped.parsers.zoomdpapi;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;

import org.junit.Test;

/**
 * Unit tests for ZoomDpapiParser and ZoomReportGenerator.
 *
 * @author Calil Khalil (Hakal)
 */
public class ZoomDpapiParserTest {

    // --- MIME type tests ---

    @Test
    public void testSupportedTypes() {
        ZoomDpapiParser parser = new ZoomDpapiParser();
        Set<MediaType> types = parser.getSupportedTypes(new ParseContext());
        assertTrue(types.contains(ZoomDpapiParser.ZOOM_INI));
        assertEquals(1, types.size());
    }

    @Test
    public void testMimeTypeConstants() {
        assertEquals("application/x-zoom-dpapi-ini", ZoomDpapiParser.ZOOM_INI.toString());
        assertEquals("application/x-zoom-meeting", ZoomDpapiParser.ZOOM_MEETING.toString());
        assertEquals("message/x-zoom-message", ZoomDpapiParser.ZOOM_MESSAGE.toString());
    }

    // --- INI parsing tests ---

    @Test
    public void testExtractEncryptedKeyValid() {
        ZoomDpapiParser parser = new ZoomDpapiParser();
        String ini = "[General]\n" +
                "language=en\n" +
                "[ZoomChat]\n" +
                "win_osencrypt_key=ZWOSKEYaGVsbG93b3JsZA==\n" +
                "[Other]\n" +
                "foo=bar\n";
        assertEquals("aGVsbG93b3JsZA==", parser.extractEncryptedKey(ini));
    }

    @Test
    public void testExtractEncryptedKeyNoSection() {
        ZoomDpapiParser parser = new ZoomDpapiParser();
        String ini = "[General]\nwin_osencrypt_key=ZWOSKEYtest\n";
        assertNull(parser.extractEncryptedKey(ini));
    }

    @Test
    public void testExtractEncryptedKeyNoPrefix() {
        ZoomDpapiParser parser = new ZoomDpapiParser();
        String ini = "[ZoomChat]\nwin_osencrypt_key=notprefixed\n";
        assertNull(parser.extractEncryptedKey(ini));
    }

    @Test
    public void testExtractEncryptedKeyEmpty() {
        ZoomDpapiParser parser = new ZoomDpapiParser();
        assertNull(parser.extractEncryptedKey(""));
    }

    @Test
    public void testExtractEncryptedKeyCaseInsensitive() {
        ZoomDpapiParser parser = new ZoomDpapiParser();
        String ini = "[zoomchat]\nWin_Osencrypt_Key=ZWOSKEYdGVzdA==\n";
        assertEquals("dGVzdA==", parser.extractEncryptedKey(ini));
    }

    @Test
    public void testExtractEncryptedKeyWindowsLineEndings() {
        ZoomDpapiParser parser = new ZoomDpapiParser();
        String ini = "[ZoomChat]\r\nwin_osencrypt_key=ZWOSKEYa2V5\r\n[Other]\r\nfoo=bar\r\n";
        assertEquals("a2V5", parser.extractEncryptedKey(ini));
    }

    // --- Report generator tests ---

    @Test
    public void testGenerateMeetingReport() {
        ZoomMeeting meeting = new ZoomMeeting();
        meeting.setTopic("Test Meeting");
        meeting.setMeetingNo("123456");
        meeting.setStartTime(1700000000L);

        ZoomMessage msg = new ZoomMessage();
        msg.setBody("Hello");
        msg.setSenderName("Alice");
        msg.setTimestamp(1700000001L);
        meeting.getMessages().add(msg);

        ZoomUserAccount account = new ZoomUserAccount();
        account.setEmail("user@test.com");
        account.setClientVersion("5.15.0");

        ZoomSystemInfo sysInfo = new ZoomSystemInfo();
        sysInfo.setProcessor("Intel i7");

        ZoomReportGenerator gen = new ZoomReportGenerator();
        byte[] html = gen.generateMeetingReport(
                "S-1-5-21-123", "testOskey", "TestUser",
                account, sysInfo, null, meeting);
        String htmlStr = new String(html);

        assertTrue(htmlStr.contains("1. Decryption Information"));
        assertTrue(htmlStr.contains("S-1-5-21-123"));
        assertTrue(htmlStr.contains("testOskey"));
        assertTrue(htmlStr.contains("2. Zoom Account"));
        assertTrue(htmlStr.contains("user@test.com"));
        assertTrue(htmlStr.contains("5.15.0"));
        assertTrue(htmlStr.contains("Intel i7"));
        assertTrue(htmlStr.contains("4. Meeting Details"));
        assertTrue(htmlStr.contains("Test Meeting"));
        assertTrue(htmlStr.contains("123456"));
        assertTrue(htmlStr.contains("Hello"));
        assertTrue(htmlStr.contains("Alice"));
    }

    @Test
    public void testReportEscaping() {
        assertEquals("&lt;script&gt;", ZoomReportGenerator.esc("<script>"));
        assertEquals("&amp;test", ZoomReportGenerator.esc("&test"));
        assertEquals("-", ZoomReportGenerator.esc(null));
    }
}
