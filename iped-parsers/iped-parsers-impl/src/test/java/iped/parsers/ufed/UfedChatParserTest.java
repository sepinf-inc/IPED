package iped.parsers.ufed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.data.IItemReader;
import iped.datasource.IDataSource;
import iped.parsers.standard.StandardParser;
import iped.parsers.ufed.model.Chat;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;
import iped.utils.EmptyInputStream;

/**
 * Covers the top-level Tika {@code parse()} entry point: embedded-document
 * counts/metadata, content type per source app, and the ignoreEmptyChats
 * option.
 *
 * <p>
 * Out of scope for this baseline suite: the searcher-integration depth
 * (resolving attachments/shared contacts/quoted messages to actual indexed
 * items and asserting on the resulting LINKED_ITEMS/SHARED_HASHES metadata).
 * The {@link IItemSearcher} mock is stubbed just enough (see
 * {@link #newContextMock()}) so the reference-resolution code path in
 * {@code ChatHandler}/{@code PartyHandler}/{@code AttachmentHandler} runs to
 * completion instead of crashing on Mockito's null defaults, but it always
 * resolves to "no match found" — a real search-integration test is a
 * natural follow-up once this baseline lands.
 */
public class UfedChatParserTest {

    private static class EmbeddedDocTracker implements EmbeddedDocumentExtractor {

        final List<Metadata> docs = new ArrayList<>();

        @Override
        public boolean shouldParseEmbedded(Metadata metadata) {
            return true;
        }

        @Override
        public void parseEmbedded(InputStream stream, ContentHandler handler, Metadata metadata, boolean outputHtml)
                throws SAXException, IOException {
            docs.add(metadata);
        }
    }

    /**
     * A minimally-stubbed IItemReader/IItemSearcher pair: enough that reference
     * -resolution code (e.g. AccountableHandler.createAccountableQuery(), which
     * dereferences item.getDataSource().getUUID()) runs its real query-building
     * logic instead of throwing a NullPointerException on Mockito's unstubbed
     * defaults. The searcher still finds nothing, matching a chat whose
     * referenced items were not indexed/carved.
     */
    private static IItemReader newItemReaderMock() {
        IItemReader item = mock(IItemReader.class);
        IDataSource dataSource = mock(IDataSource.class);
        when(dataSource.getUUID()).thenReturn(UUID.randomUUID().toString());
        when(item.getDataSource()).thenReturn(dataSource);
        return item;
    }

    private static IItemSearcher newItemSearcherMock() {
        IItemSearcher searcher = mock(IItemSearcher.class);
        when(searcher.escapeQuery(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return searcher;
    }

    private EmbeddedDocTracker parse(Chat chat) throws IOException, SAXException, TikaException {
        return parse(chat, parser -> {
        });
    }

    private EmbeddedDocTracker parse(Chat chat, Consumer<UfedChatParser> configurer)
            throws IOException, SAXException, TikaException {
        UfedChatParser parser = new UfedChatParser();
        configurer.accept(parser);

        Metadata metadata = new Metadata();
        EmbeddedDocTracker tracker = new EmbeddedDocTracker();
        ParseContext context = new ParseContext();
        context.set(IItemSearcher.class, newItemSearcherMock());
        context.set(IItemReader.class, newItemReaderMock());
        context.set(EmbeddedDocumentExtractor.class, tracker);

        TikaInputStream tikaStream = TikaInputStream.get(new EmptyInputStream());
        tikaStream.setOpenContainer(chat);

        ContentHandler handler = new BodyContentHandler(1 << 20);
        parser.parse(tikaStream, handler, metadata, context);
        return tracker;
    }

    // ── embedded document counts ─────────────────────────────────────────────

    @Test
    public void testExtractsOneChatPreviewMessagesAndActivity() throws Exception {
        Chat chat = UfedChatFixtures.buildSampleChat();
        EmbeddedDocTracker tracker = parse(chat);

        // 1 chat preview HTML + 3 messages + 1 activity log entry (holds for a
        // chat small enough to render in a single HTML fragment; see
        // testActivityLogIsDuplicatedAcrossFragments for the multi-fragment case)
        int expectedTotal = 1 + chat.getMessages().size() + chat.getActivityLog().size();
        assertEquals(expectedTotal, tracker.docs.size());
    }

    @Test
    public void testChatPreviewHasContentTypeForSource() throws Exception {
        Chat chat = UfedChatFixtures.buildSampleChat();
        EmbeddedDocTracker tracker = parse(chat);

        boolean hasWhatsAppPreview = tracker.docs.stream()
                .anyMatch(m -> UfedChatParser.appToMime.get("whatsapp").toString()
                        .equals(m.get(StandardParser.INDEXER_CONTENT_TYPE)));
        assertTrue("Chat preview doc must use the WhatsApp-specific preview media type", hasWhatsAppPreview);
    }

    @Test
    public void testChatPreviewCarriesVirtualId() throws Exception {
        Chat chat = UfedChatFixtures.buildSampleChat();
        EmbeddedDocTracker tracker = parse(chat);

        boolean hasVirtualId = tracker.docs.stream()
                .anyMatch(m -> UfedChatFixtures.CHAT_ID.equals(m.get(ExtraProperties.ITEM_VIRTUAL_ID)));
        assertTrue("Chat preview doc must carry the chat's virtual id", hasVirtualId);
    }

    @Test
    public void testExtractedMessagesReferenceParentChat() throws Exception {
        Chat chat = UfedChatFixtures.buildSampleChat();
        EmbeddedDocTracker tracker = parse(chat);

        long messagesWithParent = tracker.docs.stream()
                .filter(m -> UfedChatFixtures.CHAT_ID.equals(m.get(ExtraProperties.PARENT_VIRTUAL_ID)))
                .count();
        // messages + activity log entries are both linked to the chat as parent
        assertEquals(chat.getMessages().size() + chat.getActivityLog().size(), messagesWithParent);
    }

    @Test
    public void testActivityLogTitleIsExtracted() throws Exception {
        Chat chat = UfedChatFixtures.buildSampleChat();
        EmbeddedDocTracker tracker = parse(chat);

        // ChatActivityHandler.getTitle() format: "ChatActivity-<Action>-[<id>]"
        boolean hasActivityTitle = tracker.docs.stream()
                .map(m -> m.get(TikaCoreProperties.TITLE))
                .filter(java.util.Objects::nonNull)
                .anyMatch(title -> title.contains("ParticipantJoined") && title.contains(UfedChatFixtures.ACTIVITY_ID));
        assertTrue("Activity log doc title must reference its action and id", hasActivityTitle);
    }

    @Test
    public void testEmptyChatIsIgnoredWhenConfigured() throws Exception {
        Chat chat = new Chat();
        chat.setId("empty-chat");
        chat.setField("Source", Chat.SOURCE_WHATSAPP);

        EmbeddedDocTracker tracker = parse(chat, parser -> parser.setIgnoreEmptyChats(true));

        assertEquals("No embedded docs must be produced for an empty, ignored chat", 0, tracker.docs.size());
    }

    @Test
    public void testActivityLogIsDuplicatedAcrossFragments() throws Exception {
        // KNOWN LIMITATION, not fixed here (out of scope for a test-only PR):
        // UfedChatParser.parse() calls extractActivityLog(chat, ...) once per HTML
        // fragment inside the pagination loop, but that method always iterates the
        // FULL chat.getActivityLog() rather than a per-fragment slice (unlike
        // extractMessages(), which is correctly given a sliced subList). With
        // minChatSplitSize forcing N fragments, each of the chat's activity log
        // entries is emitted N times. This test locks in and documents that
        // observed behavior so a future fix is a deliberate, visible test change
        // rather than a silent one.
        Chat chat = UfedChatFixtures.buildSampleChat();
        int messageCount = chat.getMessages().size();
        EmbeddedDocTracker tracker = parse(chat, parser -> parser.setMinChatSplitSize(1));

        long activityDocs = tracker.docs.stream()
                .map(m -> m.get(TikaCoreProperties.TITLE))
                .filter(java.util.Objects::nonNull)
                .filter(title -> title.contains("ChatActivity"))
                .count();

        assertEquals("Each of the " + messageCount + " message fragments currently re-emits the full activity log",
                messageCount * (long) chat.getActivityLog().size(), activityDocs);
    }
}
