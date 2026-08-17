package iped.parsers.zoomdpapi;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/**
 * Unit tests for ZoomDataExtractor XML parsing, timeline building,
 * and utility methods.
 *
 * @author Calil Khalil (Hakal)
 */
public class ZoomDataExtractorTest {

    private final ZoomDataExtractor extractor = new ZoomDataExtractor();

    // --- XML file parsing tests ---

    @Test
    public void testParseFileFromXmlBasic() {
        String xml = "<msg id=\"msg123\"><meetchat meetid=\"meet456\" meet_sender_user_guid=\"guid789\" " +
                "meet_sender_conf_user_id=\"sender1\" meet_sender_node_id=\"node1\" " +
                "enc_alg=\"2\" kg=\"3\" pmc_file_sync=\"1\" meet_trans_type=\"0\">" +
                "<from n=\"Alice\"/>" +
                "<msg_type>10</msg_type>" +
                "<obj nm=\"report.pdf\" s=\"2048\" id=\"file001\" f=\"1\" k=\"base64key.sha256hash\"/>" +
                "<db key=\"dbkey123\" confId=\"conf001\"/>" +
                "</meetchat></msg>";

        ZoomSharedFile f = extractor.parseFileFromXml(xml, 1700000000L);

        assertNotNull(f);
        assertEquals("report.pdf", f.getFileName());
        assertEquals(2048L, f.getFileSize());
        assertEquals("file001", f.getFileId());
        assertEquals("msg123", f.getMsgId());
        assertEquals("base64key", f.getEncryptionKey());
        assertEquals("sha256hash", f.getFileHash());
        assertEquals("base64key.sha256hash", f.getKAttribute());
        assertEquals("dbkey123", f.getDbKey());
        assertEquals("conf001", f.getConfId());
        assertEquals("meet456", f.getMeetingId());
        assertEquals("Alice", f.getOwnerJid());
        assertEquals("guid789", f.getSenderGuid());
        assertEquals("sender1", f.getSenderId());
        assertEquals("node1", f.getSenderNodeId());
        assertEquals(2, f.getEncryptionAlg());
        assertEquals(3, f.getKeyGeneration());
        assertEquals(1, f.getFileSyncFlag());
        assertEquals(0, f.getTransType());
        assertEquals(1, f.getFileType());
    }

    @Test
    public void testParseFileFromXmlNoObj() {
        String xml = "<msg id=\"msg1\"><meetchat><msg_type>10</msg_type></meetchat></msg>";
        ZoomSharedFile f = extractor.parseFileFromXml(xml, 1700000000L);
        assertNull(f);
    }

    @Test
    public void testParseFileFromXmlNoFileName() {
        String xml = "<msg id=\"msg1\"><obj s=\"100\" id=\"f1\"/></msg>";
        ZoomSharedFile f = extractor.parseFileFromXml(xml, 1700000000L);
        assertNull(f);
    }

    @Test
    public void testParseFileFromXmlTimestampMillis() {
        String xml = "<msg id=\"msg1\"><obj nm=\"test.txt\" s=\"10\" id=\"f1\"/></msg>";
        // timestamp > 9999999999 should be divided by 1000
        ZoomSharedFile f = extractor.parseFileFromXml(xml, 1700000000000L);
        assertNotNull(f);
        assertEquals(1700000000L, f.getTimestamp());
    }

    @Test
    public void testParseFileFromXmlKeyWithoutHash() {
        String xml = "<msg id=\"msg1\"><obj nm=\"test.txt\" s=\"10\" id=\"f1\" k=\"onlykey\"/></msg>";
        ZoomSharedFile f = extractor.parseFileFromXml(xml, 100L);
        assertNotNull(f);
        assertEquals("onlykey", f.getEncryptionKey());
        assertNull(f.getFileHash());
    }

    // --- Utility method tests ---

    @Test
    public void testFormatSize() {
        assertEquals("0 B", ZoomDataExtractor.formatSize(0));
        assertEquals("500 B", ZoomDataExtractor.formatSize(500));
        assertEquals("1.0 KB", ZoomDataExtractor.formatSize(1024));
        assertEquals("1.5 KB", ZoomDataExtractor.formatSize(1536));
        assertEquals("1.0 MB", ZoomDataExtractor.formatSize(1048576));
        assertEquals("2.5 MB", ZoomDataExtractor.formatSize(2621440));
    }

    @Test
    public void testGetAvatarColor() {
        String color0 = ZoomDataExtractor.getAvatarColor(0);
        String color1 = ZoomDataExtractor.getAvatarColor(1);
        assertNotNull(color0);
        assertNotNull(color1);
        assertNotEquals(color0, color1);
        // Should wrap around after 8 colors
        assertEquals(color0, ZoomDataExtractor.getAvatarColor(8));
    }

    // --- Timeline ordering test ---

    @Test
    public void testTimelineEventSorting() {
        ZoomTimelineEvent e1 = new ZoomTimelineEvent();
        e1.setTimestamp(300L);
        e1.setType("message");

        ZoomTimelineEvent e2 = new ZoomTimelineEvent();
        e2.setTimestamp(100L);
        e2.setType("waiting_room");

        ZoomTimelineEvent e3 = new ZoomTimelineEvent();
        e3.setTimestamp(200L);
        e3.setType("avatar");

        List<ZoomTimelineEvent> timeline = new ArrayList<>();
        timeline.add(e1);
        timeline.add(e2);
        timeline.add(e3);
        Collections.sort(timeline);

        assertEquals("waiting_room", timeline.get(0).getType());
        assertEquals("avatar", timeline.get(1).getType());
        assertEquals("message", timeline.get(2).getType());
    }
}
