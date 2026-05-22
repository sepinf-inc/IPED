package iped.app.ui.ai.backend;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.List;
import java.util.ArrayList;

/**
 * Concrete implementation of {@link AIBackendService} responsible for handling
 * communication with the AI backend over HTTP.
 *
 * <p>This client supports two primary operations:</p>
 * <ul>
 *     <li><b>Chat initialization</b> via a synchronous HTTP POST request</li>
 *     <li><b>Streaming responses</b> via Server-Sent Events (SSE)</li>
 * </ul>
 *
 * <p>It uses Java's {@link HttpClient} for network communication and {@link Gson}
 * for JSON serialization/deserialization.</p>
 *
 * <p><b>Thread safety:</b> This class is thread-safe provided that the supplied
 * {@link AIBackendConfig} is immutable.</p>
 */
public class AIBackendClient implements AIBackendService {

    private static final String THINKING_BLOCK_PREFIX = "[[AI_THINKING]]";
    private static final String THINKING_BLOCK_SUFFIX = "[[/AI_THINKING]]";
    
    private final AIBackendConfig config;
    private final HttpClient httpClient;
    private final Gson gson;

    /**
     * Constructs a new {@code AIBackendClient}.
     *
     * @param config Backend configuration (must not be null).
     * @throws IllegalArgumentException if config is null
     */
    public AIBackendClient(AIBackendConfig config) {
        this.config = config;
        this.gson = new Gson();
        // Create an HTTP client explicitly locked to HTTP/1.1 (required for the backend)
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Initializes a new chat session with the backend.
     *
     * <p>This method sends the initial chat content (HTML formatted) to the backend,
     * which responds with a chat identifier</p>
     *
     * @param chatHtml Initial chat content in HTML format
     * @return The MD5 chat_hash returned by the server.
     * @throws AIBackendException if:
     * <ul>
     *     <li>The HTTP request fails</li>
     *     <li>The backend returns a non-200 status</li>
     *     <li>The backend returns an application-level error</li>
     *     <li>The response cannot be parsed</li>
     * </ul>
     */
    @Override
    public String initChat(String chatHtml) throws AIBackendException {
        try {
            // Construct the payload using the specified DTO for initChat
            AIInitChatRequest payload = new AIInitChatRequest(chatHtml);
            String jsonBody = gson.toJson(payload);

            // Build the POST request targeting the initialization endpoint
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getBaseUrl() + "/api/init_chat"))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            // Send synchronously
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // Validate HTTP response
            if (response.statusCode() != 200) {
                throw new AIBackendException("Backend returned HTTP " + response.statusCode() + ": " + response.body());
            }

            // Parse the JSON response
            JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();

            // Check backend level error
            if (responseJson.has("error")) {
                throw new AIBackendException("Backend Error: " + responseJson.get("error").getAsString());
            }

            return responseJson.get("response").getAsString();

        } catch (Exception e) {
            throw new AIBackendException("Failed to initialize chat: " + e.getMessage(), e);
        }
    }

    /**
     * Streams a chat response from the backend using Server-Sent Events (SSE).
     *
     * <p>This method sends a user question and listens for incremental responses
     * from the backend. Each chunk of data is processed and forwarded to the UI
     * via the provided {@link Consumer}.</p>
     *
     * @param chatHash Unique identifier of the chat session
     * @param question User's input question
     * @param history The previous messages of this same chat
     * @param eventHandler Callback invoked for each streamed content chunk
     * @throws AIBackendException if:
     * <ul>
     *     <li>The HTTP request fails</li>
     *     <li>The backend returns a non-200 status</li>
     *     <li>A streaming error occurs</li>
     *     <li>The stream cannot be read or parsed</li>
     * </ul>
     */
    @Override
    public void streamChatResponse(String chatHash, String question, List<AIStreamChatRequest.AIMessage> history, Consumer<String> eventHandler) throws AIBackendException {
        try {
            // Construct the payload for the query using the specified DTO
            AIStreamChatRequest payload = new AIStreamChatRequest(chatHash, question, history);
            String jsonBody = gson.toJson(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getBaseUrl() + "/api/chat/stream"))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            // response as an InputStream so we can process data as it arrives.
            HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            
            if (response.statusCode() != 200) {
                throw new AIBackendException("Backend returned HTTP " + response.statusCode());
            }

            // Process the Server-Sent Events (SSE) stream
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;

                // Add a flag to track the first final token
                boolean isFirstFinalToken = true;
                boolean isThinkingBlockOpen = false;
                
                // Blocks here until a new line arrives over the network, then processes it
                while ((line = reader.readLine()) != null) {
                    // SSE protocol dictates data lines begin with "data: "
                    if (line.startsWith("data: ")) {
                        String jsonData = line.substring(6).trim(); // Strips the "data: "
                        
                        // Ignore keep-alive pings or the final closure signal
                        if (jsonData.isEmpty() || jsonData.equals("[DONE]")) continue;

                        // Parse the JSON, figure what type of message it is
                        JsonObject eventObj = JsonParser.parseString(jsonData).getAsJsonObject();
                        String type = eventObj.has("type") ? eventObj.get("type").getAsString() : "";
                        
                        // Handle thinking_done which has no content field
                        if (type.equals("thinking_done")) {
                            if (isThinkingBlockOpen) {
                                eventHandler.accept(THINKING_BLOCK_SUFFIX + "\n");
                                isThinkingBlockOpen = false;
                            }
                            continue;
                        }
                        
                        // Isolate the actual content for other event types
                        if (eventObj.has("content")) {
                            String content = eventObj.get("content").getAsString();
                            
                            if (type.equals("status")) {
                                // Format metadata in italics for the UI, with explicit 'status' indication
                                eventHandler.accept("\n_**[Status]:** " + content + "_\n");
                            } else if (type.equals("thinking")) {
                                if (!isThinkingBlockOpen) {
                                    // Emit a dedicated block marker to avoid markdown parsing conflicts
                                    eventHandler.accept("\n" + THINKING_BLOCK_PREFIX);
                                    isThinkingBlockOpen = true;
                                }

                                eventHandler.accept(content);
                            } else if (type.equals("final")) {
                                // Check the flag before appending
                                if (isFirstFinalToken) {
                                    eventHandler.accept("\n**[FINAL ANSWER]:**\n" + content);
                                    isFirstFinalToken = false;
                                } else {
                                    eventHandler.accept(content);
                                }
                            } else if (type.equals("error")) {
                                throw new AIBackendException("Backend Streaming Error: " + content);
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            throw new AIBackendException("Failed to stream chat response: " + e.getMessage(), e);
        }
    }

    /**
     * Synchronously initializes a multi-chat session
     * <p>
     * This method converts the structured DTO into JSON and parses the resulting 
     * JSON array response back into a standard Java List of strings
     * </p>
     * @param requestPayload The initialized multi-chat configuration
     * @return A list of unique hashes representing the cached sessions on the backend
     * @throws AIBackendException if the network fails or the backend returns an error JSON object
     */
    @Override
    public List<String> initMultiChat(AIInitMultiChatRequest requestPayload) throws AIBackendException {
        try {
            String jsonBody = gson.toJson(requestPayload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getBaseUrl() + "/api/init_multichat_with_summaries"))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new AIBackendException("Backend returned HTTP " + response.statusCode() + ": " + response.body());
            }

            JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
            if (responseJson.has("error")) {
                throw new AIBackendException("Backend Error: " + responseJson.get("error").getAsString());
            }

            // backend returns: {"response": ["hash1", "hash2"]}
            List<String> cachedHashes = new ArrayList<>();
            for (JsonElement element : responseJson.getAsJsonArray("response")) {
                cachedHashes.add(element.getAsString());
            }
            return cachedHashes;

        } catch (Exception e) {
            throw new AIBackendException("Failed to initialize multi-chat: " + e.getMessage(), e);
        }
    }

    /**
     * Asynchronously streams a multi-chat response using Server-Sent Events (SSE)
     * <p>
     * This method holds an open HTTP/1.1 InputStream and processes incoming 
     * {@code data: {JSON}} strings line-by-line. It parses out standard text tokens, 
     * formats reasoning/status tags into italics, and delegates the result to the UI
     * </p>
     * * @param chatHashes The list of target session hashes
     * @param question     The prompt
     * @param history      Conversational memory
     * @param eventHandler The UI consumption callback
     * @throws AIBackendException if the HTTP stream drops or reports a backend error event
     */
    @Override
    public void streamMultiChatResponse(List<String> chatHashes, String question, List<AIStreamChatRequest.AIMessage> history, Consumer<String> eventHandler) throws AIBackendException {
        try {
            AIMultiChatStreamRequest payload = new AIMultiChatStreamRequest(chatHashes, question, history);
            String jsonBody = gson.toJson(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getBaseUrl() + "/api/multichat/stream"))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            
            if (response.statusCode() != 200) {
                throw new AIBackendException("Backend returned HTTP " + response.statusCode());
            }
            
            // The SSE parsing logic is identical to the single-chat stream
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;

                // Add a flag to track the first final token
                boolean isFirstFinalToken = true;
                boolean isThinkingBlockOpen = false;

                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String jsonData = line.substring(6).trim();

                        if (jsonData.isEmpty() || jsonData.equals("[DONE]")) continue;

                        JsonObject eventObj = JsonParser.parseString(jsonData).getAsJsonObject();
                        String type = eventObj.has("type") ? eventObj.get("type").getAsString() : "";
                        
                        // Handle thinking_done which has no content field
                        if (type.equals("thinking_done")) {
                            if (isThinkingBlockOpen) {
                                eventHandler.accept(THINKING_BLOCK_SUFFIX + "\n");
                                isThinkingBlockOpen = false;
                            }
                            continue;
                        }
                        
                        if (eventObj.has("content")) {
                            String content = eventObj.get("content").getAsString();
                            
                            if (type.equals("status")) {
                                // Format metadata in italics for the UI, with explicit 'status' indication
                                eventHandler.accept("\n_**[Status]:** " + content + "_\n");
                            } else if (type.equals("thinking")) {
                                if (!isThinkingBlockOpen) {
                                    // Emit a dedicated block marker to avoid markdown parsing conflicts
                                    eventHandler.accept("\n" + THINKING_BLOCK_PREFIX);
                                    isThinkingBlockOpen = true;
                                }

                                eventHandler.accept(content);
                            } else if (type.equals("final")) {
                                // Check the flag before appending
                                if (isFirstFinalToken) {
                                    eventHandler.accept("\n**[FINAL ANSWER]:**\n" + content);
                                    isFirstFinalToken = false;
                                } else {
                                    eventHandler.accept(content);
                                }   
                            } else if (type.equals("error")) {
                                throw new AIBackendException("Backend Streaming Error: " + content);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new AIBackendException("Failed to stream multi-chat response: " + e.getMessage(), e);
        }
    }
}
