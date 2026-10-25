package iped.engine.rag;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.sun.net.httpserver.HttpServer;

import iped.engine.rag.RAGService.HistoryTurn;

/** Pins the request bodies each provider sends, using synthetic text and a loopback server. */
public class LLMPromptRequestTest {

    private static final String QUESTION = "synthetic \"question\"";
    private static final String CONTEXT = "[7]\nsynthetic\tcontext";
    private static final List<HistoryTurn> HISTORY = Arrays.asList(new HistoryTurn("first", "answer \\ one"));

    private HttpServer server;
    private volatile String body;
    private volatile String uri;
    private volatile String geminiKey;

    @Before
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/", exchange -> {
            try (InputStream input = exchange.getRequestBody()) {
                String received = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                if (body == null) {
                    body = received;
                    uri = exchange.getRequestURI().toString();
                    geminiKey = exchange.getRequestHeaders().getFirst("x-goog-api-key");
                }
            }
            byte[] response = "{\"content\":[{\"text\":\"ok\"}],\"choices\":[{\"message\":{\"content\":\"ok\"}}],\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}]}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
    }

    @After
    public void tearDown() {
        server.stop(0);
    }

    @Test
    public void testLocalRequestMessages() throws Exception {
        for (List<HistoryTurn> history : histories()) {
            LocalLLMProvider provider = new LocalLLMProvider(url("/v1"), "m");
            String sent = send(provider, history);
            String expected = "{\"model\":\"m\",\"messages\":" + openAi(provider.buildPrompt(QUESTION, CONTEXT, history))
                    + ",\"options\":{\"num_ctx\":";
            assertTrue(sent, sent.startsWith(expected));
        }
    }

    @Test
    public void testRemoteRequestBody() throws Exception {
        for (List<HistoryTurn> history : histories()) {
            RemoteLLMProvider provider = new RemoteLLMProvider(url("/v1"), "m", "");
            String expected = "{\"model\":\"m\",\"messages\":" + openAi(provider.buildPrompt(QUESTION, CONTEXT, history))
                    + ",\"temperature\":0.0}";
            assertEquals(expected, send(provider, history));
        }
    }

    @Test
    public void testClaudeRequestBody() throws Exception {
        for (List<HistoryTurn> history : histories()) {
            ClaudeLLMProvider provider = new ClaudeLLMProvider(url("/v1/messages"), "m", "synthetic-key");
            List<LLMProvider.PromptMessage> prompt = provider.buildPrompt(QUESTION, CONTEXT, history);
            String expected = "{\"model\":\"m\",\"max_tokens\":4096,\"system\":"
                    + LocalEmbeddingProvider.toJsonString(provider.getSystemPrompt(QUESTION))
                    + ",\"messages\":" + openAi(prompt.subList(1, prompt.size())) + ",\"temperature\":0.0}";
            assertEquals(expected, send(provider, history));
        }
    }

    @Test
    public void testGeminiRequestBody() throws Exception {
        for (List<HistoryTurn> history : histories()) {
            GeminiLLMProvider provider = new GeminiLLMProvider(url("/v1beta/models/m:generateContent"), "m",
                    "synthetic-key");
            List<LLMProvider.PromptMessage> prompt = provider.buildPrompt(QUESTION, CONTEXT, history);
            StringBuilder contents = new StringBuilder();
            for (LLMProvider.PromptMessage message : prompt.subList(1, prompt.size())) {
                if (contents.length() > 0) {
                    contents.append(",");
                }
                contents.append(gemini(message.role.equals("assistant") ? "model" : message.role, message.content));
            }
            String expected = "{\"systemInstruction\":{  \"parts\":[{\"text\":"
                    + LocalEmbeddingProvider.toJsonString(provider.getSystemPrompt(QUESTION)) + "}]},\"contents\":["
                    + contents + "],\"generationConfig\":{  \"temperature\":0.0},\"safetySettings\":[";
            String sent = send(provider, history);
            assertTrue(sent, sent.startsWith(expected));
            assertEquals("synthetic-key", geminiKey);
            assertFalse(uri, uri.contains("synthetic-key"));
        }
    }

    private String send(LLMProvider provider, List<HistoryTurn> history) throws Exception {
        body = null;
        try {
            provider.generateAnswer(QUESTION, CONTEXT, history);
        } catch (IOException e) {
            // Only the request body matters; the synthetic response may not match the provider format.
        }
        return body;
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static List<List<HistoryTurn>> histories() {
        return Arrays.asList(HISTORY, Collections.emptyList());
    }

    private static String openAi(List<LLMProvider.PromptMessage> prompt) {
        StringBuilder messages = new StringBuilder("[");
        for (LLMProvider.PromptMessage message : prompt) {
            if (messages.length() > 1) {
                messages.append(",");
            }
            messages.append(json(message.role, message.content));
        }
        return messages.append("]").toString();
    }

    private static String json(String role, String content) {
        return "{\"role\":\"" + role + "\",\"content\":" + LocalEmbeddingProvider.toJsonString(content) + "}";
    }

    private static String gemini(String role, String text) {
        return "{\"role\":\"" + role + "\",\"parts\":[{\"text\":" + LocalEmbeddingProvider.toJsonString(text) + "}]}";
    }
}
