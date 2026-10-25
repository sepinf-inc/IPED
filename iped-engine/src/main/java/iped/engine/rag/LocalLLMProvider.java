package iped.engine.rag;

/**
 * Local LLM provider implementation connecting to Ollama chat endpoints.
 *
 * @author Rui Sant'Ana Junior
 */
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class LocalLLMProvider implements LLMProvider {

    /**
     * Upper bound of tokens the model may generate, reasoning included. Measured
     * on qwen3:8b with reasoning: p95 ~1,700 and max ~2,850 tokens per answer.
     */
    static final int MAX_GENERATION_TOKENS = 2560;

    private final String endpoint;
    private final String model;
    private final HttpClient client;
    private final Duration requestTimeout;

    public LocalLLMProvider(String endpoint, String model) {
        this(endpoint, model, Duration.ofSeconds(600));
    }

    LocalLLMProvider(String endpoint, String model, Duration requestTimeout) {
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("Local LLM timeout must be positive");
        }
        this.endpoint = endpoint;
        this.model = model;
        this.requestTimeout = requestTimeout;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Tokens of the context window reserved for generation. It is sent as
     * {@code num_predict}, so prompt plus generation fits the window and a
     * runaway generation always ends.
     */
    public static int generationTokenReserve(int contextWindow) {
        return Math.min(MAX_GENERATION_TOKENS, contextWindow / 2);
    }

    /** Appended when the model stops at the generation limit instead of finishing. */
    static String truncationNotice() {
        return "pt".equalsIgnoreCase(iped.localization.LocaleResolver.getLocale().getLanguage())
                ? "\n\n[Resposta interrompida: o modelo atingiu o limite de geração antes de concluir.]"
                : "\n\n[Answer cut off: the model reached the generation limit before finishing.]";
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
        int contextWindow = 4096;
        try {
            RAGService rag = RAGService.getInstance();
            if (rag != null && rag.getConfig() != null) {
                contextWindow = rag.getConfig().getLlmContextWindow();
            }
        } catch (Exception ignore) {}

        String requestBody = "{"
                + "\"model\":\"" + model + "\","
                + "\"messages\":" + LLMProvider.toChatMessagesJson(buildPrompt(question, context, history)) + ","
                + "\"options\":{\"num_ctx\":" + contextWindow + ",\"num_predict\":"
                + generationTokenReserve(contextWindow) + ",\"temperature\":0.0},"
                + "\"stream\":true"
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
                .timeout(requestTimeout)
                .build();

        long started = System.nanoTime();
        HttpResponse<InputStream> response = sendPrivilegedStream(client, request);
        // HttpRequest.timeout does not bound reads after streaming headers arrive.
        // Wait interruptibly and always close the body to release a blocked reader.
        try (InputStream body = response.body()) {
            FutureTask<String> responseTask = new FutureTask<>(() -> readResponse(response, tokenConsumer));
            Thread readerThread = new Thread(responseTask, "RAG-local-LLM-response");
            readerThread.setDaemon(true);
            long remaining = requestTimeout.toNanos() - (System.nanoTime() - started);
            if (remaining <= 0) {
                throw new HttpTimeoutException("Timed out waiting for local LLM response");
            }
            readerThread.start();
            try {
                return responseTask.get(remaining, TimeUnit.NANOSECONDS);
            } catch (TimeoutException e) {
                HttpTimeoutException timeout = new HttpTimeoutException("Timed out waiting for local LLM response");
                timeout.initCause(e);
                throw timeout;
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException) {
                    throw (IOException) cause;
                }
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                if (cause instanceof Error) {
                    throw (Error) cause;
                }
                throw new IOException("Error reading local LLM response", cause);
            } finally {
                responseTask.cancel(true);
            }
        }
    }

    private String readResponse(HttpResponse<InputStream> response,
            java.util.function.Consumer<String> tokenConsumer) throws IOException {
        if (response.statusCode() != 200) {
            String errBody = new String(response.body().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            throw new IOException("Local LLM service returned status code " + response.statusCode() + ": " + errBody);
        }

        StringBuilder fullResponse = new StringBuilder();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(response.body(), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String token = parseChatCompletionResponseChunk(line);
                if (token != null && !token.isEmpty()) {
                    fullResponse.append(token);
                    if (tokenConsumer != null) {
                        tokenConsumer.accept(token);
                    }
                }
                // The last chunk tells why generation stopped; "length" means num_predict cut it.
                if (line.contains("\"done_reason\":\"length\"")) {
                    String notice = truncationNotice();
                    fullResponse.append(notice);
                    if (tokenConsumer != null) {
                        tokenConsumer.accept(notice);
                    }
                }
            }
        }

        return fullResponse.toString();
    }

    public static String parseChatCompletionResponseChunk(String jsonLine) {
        try {
            int index = jsonLine.indexOf("\"content\"");
            if (index == -1) return null;
            index = jsonLine.indexOf(":", index);
            if (index == -1) return null;
            index = jsonLine.indexOf("\"", index);
            if (index == -1) return null;

            StringBuilder sb = new StringBuilder();
            boolean escaped = false;
            for (int i = index + 1; i < jsonLine.length(); i++) {
                char c = jsonLine.charAt(i);
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
        } catch (Exception e) {
            // Ignore partial/malformed JSON chunk lines gracefully
        }
        return null;
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
