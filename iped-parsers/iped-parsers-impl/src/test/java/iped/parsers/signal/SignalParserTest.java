package iped.parsers.signal;

import java.io.InputStream;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

import iped.parsers.standard.StandardParser;

/**
 * Unit tests for {@link SignalParser}.
 *
 * Fixture: test_signal.db — synthetic Signal Android database (NO aci column,
 * intentionally absent to verify compatibility with pre-v5.15 Signal backups):
 *
 *   Recipients:
 *     1 – Alice Walker (+5511999990001) [profile_joined_name]
 *     2 – Bob Costa   (+5511999990002) [system_joined_name]
 *     3 – Group placeholder (GRP001FORENSICS)
 *     4 – phone-only  (+5511999990004) [no name fields]
 *     5 – Device Owner (+5511999990005) [profile_joined_name] — self (device owner;
 *         from_recipient_id on all outgoing messages; member of the group)
 *
 *   Threads (DESC by date → group first, then phone-only, then alice):
 *     2 – Group "Operacao Digital"  (date=1700001000000)
 *     3 – Phone-only                (date=1700000500000)
 *     1 – Alice Walker              (date=1700000100000)
 *
 *   Messages (base types from MessageTypes.java; text rows also carry the
 *   SECURE_MESSAGE_BIT and PUSH_MESSAGE_BIT flags a real database has, so the
 *   base-type masking is exercised):
 *     Thread 1 (Alice):  incoming (20), sent (23), null-body sent (23),
 *                        system JOINED_TYPE (4, filtered)
 *     Thread 2 (Group):  incoming from Alice (20), incoming from Bob (20, emoji+HTML
 *                        chars), OUTGOING from self (23) — tests group outgoing
 *                        MESSAGE_TO, GROUP_CALL (12), and incoming from recipient 4,
 *                        who is NOT in group_membership (a former member)
 *     Thread 3 (Phone):  outgoing audio call (2), incoming audio call (1),
 *                        missed audio call (3), missed video call (8)
 *
 *   Calls (table call). Only the message rows a call row points to are replaced, so
 *   the missed video call row 12, which has no call row, is still extracted from the
 *   message table, and calls with no message row exist only here:
 *     peer 4: outgoing audio accepted (msg 8), incoming audio accepted (msg 9),
 *             incoming audio missed (msg 10), incoming video declined (no msg row)
 *     peer 3: outgoing group call accepted (msg 11), incoming group call rung by
 *             Alice (no msg row, ringer=1), generic group call event with no ringer
 *
 *   Expected output (extractMessages=true):
 *     3 x-signal-chat + 14 x-signal-message = 17 docs
 */
public class SignalParserTest extends AbstractPkgTest {

    private static final String FIXTURE = "test-files/test_signal.db";

    private static final int EXPECTED_CHAT_DOCS    = 3;
    private static final int EXPECTED_MESSAGE_DOCS = 15;  // system (JOINED_TYPE) excluded; 3+7+5
    private static final int EXPECTED_TOTAL_DOCS   = 18;

    private static final String EXPECTED_GROUP_TITLE      = "Signal Group - Operacao Digital";
    private static final String EXPECTED_INDIVIDUAL_TITLE = "Signal Chat - Alice Walker (+5511999990001)";
    private static final String EXPECTED_PHONE_TITLE      = "Signal Chat - +5511999990004";

    private static final String ALICE_FULL_ID = "Alice Walker (+5511999990001)";
    private static final String BOB_FULL_ID   = "Bob Costa (+5511999990002)";
    private static final String SELF_FULL_ID  = "Device Owner (+5511999990005)";

    private EmbeddedSignalParser parse(boolean extractMessages) throws Exception {
        SignalParser parser = new SignalParser();
        parser.setExtractMessages(extractMessages);

        ContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        metadata.set(StandardParser.INDEXER_CONTENT_TYPE, SignalParser.SIGNAL_DB.toString());

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(FIXTURE)) {
            assertNotNull("Fixture not found: " + FIXTURE, is);
            parser.parse(is, handler, metadata, signalContext);
        }
        return signalTracker;
    }

    // ── Document counts ───────────────────────────────────────────────────────

    public void testTotalDocumentCount() throws Exception {
        EmbeddedSignalParser tracker = parse(true);
        assertEquals("Expected " + EXPECTED_TOTAL_DOCS + " total embedded documents",
                EXPECTED_TOTAL_DOCS, tracker.contentTypes.size());
    }

    public void testChatDocumentCount() throws Exception {
        EmbeddedSignalParser tracker = parse(true);
        long chatCount = tracker.contentTypes.stream()
                .filter(t -> t.equals(SignalParser.SIGNAL_CHAT.toString()))
                .count();
        assertEquals(EXPECTED_CHAT_DOCS, (int) chatCount);
    }

    public void testMessageDocumentCount() throws Exception {
        EmbeddedSignalParser tracker = parse(true);
        long msgCount = tracker.contentTypes.stream()
                .filter(t -> t.equals(SignalParser.SIGNAL_MESSAGE.toString()))
                .count();
        // 13 message rows + 7 call rows; 1 system filtered, 4 message rows replaced
        // by their call rows → 15 indexed (3+7+5)
        assertEquals("System messages must be excluded from indexed message count",
                EXPECTED_MESSAGE_DOCS, (int) msgCount);
    }

    // ── Chat ordering and titles ──────────────────────────────────────────────

    public void testGroupChatIsFirst() throws Exception {
        EmbeddedSignalParser tracker = parse(true);
        assertFalse("titles should not be empty", tracker.titles.isEmpty());
        assertEquals("Group chat must come first (most recent thread)",
                EXPECTED_GROUP_TITLE, tracker.titles.get(0));
    }

    public void testIndividualChatTitle() throws Exception {
        EmbeddedSignalParser tracker = parse(true);
        assertTrue("Individual chat title must be present",
                tracker.titles.contains(EXPECTED_INDIVIDUAL_TITLE));
    }

    public void testPhoneOnlyContactTitle() throws Exception {
        EmbeddedSignalParser tracker = parse(true);
        assertTrue("Phone-only contact must show e164 number when no name is available",
                tracker.titles.contains(EXPECTED_PHONE_TITLE));
    }

    // ── Message bodies ────────────────────────────────────────────────────────

    public void testGroupMessageBodyAlice() throws Exception {
        EmbeddedSignalParser tracker = parse(true);
        assertTrue("Alice's group message body must be present",
                tracker.messageBodies.contains("Hi team, forensics meeting tomorrow!"));
    }

    public void testIndividualReceivedMessageBody() throws Exception {
        EmbeddedSignalParser tracker = parse(true);
        assertTrue("Received message body must be present",
                tracker.messageBodies.contains("Hello from Alice!"));
    }

    public void testIndividualSentMessageBody() throws Exception {
        EmbeddedSignalParser tracker = parse(true);
        assertTrue("Sent message body must be present",
                tracker.messageBodies.contains("Hi Alice, how are you?"));
    }

    public void testNullBodyIsLabeledAttachment() throws Exception {
        EmbeddedSignalParser tracker = parse(true);
        assertTrue("Null-body message must be labeled [Empty message], since attachments "
                + "are not extracted yet and the body is also null for deleted rows",
                tracker.messageBodies.contains("[Empty message]"));
    }

    public void testEmojiAndSpecialCharsInMessageBody() throws Exception {
        EmbeddedSignalParser tracker = parse(true);
        // Body stored raw in MESSAGE_BODY metadata (HTML escaping is only for the report)
        String expected = "🔒 Evidência & <prova> \"anexada\"";
        assertTrue("Emoji and HTML-special chars must pass through unescaped in MESSAGE_BODY",
                tracker.messageBodies.contains(expected));
    }

    public void testCallBodyLabels() throws Exception {
        // Calls come from the call table, which knows direction, media and outcome
        EmbeddedSignalParser tracker = parse(true);
        assertTrue("Outgoing audio call must describe its outcome",
                tracker.messageBodies.contains("[Outgoing audio call (accepted)]"));
        assertTrue("Incoming missed call must be described as missed",
                tracker.messageBodies.contains("[Incoming audio call (missed)]"));
        assertTrue("Group call must be described as such",
                tracker.messageBodies.contains("[Outgoing group call (accepted)]"));
    }

    public void testCallWithoutMessageRowIsExtracted() throws Exception {
        // A call whose message row is gone has message_id NULL and exists only in the
        // call table: reading messages alone would lose it
        EmbeddedSignalParser tracker = parse(true);
        assertTrue("Call with no message row must still be extracted",
                tracker.messageBodies.contains("[Incoming video call (declined)]"));
    }

    public void testCallsAreNotCountedTwice() throws Exception {
        // Call rows exist in both tables; they must be read from the call table only
        EmbeddedSignalParser tracker = parse(true);
        long callBodies = tracker.messageBodies.stream().filter(b -> b.contains(" call (")).count();
        assertEquals("Each call must produce exactly one message", 6, callBodies);
        assertTrue("A generic group call event carries no outcome in its label",
                tracker.messageBodies.contains("[group call]"));
    }

    public void testCallRowWithoutCallTableEntryIsKept() throws Exception {
        // Message row 12 is a missed video call with no row in the call table: it must
        // still be extracted, since only rows a call row points to are replaced
        EmbeddedSignalParser tracker = parse(true);
        assertTrue("Call present only in the message table must be kept",
                tracker.messageBodies.contains("[Missed Call]"));
    }

    public void testGroupCallWithoutRingerHasNoSender() throws Exception {
        // With no ringer recorded, the caller is unknown — attributing the call to the
        // group recipient would invent a person node in link analysis
        EmbeddedSignalParser tracker = parse(true);
        assertFalse("The group recipient must not be reported as a caller",
                tracker.messageFroms.stream().anyMatch(f -> f.startsWith("Unknown")));
    }

    public void testIncomingGroupCallSenderIsTheRinger() throws Exception {
        // On a group thread the peer is the group itself, so the caller must come from
        // the ringer column — otherwise the group would be reported as the sender
        EmbeddedSignalParser tracker = parse(true);
        assertTrue("Incoming group call must be attributed to the ringer",
                tracker.messageFroms.contains(ALICE_FULL_ID));
        assertTrue("Incoming group call must be described as a group call",
                tracker.messageBodies.contains("[Incoming group call (accepted)]"));
    }

    public void testSentMessagesUseBaseSentType() throws Exception {
        // BASE_SENT_TYPE (23) is what real databases store for sent messages, and it
        // arrives with flag bits set. Both bodies below come from such rows: if the
        // base-type masking regressed they would be classified as system and dropped.
        EmbeddedSignalParser tracker = parse(true);
        assertTrue("Sent message with flag bits must be indexed",
                tracker.messageBodies.contains("Hi Alice, how are you?"));
        assertTrue("Sent group message with flag bits must be indexed",
                tracker.messageBodies.contains("Yes, I will be there."));
    }

    // ── FROM / TO / participants ──────────────────────────────────────────────

    public void testGroupMessageSenderIsAlice() throws Exception {
        EmbeddedSignalParser tracker = parse(true);
        assertTrue("Alice must appear as a group message sender",
                tracker.messageFroms.contains(ALICE_FULL_ID));
    }

    public void testGroupOutgoingBody() throws Exception {
        EmbeddedSignalParser tracker = parse(true);
        assertTrue("Group outgoing message body must be present",
                tracker.messageBodies.contains("Yes, I will be there."));
    }

    public void testGroupOutgoingMessageTo() throws Exception {
        // When self sends a message in a group, MESSAGE_TO must be the group title,
        // NOT "Unknown" (which is what the group placeholder contact returns via getFullId())
        EmbeddedSignalParser tracker = parse(true);
        // The group_id is appended so untitled or same-named groups stay distinct nodes
        assertTrue("Group outgoing message must have group title and id as MESSAGE_TO",
                tracker.messageTos.contains(EXPECTED_GROUP_TITLE + " (id:GRP001FORENSICS)"));
    }

    public void testFormerGroupMemberIsNamed() throws Exception {
        // Senders are resolved against the whole recipient table, not just current group
        // membership, so someone who left the group is still identified by name/number
        // instead of falling back to "Unknown".
        EmbeddedSignalParser tracker = parse(true);
        assertTrue("Former group member must be named as sender",
                tracker.messageFroms.contains("+5511999990004"));
        assertFalse("No group message should fall back to Unknown",
                tracker.messageFroms.contains("Unknown"));
    }

    public void testGroupMessageSenderIsBob() throws Exception {
        EmbeddedSignalParser tracker = parse(true);
        assertTrue("Bob must appear as a group message sender (emoji message)",
                tracker.messageFroms.contains(BOB_FULL_ID));
    }

    public void testOutgoingMessageRecipient() throws Exception {
        EmbeddedSignalParser tracker = parse(true);
        assertTrue("Outgoing message recipient must be Alice",
                tracker.messageTos.contains(ALICE_FULL_ID));
    }

    public void testCallOutgoingIsFromMe() throws Exception {
        EmbeddedSignalParser tracker = parse(true);
        assertTrue("Outgoing call must have phone contact as TO",
                tracker.messageTos.contains("+5511999990004"));
        assertTrue("Outgoing messages must carry self as MESSAGE_FROM",
                tracker.messageFroms.contains(SELF_FULL_ID));
    }

    // ── Phase 2: device owner (self) in PARTICIPANTS ──────────────────────────

    public void testSelfInParticipants() throws Exception {
        // Self (device owner, identified from from_recipient_id on outgoing messages)
        // must appear as the first PARTICIPANTS entry in every chat, matching the
        // convention established by the WhatsApp and Threema parsers.
        EmbeddedSignalParser tracker = parse(true);
        long selfCount = tracker.participants.stream()
                .filter(p -> p.equals(SELF_FULL_ID))
                .count();
        assertEquals("Self must appear as participant in every chat",
                EXPECTED_CHAT_DOCS, (int) selfCount);
    }

    public void testGroupParticipantAlice() throws Exception {
        EmbeddedSignalParser tracker = parse(true);
        assertTrue("Alice must appear as a group participant",
                tracker.participants.contains(ALICE_FULL_ID));
    }

    // ── GROUP_ID ──────────────────────────────────────────────────────────────

    public void testGroupIdUsesActualSignalGroupId() throws Exception {
        // GROUP_ID must use the actual Signal group_id from recipient.group_id,
        // NOT a synthetic "SignalThread_<id>" string, so link analysis can correlate
        // the same group across multiple databases.
        EmbeddedSignalParser tracker = parse(true);
        assertEquals("Exactly one group chat must have GROUP_ID", 1, tracker.groupIds.size());
        assertEquals("GROUP_ID must be the actual Signal group_id from the recipient table",
                "GRP001FORENSICS", tracker.groupIds.get(0));
    }

    // ── USER_ACCOUNT_TYPE (required for IPED communication graph) ────────────

    public void testMessageHasUserAccountType() throws Exception {
        EmbeddedSignalParser tracker = parse(true);
        assertEquals("Every message doc must carry USER_ACCOUNT_TYPE",
                EXPECTED_MESSAGE_DOCS, tracker.userAccountTypes.size());
        assertTrue("USER_ACCOUNT_TYPE must be the service name, as the other parsers do",
                tracker.userAccountTypes.stream().allMatch(t -> t.equals(SignalParser.SIGNAL)));
    }

    // ── Dates ─────────────────────────────────────────────────────────────────

    public void testMessageDatesPresent() throws Exception {
        EmbeddedSignalParser tracker = parse(true);
        assertEquals("All indexable messages must have a date",
                EXPECTED_MESSAGE_DOCS, tracker.messageDates.size());
    }

    // ── Template injection prevention ────────────────────────────────────────

    public void testTemplateInjectionInGroupTitle() throws Exception {
        // A group named "${messages}" or "${javascript}" must not cause the template
        // placeholders to be re-evaluated after substitution (report corruption / evidence
        // integrity attack). The ReportGenerator uses single-pass Matcher.replaceAll() to
        // prevent this. We verify by directly exercising the HTML generator.
        SignalContact injectedGroup = new SignalContact(99L, null, null, null, null, null, "INJECT001");
        SignalChat chat = new SignalChat();
        chat.setId(99L);
        chat.setContact(injectedGroup);
        chat.setGroupTitle("${messages}");  // attacker-controlled group name
        chat.setMessages(new java.util.ArrayList<>());

        ReportGenerator gen = new ReportGenerator();
        byte[] html = gen.generateChatHtml(chat);
        String output = new String(html, java.nio.charset.StandardCharsets.UTF_8);

        // The literal string "${messages}" must appear in the title position,
        // NOT cause message duplication or blank-out.
        assertTrue("Injected placeholder must appear escaped in title",
                output.contains("${messages}"));
        // Must not contain a second &lt;div class=&quot;messages&quot;&gt; block
        // from the title position (template duplication check).
        int messagesBlockCount = countOccurrences(output, "<div class=\"messages\">");
        assertEquals("Messages block must appear exactly once (no duplication from title injection)",
                1, messagesBlockCount);
    }

    private static int countOccurrences(String text, String sub) {
        int count = 0, idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) { count++; idx += sub.length(); }
        return count;
    }

    // ── Encrypted / unreadable databases ─────────────────────────────────────

    public void testEncryptedDatabaseIsSkipped() throws Exception {
        // signal.db is SQLCipher-encrypted by default and is matched by file name, so
        // its content is not a readable SQLite database. Such items must be skipped
        // quietly instead of raising a parsing error for every Signal install in a case.
        SignalParser parser = new SignalParser();
        ContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        metadata.set(StandardParser.INDEXER_CONTENT_TYPE, SignalParser.SIGNAL_DB.toString());

        byte[] encrypted = new byte[1024];
        new java.util.Random(42).nextBytes(encrypted);

        parser.parse(new java.io.ByteArrayInputStream(encrypted), handler, metadata, signalContext);

        assertTrue("No documents should be extracted from an unreadable database",
                signalTracker.contentTypes.isEmpty());
    }

    // ── extractMessages flag ──────────────────────────────────────────────────

    public void testNoMessagesWhenExtractDisabled() throws Exception {
        EmbeddedSignalParser tracker = parse(false);
        long msgCount = tracker.contentTypes.stream()
                .filter(t -> t.equals(SignalParser.SIGNAL_MESSAGE.toString()))
                .count();
        assertEquals("No message docs when extractMessages=false", 0L, msgCount);
        assertEquals("Still " + EXPECTED_CHAT_DOCS + " chat docs when extractMessages=false",
                EXPECTED_CHAT_DOCS, (int) tracker.contentTypes.stream()
                        .filter(t -> t.equals(SignalParser.SIGNAL_CHAT.toString()))
                        .count());
    }
}
