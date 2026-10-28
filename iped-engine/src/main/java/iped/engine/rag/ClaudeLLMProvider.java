package iped.engine.rag;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * LLM provider implementation that interacts with Anthropic's Claude API
 * (e.g., claude-3-5-sonnet-20240620, etc.).
 *
 * @author Rui Sant'Ana Junior
 */
public class ClaudeLLMProvider implements LLMProvider {

    private final String model;
    private final String apiKey;
    private final String customEndpoint;
    private final HttpClient client;

    public ClaudeLLMProvider(String customEndpoint, String model, String apiKey) {
        this.customEndpoint = customEndpoint;
        this.model = (model == null || model.isEmpty()) ? "claude-sonnet-4-6" : model;
        this.apiKey = apiKey;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public String generateAnswer(String question, String context) throws IOException, InterruptedException {
        return generateAnswer(question, context, java.util.Collections.emptyList());
    }

    @Override
    public String generateAnswer(String question, String context, java.util.List<RAGService.HistoryTurn> history) throws IOException, InterruptedException {
        return generateAnswerStream(question, context, history, null);
    }

    @Override
    public String generateAnswerStream(String question, String context, java.util.List<RAGService.HistoryTurn> history,
            java.util.function.Consumer<String> tokenConsumer) throws IOException, InterruptedException {
        // Claude takes the system instruction apart from the conversation messages.
        java.util.List<PromptMessage> messages = buildPrompt(question, context, history);
        String systemPrompt = messages.get(0).content;
        boolean isStreaming = (tokenConsumer != null);

        String requestBody = "{"
                + "\"model\":\"" + model + "\","
                + "\"max_tokens\":4096,"
                + "\"system\":" + LocalEmbeddingProvider.toJsonString(systemPrompt) + ","
                + "\"messages\":" + LLMProvider.toChatMessagesJson(messages.subList(1, messages.size())) + ","
                + "\"temperature\":0.0"
                + (isStreaming ? ",\"stream\":true" : "")
                + "}";


        String targetUrl;
        if (customEndpoint != null && !customEndpoint.trim().isEmpty()) {
            targetUrl = customEndpoint;
        } else {
            targetUrl = "https://api.anthropic.com/v1/messages";
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(60))
                .build();

        int maxRetries = 3;
        long backoffMs = 2000;
        int lastStatusCode = -1;
        String lastResponseBody = "";

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            boolean answerStarted = false;
            try {
                int statusCode;
                if (isStreaming) {
                    HttpResponse<java.io.InputStream> response = sendPrivilegedStream(client, request);
                    statusCode = response.statusCode();
                    try (java.io.InputStream body = response.body()) {
                        if (statusCode == 200) {
                            // Text may already be on screen: a failure from here on is not retried.
                            answerStarted = true;
                            return LLMEventStream.read(body, STREAM_EVENTS, tokenConsumer);
                        }
                        lastResponseBody = new String(body.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    }
                } else {
                    HttpResponse<String> response = sendPrivileged(client, request);
                    statusCode = response.statusCode();
                    lastResponseBody = response.body();
                    if (statusCode == 200) {
                        return parseClaudeResponse(response.body());
                    }
                }
                lastStatusCode = statusCode;

                if (statusCode == 429) {
                    Thread.sleep(backoffMs);
                } else if (statusCode >= 500) {
                    Thread.sleep(backoffMs);
                } else {
                    throw new IOException("Claude LLM service returned status code " + statusCode + ": " + lastResponseBody);
                }
            } catch (IOException e) {
                if (attempt == maxRetries - 1 || answerStarted) {
                    throw e;
                }
                Thread.sleep(backoffMs);
            }
            backoffMs *= 2;
        }
        throw new IOException("Failed to generate answer from Claude LLM after " + maxRetries + " attempts. Last status: " + lastStatusCode + ", Response: " + lastResponseBody);
    }

    public static String parseClaudeResponse(String json) throws IOException {
        int index = json.indexOf("\"text\"");
        if (index == -1) {
            throw new IOException("Failed to parse Claude response. Could not find 'text' key in response JSON: " + json);
        }
        index = json.indexOf(":", index);
        if (index == -1) {
            throw new IOException("Failed to parse Claude response. Invalid JSON format near 'text' in: " + json);
        }
        index = json.indexOf("\"", index);
        if (index == -1) {
            throw new IOException("Failed to parse Claude response. Could not find start of text string in: " + json);
        }

        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = index + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    default: sb.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        throw new IOException("Failed to parse Claude response. Unclosed text string in response JSON: " + json);
    }

    /**
     * Messages API stream: the text comes in {@code content_block_delta} events, and
     * {@code message_delta} tells why generation stopped.
     */
    private static final LLMEventStream.Events STREAM_EVENTS = new LLMEventStream.Events() {
        @Override
        public String text(com.google.gson.JsonObject event) throws IOException {
            String type = LLMEventStream.string(event, "type");
            if ("error".equals(type)) {
                throw new IOException("Claude LLM service returned an error: " + event.get("error"));
            }
            com.google.gson.JsonObject delta = LLMEventStream.object(event, "delta");
            if ("content_block_delta".equals(type) && "text_delta".equals(LLMEventStream.string(delta, "type"))) {
                return LLMEventStream.string(delta, "text");
            }
            return null;
        }

        @Override
        public boolean cutOff(com.google.gson.JsonObject event) {
            return "message_delta".equals(LLMEventStream.string(event, "type"))
                    && "max_tokens".equals(LLMEventStream.string(LLMEventStream.object(event, "delta"), "stop_reason"));
        }
    };

    private static HttpResponse<java.io.InputStream> sendPrivilegedStream(HttpClient client, HttpRequest request) throws IOException, InterruptedException {
        try {
            return java.security.AccessController.doPrivileged(
                    (java.security.PrivilegedExceptionAction<HttpResponse<java.io.InputStream>>) () -> client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            );
        } catch (java.security.PrivilegedActionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            if (cause instanceof InterruptedException) throw (InterruptedException) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IOException(cause);
        }
    }

    private static HttpResponse<String> sendPrivileged(HttpClient client, HttpRequest request) throws IOException, InterruptedException {
        try {
            return java.security.AccessController.doPrivileged(
                    (java.security.PrivilegedExceptionAction<HttpResponse<String>>) () -> client.send(request, HttpResponse.BodyHandlers.ofString())
            );
        } catch (java.security.PrivilegedActionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            if (cause instanceof InterruptedException) throw (InterruptedException) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IOException(cause);
        }
    }
}
