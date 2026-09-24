package iped.app.ui.ai.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class Conversation {
    private String id;
    private String title;
    private String status; // e.g. "active", "deleted"
    private long createdAt;
    private long lastModified;
    private List<AIChatMessage> messages;

    public Conversation() {
        this.id = UUID.randomUUID().toString(); // Universally Unique Identifier
        this.title = "New Conversation";
        this.status = "active";
        this.createdAt = System.currentTimeMillis();
        this.lastModified = this.createdAt;
        this.messages = new ArrayList<>();
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

    public List<AIChatMessage> getMessages() { 
        if (messages == null) messages = new ArrayList<>();
        return messages; 
    }
    public void setMessages(List<AIChatMessage> messages) { this.messages = messages; }

    public abstract boolean isAgentConversation();

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
