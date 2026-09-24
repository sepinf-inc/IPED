package iped.app.ui.ai.backend;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * DTO for initializing a full, non-summarized multi-chat session.
 * Expects a list of raw WhatsApp HTML export strings.
 */
public class AIInitMultiChatFullRequest {
    
    @SerializedName("chat_contents")
    private List<String> chatContents;

    public AIInitMultiChatFullRequest(List<String> chatContents) {
        this.chatContents = chatContents;
    }

    public List<String> getChatContents() {
        return chatContents;
    }

    public void setChatContents(List<String> chatContents) {
        this.chatContents = chatContents;
    }
}
