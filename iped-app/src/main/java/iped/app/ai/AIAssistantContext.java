package iped.app.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import iped.data.IItemId;

/**
 * Context information for AI requests
 * Contains selected items, metadata, and conversation history
 */
public class AIAssistantContext {
    
    private List<IItemId> selectedItems = new ArrayList<>();
    private String searchQuery;
    private Map<String, Object> metadata = new HashMap<>();
    private String viewerContent;  // Content from currently displayed viewer
    private List<ConversationMessage> conversationHistory = new ArrayList<>();
    
    /**
     * Adds a selected item to the context
     */
    public void addSelectedItem(IItemId itemId) {
        if (itemId != null) {
            selectedItems.add(itemId);
        }
    }
    
    /**
     * Gets all selected items
     */
    public List<IItemId> getSelectedItems() {
        return selectedItems;
    }
    
    /**
     * Sets the current search query
     */
    public void setSearchQuery(String query) {
        this.searchQuery = query;
    }
    
    /**
     * Gets the current search query
     */
    public String getSearchQuery() {
        return searchQuery;
    }
    
    /**
     * Sets content from currently displayed viewer
     */
    public void setViewerContent(String content) {
        this.viewerContent = content;
    }
    
    /**
     * Gets content from currently displayed viewer
     */
    public String getViewerContent() {
        return viewerContent;
    }
    
    /**
     * Checks if viewer content is available
     */
    public boolean hasViewerContent() {
        return viewerContent != null && !viewerContent.trim().isEmpty();
    }
    
    /**
     * Adds metadata to the context
     */
    public void addMetadata(String key, Object value) {
        metadata.put(key, value);
    }
    
    /**
     * Gets metadata value
     */
    public Object getMetadata(String key) {
        return metadata.get(key);
    }
    
    /**
     * Gets all metadata
     */
    public Map<String, Object> getAllMetadata() {
        return metadata;
    }
    
    /**
     * Adds a message to conversation history
     */
    public void addToHistory(String role, String content) {
        conversationHistory.add(new ConversationMessage(role, content));
    }
    
    /**
     * Gets conversation history
     */
    public List<ConversationMessage> getConversationHistory() {
        return conversationHistory;
    }
    
    /**
     * Clears conversation history
     */
    public void clearHistory() {
        conversationHistory.clear();
    }
    
    /**
     * Gets the number of messages in history
     */
    public int getHistorySize() {
        return conversationHistory.size();
    }
    
    /**
     * Limits conversation history to max size
     */
    public void limitHistory(int maxSize) {
        if (conversationHistory.size() > maxSize) {
            int toRemove = conversationHistory.size() - maxSize;
            conversationHistory.subList(0, toRemove).clear();
        }
    }
    
    /**
     * Represents a message in the conversation
     */
    public static class ConversationMessage {
        private final String role;  // "user", "assistant", or "system"
        private final String content;
        private final long timestamp;
        
        public ConversationMessage(String role, String content) {
            this.role = role;
            this.content = content;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getRole() {
            return role;
        }
        
        public String getContent() {
            return content;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        @Override
        public String toString() {
            return role + ": " + content;
        }
    }
}