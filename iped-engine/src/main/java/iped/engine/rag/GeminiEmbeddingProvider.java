package iped.engine.rag;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Embedding provider that calls the Google Generative Language API
 * (Gemini Embeddings).
 *
 * @author Rui Sant'Ana Junior
 *
 * <p>Endpoint used:
 * {@code POST https://generativelanguage.googleapis.com/v1beta/models/{model}:embedContent?key={apiKey}}
 *
 * <p>Request body:
 * <pre>
 * {
 *   "model": "models/text-embedding-004",
 *   "content": { "parts": [{ "text": "..." }] }
 * }
 * </pre>
 *
 * <p>Response:
 * <pre>
 * {
 *   "embedding": { "values": [0.123, ...] }
 * }
 * </pre>
 *
 * Recommended model: {@code text-embedding-004} (768 dimensions).
 * Set {@code embeddingDimensions = 768} in RAGConfig.txt when using this model.
 */
public class GeminiEmbeddingProvider implements EmbeddingProvider {

    private static final String BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/";

    private final String model;
    private final String apiKey;
    private final String customEndpoint;
    private final int outputDimensionality;
    private final HttpClient client;

    public GeminiEmbeddingProvider(String customEndpoint, String model, String apiKey, int outputDimensionality) {
        this.customEndpoint = customEndpoint;
        this.model = (model == null || model.isBlank()) ? "gemini-embedding-001" : model;
        this.apiKey = apiKey;
        this.outputDimensionality = outputDimensionality;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public GeminiEmbeddingProvider(String customEndpoint, String model, String apiKey) {
        this(customEndpoint, model, apiKey, 768);
    }

    @Override
    public float[] generateEmbedding(String text) throws IOException, InterruptedException {
        String targetUrl;
        if (customEndpoint != null && !customEndpoint.isBlank()) {
            targetUrl = customEndpoint;
        } else {
            // model may be given as "text-embedding-004" or "models/text-embedding-004"
            String modelPath = model.startsWith("models/") ? model : "models/" + model;
            targetUrl = BASE_URL + modelPath.replaceFirst("models/", "")
                    + ":embedContent?key=" + apiKey;
        }

        String modelName = model.startsWith("models/") ? model.substring(7) : model;
        String requestBody = "{"
                + "\"model\":\"models/" + modelName + "\","
                + "\"content\":{\"parts\":[{\"text\":" + LocalEmbeddingProvider.toJsonString(text) + "}]}";
        if (outputDimensionality > 0 && !"embedding-001".equalsIgnoreCase(modelName)) {
            requestBody += ",\"outputDimensionality\":" + outputDimensionality;
        }
        requestBody += "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(30))
                .build();

        int maxRetries = 5;
        long backoffMs = 2000;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                HttpResponse<String> response = sendPrivileged(client, request);

                if (response.statusCode() == 200) {
                    return parseGeminiEmbeddingResponse(response.body());
                } else if (response.statusCode() == 429) {
                    String retryAfter = response.headers().firstValue("Retry-After").orElse("");
                    long sleepMs = backoffMs;
                    if (!retryAfter.isBlank()) {
                        try { sleepMs = Long.parseLong(retryAfter) * 1000; }
                        catch (NumberFormatException ignore) {}
                    }
                    Thread.sleep(sleepMs);
                } else if (response.statusCode() >= 500) {
                    Thread.sleep(backoffMs);
                } else {
                    throw new IOException("Gemini Embedding API returned status "
                            + response.statusCode() + ": " + response.body());
                }
            } catch (IOException e) {
                if (attempt == maxRetries - 1) throw e;
                Thread.sleep(backoffMs);
            }
            backoffMs *= 2;
        }
        throw new IOException("Failed to generate Gemini embedding after " + maxRetries + " attempts.");
    }

    /**
     * Parses the {@code embedding.values} array from the Gemini API JSON response.
     *
     * Expected structure:
     * <pre>{"embedding":{"values":[0.123,-0.456,...]}}</pre>
     */
    static float[] parseGeminiEmbeddingResponse(String json) throws IOException {
        // Find "values":[
        int valuesIdx = json.indexOf("\"values\"");
        if (valuesIdx == -1) {
            throw new IOException("Gemini embedding response missing 'values' field: " + json);
        }
        int arrStart = json.indexOf('[', valuesIdx);
        int arrEnd   = json.indexOf(']', arrStart);
        if (arrStart == -1 || arrEnd == -1) {
            throw new IOException("Gemini embedding response has malformed 'values' array: " + json);
        }

        String arrContent = json.substring(arrStart + 1, arrEnd).trim();
        if (arrContent.isEmpty()) {
            throw new IOException("Gemini embedding response returned empty values array.");
        }

        String[] parts = arrContent.split(",");
        List<Float> floats = new ArrayList<>(parts.length);
        for (String part : parts) {
            String t = part.trim();
            if (!t.isEmpty()) {
                floats.add(Float.parseFloat(t));
            }
        }

        float[] result = new float[floats.size()];
        for (int i = 0; i < floats.size(); i++) result[i] = floats.get(i);
        return result;
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
