package iped.engine.rag;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Test;

import com.sun.net.httpserver.HttpServer;

/** Streaming of the Claude, Gemini and OpenAI-compatible providers, with synthetic events from a loopback server. */
public class LLMProviderStreamTest {

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile String requestBody;
    private volatile String lastUri;

    @After
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void testClaudeAnswerArrivesInPieces() throws Exception {
        serve(200, "event: message_start\n"
                + "data: {\"type\":\"message_start\",\"message\":{\"content\":[]}}\n\n"
                + "event: content_block_start\n"
                + "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}\n\n"
                + "event: ping\n"
                + "data: {\"type\":\"ping\"}\n\n"
                + "event: content_block_delta\n"
                + "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Synthetic \\\"answer\\\"\"}}\n\n"
                + "event: content_block_delta\n"
                + "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\" in pi\\u00e9ces [1]\"}}\n\n"
                + "event: message_delta\n"
                + "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}}\n\n"
                + "event: message_stop\n"
                + "data: {\"type\":\"message_stop\"}\n\n");
        List<String> tokens = new ArrayList<>();
        String answer = claude().generateAnswerStream("q", "c", Collections.emptyList(), tokens::add);

        assertEquals("Synthetic \"answer\" in piéces [1]", answer);
        assertEquals(Arrays.asList("Synthetic \"answer\"", " in piéces [1]"), tokens);
        assertTrue(requestBody, requestBody.endsWith(",\"temperature\":0.0,\"stream\":true}"));
    }

    @Test
    public void testClaudeTokenLimitIsVisible() throws Exception {
        serve(200, "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Cut\"}}\n\n"
                + "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"max_tokens\"}}\n\n");
        List<String> tokens = new ArrayList<>();
        String answer = claude().generateAnswerStream("q", "c", Collections.emptyList(), tokens::add);

        assertEquals("Cut" + LocalLLMProvider.truncationNotice(), answer);
        assertEquals(Arrays.asList("Cut", LocalLLMProvider.truncationNotice()), tokens);
    }

    @Test
    public void testClaudeErrorAfterTextIsNotRetried() throws Exception {
        serve(200, "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Partial\"}}\n\n"
                + "data: {\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}\n\n");
        List<String> tokens = new ArrayList<>();
        try {
            claude().generateAnswerStream("q", "c", Collections.emptyList(), tokens::add);
            fail("Expected the error event to fail the answer");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("overloaded_error"));
        }
        // A retry would repeat the text already shown.
        assertEquals(Collections.singletonList("Partial"), tokens);
        assertEquals(1, requests.get());
    }

    @Test
    public void testClaudeClientErrorKeepsTheResponseBody() throws Exception {
        serve(400, "{\"type\":\"error\",\"error\":{\"message\":\"synthetic bad request\"}}");
        try {
            claude().generateAnswerStream("q", "c", Collections.emptyList(), token -> { });
            fail("Expected HTTP error");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("400"));
            assertTrue(expected.getMessage(), expected.getMessage().contains("synthetic bad request"));
        }
    }

    @Test
    public void testRemoteAnswerArrivesInPieces() throws Exception {
        serve(200, "data: {\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"\"},\"finish_reason\":null}]}\n\n"
                + "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":null},\"finish_reason\":null}]}\n\n"
                + "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"First\\n\"},\"finish_reason\":null}]}\n\n"
                + "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Second \\u00e7\"},\"finish_reason\":null}]}\n\n"
                + "data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
                + "data: {\"choices\":[],\"usage\":{\"completion_tokens\":3}}\n\n"
                + "data: [DONE]\n\n");
        List<String> tokens = new ArrayList<>();
        String answer = remote().generateAnswerStream("q", "c", Collections.emptyList(), tokens::add);

        assertEquals("First\nSecond ç", answer);
        assertEquals(Arrays.asList("First\n", "Second ç"), tokens);
        assertTrue(requestBody, requestBody.endsWith(",\"temperature\":0.0,\"stream\":true}"));
    }

    @Test
    public void testRemoteTokenLimitIsVisible() throws Exception {
        serve(200, "data: {\"choices\":[{\"delta\":{\"content\":\"Cut\"},\"finish_reason\":null}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"length\"}]}\n\n"
                + "data: [DONE]\n\n");
        String answer = remote().generateAnswerStream("q", "c", Collections.emptyList(), token -> { });

        assertEquals("Cut" + LocalLLMProvider.truncationNotice(), answer);
    }

    @Test
    public void testRemoteErrorEventFailsTheAnswer() throws Exception {
        serve(200, "data: {\"error\":{\"message\":\"synthetic failure\"}}\n\n");
        try {
            remote().generateAnswerStream("q", "c", Collections.emptyList(), token -> { });
            fail("Expected the error event to fail the answer");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("synthetic failure"));
        }
        assertEquals(1, requests.get());
    }

    @Test
    public void testGeminiAnswerArrivesInPieces() throws Exception {
        serve(200, "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"First \"},{\"text\":\"part\"}],\"role\":\"model\"}}]}\n\n"
                + "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\" ma\\u00e7\\u00e3 [2]\"}],\"role\":\"model\"},\"finishReason\":\"STOP\"}],"
                + "\"usageMetadata\":{\"candidatesTokenCount\":4}}\n\n");
        List<String> tokens = new ArrayList<>();
        String answer = gemini().generateAnswerStream("q", "c", Collections.emptyList(), tokens::add);

        assertEquals("First part maçã [2]", answer);
        assertEquals(Arrays.asList("First part", " maçã [2]"), tokens);
        assertTrue(lastUri, lastUri.endsWith("/v1beta/models/m:streamGenerateContent?alt=sse"));
    }

    @Test
    public void testGeminiTokenLimitIsVisible() throws Exception {
        serve(200, "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Cut\"}]}}]}\n\n"
                + "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"\"}]},\"finishReason\":\"MAX_TOKENS\"}]}\n\n");
        String answer = gemini().generateAnswerStream("q", "c", Collections.emptyList(), token -> { });

        assertEquals("Cut" + LocalLLMProvider.truncationNotice(), answer);
    }

    @Test
    public void testGeminiErrorAfterTextIsNotRetried() throws Exception {
        serve(200, "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Partial\"}]}}]}\n\n"
                + "data: {\"error\":{\"code\":503,\"message\":\"synthetic overload\"}}\n\n");
        List<String> tokens = new ArrayList<>();
        try {
            gemini().generateAnswerStream("q", "c", Collections.emptyList(), tokens::add);
            fail("Expected the error event to fail the answer");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("synthetic overload"));
        }
        assertEquals(Collections.singletonList("Partial"), tokens);
        assertEquals(1, requests.get());
    }

    private GeminiLLMProvider gemini() {
        return new GeminiLLMProvider(url("/v1beta/models/m:generateContent"), "m", "synthetic-key");
    }

    private ClaudeLLMProvider claude() {
        return new ClaudeLLMProvider(url("/v1/messages"), "m", "synthetic-key");
    }

    private RemoteLLMProvider remote() {
        return new RemoteLLMProvider(url("/v1"), "m", "");
    }

    private void serve(int status, String events) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            lastUri = exchange.getRequestURI().toString();
            try (InputStream input = exchange.getRequestBody()) {
                requestBody = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            exchange.getResponseHeaders().add("Content-Type", status == 200 ? "text/event-stream" : "application/json");
            // Length 0: chunked, so each event is flushed as a stream would be.
            exchange.sendResponseHeaders(status, 0);
            try (OutputStream output = exchange.getResponseBody()) {
                for (String event : events.split("(?<=\n\n)")) {
                    output.write(event.getBytes(StandardCharsets.UTF_8));
                    output.flush();
                }
            }
        });
        server.start();
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }
}
