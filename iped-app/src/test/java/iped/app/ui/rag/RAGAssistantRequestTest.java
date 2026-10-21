package iped.app.ui.rag;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import iped.engine.config.RAGConfig;
import iped.engine.rag.LLMProvider;
import iped.engine.rag.RAGSearchScope;
import iped.engine.rag.RAGService;
import iped.engine.rag.RAGService.RAGSourceDoc;

public class RAGAssistantRequestTest {

    private RAGAssistantPanel panel;
    private Object previousService;
    private final List<CountDownLatch> releases = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        Field singleton = RAGService.class.getDeclaredField("instance");
        singleton.setAccessible(true);
        previousService = singleton.get(null);
        singleton.set(null, null); // Never load a real case database in a UI test.
        edt(() -> panel = new RAGAssistantPanel());
    }

    @After
    public void tearDown() throws Exception {
        for (CountDownLatch latch : releases) {
            latch.countDown();
        }
        edt(() -> panel.dispose());
        Field singleton = RAGService.class.getDeclaredField("instance");
        singleton.setAccessible(true);
        singleton.set(null, previousService);
    }

    @Test
    public void testNavigationKeepsOriginAndCompleteAnswer() throws Exception {
        FakeService service = service();
        Object origin = field(panel, "activeSession");
        start(service);
        edt(() -> invoke(panel, "startNewSession"));
        Object visible = field(panel, "activeSession");
        edt(() -> ((JTextArea) field(panel, "questionArea")).setText("draft in another chat"));
        service.release.countDown();
        finished();
        assertEquals("Complete answer [7]", field(entries(origin).get(0), "rawAnswer"));
        assertTrue(entries(visible).isEmpty());
        assertSame(visible, field(panel, "activeSession"));
        assertEquals(field(origin, "sessionId"), service.savedSessions.get(0));
        assertEquals("draft in another chat", ((JTextArea) field(panel, "questionArea")).getText());
        assertFalse(((JEditorPane) field(panel, "responsePane")).getText().contains("Complete answer"));
        String requestId = (String) field(entries(origin).get(0), "contextRequestId");
        RAGContextSnapshot snapshot = cache().get(requestId);
        assertEquals("case-A#0", snapshot.fragments.get(0).contenttrackID);
        assertEquals(snapshot.fragments.get(0).content,
                service.context.substring(snapshot.fragments.get(0).bodyStart, snapshot.fragments.get(0).bodyEnd));
    }

    @Test
    public void testCancellationAndLateTokensCannotReplaceNewWorker() throws Exception {
        FakeService old = service();
        old.ignoreInterrupt = true;
        start(old);
        FakeService next = service();
        edt(() -> {
            invoke(panel, "onCancel");
            invoke(panel, "startNewSession");
            panel.startAnswer("new question", next, null);
        });
        assertTrue(next.started.await(5, TimeUnit.SECONDS));
        Object current = field(panel, "currentWorker");
        old.release.countDown();
        assertTrue(old.returned.await(5, TimeUnit.SECONDS));
        edt(() -> {
            assertSame(current, field(panel, "currentWorker"));
            assertFalse(((JButton) field(panel, "askButton")).isEnabled());
        });
        next.release.countDown();
        finished();
        assertTrue(old.savedSessions.isEmpty());
        assertEquals(1, next.savedSessions.size());
    }

    @Test
    public void testCaseDisposeDiscardsCompletionAndTrace() throws Exception {
        FakeService service = service();
        service.ignoreInterrupt = true;
        Object origin = field(panel, "activeSession");
        start(service);
        edt(() -> panel.dispose());
        service.release.countDown();
        assertTrue(service.returned.await(5, TimeUnit.SECONDS));
        edt(() -> panel.startAnswer("ignored", service, null));
        assertNull(field(panel, "currentWorker"));
        assertTrue(entries(origin).isEmpty());
        assertTrue(service.savedSessions.isEmpty());
    }

    @Test
    public void testCaseReloadSuspendsThenAllowsNewRequests() throws Exception {
        FakeService service = service();
        service.ignoreInterrupt = true;
        start(service);
        edt(() -> {
            panel.setCaseLoading(true);
            panel.startAnswer("must not start", service, null);
            assertFalse(((JButton) field(panel, "askButton")).isEnabled());
            assertFalse(((JButton) field(panel, "cancelButton")).isEnabled());
        });
        service.release.countDown();
        assertTrue(service.returned.await(5, TimeUnit.SECONDS));
        FakeService next = service();
        next.release.countDown();
        edt(() -> {
            panel.setCaseLoading(false);
            panel.startAnswer("after reload", next, null);
        });
        finished();
        assertTrue(service.savedSessions.isEmpty());
        assertEquals(1, next.savedSessions.size());
    }

    @Test
    public void testDeletingOriginCancelsAndRemovesItsSnapshots() throws Exception {
        FakeService first = service();
        first.release.countDown();
        edt(() -> panel.startAnswer("first", first, null));
        finished();
        Object origin = field(panel, "activeSession");
        String trace = (String) field(entries(origin).get(0), "contextRequestId");
        assertNotNull(cache().get(trace));
        FakeService pending = service();
        pending.ignoreInterrupt = true;
        start(pending);
        edt(() -> panel.removeSession(0));
        pending.release.countDown();
        assertTrue(pending.returned.await(5, TimeUnit.SECONDS));
        edt(() -> assertTrue((Boolean) field(origin, "deleted")));
        assertNull(cache().get(trace));
        assertTrue(pending.savedSessions.isEmpty());
        assertTrue(((List<?>) field(panel, "sessions")).isEmpty());
    }

    @Test
    public void testPackingSkipsLargeSourceAndCapturesLaterSmallSource() throws Exception {
        FakeService service = service();
        service.config.window = 1400;
        service.docs = Arrays.asList(doc("7", "case-A#0", "x".repeat(500)),
                doc("7", "case-B#0", "smaller"), doc("8", "case-C#0", ""));
        service.release.countDown();
        edt(() -> panel.startAnswer("q", service, null));
        finished();
        Object entry = entries(field(panel, "activeSession")).get(0);
        RAGContextSnapshot result = cache().get((String) field(entry, "contextRequestId"));
        assertEquals(1, result.fragments.size());
        assertEquals("case-B#0", result.fragments.get(0).contenttrackID);
        assertEquals(1, result.fragments.get(0).retrievedOrdinal);
        assertEquals(2, result.omissions.size());
        assertFalse(result.omissions.get(0).emptyContent);
        assertTrue(result.omissions.get(1).emptyContent);
        assertEquals("[7]\nScore Híbrido: 1.000\n---\nsmaller\n\n========================================\n\n",
                service.context);
    }

    @Test
    public void testFallbackNonStreamingReturnIsRecorded() throws Exception {
        FakeService service = service();
        service.noStreaming = true;
        service.release.countDown();
        edt(() -> panel.startAnswer("q", service, null));
        finished();
        assertEquals("Complete answer [7]", field(entries(field(panel, "activeSession")).get(0), "rawAnswer"));
    }

    @Test
    public void testProviderFailureDoesNotSaveSuccessfulEntry() throws Exception {
        FakeService service = service();
        service.fail = true;
        service.release.countDown();
        edt(() -> panel.startAnswer("q", service, null));
        finished();
        assertTrue(service.savedSessions.isEmpty());
        assertTrue(entries(field(panel, "activeSession")).isEmpty());
        assertTrue(((JButton) field(panel, "askButton")).isEnabled());
    }

    @Test
    public void testTraceLimitDoesNotTruncateProviderContext() throws Exception {
        FakeService service = service();
        service.config.remote = true;
        String body = "x".repeat(RAGContextSnapshot.MAX_BODY_CHARS + 1);
        service.docs = Arrays.asList(doc("7", "case-A#0", body));
        service.release.countDown();
        edt(() -> panel.startAnswer("q", service, null));
        finished();
        assertTrue(service.context.contains(body));
        Object entry = entries(field(panel, "activeSession")).get(0);
        assertNull(cache().get((String) field(entry, "contextRequestId")));
        assertEquals("Complete answer [7]", field(entry, "rawAnswer"));
    }

    @Test
    public void testNoRetrievedSourcesDoesNotCreateSentContextSnapshot() throws Exception {
        FakeService service = service();
        service.docs = new ArrayList<>();
        edt(() -> panel.startAnswer("q", service, null));
        finished();
        assertNull(service.context);
        assertEquals(1, service.started.getCount());
        Object entry = entries(field(panel, "activeSession")).get(0);
        assertNull(cache().get((String) field(entry, "contextRequestId")));
    }

    private void start(FakeService service) throws Exception {
        edt(() -> panel.startAnswer("question", service, null));
        assertTrue(service.started.await(5, TimeUnit.SECONDS));
    }

    private void finished() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        boolean[] done = { false };
        while (System.nanoTime() < deadline) {
            edt(() -> done[0] = field(panel, "currentWorker") == null);
            if (done[0]) {
                return;
            }
            Thread.sleep(10);
        }
        fail("Worker did not complete on the EDT");
    }

    private RAGContextSnapshot.Cache cache() {
        return (RAGContextSnapshot.Cache) field(panel, "contextSnapshots");
    }

    @SuppressWarnings("unchecked")
    private List<Object> entries(Object session) {
        return (List<Object>) field(session, "entries");
    }

    private static Object field(Object object, String name) {
        try {
            Field field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(object);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void invoke(Object object, String name) {
        try {
            Method method = object.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(object);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void edt(Runnable runnable) throws Exception {
        SwingUtilities.invokeAndWait(runnable);
    }

    private FakeService service() {
        FakeService service = new FakeService();
        releases.add(service.release);
        return service;
    }

    private static RAGSourceDoc doc(String id, String track, String content) {
        RAGSourceDoc doc = new RAGSourceDoc();
        doc.itemId = id;
        doc.contenttrackID = track;
        doc.content = content;
        doc.score = 1;
        return doc;
    }

    private static class FakeService extends RAGService {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch returned = new CountDownLatch(1);
        final TestConfig config;
        final List<String> savedSessions = new ArrayList<>();
        List<RAGSourceDoc> docs = Arrays.asList(doc("7", "case-A#0", "Synthetic source."));
        boolean ignoreInterrupt;
        boolean noStreaming;
        boolean fail;
        String context;

        FakeService() {
            this(new TestConfig());
        }

        private FakeService(TestConfig config) {
            super(null, config);
            this.config = config;
        }

        @Override
        public float[] getEmbedding(String text) {
            return new float[] { 1 };
        }

        @Override
        public List<RAGSourceDoc> performHybridSearchInScope(String q, float[] vector, RAGSearchScope scope) {
            return docs;
        }

        @Override
        public LLMProvider getLlmProvider() {
            return new LLMProvider() {
                @Override
                public String generateAnswer(String question, String context, List<HistoryTurn> history) {
                    return "Complete answer [7]";
                }

                @Override
                public String generateAnswerStream(String question, String text, List<HistoryTurn> history,
                        Consumer<String> consumer) throws IOException, InterruptedException {
                    context = text;
                    started.countDown();
                    try {
                        boolean waiting = true;
                        while (waiting) {
                            try {
                                if (!release.await(5, TimeUnit.SECONDS)) {
                                    throw new IOException("Test release timeout");
                                }
                                waiting = false;
                            } catch (InterruptedException e) {
                                if (!ignoreInterrupt) {
                                    throw e;
                                }
                            }
                        }
                        if (fail) {
                            throw new IOException("Synthetic provider failure");
                        }
                        if (noStreaming) {
                            return LLMProvider.super.generateAnswerStream(question, text, history, consumer);
                        }
                        consumer.accept("Partial stream");
                        return "Complete answer [7]";
                    } finally {
                        returned.countDown();
                    }
                }
            };
        }

        @Override
        public synchronized long saveHistoryEntry(String session, String q, String html, List<RAGSourceDoc> docs) {
            savedSessions.add(session);
            return savedSessions.size();
        }
    }

    private static class TestConfig extends RAGConfig {
        int window = 9216;
        boolean remote;

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public int getLlmContextWindow() {
            return window;
        }

        @Override
        public String getLlmProvider() {
            return remote ? "remote" : "local";
        }
    }
}
