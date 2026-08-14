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
        String systemPrompt = getSystemPrompt(question);
        String userPrompt = "Context:\n" + context + "\n\nQuestion: " + question + "\n\nAnswer:";

        StringBuilder messagesJson = new StringBuilder();
        messagesJson.append("[");
        messagesJson.append("{\"role\":\"system\",\"content\":").append(LocalEmbeddingProvider.toJsonString(systemPrompt)).append("}");
        
        if (history != null) {
            for (RAGService.HistoryTurn turn : history) {
                messagesJson.append(",{\"role\":\"user\",\"content\":").append(LocalEmbeddingProvider.toJsonString("Question: " + turn.question)).append("}");
                messagesJson.append(",{\"role\":\"assistant\",\"content\":").append(LocalEmbeddingProvider.toJsonString(turn.answer)).append("}");
            }
        }
        
        messagesJson.append(",{\"role\":\"user\",\"content\":").append(LocalEmbeddingProvider.toJsonString(userPrompt)).append("}");
        messagesJson.append("]");

        StringBuilder bodyJson = new StringBuilder();
        bodyJson.append("{\"model\":\"").append(model).append("\",\"messages\":").append(messagesJson.toString());
        if (!model.startsWith("o1") && !model.startsWith("o3")) {
            bodyJson.append(",\"temperature\":0.0");
        }
        bodyJson.append("}");
        String requestBody = bodyJson.toString();

        String targetUrl = endpoint.endsWith("/chat/completions") ? endpoint : endpoint + "/chat/completions";
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(60));

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

        for (attempt = 0; attempt < maxRetries; attempt++) {
            try {
                HttpResponse<String> response = sendPrivileged(client, request);
                lastStatusCode = response.statusCode();
                lastResponseBody = response.body();

                if (response.statusCode() == 200) {
                    return LocalLLMProvider.parseChatCompletionResponse(response.body());
                } else if (response.statusCode() == 429) {
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
                    throw new IOException("Remote LLM service returned status code " + response.statusCode() + ": " + response.body());
                }
            } catch (IOException e) {
                if (attempt == maxRetries - 1) {
                    throw e;
                }
                Thread.sleep(backoffMs);
            }
            backoffMs *= 2;
        }
        throw new IOException("Failed to generate answer from LLM after " + maxRetries + " attempts. Last status: " + lastStatusCode + ", Response: " + lastResponseBody);
    }
    
    // Declared attempt for loop scope clarity in case compilations check this
    private int attempt;

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
