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
 *
 *   Threads (DESC by date → group first, then phone-only, then alice):
 *     2 – Group "Operacao Digital"  (date=1700001000000)
 *     3 – Phone-only                (date=1700000500000)
 *     1 – Alice Walker              (date=1700000100000)
 *
 *   Messages:
 *     Thread 1 (Alice):  incoming, sent, null-body sent, system (type=14, filtered)
 *     Thread 2 (Group):  incoming from Alice, incoming from Bob (emoji+HTML chars),
 *                        OUTGOING from self (type=2) — tests group outgoing MESSAGE_TO
 *     Thread 3 (Phone):  CALL_OUTGOING (type=1), CALL_INCOMING (type=21), CALL_MISSED (type=22)
 *
 *   Expected output (extractMessages=true):
 *     3 x-signal-chat + 9 x-signal-message = 12 docs
 */
public class SignalParserTest extends AbstractPkgTest {

    private static final String FIXTURE = "test-files/test_signal.db";

    private static final int EXPECTED_CHAT_DOCS    = 3;
    private static final int EXPECTED_MESSAGE_DOCS = 9;   // system (type=14) excluded; 3+3+3
    private static final int EXPECTED_TOTAL_DOCS   = 12;

    private static final String EXPECTED_GROUP_TITLE      = "Signal Group - Operacao Digital";
    private static final String EXPECTED_INDIVIDUAL_TITLE = "Signal Chat - Alice Walker (+5511999990001)";
    private static final String EXPECTED_PHONE_TITLE      = "Signal Chat - +5511999990004";

    private static final String ALICE_FULL_ID = "Alice Walker (+5511999990001)";
    private static final String BOB_FULL_ID   = "Bob Costa (+5511999990002)";

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
        // 9 raw messages; 1 system (type=14) filtered → 8 indexed
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
        assertTrue("Null-body message must be labeled [Attachment]",
                tracker.messageBodies.contains("[Attachment]"));
    }

    public void testEmojiAndSpecialCharsInMessageBody() throws Exception {
        EmbeddedSignalParser tracker = parse(true);
        // Body stored raw in MESSAGE_BODY metadata (HTML escaping is only for the report)
        String expected = "🔒 Evidência & <prova> \"anexada\"";
        assertTrue("Emoji and HTML-special chars must pass through unescaped in MESSAGE_BODY",
                tracker.messageBodies.contains(expected));
    }

    public void testCallBodyLabels() throws Exception {
        EmbeddedSignalParser tracker = parse(true);
        assertTrue("Outgoing call must be labeled [Outgoing Call]",
                tracker.messageBodies.contains("[Outgoing Call]"));
        assertTrue("Incoming call must be labeled [Incoming Call]",
                tracker.messageBodies.contains("[Incoming Call]"));
        assertTrue("Missed call must be labeled [Missed Call]",
                tracker.messageBodies.contains("[Missed Call]"));
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
        assertTrue("Group outgoing message must have group title as MESSAGE_TO",
                tracker.messageTos.contains(EXPECTED_GROUP_TITLE));
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
        assertTrue("Must have at least one outgoing-direction marker (empty MESSAGE_FROM)",
                tracker.messageFroms.stream().anyMatch(String::isEmpty));
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
        assertTrue("USER_ACCOUNT_TYPE must equal SIGNAL_MESSAGE MIME type",
                tracker.userAccountTypes.stream()
                        .allMatch(t -> t.equals(SignalParser.SIGNAL_MESSAGE.toString())));
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
