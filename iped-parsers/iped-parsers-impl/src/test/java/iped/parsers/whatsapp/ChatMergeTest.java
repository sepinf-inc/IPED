package iped.parsers.whatsapp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.Test;

import iped.parsers.whatsapp.Message.MessageType;
import junit.framework.TestCase;

public class ChatMergeTest extends TestCase {

    private static final String REMOTE = "573000000002@s.whatsapp.net";

    private static Message message(long sortId, String text, boolean deleted) {
        Message m = new Message();
        m.setSortId(sortId);
        m.setTimeStamp(new Date(sortId));
        m.setFromMe(true);
        m.setMessageType(MessageType.TEXT_MESSAGE);
        m.setData(text);
        m.setDeleted(deleted);
        return m;
    }

    private static List<Chat> chatList(Message... messages) {
        Chat c = new Chat(new WAContact(REMOTE));
        c.setId(137);
        c.setMessages(new ArrayList<>(Arrays.asList(messages)));
        List<Chat> list = new ArrayList<>();
        list.add(c);
        return list;
    }

    @Test
    public void testBackupMessageReplacesCarvedCopy() {
        Message active = message(1000, "active message", false);
        Message carved = message(2000, "Guarda esto por si acaso", true);
        List<Chat> main = chatList(active, carved);

        Message fromBackup = message(2000, "Guarda esto por si acaso", false);
        List<Chat> backup = chatList(message(1000, "active message", false), fromBackup);

        ChatMerge merge = new ChatMerge(main, "msgstore-2026-09-17.1.db");
        assertEquals(1, merge.mergeChatList(backup));

        List<Message> messages = main.get(0).getMessages();
        assertEquals(2, messages.size());
        assertSame(fromBackup, messages.get(1));
        assertFalse(messages.get(1).isDeleted());
        assertEquals("msgstore-2026-09-17.1.db", messages.get(1).getRecoveredFrom());
        assertNull(messages.get(0).getRecoveredFrom());
    }

    @Test
    public void testCarvedCopyWithOtherContentIsKept() {
        Message carved = message(2000, "previous content", true);
        List<Chat> main = chatList(carved);

        List<Chat> backup = chatList(message(2000, "other content", false));

        ChatMerge merge = new ChatMerge(main, "msgstore.db.crypt14");
        assertEquals(0, merge.mergeChatList(backup));

        List<Message> messages = main.get(0).getMessages();
        assertEquals(1, messages.size());
        assertSame(carved, messages.get(0));
        assertNull(carved.getRecoveredFrom());
    }

    @Test
    public void testCarvedCopyOfActiveMessageIsNotReplaced() {
        Message active = message(2000, "Guarda esto por si acaso", false);
        Message carved = message(2000, "Guarda esto por si acaso", true);
        List<Chat> main = chatList(active, carved);

        List<Chat> backup = chatList(message(2000, "Guarda esto por si acaso", false));

        ChatMerge merge = new ChatMerge(main, "msgstore.db.crypt14");
        assertEquals(0, merge.mergeChatList(backup));

        List<Message> messages = main.get(0).getMessages();
        assertEquals(2, messages.size());
        assertTrue(messages.contains(active));
        assertTrue(messages.contains(carved));
    }

    @Test
    public void testCarvedBackupMessageDoesNotReplaceCarvedCopy() {
        Message carved = message(2000, "Guarda esto por si acaso", true);
        List<Chat> main = chatList(carved);

        List<Chat> backup = chatList(message(2000, "Guarda esto por si acaso", true));

        ChatMerge merge = new ChatMerge(main, "msgstore.db.crypt14");
        assertEquals(0, merge.mergeChatList(backup));
        assertSame(carved, main.get(0).getMessages().get(0));
    }

    @Test
    public void testRecoveredMessagesAreSorted() {
        Message active = message(2000, "active", false);
        List<Chat> main = chatList(active);
        Message before = message(1000, "before", false);
        Message after = message(3000, "after", false);
        List<Chat> backup = chatList(after, message(2000, "active", false), before);

        ChatMerge merge = new ChatMerge(main, "msgstore.db.crypt14");
        assertEquals(2, merge.mergeChatList(backup));

        List<Message> messages = main.get(0).getMessages();
        assertEquals(Arrays.asList(before, active, after), messages);
        assertEquals("msgstore.db.crypt14", before.getRecoveredFrom());
        assertNull(active.getRecoveredFrom());
    }

    @Test
    public void testOnlyFirstBackupMessageWithMissingSortKeyIsRecovered() {
        List<Chat> main = chatList(message(1000, "active", false));
        Message first = message(2000, "first", false);
        List<Chat> backup = chatList(message(1000, "active", false), first, message(2000, "second", false));

        ChatMerge merge = new ChatMerge(main, "msgstore.db.crypt14");
        assertEquals(1, merge.mergeChatList(backup));

        List<Message> messages = main.get(0).getMessages();
        assertEquals(2, messages.size());
        assertSame(first, messages.get(1));
    }
}
