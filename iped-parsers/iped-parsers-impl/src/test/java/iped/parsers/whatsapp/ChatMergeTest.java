package iped.parsers.whatsapp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.Test;

import iped.parsers.whatsapp.Message.MessageType;
import junit.framework.TestCase;

/**
 * The scenarios follow how WhatsApp for Android stores messages:
 * <ul>
 * <li>every message has a message key (key_id) and a sort key (sort_id);</li>
 * <li>an edited message keeps its row and sort key and gets a new message key
 * and an edit timestamp;</li>
 * <li>a message deleted for everyone is replaced by a placeholder row with the
 * same sort key, a new message key and another message type;</li>
 * <li>a record carved from deleted space has the columns of the message table
 * only: it may be truncated, has no edit timestamp and may have lost its key or
 * its sort key (which comes after the text), being then sorted by time.</li>
 * </ul>
 */
public class ChatMergeTest extends TestCase {

    private static final String REMOTE = "573000000002@s.whatsapp.net";
    private static final String BACKUP = "msgstore-2026-09-17.1.db.crypt15";

    private static Message active(long sortId, String key, String text) {
        Message m = new Message();
        m.setSortId(sortId);
        m.setTimeStamp(new Date(1700000000000L + sortId));
        m.setFromMe(true);
        m.setMessageType(MessageType.TEXT_MESSAGE);
        m.setData(text);
        m.setUuid(key);
        return m;
    }

    private static Message carved(long sortId, String key, String text) {
        Message m = active(sortId, key, text);
        m.setDeleted(true);
        return m;
    }

    /** Carved record that lost its sort key. */
    private static Message carvedWithoutSortKey(long sortIdOfMessage, String key, String text) {
        Message m = carved(sortIdOfMessage, key, text);
        m.setSortId(0);
        return m;
    }

    private static Message edited(long sortId, String newKey, String text) {
        Message m = active(sortId, newKey, text);
        m.setEditTimeStamp(new Date(m.getTimeStamp().getTime() + 60000));
        return m;
    }

    private static Message revoked(long sortId, String newKey) {
        Message m = active(sortId, newKey, null);
        m.setMessageType(MessageType.DELETED_BY_SENDER);
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

    private static List<Message> merge(List<Chat> main, List<Chat> backup, int expectedRecovered) {
        ChatMerge merge = new ChatMerge(main, BACKUP);
        assertEquals(expectedRecovered, merge.mergeChatList(backup));
        return main.get(0).getMessages();
    }

    /** Message deleted after the backup: the main DB only has its carved record. */
    @Test
    public void testBackupMessageReplacesCarvedCopy() {
        Message other = active(1000, "KEY1", "active message");
        List<Chat> main = chatList(other, carved(2000, "KEY2", "Guarda esto por si acaso"));

        Message fromBackup = active(2000, "KEY2", "Guarda esto por si acaso");
        List<Chat> backup = chatList(active(1000, "KEY1", "active message"), fromBackup);

        List<Message> messages = merge(main, backup, 1);
        assertEquals(Arrays.asList(other, fromBackup), messages);
        assertFalse(fromBackup.isDeleted());
        assertEquals(BACKUP, fromBackup.getRecoveredFrom());
        assertNull(other.getRecoveredFrom());
    }

    /** Same, with a carved record that lost the end of its text. */
    @Test
    public void testBackupMessageReplacesTruncatedCarvedCopy() {
        List<Chat> main = chatList(carved(2000, "KEY2", "Guarda esto por"));

        Message fromBackup = active(2000, "KEY2", "Guarda esto por si acaso");
        List<Chat> backup = chatList(fromBackup);

        List<Message> messages = merge(main, backup, 1);
        assertEquals(Arrays.asList(fromBackup), messages);
        assertEquals(BACKUP, fromBackup.getRecoveredFrom());
    }

    /** Same, with a carved record that lost its message key. */
    @Test
    public void testBackupMessageReplacesCarvedCopyWithoutKey() {
        List<Chat> main = chatList(carved(2000, null, "Guarda esto por si acaso"));

        Message fromBackup = active(2000, "KEY2", "Guarda esto por si acaso");
        List<Chat> backup = chatList(fromBackup);

        List<Message> messages = merge(main, backup, 1);
        assertEquals(Arrays.asList(fromBackup), messages);
    }

    /**
     * Same, with a carved record that lost its sort key: it is not found by sort
     * key, so it is matched by message key.
     */
    @Test
    public void testBackupMessageReplacesCarvedCopyWithoutSortKey() {
        Message other = active(1000, "KEY1", "active message");
        Message later = active(3000, "KEY3", "later message");
        List<Chat> main = chatList(other, later, carvedWithoutSortKey(2000, "KEY2", "Guarda esto por"));

        Message fromBackup = active(2000, "KEY2", "Guarda esto por si acaso");
        List<Chat> backup = chatList(active(1000, "KEY1", "active message"), fromBackup,
                active(3000, "KEY3", "later message"));

        List<Message> messages = merge(main, backup, 1);
        assertEquals(Arrays.asList(other, fromBackup, later), messages);
        assertEquals(BACKUP, fromBackup.getRecoveredFrom());
    }

    /**
     * A carved record without sort key and without message key can not be matched:
     * it is kept, and the backup message is recovered too.
     */
    @Test
    public void testCarvedCopyWithoutSortKeyAndMessageKeyIsKept() {
        Message carved = carvedWithoutSortKey(2000, null, "Guarda esto por");
        List<Chat> main = chatList(carved);

        Message fromBackup = active(2000, "KEY2", "Guarda esto por si acaso");
        List<Chat> backup = chatList(fromBackup);

        List<Message> messages = merge(main, backup, 1);
        assertEquals(2, messages.size());
        assertTrue(messages.contains(carved));
        assertTrue(messages.contains(fromBackup));
    }

    /**
     * Not how current WhatsApp versions work, as the placeholder of a message
     * deleted for everyone gets a new key. If a version keeps the key, the
     * placeholder in the backup must not replace the recovered content.
     */
    @Test
    public void testRevokedPlaceholderWithSameKeyDoesNotReplaceCarvedContent() {
        Message carved = carved(2000, "KEY2", "Guarda esto por si acaso");
        List<Chat> main = chatList(carved);

        List<Chat> backup = chatList(revoked(2000, "KEY2"));

        List<Message> messages = merge(main, backup, 0);
        assertEquals(Arrays.asList(carved), messages);
        assertNull(carved.getRecoveredFrom());
    }

    /**
     * A message that became a system message keeps its key (seen in real
     * databases). The system message in the backup has no content, so it must not
     * replace the recovered content.
     */
    @Test
    public void testSystemMessageWithSameKeyDoesNotReplaceCarvedContent() {
        Message carved = carved(2000, "KEY2", "Guarda esto por si acaso");
        List<Chat> main = chatList(carved);

        Message system = active(2000, "KEY2", null);
        system.setMessageType(MessageType.EPHEMERAL_CHANGED);
        List<Chat> backup = chatList(system);

        List<Message> messages = merge(main, backup, 0);
        assertEquals(Arrays.asList(carved), messages);
        assertNull(carved.getRecoveredFrom());
    }

    /**
     * Edited message deleted after the backup. The carved record already has the
     * new key, but carved records never have an edit timestamp.
     */
    @Test
    public void testBackupEditedMessageReplacesCarvedCopy() {
        List<Chat> main = chatList(carved(2000, "NEW_KEY", "edited cont"));

        Message fromBackup = edited(2000, "NEW_KEY", "edited content");
        List<Chat> backup = chatList(fromBackup);

        List<Message> messages = merge(main, backup, 1);
        assertEquals(Arrays.asList(fromBackup), messages);
        assertEquals(BACKUP, fromBackup.getRecoveredFrom());
    }

    /**
     * Edited message: the main DB has it and the carved previous version, the
     * backup was made before the edit and has the complete previous version.
     */
    @Test
    public void testBackupMessageReplacesCarvedPreviousVersionOfEditedMessage() {
        Message current = edited(2000, "NEW_KEY", "edited content");
        List<Chat> main = chatList(current, carved(2000, "ORIGINAL_KEY", "previous cont"));

        Message fromBackup = active(2000, "ORIGINAL_KEY", "previous content");
        List<Chat> backup = chatList(fromBackup);

        List<Message> messages = merge(main, backup, 1);
        assertEquals(2, messages.size());
        assertTrue(messages.contains(current));
        assertTrue(messages.contains(fromBackup));
        assertEquals(BACKUP, fromBackup.getRecoveredFrom());
        assertNull(current.getRecoveredFrom());
    }

    /** Same, for a message deleted for everyone after the backup. */
    @Test
    public void testBackupMessageReplacesCarvedPreviousVersionOfRevokedMessage() {
        Message placeholder = revoked(2000, "REVOKE_KEY");
        List<Chat> main = chatList(placeholder, carved(2000, "ORIGINAL_KEY", "Guarda esto por"));

        Message fromBackup = active(2000, "ORIGINAL_KEY", "Guarda esto por si acaso");
        List<Chat> backup = chatList(fromBackup);

        List<Message> messages = merge(main, backup, 1);
        assertEquals(2, messages.size());
        assertTrue(messages.contains(placeholder));
        assertTrue(messages.contains(fromBackup));
        assertEquals(BACKUP, fromBackup.getRecoveredFrom());
    }

    /**
     * Edited message and backup made after the edit: the backup has the same
     * version as the main DB, so the carved previous version is kept as it is.
     */
    @Test
    public void testCarvedPreviousVersionIsKept() {
        Message current = edited(2000, "NEW_KEY", "edited content");
        Message previous = carved(2000, "ORIGINAL_KEY", "previous content");
        List<Chat> main = chatList(current, previous);

        List<Chat> backup = chatList(edited(2000, "NEW_KEY", "edited content"));

        List<Message> messages = merge(main, backup, 0);
        assertEquals(2, messages.size());
        assertTrue(messages.contains(current));
        assertTrue(messages.contains(previous));
        assertNull(previous.getRecoveredFrom());
    }

    /**
     * Stale copy of a message that is still active (e.g. left behind when the
     * page was reorganized), which lost its key and so was not filtered out when
     * the main DB was parsed. The backup record must not replace it.
     */
    @Test
    public void testCarvedCopyOfActiveMessageIsNotReplaced() {
        Message current = active(2000, "KEY2", "Guarda esto por si acaso");
        Message staleCopy = carved(2000, null, "Guarda esto por");
        List<Chat> main = chatList(current, staleCopy);

        List<Chat> backup = chatList(active(2000, "KEY2", "Guarda esto por si acaso"));

        List<Message> messages = merge(main, backup, 0);
        assertEquals(2, messages.size());
        assertTrue(messages.contains(current));
        assertTrue(messages.contains(staleCopy));
    }

    /** The backup DB is carved too: a carved record does not replace another. */
    @Test
    public void testCarvedBackupMessageDoesNotReplaceCarvedCopy() {
        Message carved = carved(2000, "KEY2", "Guarda esto por si acaso");
        List<Chat> main = chatList(carved);

        List<Chat> backup = chatList(carved(2000, "KEY2", "Guarda esto por si acaso"));

        List<Message> messages = merge(main, backup, 0);
        assertSame(carved, messages.get(0));
    }

    /** Messages deleted after the backup, with no carved record in the main DB. */
    @Test
    public void testRecoveredMessagesAreSorted() {
        Message current = active(2000, "KEY2", "active");
        List<Chat> main = chatList(current);
        Message before = active(1000, "KEY1", "before");
        Message after = active(3000, "KEY3", "after");
        List<Chat> backup = chatList(after, active(2000, "KEY2", "active"), before);

        List<Message> messages = merge(main, backup, 2);
        assertEquals(Arrays.asList(before, current, after), messages);
        assertEquals(BACKUP, before.getRecoveredFrom());
        assertNull(current.getRecoveredFrom());
    }

    /**
     * Edited message deleted after the backup, with no carved record in the main
     * DB. The backup has the edited message and its carved previous version, with
     * the same sort key: only the first one, the regular message, is recovered.
     */
    @Test
    public void testOnlyFirstBackupMessageWithMissingSortKeyIsRecovered() {
        Message other = active(1000, "KEY1", "active");
        List<Chat> main = chatList(other);
        Message current = edited(2000, "NEW_KEY", "edited content");
        List<Chat> backup = chatList(active(1000, "KEY1", "active"), current,
                carved(2000, "ORIGINAL_KEY", "previous content"));

        List<Message> messages = merge(main, backup, 1);
        assertEquals(Arrays.asList(other, current), messages);
    }
}
