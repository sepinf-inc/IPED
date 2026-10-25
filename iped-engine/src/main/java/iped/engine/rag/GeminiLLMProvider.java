package iped.engine.rag;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class GeminiLLMProvider implements LLMProvider {

    private final String model;
    private final String apiKey;
    private final String customEndpoint;
    private final HttpClient client;

    public GeminiLLMProvider(String customEndpoint, String model, String apiKey) {
        this.customEndpoint = customEndpoint;
        this.model = (model == null || model.isEmpty()) ? "gemini-1.5-flash" : model;
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
        // Gemini takes the system instruction apart and names the assistant role "model".
        java.util.List<PromptMessage> messages = buildPrompt(question, context, history);
        String systemPrompt = messages.get(0).content;

        StringBuilder contentsJson = new StringBuilder();
        contentsJson.append("[");
        for (PromptMessage message : messages.subList(1, messages.size())) {
            if (contentsJson.length() > 1) contentsJson.append(",");
            String role = ROLE_ASSISTANT.equals(message.role) ? "model" : message.role;
            contentsJson.append("{\"role\":\"").append(role).append("\",\"parts\":[{\"text\":")
                    .append(LocalEmbeddingProvider.toJsonString(message.content)).append("}]}");
        }
        contentsJson.append("]");

        String requestBody = "{"
                + "\"systemInstruction\":{"
                + "  \"parts\":[{\"text\":" + LocalEmbeddingProvider.toJsonString(systemPrompt) + "}]"
                + "},"
                + "\"contents\":" + contentsJson.toString() + ","
                + "\"generationConfig\":{"
                + "  \"temperature\":0.0"
                + "},"
                + "\"safetySettings\":["
                + "  {\"category\":\"HARM_CATEGORY_HARASSMENT\",\"threshold\":\"BLOCK_NONE\"},"
                + "  {\"category\":\"HARM_CATEGORY_HATE_SPEECH\",\"threshold\":\"BLOCK_NONE\"},"
                + "  {\"category\":\"HARM_CATEGORY_SEXUALLY_EXPLICIT\",\"threshold\":\"BLOCK_NONE\"},"
                + "  {\"category\":\"HARM_CATEGORY_DANGEROUS_CONTENT\",\"threshold\":\"BLOCK_NONE\"}"
                + "]"
                + "}";

        boolean isStreaming = (tokenConsumer != null);
        String targetUrl;
        if (customEndpoint != null && !customEndpoint.trim().isEmpty()) {
            targetUrl = customEndpoint;
            if (isStreaming && targetUrl.contains(":generateContent")) {
                targetUrl = targetUrl.replace(":generateContent", ":streamGenerateContent?alt=sse");
            }
        } else {
            if (isStreaming) {
                targetUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":streamGenerateContent?alt=sse";
            } else {
                targetUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";
            }
        }

        // The key goes in a header, never in the URL, so it does not leak into logs or proxies.
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(120));
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            requestBuilder.header("x-goog-api-key", apiKey.trim());
        }
        HttpRequest request = requestBuilder.build();

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
                        return parseGeminiResponse(response.body());
                    }
                }
                lastStatusCode = statusCode;

                if (statusCode == 429 || statusCode >= 500) {
                    Thread.sleep(backoffMs);
                } else {
                    throw new IOException("Gemini LLM service returned status code " + statusCode + ": " + lastResponseBody);
                }
            } catch (IOException e) {
                if (attempt == maxRetries - 1 || answerStarted) {
                    throw e;
                }
                Thread.sleep(backoffMs);
            }
            backoffMs *= 2;
        }
        throw new IOException("Failed to generate answer from Gemini LLM after " + maxRetries + " attempts. Last status: " + lastStatusCode + ", Response: " + lastResponseBody);
    }

    /**
     * streamGenerateContent with alt=sse: each event is a partial response with the
     * text in {@code candidates[0].content.parts}, and {@code finishReason} tells
     * why generation stopped.
     */
    private static final LLMEventStream.Events STREAM_EVENTS = new LLMEventStream.Events() {
        @Override
        public String text(com.google.gson.JsonObject event) throws IOException {
            if (event.has("error")) {
                throw new IOException("Gemini LLM service returned an error: " + event.get("error"));
            }
            com.google.gson.JsonObject content = LLMEventStream.object(firstCandidate(event), "content");
            com.google.gson.JsonElement parts = content == null ? null : content.get("parts");
            if (parts == null || !parts.isJsonArray()) {
                return null;
            }
            StringBuilder text = new StringBuilder();
            for (com.google.gson.JsonElement part : parts.getAsJsonArray()) {
                String partText = part.isJsonObject() ? LLMEventStream.string(part.getAsJsonObject(), "text") : null;
                if (partText != null) {
                    text.append(partText);
                }
            }
            return text.toString();
        }

        @Override
        public boolean cutOff(com.google.gson.JsonObject event) {
            return "MAX_TOKENS".equals(LLMEventStream.string(firstCandidate(event), "finishReason"));
        }

        private com.google.gson.JsonObject firstCandidate(com.google.gson.JsonObject event) {
            com.google.gson.JsonElement candidates = event.get("candidates");
            if (candidates == null || !candidates.isJsonArray() || candidates.getAsJsonArray().size() == 0
                    || !candidates.getAsJsonArray().get(0).isJsonObject()) {
                return null;
            }
            return candidates.getAsJsonArray().get(0).getAsJsonObject();
        }
    };

    public static String parseGeminiResponse(String json) throws IOException {
        int index = json.indexOf("\"text\"");
        if (index == -1) {
            throw new IOException("Failed to parse Gemini response. Could not find 'text' key in response JSON: " + json);
        }
        index = json.indexOf(":", index);
        if (index == -1) {
            throw new IOException("Failed to parse Gemini response. Invalid JSON format near 'text' in: " + json);
        }
        index = json.indexOf("\"", index);
        if (index == -1) {
            throw new IOException("Failed to parse Gemini response. Could not find start of text string in: " + json);
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
        throw new IOException("Failed to parse Gemini response. Unclosed text string in response JSON: " + json);
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
}
