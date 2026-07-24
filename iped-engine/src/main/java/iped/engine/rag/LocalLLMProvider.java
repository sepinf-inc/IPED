package iped.engine.rag;

/**
 * Local LLM provider implementation connecting to Ollama chat endpoints.
 *
 * @author Rui Sant'Ana Junior
 */
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class LocalLLMProvider implements LLMProvider {

    private final String endpoint;
    private final String model;
    private final HttpClient client;

    public LocalLLMProvider(String endpoint, String model) {
        this.endpoint = endpoint;
        this.model = model;
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
        
        int contextWindow = 4096;
        try {
            RAGService rag = RAGService.getInstance();
            if (rag != null && rag.getConfig() != null) {
                contextWindow = rag.getConfig().getLlmContextWindow();
            }
        } catch (Exception ignore) {}

        StringBuilder messagesJson = new StringBuilder();
        messagesJson.append("[");
        
        // 1. Add system prompt as a system message
        messagesJson.append("{\"role\":\"system\",\"content\":").append(LocalEmbeddingProvider.toJsonString(systemPrompt)).append("}");
        
        // 2. Add history turns
        if (history != null) {
            for (RAGService.HistoryTurn turn : history) {
                messagesJson.append(",{\"role\":\"user\",\"content\":").append(LocalEmbeddingProvider.toJsonString("Question: " + turn.question)).append("}");
                messagesJson.append(",{\"role\":\"assistant\",\"content\":").append(LocalEmbeddingProvider.toJsonString(turn.answer)).append("}");
            }
        }
        
        // 3. Add clean user prompt
        String userPrompt = "Context:\n" + context + "\n\nQuestion: " + question + "\n\nAnswer:";
        messagesJson.append(",{\"role\":\"user\",\"content\":").append(LocalEmbeddingProvider.toJsonString(userPrompt)).append("}");
        messagesJson.append("]");

        String requestBody = "{"
                + "\"model\":\"" + model + "\","
                + "\"messages\":" + messagesJson.toString() + ","
                + "\"options\":{\"num_ctx\":" + contextWindow + ",\"temperature\":0.0},"
                + "\"stream\":false"
                + "}";

        String resolvedUri = endpoint;
        if (resolvedUri.contains("/v1")) {
            resolvedUri = resolvedUri.replace("/v1", "/api/chat");
        } else if (!resolvedUri.endsWith("/api/chat")) {
            resolvedUri = resolvedUri.endsWith("/") ? resolvedUri + "api/chat" : resolvedUri + "/api/chat";
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(resolvedUri))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(300))
                .build();

        HttpResponse<String> response = sendPrivileged(client, request);

        if (response.statusCode() != 200) {
            throw new IOException("Local LLM service returned status code " + response.statusCode() + ": " + response.body());
        }

        return parseChatCompletionResponse(response.body());
    }

    public static String parseChatCompletionResponse(String json) throws IOException {
        int index = json.indexOf("\"content\"");
        if (index == -1) {
            throw new IOException("Failed to parse LLM response. Could not find 'content' key in response JSON: " + json);
        }
        index = json.indexOf(":", index);
        if (index == -1) {
            throw new IOException("Failed to parse LLM response. Invalid JSON format near 'content' in: " + json);
        }
        index = json.indexOf("\"", index);
        if (index == -1) {
            throw new IOException("Failed to parse LLM response. Could not find start of content string in: " + json);
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
        throw new IOException("Failed to parse LLM response. Unclosed content string in response JSON: " + json);
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
