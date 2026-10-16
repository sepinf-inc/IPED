package iped.app.ui.ai.backend;

import java.util.List;


/**
 * A Data Transfer Object (DTO) representing the initialization payload for a Multi-Chat session.
 * <p>
 * Unlike a standard single chat that uploads raw HTML, the Multi-Chat endpoint 
 * expects a curated list of pre-computed summaries. This class maps exactly to 
 * the JSON schema expected by the backend
 * </p>
 */
public class AIInitMultiChatRequest {
    
    /**
     * The list of chats to be analyzed together.
     * Note: The naming convention is used intentionally here to map 
     * directly to the backend's expected JSON schema 
     */
    private final List<SummarizedChat> summarized_chats;

    public AIInitMultiChatRequest(List<SummarizedChat> summarizedChats) {
        this.summarized_chats = summarizedChats;
    }

    public List<SummarizedChat> getSummarizedChats() { return summarized_chats; }

    /**
     * Inner class mapping to SummarizedChat Python model
     * <p>
     * Note that SummarizedChat refers to a single chat within summarized_chats. As such,
     * each has only one summary and one summary id. Therefore, when constructing a SummarizedChat
     * entity, consider that the backend requires a list of summaries and summaryIds, even if
     * in reality the chat has only one of each
     * </p>
     */
    public static class SummarizedChat {
        private final String chat_id;
        private final String chat_name;
        private final List<String> summaries;
        private final List<String> summary_ids;

        public SummarizedChat(String chatId, String chatName, List<String> summaries, List<String> summaryIds) {
            this.chat_id = chatId;
            this.chat_name = chatName;
            this.summaries = summaries;
            this.summary_ids = summaryIds;
        }

        // Public getter methods
        public String getChatId() { return chat_id; }
        public String getChatName() { return chat_name; }
        public List<String> getSummaries() { return summaries; }
        public List<String> getSummaryIds() { return summary_ids; }
    }
}
