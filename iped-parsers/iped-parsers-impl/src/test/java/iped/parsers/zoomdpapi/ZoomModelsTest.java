package iped.parsers.zoomdpapi;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

import org.junit.Test;

/**
 * Unit tests for Zoom DPAPI data model classes.
 *
 * @author Calil Khalil (Hakal)
 */
public class ZoomModelsTest {

    @Test
    public void testZoomData() {
        ZoomData data = new ZoomData("/users/target", "/zoom/ini/path");
        assertEquals("/users/target", data.getUserPath());
        assertEquals("/zoom/ini/path", data.getIniFilePath());

        data.setSid("S-1-5-21-123456");
        assertEquals("S-1-5-21-123456", data.getSid());

        data.setMasterKeyGuid("abc-def-123");
        assertEquals("abc-def-123", data.getMasterKeyGuid());

        data.setEncryptedBlob("base64blob==");
        assertEquals("base64blob==", data.getEncryptedBlob());
        assertEquals("base64blob==", data.getOskeyBase64());

        data.setDecryptedOskey("decryptedKey123");
        assertEquals("decryptedKey123", data.getDecryptedOskey());
    }

    @Test
    public void testMasterKeyData() {
        MasterKeyData mk = new MasterKeyData("guid-123", "/path/to/key", "S-1-5-21-999");
        assertEquals("guid-123", mk.getGuid());
        assertEquals("/path/to/key", mk.getFilePath());
        assertEquals("S-1-5-21-999", mk.getSid());

        mk.setHash("$DPAPImk$...");
        assertEquals("$DPAPImk$...", mk.getHash());

        mk.setRecoveredPassword("password123");
        assertEquals("password123", mk.getRecoveredPassword());

        mk.setDecryptedMasterKey("aabbccdd");
        assertEquals("aabbccdd", mk.getDecryptedMasterKey());
    }

    @Test
    public void testZoomUserAccount() {
        ZoomUserAccount account = new ZoomUserAccount();
        account.setFirstName("John");
        account.setLastName("Doe");
        assertEquals("John Doe", account.getName());

        account.setEmail("john@example.com");
        account.setZoomJid("jid123");
        account.setClientVersion("5.15.0");
        account.setLastLoginTime(1700000000L);
        assertEquals("john@example.com", account.getEmail());
    }

    @Test
    public void testZoomUserAccountNameFallback() {
        ZoomUserAccount account = new ZoomUserAccount();
        assertNull(account.getName());

        account.setEmail("test@test.com");
        assertEquals("test@test.com", account.getName());

        account.setLastName("Smith");
        assertEquals("Smith", account.getName());

        account.setFirstName("Jane");
        assertEquals("Jane Smith", account.getName());
    }

    @Test
    public void testZoomMessage() {
        ZoomMessage msg = new ZoomMessage();
        msg.setId("msg-001");
        msg.setMeetingId("meet-001");
        msg.setBody("Hello world");
        msg.setTimestamp(1700000000000L);
        msg.setSenderName("Alice");
        msg.setSenderGuid("guid-alice");
        msg.setMsgType(1);
        msg.setFileTransfer(false);

        assertEquals("msg-001", msg.getId());
        assertEquals("Hello world", msg.getBody());
        assertNotNull(msg.getDate());
        assertFalse(msg.isFileTransfer());
    }

    @Test
    public void testZoomMessageDateNull() {
        ZoomMessage msg = new ZoomMessage();
        msg.setTimestamp(0);
        assertNull(msg.getDate());
    }

    @Test
    public void testZoomMessageOrdering() {
        ZoomMessage m1 = new ZoomMessage();
        m1.setTimestamp(100L);
        ZoomMessage m2 = new ZoomMessage();
        m2.setTimestamp(200L);
        ZoomMessage m3 = new ZoomMessage();
        m3.setTimestamp(50L);

        List<ZoomMessage> list = new ArrayList<>();
        list.add(m1);
        list.add(m2);
        list.add(m3);
        Collections.sort(list);

        assertEquals(50L, list.get(0).getTimestamp());
        assertEquals(100L, list.get(1).getTimestamp());
        assertEquals(200L, list.get(2).getTimestamp());
    }

    @Test
    public void testZoomMeeting() {
        ZoomMeeting meeting = new ZoomMeeting();
        meeting.setMeetingId("meet-001");
        meeting.setMeetingNo("123456789");
        meeting.setTopic("Team Standup");
        meeting.setHostName("Bob");
        meeting.setStartTime(1700000000L);
        meeting.setDuration(3600);

        assertEquals("Zoom Meeting - Team Standup (123456789)", meeting.getTitle());
        assertTrue(meeting.getMessages().isEmpty());
        assertTrue(meeting.getParticipants().isEmpty());
        assertTrue(meeting.getSharedFiles().isEmpty());
        assertTrue(meeting.getRecordings().isEmpty());
    }

    @Test
    public void testZoomMeetingTitleNoTopic() {
        ZoomMeeting meeting = new ZoomMeeting();
        assertEquals("Zoom Meeting", meeting.getTitle());
    }

    @Test
    public void testZoomParticipant() {
        ZoomParticipant p = new ZoomParticipant();
        p.setName("Alice");
        p.setRoleType(1);
        assertEquals("Host", p.getRoleName());

        p.setRoleType(2);
        assertEquals("Co-Host", p.getRoleName());

        p.setRoleType(0);
        assertEquals("Participant", p.getRoleName());
    }

    @Test
    public void testZoomSharedFile() {
        ZoomSharedFile file = new ZoomSharedFile();
        file.setFileId("file-001");
        file.setFileName("document.pdf");
        file.setFileSize(1024L);
        file.setFileHash("sha256hash");
        file.setEncryptionKey("aeskey123");
        file.setEncryptionAlg(2);

        assertEquals("file-001", file.getFileId());
        assertEquals("document.pdf", file.getFileName());
        assertEquals(1024L, file.getFileSize());
    }

    @Test
    public void testZoomRecording() {
        ZoomRecording rec = new ZoomRecording();
        rec.setMeetingId("meet-001");
        rec.setLocal(true);
        rec.setLocation("/recordings/meeting1.mp4");
        rec.setPasscode("abc123");
        rec.setDuration(1800);

        assertTrue(rec.isLocal());
        assertEquals("/recordings/meeting1.mp4", rec.getLocation());
    }

    @Test
    public void testZoomKeyValue() {
        ZoomKeyValue kv = new ZoomKeyValue();
        kv.setSection("system");
        kv.setKey("processor");
        kv.setValue("Intel i7");
        kv.setDecrypted(true);

        assertEquals("system", kv.getSection());
        assertTrue(kv.isDecrypted());
    }

    @Test
    public void testZoomSystemInfo() {
        ZoomSystemInfo info = new ZoomSystemInfo();
        info.setProcessor("Intel Core i7-12700K");
        info.setVideoController("NVIDIA RTX 3080");
        info.setComputerSystem("Dell XPS");
        info.setClientGuid("guid-123");
        info.setFingerprint("fp-abc");

        assertEquals("Intel Core i7-12700K", info.getProcessor());
        assertEquals("NVIDIA RTX 3080", info.getVideoController());
    }

    @Test
    public void testZoomTimelineEventOrdering() {
        ZoomTimelineEvent e1 = new ZoomTimelineEvent();
        e1.setTimestamp(300L);
        e1.setType("message");
        ZoomTimelineEvent e2 = new ZoomTimelineEvent();
        e2.setTimestamp(100L);
        e2.setType("waiting_room");

        List<ZoomTimelineEvent> list = new ArrayList<>();
        list.add(e1);
        list.add(e2);
        Collections.sort(list);

        assertEquals("waiting_room", list.get(0).getType());
        assertEquals("message", list.get(1).getType());
    }
}
