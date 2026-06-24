package iped.app.ui.ai.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Conversation {
    private String id;
    private String title;
    private String status; // e.g. "active", "deleted"
    private long createdAt;
    private long lastModified;
    private List<Integer> contextIds;
    private List<String> chatHashes;
    private List<AIChatMessage> messages;
    private boolean isAgentConversation;

    public Conversation() {
        this.id = UUID.randomUUID().toString(); //  Universally Unique Identifier
        this.title = "New Conversation";
        this.status = "active";
        this.createdAt = System.currentTimeMillis();
        this.lastModified = this.createdAt;
        this.contextIds = new ArrayList<>();
        this.chatHashes = new ArrayList<>();
        this.messages = new ArrayList<>();
        this.isAgentConversation = false;
    }

    // Standard Getters and Setters
    public String getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public long getCreatedAt() { return createdAt; }
    public long getLastModified() { return lastModified; }
    public void updateLastModified() { this.lastModified = System.currentTimeMillis(); }

    public List<Integer> getContextIds() {
        if (isAgentConversation) return new ArrayList<>();
        if (contextIds == null) contextIds = new ArrayList<>();
        return contextIds; 
    }
    public void setContextIds(List<Integer> contextIds) {
        if (isAgentConversation) return;
        this.contextIds = contextIds;
    }

    public List<String> getChatHashes() {
        if (isAgentConversation) return new ArrayList<>();
        if (chatHashes == null) chatHashes = new ArrayList<>();
        return chatHashes; 
    }
    public void setChatHashes(List<String> chatHashes) {
        if (isAgentConversation) return;
        this.chatHashes = chatHashes;
    }

    public List<AIChatMessage> getMessages() { 
        if (messages == null) messages = new ArrayList<>();
        return messages; 
    }
    public void setMessages(List<AIChatMessage> messages) { this.messages = messages; }

    public boolean isAgentConversation() {
        return isAgentConversation;
    }

    public void setAgentConversation(boolean isAgentConversation) {
        this.isAgentConversation = isAgentConversation;
    }

    /**
     * Returns true when this conversation already has a completed assistant reply.
     */
    public boolean hasAssistantReply() {
        for (AIChatMessage msg : messages) {
            if ("assistant".equals(msg.getType())) {
                return true;
            }
        }
        return false;
    }
}
