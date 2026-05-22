package iped.app.ui.ai.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Conversation {
    private String id;
    private String title;
    private long createdAt;
    private long lastModified;
    private List<Integer> contextIds;
    private List<String> chatHashes;
    private List<AIChatMessage> messages;

    public Conversation() {
        this.id = UUID.randomUUID().toString(); //  Universally Unique Identifier
        this.createdAt = System.currentTimeMillis();
        this.lastModified = this.createdAt;
        this.contextIds = new ArrayList<>();
        this.chatHashes = new ArrayList<>();
        this.messages = new ArrayList<>();
        this.title = "New Conversation";
    }

    // Standard Getters and Setters
    public String getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public long getCreatedAt() { return createdAt; }
    public long getLastModified() { return lastModified; }
    public void updateLastModified() { this.lastModified = System.currentTimeMillis(); }

    public List<Integer> getContextIds() { 
        if (contextIds == null) contextIds = new ArrayList<>();
        return contextIds; 
    }
    public void setContextIds(List<Integer> contextIds) { this.contextIds = contextIds; }

    public List<String> getChatHashes() { 
        if (chatHashes == null) chatHashes = new ArrayList<>();
        return chatHashes; 
    }
    public void setChatHashes(List<String> chatHashes) { this.chatHashes = chatHashes; }

    public List<AIChatMessage> getMessages() { 
        if (messages == null) messages = new ArrayList<>();
        return messages; 
    }
    public void setMessages(List<AIChatMessage> messages) { this.messages = messages; }

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
