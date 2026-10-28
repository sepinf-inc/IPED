package iped.engine.rag;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Remote LLM provider implementation.
 *
 * @author Rui Sant'Ana Junior
 */
public class RemoteLLMProvider implements LLMProvider {

    private final String endpoint;
    private final String model;
    private final String apiKey;
    private final HttpClient client;

    public RemoteLLMProvider(String endpoint, String model, String apiKey) {
        this.endpoint = endpoint;
        this.model = model;
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
        String messagesJson = LLMProvider.toChatMessagesJson(buildPrompt(question, context, history));
        boolean isStreaming = (tokenConsumer != null);

        StringBuilder bodyJson = new StringBuilder();
        bodyJson.append("{\"model\":\"").append(model).append("\",\"messages\":").append(messagesJson);
        if (!model.startsWith("o1") && !model.startsWith("o3")) {
            bodyJson.append(",\"temperature\":0.0");
        }
        if (isStreaming) {
            bodyJson.append(",\"stream\":true");
        }
        bodyJson.append("}");
        String requestBody = bodyJson.toString();

        String targetUrl = endpoint.endsWith("/chat/completions") ? endpoint : endpoint + "/chat/completions";
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(120));

        if (endpoint.contains("azure.com")) {
            requestBuilder.header("api-key", apiKey);
        } else if (apiKey != null && !apiKey.trim().isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + apiKey.trim());
        }

        HttpRequest request = requestBuilder.build();
        int maxRetries = 4;
        long backoffMs = 2000;
        int lastStatusCode = -1;
        String lastResponseBody = "";

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            boolean answerStarted = false;
            try {
                HttpResponse<?> response;
                if (isStreaming) {
                    HttpResponse<java.io.InputStream> streamResponse = sendPrivilegedStream(client, request);
                    response = streamResponse;
                    try (java.io.InputStream body = streamResponse.body()) {
                        if (streamResponse.statusCode() == 200) {
                            // Text may already be on screen: a failure from here on is not retried.
                            answerStarted = true;
                            return LLMEventStream.read(body, STREAM_EVENTS, tokenConsumer);
                        }
                        lastResponseBody = new String(body.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    }
                } else {
                    HttpResponse<String> stringResponse = sendPrivileged(client, request);
                    response = stringResponse;
                    lastResponseBody = stringResponse.body();
                    if (stringResponse.statusCode() == 200) {
                        return LocalLLMProvider.parseChatCompletionResponse(stringResponse.body());
                    }
                }
                lastStatusCode = response.statusCode();

                if (response.statusCode() == 429) {
                    String retryAfter = response.headers().firstValue("Retry-After").orElse("");
                    long sleepMs = backoffMs;
                    if (!retryAfter.isEmpty()) {
                        try {
                            sleepMs = Long.parseLong(retryAfter) * 1000;
                        } catch (NumberFormatException ignored) {}
                    }
                    Thread.sleep(sleepMs);
                } else if (response.statusCode() >= 500) {
                    Thread.sleep(backoffMs);
                } else {
                    throw new IOException("Remote LLM service returned status code " + response.statusCode() + ": " + lastResponseBody);
                }
            } catch (IOException e) {
                if (attempt == maxRetries - 1 || answerStarted) {
                    throw e;
                }
                Thread.sleep(backoffMs);
            }
            backoffMs *= 2;
        }
        throw new IOException("Failed to generate answer from LLM after " + maxRetries + " attempts. Last status: " + lastStatusCode + ", Response: " + lastResponseBody);
    }

    /**
     * Chat Completions stream: the text comes in {@code choices[0].delta.content},
     * and {@code finish_reason} tells why generation stopped.
     */
    private static final LLMEventStream.Events STREAM_EVENTS = new LLMEventStream.Events() {
        @Override
        public String text(com.google.gson.JsonObject event) throws IOException {
            if (event.has("error")) {
                throw new IOException("Remote LLM service returned an error: " + event.get("error"));
            }
            return LLMEventStream.string(LLMEventStream.object(firstChoice(event), "delta"), "content");
        }

        @Override
        public boolean cutOff(com.google.gson.JsonObject event) {
            return "length".equals(LLMEventStream.string(firstChoice(event), "finish_reason"));
        }

        private com.google.gson.JsonObject firstChoice(com.google.gson.JsonObject event) {
            com.google.gson.JsonElement choices = event.get("choices");
            if (choices == null || !choices.isJsonArray() || choices.getAsJsonArray().size() == 0
                    || !choices.getAsJsonArray().get(0).isJsonObject()) {
                return null;
            }
            return choices.getAsJsonArray().get(0).getAsJsonObject();
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
