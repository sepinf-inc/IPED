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
        String systemPrompt = getSystemPrompt(question);
        String userPrompt = "Context:\n" + context + "\n\nQuestion: " + question + "\n\nAnswer:";

        StringBuilder messagesJson = new StringBuilder();
        messagesJson.append("[");
        
        boolean first = true;
        if (history != null) {
            for (RAGService.HistoryTurn turn : history) {
                if (!first) messagesJson.append(",");
                messagesJson.append("{\"role\":\"user\",\"content\":").append(LocalEmbeddingProvider.toJsonString("Question: " + turn.question)).append("}");
                messagesJson.append(",{\"role\":\"assistant\",\"content\":").append(LocalEmbeddingProvider.toJsonString(turn.answer)).append("}");
                first = false;
            }
        }
        
        if (!first) messagesJson.append(",");
        messagesJson.append("{\"role\":\"user\",\"content\":").append(LocalEmbeddingProvider.toJsonString(userPrompt)).append("}");
        messagesJson.append("]");

        String requestBody = "{"
                + "\"model\":\"" + model + "\","
                + "\"max_tokens\":1024,"
                + "\"system\":" + LocalEmbeddingProvider.toJsonString(systemPrompt) + ","
                + "\"messages\":" + messagesJson.toString() + ","
                + "\"temperature\":0.0"
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
            try {
                HttpResponse<String> response = sendPrivileged(client, request);
                lastStatusCode = response.statusCode();
                lastResponseBody = response.body();

                if (response.statusCode() == 200) {
                    return parseClaudeResponse(response.body());
                } else if (response.statusCode() == 429) {
                    Thread.sleep(backoffMs);
                } else if (response.statusCode() >= 500) {
                    Thread.sleep(backoffMs);
                } else {
                    throw new IOException("Claude LLM service returned status code " + response.statusCode() + ": " + response.body());
                }
            } catch (IOException e) {
                if (attempt == maxRetries - 1) {
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
