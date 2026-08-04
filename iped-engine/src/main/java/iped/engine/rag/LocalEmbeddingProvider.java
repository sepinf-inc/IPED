package iped.engine.rag;

/**
 * Local embedding provider implementation connecting to Ollama endpoints.
 *
 * @author Rui Sant'Ana Junior
 */
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class LocalEmbeddingProvider implements EmbeddingProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalEmbeddingProvider.class);

    private final String endpoint;
    private final String model;
    private final int subBatchSize;
    private final HttpClient client;

    public LocalEmbeddingProvider(String endpoint, String model, int subBatchSize) {
        this.endpoint = endpoint;
        this.model = model;
        this.subBatchSize = subBatchSize;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public float[] generateEmbedding(String text) throws IOException, InterruptedException {
        if (text != null && text.length() > 4000) {
            text = text.substring(0, 4000);
        }
        try {
            return doGenerateEmbedding(text);
        } catch (IOException e) {
            // Auto-fallback for dense text (e.g. minified JSON, base64 hashes) that exceeds
            // the model's tokenizer context window despite the 4000-char cap.
            // Detected via Ollama's error message substring "exceeds the context length".
            // NOTE: If a future Ollama version changes this error message, the fallback
            // will not trigger and the IOException will be re-thrown normally (safe behavior).
            if (e.getMessage() != null && e.getMessage().contains("exceeds the context length") && text != null && text.length() > 1500) {
                LOGGER.warn("Embedding text exceeded model context window, retrying with 1500 chars truncation");
                return doGenerateEmbedding(text.substring(0, 1500));
            }
            throw e;
        }
    }

    private float[] doGenerateEmbedding(String text) throws IOException, InterruptedException {
        boolean isOllamaNative = endpoint.contains("/api/embeddings");
        String requestBody;

        if (isOllamaNative) {
            requestBody = "{\"model\":\"" + model + "\",\"prompt\":" + toJsonString(text) + "}";
        } else {
            requestBody = "{\"model\":\"" + model + "\",\"input\":" + toJsonString(text) + "}";
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = sendPrivileged(client, request);

        if (response.statusCode() == 200) {
            return parseEmbeddingsArray(response.body());
        }
        throw new IOException("Local embedding service returned status code " + response.statusCode() + ": " + response.body());
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
        int batchSize = this.subBatchSize; // Configurable via RAGConfig.txt (default: 16)

        for (int start = 0; start < texts.size(); start += batchSize) {
            int end = Math.min(start + batchSize, texts.size());
            java.util.List<String> subBatch = texts.subList(start, end);
            try {
                java.util.List<float[]> batchResult = processSubBatch(subBatch, 30);
                if (batchResult != null && batchResult.size() == subBatch.size()) {
                    allResults.addAll(batchResult);
                    continue;
                }
            } catch (Exception e) {
                LOGGER.warn("Local embedding sub-batch failed ({}), falling back to individual item processing", e.getMessage());
                // Socket exhaustion / TCP port buffer relief pause
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }

            // Fallback for items individually if batch fails or returns invalid size
            for (String txt : subBatch) {
                allResults.add(generateEmbedding(txt));
            }
        }

        return allResults;
    }

    private java.util.List<float[]> processSubBatch(java.util.List<String> subBatch, int timeoutSeconds) throws IOException, InterruptedException {
        String batchUrl = endpoint;
        if (endpoint.contains("/api/embeddings") || endpoint.contains("/api/embed")) {
            int idx = endpoint.indexOf("/api/");
            if (idx != -1) {
                batchUrl = endpoint.substring(0, idx) + "/v1/embeddings";
            }
        }

        StringBuilder inputsJson = new StringBuilder("[");
        for (int i = 0; i < subBatch.size(); i++) {
            String t = subBatch.get(i);
            // Cap at 4000 chars to cover full 3500 chunkSize without truncating evidence
            if (t != null && t.length() > 4000) {
                t = t.substring(0, 4000);
            }
            inputsJson.append(toJsonString(t));
            if (i < subBatch.size() - 1) {
                inputsJson.append(",");
            }
        }
        inputsJson.append("]");

        String requestBody = "{\"model\":\"" + model + "\",\"input\":" + inputsJson.toString() + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(batchUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();

        HttpResponse<String> response = sendPrivileged(client, request);

        if (response.statusCode() == 200) {
            java.util.List<float[]> res = parseBatchEmbeddings(response.body());
            if (res != null && res.size() == subBatch.size()) {
                return res;
            }
        }
        throw new IOException("Local embedding batch returned status code " + response.statusCode() + ": " + response.body());
    }

    private static final Gson GSON = new Gson();

    public static java.util.List<float[]> parseBatchEmbeddings(String json) throws IOException {
        if (json == null) return null;
        java.util.List<float[]> list = new java.util.ArrayList<>();
        try {
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            
            // OpenAI format: "data": [ {"embedding": [...], "index": 0}, ... ]
            if (obj.has("data")) {
                JsonArray data = obj.getAsJsonArray("data");
                java.util.Map<Integer, float[]> indexMap = new java.util.TreeMap<>();
                for (int i = 0; i < data.size(); i++) {
                    JsonObject item = data.get(i).getAsJsonObject();
                    int index = item.has("index") ? item.get("index").getAsInt() : i;
                    if (item.has("embedding")) {
                        JsonArray emb = item.getAsJsonArray("embedding");
                        float[] vec = new float[emb.size()];
                        for (int j = 0; j < emb.size(); j++) {
                            vec[j] = emb.get(j).getAsFloat();
                        }
                        indexMap.put(index, vec);
                    }
                }
                for (float[] vec : indexMap.values()) {
                    list.add(vec);
                }
                return list;
            }
            
            // Ollama native format: "embeddings": [[...], [...]]
            if (obj.has("embeddings")) {
                JsonArray embs = obj.getAsJsonArray("embeddings");
                for (int i = 0; i < embs.size(); i++) {
                    JsonArray emb = embs.get(i).getAsJsonArray();
                    float[] vec = new float[emb.size()];
                    for (int j = 0; j < emb.size(); j++) {
                        vec[j] = emb.get(j).getAsFloat();
                    }
                    list.add(vec);
                }
                return list;
            }
            
            return null;
        } catch (Exception e) {
            throw new IOException("Failed to parse batch embeddings from JSON: " + json, e);
        }
    }

    public static String toJsonString(String text) {
        if (text == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        String t = "000" + Integer.toHexString(c);
                        sb.append("\\u" + t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    public static float[] parseEmbeddingsArray(String json) throws IOException {
        try {
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            JsonArray arr = null;
            if (obj.has("embedding")) {
                arr = obj.getAsJsonArray("embedding");
            } else if (obj.has("embeddings")) {
                JsonArray outer = obj.getAsJsonArray("embeddings");
                if (outer.size() > 0) {
                    arr = outer.get(0).getAsJsonArray();
                }
            } else {
                for (java.util.Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    if (entry.getValue().isJsonArray()) {
                        arr = entry.getValue().getAsJsonArray();
                        break;
                    }
                }
            }

            if (arr != null) {
                float[] result = new float[arr.size()];
                for (int i = 0; i < arr.size(); i++) {
                    result[i] = arr.get(i).getAsFloat();
                }
                return result;
            }
            throw new IOException("No embedding array found in JSON: " + json);
        } catch (Exception e) {
            throw new IOException("Failed to parse embedding vector from JSON: " + json, e);
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
