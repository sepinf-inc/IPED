package iped.parsers.ufed.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * Merge of own and forwarded embedded message attachments (synthetic data only).
 */
public class InstantMessageAttachmentsTest {

    private static Attachment attachment(String id, String fileId, byte[] content) {
        Attachment a = new Attachment();
        a.setId(id);
        a.setAttribute("id", id);
        if (fileId != null) {
            a.setAttribute("file_id", fileId);
        }
        a.setUnreferencedContent(content);
        return a;
    }

    private static InstantMessage forwarded(List<Attachment> own, List<Attachment> embeddedAttachments) {
        InstantMessage embedded = new InstantMessage();
        embedded.setId("embedded-id");
        embedded.setAttribute("pa_id", "embedded-pa-id");
        embedded.getAttachments().addAll(embeddedAttachments);

        QuotedMessageData quoted = new QuotedMessageData();
        quoted.setField("ReferenceId", "embedded-pa-id");

        InstantMessage message = new InstantMessage();
        message.setId("message-id");
        message.addFieldValue("Labels", "Forwarded");
        message.getExtraData().setQuotedMessage(quoted);
        message.setEmbeddedMessage(embedded);
        message.getAttachments().addAll(own);
        return message;
    }

    @Test
    public void testOnlyEmbeddedAttachments() {
        Attachment e1 = attachment("e1", "file-1", null);
        InstantMessage m = forwarded(Arrays.asList(), Arrays.asList(e1));

        assertTrue(m.getForwardedEmbeddedMessage().isPresent());
        assertEquals(Arrays.asList(e1), m.getAllAttachments());
        assertTrue(m.getOwnAttachmentsNotForwarded().isEmpty());
    }

    @Test
    public void testSameAttachmentIsNotDuplicated() {
        Attachment own = attachment("o1", "file-1", null);
        Attachment emb = attachment("e1", "file-1", null);
        InstantMessage m = forwarded(Arrays.asList(own), Arrays.asList(emb));

        List<Attachment> all = m.getAllAttachments();
        assertEquals(1, all.size());
        assertSame("own attachment wins on tie", own, all.get(0));
        assertTrue(m.getOwnAttachmentsNotForwarded().isEmpty());
        assertEquals(Arrays.asList(own), m.getForwardedEmbeddedAttachments());
    }

    @Test
    public void testDuplicatedAttachmentWithContentIsPreferred() {
        Attachment own = attachment("o1", "file-1", null);
        Attachment emb = attachment("e1", "file-1", new byte[] { 1 });
        InstantMessage m = forwarded(Arrays.asList(own), Arrays.asList(emb));

        assertEquals(Arrays.asList(emb), m.getAllAttachments());

        own.setUnreferencedContent(new byte[] { 2 });
        assertEquals(Arrays.asList(own), m.getAllAttachments());
    }

    @Test
    public void testDistinctAttachmentsAreKept() {
        Attachment own = attachment("o1", "file-1", null);
        Attachment emb = attachment("e1", "file-2", null);
        InstantMessage m = forwarded(Arrays.asList(own), Arrays.asList(emb));

        assertEquals(Arrays.asList(own, emb), m.getAllAttachments());
        assertEquals(Arrays.asList(own), m.getOwnAttachmentsNotForwarded());
        assertEquals(Arrays.asList(emb), m.getForwardedEmbeddedAttachments());
    }

    @Test
    public void testNotForwardedIgnoresEmbeddedAttachments() {
        Attachment own = attachment("o1", "file-1", null);
        Attachment emb = attachment("e1", "file-2", null);
        InstantMessage m = forwarded(Arrays.asList(own), Arrays.asList(emb));
        m.getExtraData().setQuotedMessage(null);
        m.setField("Labels", null);

        assertFalse(m.getForwardedEmbeddedMessage().isPresent());
        assertSame(m.getAttachments(), m.getAllAttachments());
    }
}
