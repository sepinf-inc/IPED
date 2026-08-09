package iped.engine.rag;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Remote embedding provider implementation.
 *
 * @author Rui Sant'Ana Junior
 */
public class RemoteEmbeddingProvider implements EmbeddingProvider {

    private final String endpoint;
    private final String model;
    private final String apiKey;
    private final int maxBatchChars;
    private final HttpClient client;

    public RemoteEmbeddingProvider(String endpoint, String model, String apiKey) {
        this(endpoint, model, apiKey, 42000);
    }

    public RemoteEmbeddingProvider(String endpoint, String model, String apiKey, int maxBatchChars) {
        this.endpoint = endpoint;
        this.model = model;
        this.apiKey = apiKey;
        this.maxBatchChars = maxBatchChars > 0 ? maxBatchChars : 42000;
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Resolves official vector dimensions for well-known commercial cloud embedding models
     * (OpenAI, Cohere, etc.) when no dynamic API inspection is available.
     */
    public static int resolveRemoteVectorDimensions(String modelName) {
        if (modelName == null) return 1024;
        String lower = modelName.toLowerCase();
        if (lower.contains("text-embedding-3-small") || lower.contains("ada-002")) {
            return 1536;
        }
        if (lower.contains("text-embedding-3-large")) {
            return 3072;
        }
        if (lower.contains("embed-multilingual-v3") || lower.contains("embed-english-v3")) {
            return 1024;
        }
        if (lower.contains("embed-english-light")) {
            return 384;
        }
        return 1024;
    }

    @Override
    public java.util.List<float[]> generateEmbeddings(java.util.List<String> texts) throws IOException, InterruptedException {
        if (texts == null || texts.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        if (texts.size() == 1) {
            return java.util.Collections.singletonList(generateEmbedding(texts.get(0)));
        }

        java.util.List<float[]> allResults = new java.util.ArrayList<>(texts.size());
        int maxChars = this.maxBatchChars > 0 ? this.maxBatchChars : 42000;

        int index = 0;
        while (index < texts.size()) {
            int count = 0;
            int totalChars = 0;
            while (index + count < texts.size()) {
                String nextText = texts.get(index + count);
                int len = nextText != null ? nextText.length() : 0;
                if (count > 0 && totalChars + len > maxChars) {
                    break;
                }
                totalChars += len;
                count++;
            }

            java.util.List<String> subBatch = texts.subList(index, index + count);
            index += count;

            try {
                LocalEmbeddingProvider.totalHttpBatches.incrementAndGet();
                LocalEmbeddingProvider.totalBatchChars.addAndGet(totalChars);
                java.util.List<float[]> batchResult = processSubBatch(subBatch);
                if (batchResult != null && batchResult.size() == subBatch.size()) {
                    allResults.addAll(batchResult);
                    continue;
                }
            } catch (Exception e) {
                // Fallback for items individually if batch fails
            }

            for (String txt : subBatch) {
                allResults.add(generateEmbedding(txt));
            }
        }

        return allResults;
    }

    private java.util.List<float[]> processSubBatch(java.util.List<String> subBatch) throws IOException, InterruptedException {
        StringBuilder inputsJson = new StringBuilder("[");
        for (int i = 0; i < subBatch.size(); i++) {
            String t = subBatch.get(i);
            if (t != null && t.length() > 4000) {
                t = t.substring(0, 4000);
            }
            inputsJson.append(LocalEmbeddingProvider.toJsonString(t));
            if (i < subBatch.size() - 1) {
                inputsJson.append(",");
            }
        }
        inputsJson.append("]");

        String requestBody = "{\"model\":\"" + model + "\",\"input\":" + inputsJson.toString() + "}";

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(30));

        if (endpoint.contains("azure.com")) {
            requestBuilder.header("api-key", apiKey);
        } else if (apiKey != null && !apiKey.trim().isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + apiKey.trim());
        }

        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = sendPrivileged(client, request);

        if (response.statusCode() == 200) {
            return LocalEmbeddingProvider.parseBatchEmbeddings(response.body());
        }
        throw new IOException("Remote batch embedding status code " + response.statusCode() + ": " + response.body());
    }

    @Override
    public float[] generateEmbedding(String text) throws IOException, InterruptedException {
        String requestBody = "{\"model\":\"" + model + "\",\"input\":" + LocalEmbeddingProvider.toJsonString(text) + "}";

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(30));

        if (endpoint.contains("azure.com")) {
            requestBuilder.header("api-key", apiKey);
        } else if (apiKey != null && !apiKey.trim().isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + apiKey.trim());
        }

        HttpRequest request = requestBuilder.build();
        int maxRetries = 5;
        long backoffMs = 2000;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                HttpResponse<String> response = sendPrivileged(client, request);

                if (response.statusCode() == 200) {
                    return LocalEmbeddingProvider.parseEmbeddingsArray(response.body());
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
                    throw new IOException("Remote embedding service returned status code " + response.statusCode() + ": " + response.body());
                }
            } catch (IOException e) {
                if (attempt == maxRetries - 1) {
                    throw e;
                }
                Thread.sleep(backoffMs);
            }
            backoffMs *= 2;
        }
        throw new IOException("Failed to generate embedding after " + maxRetries + " attempts due to rate limit or connection errors.");
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
