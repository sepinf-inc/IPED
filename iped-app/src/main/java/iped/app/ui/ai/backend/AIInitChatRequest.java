package iped.app.ui.ai.backend;

/**
 * A Data Transfer Object (DTO) representing the initial payload sent to the backend.
 * <p>
 * This class encapsulates the context data that needs to be uploaded to the LLM to start
 * a new stateful chat session. 
 * </p>
 */
public class AIInitChatRequest {
    
    /**
     * The raw text or HTML content to be analyzed by the AI.
     * Note: The snake_case naming convention is used intentionally here to map 
     * directly to the Python backend's expected JSON schema 
     */
    private final String chat_content;

    public AIInitChatRequest(String chat_content) {
        this.chat_content = chat_content;
    }

    public String getChatContent() {
        return chat_content;
    }
}
