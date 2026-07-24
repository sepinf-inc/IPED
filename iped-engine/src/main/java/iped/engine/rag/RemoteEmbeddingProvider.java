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
    private final HttpClient client;

    public RemoteEmbeddingProvider(String endpoint, String model, String apiKey) {
        this.endpoint = endpoint;
        this.model = model;
        this.apiKey = apiKey;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
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
