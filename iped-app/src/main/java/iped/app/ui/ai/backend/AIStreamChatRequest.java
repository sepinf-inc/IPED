package iped.app.ui.ai.backend;

import java.util.List;

/**
 * A Data Transfer Object (DTO) representing the conversation request sent to the backend
 * <p>
 * Sent to /api/chat/stream to query a previously initialized chat
 * </p>
 */
public class AIStreamChatRequest {
    
    /**
     * Note: The naming convention is used intentionally here to map 
     * directly to the Python backend's expected JSON schema 
     */
    private final String chat_hash;
    private final String user_question;
    private final List<AIMessage> previousmessages;

    public AIStreamChatRequest(String chatHash, String userQuestion, List<AIMessage> previousmessages) {
        this.chat_hash = chatHash;
        this.user_question = userQuestion;
        this.previousmessages = previousmessages;
    }

    // Public getter methods
    public String getChatHash() { return chat_hash; }
    public String getUserQuestion() { return user_question; }
    public List<AIMessage> getPreviousmessages() { return previousmessages; }

    /**
     * Inner class representing the individual chat messages for context history.
     */
    public static class AIMessage {
        private final String role;    // "user" or "assistant"
        private final String content; // The message text

        public AIMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        // Public getter methods 
        public String getRole() { return role; }
        public String getContent() { return content; }
    }
}
