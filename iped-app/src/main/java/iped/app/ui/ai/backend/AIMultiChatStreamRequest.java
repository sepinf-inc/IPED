package iped.app.ui.ai.backend;

import java.util.List;

/**
 * A Data Transfer Object (DTO) for streaming both Summarized and Full multi-chat responses
 * <p>
 * Maps to the backend's MultiChatConversationRequest schema
 * </p>
 */
public class AIMultiChatStreamRequest {

    /**
     * {@code chats_hashes} is the correct name in the Python schema, despite the unnecessary 's'.
     * <b>Do not</b> rename this to {@code chat_hashes}
     */
    private final List<String> chats_hashes;
    private final String user_question;
    private final List<AIStreamChatRequest.AIMessage> previousmessages; // Reuse the AIMessage class built for single-chat
    
    /**
     * Constructs a new streaming request for a multi-chat session.
     * @param chatsHashes      The list of session IDs to query against.
     * @param userQuestion     The prompt text from the user.
     * @param previousmessages The accumulated chat history for context.
     */
    public AIMultiChatStreamRequest(List<String> chatsHashes, String userQuestion, List<AIStreamChatRequest.AIMessage> previousmessages) {
        this.chats_hashes = chatsHashes;
        this.user_question = userQuestion;
        this.previousmessages = previousmessages;
    }

    // Public getter methods
    public List<String> getChatsHashes() { return chats_hashes; }
    public String getUserQuestion() { return user_question; }
    public List<AIStreamChatRequest.AIMessage> getPreviousmessages() { return previousmessages; }
}

