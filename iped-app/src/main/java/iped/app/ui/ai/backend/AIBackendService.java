package iped.app.ui.ai.backend;

import java.util.function.Consumer;
import java.util.List;

public interface AIBackendService {

    // --- SINGLE-CHAT ENDPOINTS ---

    /**
     * Sends the extracted HTML content to the backend to initialize the chat.
     * @param chatHtml The raw WhatsApp HTML string.
     * @return The MD5 chat_hash returned by the server.
     * @throws AIBackendException if the server rejects the HTML or is unreachable.
     */
    String initChat(String chatHtml) throws AIBackendException;

    /**
     * Queries the LLM regarding a previously initialized chat.
     * @param chatHash The hash returned from initChat.
     * @param question The user's question.
     * @param history The chat history composed of previous messages
     * @param eventHandler A callback that receives streamed tokens/status updates from the LLM.
     * @throws AIBackendException if the streaming connection fails or returns an error token.
     */
    void streamChatResponse(String chatHash, String question, List<AIStreamChatRequest.AIMessage> history, Consumer<String> eventHandler) throws AIBackendException;

    // --- MULTI-CHAT ENDPOINTS ---

    /**
     * Initializes a multi-chat session by uploading a batch of pre-generated summaries
     * <p>
     * This method avoids LLM token-limit errors by explicitly refusing raw HTML and 
     * relying only on condensed metadata and summaries
     * </p>
     * @param request The structured payload containing the batch of summaries
     * @return A list of successfully cached session hashes, one for each summary provided
     * @throws AIBackendException if the backend rejects the payload format or is unreachable
     */
    List<String> initMultiChat(AIInitMultiChatRequest request) throws AIBackendException;

    /**
     * Streams an answer spanning multiple cached conversation summaries
     * @param chatHashes   The list of session hashes returned by {@link #initMultiChat}
     * @param question     The user's prompt requiring cross-file analysis
     * @param history      The chat history providing multi-turn conversational memory
     * @param eventHandler A callback that receives streamed tokens/status updates from the LLM
     * @throws AIBackendException if the streaming connection fails or returns an error token
     */
    void streamMultiChatResponse(List<String> chatHashes, String question, List<AIStreamChatRequest.AIMessage> history, Consumer<String> eventHandler) throws AIBackendException;

    // --- MULTI-CHAT-FULL ENDPOINTS ---

    /**
     * Initializes a full multi-chat session by sending raw HTML contents.
     * @param request The initialization payload containing a list of raw HTML strings.
     * @return A list of successfully generated MD5 chat hashes.
     * @throws AIBackendException if the server rejects the HTML or is unreachable.
     */
    List<String> initMultiChatFull(AIInitMultiChatFullRequest request) throws AIBackendException;

    /**
     * Streams the response for a full multi-chat session using Server-Sent Events.
     * @param chatHashes   The list of session hashes returned by initMultiChatFull.
     * @param question     The user's prompt.
     * @param history      The chat history providing multi-turn conversational memory.
     * @param eventHandler Callback triggered for every SSE token received.
     * @throws AIBackendException if the streaming connection fails.
     */
    void streamMultiChatFullResponse(List<String> chatHashes, String question, List<AIStreamChatRequest.AIMessage> history, Consumer<String> eventHandler) throws AIBackendException;
}