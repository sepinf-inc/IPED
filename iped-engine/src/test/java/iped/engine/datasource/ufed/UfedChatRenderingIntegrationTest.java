package iped.engine.datasource.ufed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import iped.data.IItemReader;
import iped.datasource.IDataSource;
import iped.engine.datasource.ufed.UfedModelHandler.UfedModelListener;
import iped.parsers.ufed.ReportGenerator;
import iped.parsers.ufed.UfedChatParser;
import iped.parsers.ufed.model.BaseModel;
import iped.parsers.ufed.model.Chat;
import iped.search.IItemSearcher;
import iped.utils.EmptyInputStream;

/**
 * Integration test proving the REAL XML-driven pipeline works end to end:
 * {@link UfedModelHandler} (this module) parses a real UFDR-exported XML
 * fragment into a {@link Chat}, which is then fed into {@link ReportGenerator}
 * and {@link UfedChatParser} (iped-parsers-impl module).
 *
 * <p>
 * This exists alongside {@link UfedModelHandlerChatTest} (same XML fixture)
 * and iped-parsers-impl's ReportGeneratorTest/UfedChatParserTest (hand-built
 * Chat fixtures) to close a gap those leave open: a hand-built Chat can drift
 * from what the real handler actually produces (e.g. it never calls {@code
 * chat.indexMessages()} or copies XML attributes like {@code extractionId}),
 * so passing hand-built-fixture tests do not, by themselves, prove the real
 * parser output renders correctly. This test exercises code paths the
 * hand-built fixtures cannot reach — e.g. reply/quote rendering (the fixture's
 * reply resolves through a directly-embedded {@code InstantMessage} rather
 * than the {@code chat.indexMessages()} ID-lookup fallback; see
 * {@link #testHtmlContainsReplyQuote()}), and shared-contact/attachment
 * fields ({@code ContactEntry}, {@code file_id}, ...) a hand-built fixture
 * would have to remember to populate by hand.
 *
 * <p>
 * iped-engine has no Mockito dependency (unlike iped-parsers-impl), so
 * IItemSearcher/IItemReader are stubbed with a small generic
 * {@link Proxy}-based double instead.
 */
public class UfedChatRenderingIntegrationTest {

    // Shared across all @Test methods (parsing the fixture is the expensive
    // part). Safe regardless of JUnit's execution order: ReportGenerator only
    // reads the Chat (its own pagination cursor is a per-instance field, not
    // stored on Chat), and while UfedChatParser.parse() does sort
    // chat.getMessages() in place and mark Party.referenceLoaded, none of the
    // assertions in this class depend on message order, and the stub searcher
    // always resolves references to "not found" deterministically however many
    // times loadReferences() runs.
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

        InputStream xmlFile = UfedChatRenderingIntegrationTest.class.getResourceAsStream("/ufed-model-chat.xml");
        saxParser.parse(xmlFile, handler);
    }

    private static String generateFullHtml(Chat chat) throws Exception {
        ReportGenerator generator = new ReportGenerator(chat, 6_000_000);
        StringBuilder html = new StringBuilder();
        byte[] bytes;
        while ((bytes = generator.generateNextChatHtml()) != null) {
            html.append(new String(bytes, StandardCharsets.UTF_8));
        }
        return html.toString();
    }

    // ── ReportGenerator against the real, XML-parsed Chat ────────────────────

    @Test
    public void testGeneratesHtmlForRealUfedChat() throws Exception {
        assertNotNull("ReportGenerator must render HTML for a real UfedModelHandler-parsed chat",
                generateFullHtml(parsedChat));
    }

    @Test
    public void testHtmlContainsParticipantName() throws Exception {
        // "Clefable" is the phone owner and the sender of two messages in the real
        // fixture (see UfedModelHandlerChatTest). Checking the per-message sender
        // span specifically -- not just html.contains("Clefable") -- makes this
        // robust to fixture/chat-type changes: for this one-on-one chat the header
        // actually only shows the OTHER participant (ChatHandler.getTitle(true,
        // false) suppresses the owner), so a plain substring check happens to be
        // safe here too, but the span check is what directly verifies per-message
        // attribution (the exact gap found and fixed in the sibling
        // ReportGeneratorTest, where the header DID include the checked name).
        String html = generateFullHtml(parsedChat);
        assertTrue("Rendered HTML must attribute at least one message to its sender",
                html.contains("<span class=\"name\">Clefable"));
    }

    @Test
    public void testHtmlContainsAudioAttachmentMarker() throws Exception {
        // The real attachment's ContentType is "audio/ogg; codecs=opus"
        String html = generateFullHtml(parsedChat);
        assertTrue("An audio/* content type must render through the audio branch",
                html.contains("class=\"audioImg"));
    }

    @Test
    public void testHtmlContainsAttachmentTitle() throws Exception {
        // The real attachment's Title field is "Duration: 00:00:03"
        String html = generateFullHtml(parsedChat);
        assertTrue("The attachment title must be rendered when not already present in the body",
                html.contains("Duration: 00:00:03"));
    }

    @Test
    public void testHtmlContainsSharedContactName() throws Exception {
        String html = generateFullHtml(parsedChat);
        assertTrue("Rendered HTML must contain the shared contact's name", html.contains("Charmeleon"));
    }

    @Test
    public void testHtmlContainsReplyQuote() throws Exception {
        // Message '2833f15a-...' replies to a message from "Pidgeot" (see
        // UfedModelHandlerChatTest.testMessageWithReplyMessageData). "Pidgeot"
        // appears nowhere else in the fixture, so this can only come from
        // ReportGenerator.printQuote()'s rendering. For THIS message the quoted
        // InstantMessage is directly embedded in ReplyMessageData (the real
        // UfedModelHandler wires that on the ReplyMessageData->InstantMessage
        // parent/child relationship), which InstantMessage.findReplyMessage()
        // checks before falling back to chat.indexMessages()-based ID lookup --
        // so this specific case doesn't exercise that ID-lookup fallback, only
        // the embedded-message path (still one hand-built fixtures never covered,
        // since none of them populated reply/quote data at all).
        String html = generateFullHtml(parsedChat);
        assertTrue("Rendered HTML must contain the quoted message's sender", html.contains("Pidgeot"));
    }

    // ── UfedChatParser against the real, XML-parsed Chat ─────────────────────

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

    // Scoped workaround: iped-engine has no Mockito dependency (unlike
    // iped-parsers-impl). Fine for stubbing 2 narrow interfaces here; if a
    // future iped-engine test needs interaction verification or many more
    // stubbed methods, add mockito-core as a test-scope dependency instead of
    // growing this helper.
    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> iface, Map<String, Object> values) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (values.containsKey(method.getName())) {
                return values.get(method.getName());
            }
            Class<?> r = method.getReturnType();
            if (r == boolean.class)
                return Boolean.FALSE;
            if (r.isPrimitive())
                return 0;
            if (List.class.isAssignableFrom(r))
                return Collections.emptyList();
            return null;
        };
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] { iface }, handler);
    }

    private static IItemReader newItemReaderStub() {
        Map<String, Object> dataSourceValues = new HashMap<>();
        dataSourceValues.put("getUUID", "00000000-0000-0000-0000-000000000000");
        IDataSource dataSource = stub(IDataSource.class, dataSourceValues);

        Map<String, Object> itemValues = new HashMap<>();
        itemValues.put("getDataSource", dataSource);
        return stub(IItemReader.class, itemValues);
    }

    private static IItemSearcher newItemSearcherStub() {
        Map<String, Object> searcherValues = new HashMap<>();
        searcherValues.put("search", Collections.emptyList());
        // best-effort identity escaping is close enough for this stub's purpose
        InvocationHandler handler = (proxy, method, args) -> {
            if ("escapeQuery".equals(method.getName())) {
                return args[0];
            }
            if ("search".equals(method.getName()) || "searchIterable".equals(method.getName())) {
                return Collections.emptyList();
            }
            return null;
        };
        return (IItemSearcher) Proxy.newProxyInstance(IItemSearcher.class.getClassLoader(),
                new Class<?>[] { IItemSearcher.class }, handler);
    }

    @Test
    public void testParsesRealUfedChatWithoutErrors() throws IOException, SAXException, TikaException {
        UfedChatParser parser = new UfedChatParser();
        Metadata metadata = new Metadata();

        EmbeddedDocTracker tracker = new EmbeddedDocTracker();
        ParseContext context = new ParseContext();
        context.set(IItemSearcher.class, newItemSearcherStub());
        context.set(IItemReader.class, newItemReaderStub());
        context.set(EmbeddedDocumentExtractor.class, tracker);

        TikaInputStream tikaStream = TikaInputStream.get(new EmptyInputStream());
        tikaStream.setOpenContainer(parsedChat);

        parser.parse(tikaStream, new BodyContentHandler(1 << 20), metadata, context);

        // 1 chat preview HTML + one embedded doc per message; the real fixture has
        // no CHAT-LEVEL ActivityLog entry (its single ChatActivity is attached to
        // one message instead, a distinct code path -- see class Javadoc)
        int expectedTotal = 1 + parsedChat.getMessages().size() + parsedChat.getActivityLog().size();
        assertEquals("UfedChatParser must extract one embedded doc per real message, plus the chat preview",
                expectedTotal, tracker.docs.size());
    }
}
