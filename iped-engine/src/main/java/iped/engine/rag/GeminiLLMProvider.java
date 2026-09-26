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
        String systemPrompt = getSystemPrompt(question);
        String userPrompt = "Context:\n" + context + "\n\nQuestion: " + question + "\n\nAnswer:";

        StringBuilder contentsJson = new StringBuilder();
        contentsJson.append("[");
        
        boolean first = true;
        if (history != null) {
            for (RAGService.HistoryTurn turn : history) {
                if (!first) contentsJson.append(",");
                contentsJson.append("{\"role\":\"user\",\"parts\":[{\"text\":").append(LocalEmbeddingProvider.toJsonString("Question: " + turn.question)).append("}]}");
                contentsJson.append(",{\"role\":\"model\",\"parts\":[{\"text\":").append(LocalEmbeddingProvider.toJsonString(turn.answer)).append("}]}");
                first = false;
            }
        }
        
        if (!first) contentsJson.append(",");
        contentsJson.append("{\"role\":\"user\",\"parts\":[{\"text\":").append(LocalEmbeddingProvider.toJsonString(userPrompt)).append("}]}");
        contentsJson.append("]");

        String requestBody = "{"
                + "\"systemInstruction\":{"
                + "  \"parts\":[{\"text\":" + LocalEmbeddingProvider.toJsonString(systemPrompt) + "}]"
                + "},"
                + "\"contents\":" + contentsJson.toString() + ","
                + "\"generationConfig\":{"
                + "  \"temperature\":0.0"
                + "}"
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
                targetUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":streamGenerateContent?alt=sse&key=" + apiKey;
            } else {
                targetUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;
            }
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(120))
                .build();

        int maxRetries = 3;
        long backoffMs = 2000;
        int lastStatusCode = -1;
        String lastResponseBody = "";

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                if (isStreaming) {
                    HttpResponse<java.io.InputStream> response = sendPrivilegedStream(client, request);
                    lastStatusCode = response.statusCode();

                    if (response.statusCode() == 200) {
                        StringBuilder fullResponse = new StringBuilder();
                        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                                new java.io.InputStreamReader(response.body(), java.nio.charset.StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                line = line.trim();
                                if (line.startsWith("data:")) {
                                    line = line.substring(5).trim();
                                }
                                if (line.isEmpty() || line.equals("[DONE]")) continue;
                                String chunk = parseGeminiResponseChunk(line);
                                if (chunk != null && !chunk.isEmpty()) {
                                    fullResponse.append(chunk);
                                    if (tokenConsumer != null) {
                                        tokenConsumer.accept(chunk);
                                    }
                                }
                            }
                        }
                        return fullResponse.toString();
                    } else if (response.statusCode() == 429 || response.statusCode() >= 500) {
                        Thread.sleep(backoffMs);
                    } else {
                        String errBody = new String(response.body().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        throw new IOException("Gemini LLM service returned status code " + response.statusCode() + ": " + errBody);
                    }
                } else {
                    HttpResponse<String> response = sendPrivileged(client, request);
                    lastStatusCode = response.statusCode();
                    lastResponseBody = response.body();

                    if (response.statusCode() == 200) {
                        return parseGeminiResponse(response.body());
                    } else if (response.statusCode() == 429 || response.statusCode() >= 500) {
                        Thread.sleep(backoffMs);
                    } else {
                        throw new IOException("Gemini LLM service returned status code " + response.statusCode() + ": " + response.body());
                    }
                }
            } catch (IOException e) {
                if (attempt == maxRetries - 1) {
                    throw e;
                }
                Thread.sleep(backoffMs);
            }
            backoffMs *= 2;
        }
        throw new IOException("Failed to generate answer from Gemini LLM after " + maxRetries + " attempts. Last status: " + lastStatusCode + ", Response: " + lastResponseBody);
    }

    public static String parseGeminiResponseChunk(String jsonChunk) {
        return parseGeminiResponseQuiet(jsonChunk);
    }

    public static String parseGeminiResponseQuiet(String json) {
        try {
            return parseGeminiResponse(json);
        } catch (Exception e) {
            return null;
        }
    }

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
