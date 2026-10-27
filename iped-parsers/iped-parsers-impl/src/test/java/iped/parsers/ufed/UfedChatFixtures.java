package iped.parsers.ufed;

import java.util.Date;
import java.util.List;

import iped.parsers.ufed.model.Attachment;
import iped.parsers.ufed.model.Chat;
import iped.parsers.ufed.model.ChatActivity;
import iped.parsers.ufed.model.Contact;
import iped.parsers.ufed.model.ContactEntry;
import iped.parsers.ufed.model.InstantMessage;
import iped.parsers.ufed.model.Party;

/**
 * Builds a small, hand-crafted {@link Chat} model tree used by
 * {@link ReportGeneratorTest} and {@link UfedChatParserTest}.
 *
 * The model classes are populated directly via {@code setField(...)}, the same
 * generic mechanism the real XML-driven UfedModelHandler (iped-engine module)
 * uses, so no UFDR fixture file or cross-module test dependency is needed.
 */
final class UfedChatFixtures {

    static final String CHAT_ID = "chat-1";
    static final String OWNER_ID = "5511900000000@s.whatsapp.net";
    static final String ALICE_NAME = "Alice Wonder";
    static final String ALICE_ID = "5511999998888@s.whatsapp.net";
    static final String MSG1_ID = "msg-1";
    static final String MSG1_BODY = "Hello team!";
    static final String MSG2_ID = "msg-2";
    static final String ATTACHMENT_URL = "https://example.com/shared-photo";
    static final String MSG3_ID = "msg-3";
    static final String SHARED_CONTACT_NAME = "Charmeleon";
    static final String SHARED_CONTACT_PHONE = "+55 11 99999-2222";
    static final String ACTIVITY_ID = "activity-1";
    static final String ACTIVITY_SYSTEM_MESSAGE = "Alice Wonder joined the group";

    private UfedChatFixtures() {
    }

    static Chat buildSampleChat() {

        Chat chat = new Chat();
        chat.setId(CHAT_ID);
        chat.setField("Source", Chat.SOURCE_WHATSAPP);
        chat.setField("ChatType", Chat.TYPE_ONEONONE);
        chat.setField("StartTime", new Date(1_650_000_000_000L));

        Party owner = new Party();
        owner.setId("party-owner");
        owner.setField("Identifier", OWNER_ID);
        owner.setField("IsPhoneOwner", Boolean.TRUE);
        chat.getParticipants().add(owner);

        Party alice = new Party();
        alice.setId("party-alice");
        alice.setField("Name", ALICE_NAME);
        alice.setField("Identifier", ALICE_ID);
        alice.setField("IsPhoneOwner", Boolean.FALSE);
        chat.getParticipants().add(alice);

        // msg 1: plain text message received from Alice
        InstantMessage msg1 = new InstantMessage(chat);
        msg1.setId(MSG1_ID);
        msg1.setField("Body", MSG1_BODY);
        msg1.setField("TimeStamp", new Date(1_650_000_100_000L));
        msg1.setFrom(alice);
        chat.getMessages().add(msg1);

        // msg 2: sent by the phone owner, with a URL-type attachment
        InstantMessage msg2 = new InstantMessage(chat);
        msg2.setId(MSG2_ID);
        msg2.setField("TimeStamp", new Date(1_650_000_200_000L));
        msg2.setFrom(owner);
        Attachment attachment = new Attachment();
        attachment.setId("att-1");
        attachment.setAttribute("file_id", "file-1");
        attachment.setField("ContentType", "URL");
        attachment.setField("URL", ATTACHMENT_URL);
        msg2.getAttachments().add(attachment);
        chat.getMessages().add(msg2);

        // msg 3: received from Alice, sharing a contact
        InstantMessage msg3 = new InstantMessage(chat);
        msg3.setId(MSG3_ID);
        msg3.setField("TimeStamp", new Date(1_650_000_300_000L));
        msg3.setFrom(alice);
        Contact sharedContact = new Contact();
        sharedContact.setId("contact-1");
        sharedContact.setField("Name", SHARED_CONTACT_NAME);
        ContactEntry phoneEntry = new ContactEntry("PhoneNumber");
        phoneEntry.setField("Value", SHARED_CONTACT_PHONE);
        sharedContact.getContactEntries().put("PhoneNumber", List.of(phoneEntry));
        msg3.getSharedContacts().add(sharedContact);
        chat.getMessages().add(msg3);

        // chat activity: Alice joined the chat
        ChatActivity activity = new ChatActivity();
        activity.setId(ACTIVITY_ID);
        activity.setField("Action", "ParticipantJoined");
        activity.setField("SystemMessageBody", ACTIVITY_SYSTEM_MESSAGE);
        activity.setParticipant(alice);
        chat.getActivityLog().add(activity);

        return chat;
    }

    /**
     * A minimal one-message chat whose single attachment has the given
     * ContentType, for tests that exercise {@code ReportGenerator}'s per-type
     * attachment rendering dispatch.
     */
    static Chat buildChatWithSingleAttachment(String contentType) {

        Chat chat = new Chat();
        chat.setId("chat-attachment");
        chat.setField("Source", Chat.SOURCE_WHATSAPP);
        chat.setField("ChatType", Chat.TYPE_ONEONONE);

        Party owner = new Party();
        owner.setId("party-owner");
        owner.setField("IsPhoneOwner", Boolean.TRUE);
        chat.getParticipants().add(owner);

        InstantMessage msg = new InstantMessage(chat);
        msg.setId("msg-attachment");
        msg.setField("TimeStamp", new Date(1_650_000_000_000L));
        msg.setFrom(owner);
        Attachment attachment = new Attachment();
        attachment.setId("att-image");
        attachment.setField("ContentType", contentType);
        msg.getAttachments().add(attachment);
        chat.getMessages().add(msg);

        return chat;
    }
}
