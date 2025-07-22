package iped.engine.datasource.ufed;



import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.Optional;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.Attributes;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import iped.engine.datasource.ufed.UfedModelHandler.UfedModelListener;
import iped.parsers.ufed.model.Attachment;
import iped.parsers.ufed.model.BaseModel;
import iped.parsers.ufed.model.Chat;
import iped.parsers.ufed.model.ChatActivity;
import iped.parsers.ufed.model.Contact;
import iped.parsers.ufed.model.ContactEntry;
import iped.parsers.ufed.model.InstantMessage;
import iped.parsers.ufed.model.JumpTarget;
import iped.parsers.ufed.model.Party;
import iped.parsers.ufed.model.ReplyMessageData;
import iped.utils.DateUtil;


public class UfedModelHandlerChatTest {

    private static Chat parsedChat;

    @BeforeClass
    public static void setUp() throws Exception {

        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = factory.newSAXParser();
        XMLReader xmlReader = saxParser.getXMLReader();
        DefaultHandler parentHandler = new DefaultHandler();
        UfedModelListener listener = new UfedModelHandler.UfedModelListener() {
            @Override
            public void onModelStarted(BaseModel model, Attributes attr) {
            }
            @Override
            public void onModelCompleted(BaseModel model) {
                parsedChat = (Chat) model;
            }
        };

        UfedModelHandler handler = new UfedModelHandler(xmlReader, parentHandler, listener);

        InputStream xmlFile = UfedModelHandlerChatTest.class.getResourceAsStream("/ufed-model-chat.xml");
        saxParser.parse(xmlFile, handler);
    }

    @Test
    public void testRootChatObjectIsParsed() {
        assertNotNull("The root Chat object should not be null.", parsedChat);
        assertEquals("Chat ID should be parsed correctly.", "5c1b7590-ec7d-43ec-8ae0-f1ff3967d30d", parsedChat.getId());
        assertEquals("Deleted state attribute should be parsed.", "Intact", parsedChat.getDeletedState());
        assertEquals("Decoding confidence attribute should be parsed.", "High", parsedChat.getDecodingConfidence());
        assertEquals("Source index attribute should be parsed.", 89030, parsedChat.getSourceIndex());
        assertEquals("Source field should be parsed.", "WhatsApp", parsedChat.getSource());
        assertEquals("StartTime field should be parsed as Instant.", DateUtil.tryToParseDate ("2024-09-28T14:59:35.230Z"), parsedChat.getStartTime());
    }

    @Test
    public void testParticipantsAreParsed() {
        assertNotNull("Participants list should not be null.", parsedChat.getParticipants());
        assertFalse("Participants list should not be empty.", parsedChat.getParticipants().isEmpty());
        assertEquals("Should find 2 participants.", 2, parsedChat.getParticipants().size());

        Party clefable = parsedChat.getParticipants().stream()
                .filter(p -> "Clefable".equals(p.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull("Participant 'Clefable' should be found.", clefable);
        assertEquals("Identifier should be correct.", "5511999998888@s.whatsapp.net", clefable.getIdentifier());
        assertTrue("isPhoneOwner should be true.", clefable.isPhoneOwner());
    }

    @Test
    public void testMessagesAreParsed() {
        assertNotNull("Messages list should not be null.", parsedChat.getMessages());
        assertFalse("Messages list should not be empty.", parsedChat.getMessages().isEmpty());
    }

    @Test
    public void testMessageWithAttachment() {
        Optional<InstantMessage> msgWithAttachment = parsedChat.getMessages().stream()
            .filter(m -> "2ee39d83-7b1e-48df-bfe7-ef8039de7ae2".equals(m.getId()))
            .findFirst();

        assertTrue("Message with attachment should be found.", msgWithAttachment.isPresent());
        assertFalse("Attachments list should not be empty.", msgWithAttachment.get().getAttachments().isEmpty());

        Attachment attachment = msgWithAttachment.get().getAttachments().get(0);
        assertEquals("Attachment file_id should be parsed.", "d70bf66b-49b6-4185-b2cd-1db458e19267", attachment.getFileId());
        assertEquals("Attachment filename should be parsed.", "PTT-20241103-WA0101.opus", attachment.getFilename());
        assertEquals("Attachment extracted path should be parsed.", "files/Audio/PTT-20241103-WA0101.opus", attachment.getAttachmentExtractedPath());
    }

    @Test
    public void testMessageWithSharedContact() {
        Optional<InstantMessage> msgWithContact = parsedChat.getMessages().stream()
            .filter(m -> "076caedd-17fd-4d81-b6ce-132de4bedfa2".equals(m.getId()))
            .findFirst();

        assertTrue("Message with shared contact should be found.", msgWithContact.isPresent());
        assertFalse("Shared contacts list should not be empty.", msgWithContact.get().getSharedContacts().isEmpty());

        Contact contact = msgWithContact.get().getSharedContacts().get(0);
        assertEquals("Charmeleon", contact.getName());
        assertEquals(2, contact.getContactEntries().size());

        Optional<ContactEntry> userIdEntry = contact.getUserID().flatMap(list -> list.stream().findFirst());
        assertTrue(userIdEntry.isPresent());
        assertEquals("5511999997745@s.whatsapp.net", userIdEntry.get().getValue());

        Optional<ContactEntry> phoneEntry = contact.getPhoneNumber().flatMap(list -> list.stream().findFirst());
        assertTrue(phoneEntry.isPresent());
        assertEquals("+55 11 99999-22222", phoneEntry.get().getValue());
    }

    @Test
    public void testMessageWithJumpTargets() {
        Optional<InstantMessage> msgWithJumpTarget = parsedChat.getMessages().stream()
            .filter(m -> "c8fd9822-dd2c-41d1-af63-6675db3d8864".equals(m.getId()))
            .findFirst();

        assertTrue("Message with jump target should be found.", msgWithJumpTarget.isPresent());
        assertFalse("Jump targets list should not be empty.", msgWithJumpTarget.get().getJumpTargets().isEmpty());

        JumpTarget target = msgWithJumpTarget.get().getJumpTargets().iterator().next();
        assertEquals("4c658373-983a-4bc6-bf93-e06115de4fa5", target.getId());
        assertFalse("isModel attribute should be false.", target.isModel());
    }

    @Test
    public void testMessageWithChatActivity() {
        Optional<InstantMessage> msgWithActivity = parsedChat.getMessages().stream()
                .filter(m -> "db1afcb5-e03e-4fca-bb9e-9ebe1c00bf48".equals(m.getId()))
                .findFirst();

        assertTrue("Message with ChatActivity should be found.", msgWithActivity.isPresent());
        assertNotNull("ActivityLog should not be empty.", msgWithActivity.get().getActivityLog());

        ChatActivity activity = msgWithActivity.get().getActivityLog();
        assertEquals("ParticipantJoined", activity.getAction());
        assertEquals("You joined the group via invite link", activity.getSystemMessageBody());
        assertNotNull(activity.getParticipant());
        assertEquals("Squirtle", activity.getParticipant().getName());
    }

    @Test
    public void testMessageWithReplyMessageData() {
        Optional<InstantMessage> msgWithReply = parsedChat.getMessages().stream()
                .filter(m -> "2833f15a-b6e2-4b0e-b7b2-4691528f1ed0".equals(m.getId()))
                .findFirst();

        assertTrue("Message with ReplyMessageData should be found.", msgWithReply.isPresent());

        ReplyMessageData replyData = msgWithReply.get().getExtraData().getReplyMessage().orElse(null);
        assertNotNull("ReplyMessageData should be present in MessageExtraData.", replyData);
        assertEquals("Reply", replyData.getLabel());
        assertEquals("1654984910:14433", replyData.getOriginalMessageID());
        assertNotNull(replyData.getInstantMessage());
        assertEquals("Pidgeot", replyData.getInstantMessage().getFrom().get().getName());
    }
}
