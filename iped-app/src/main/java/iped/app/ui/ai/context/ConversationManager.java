package iped.app.ui.ai.context;

import iped.app.ui.ai.model.AIChatMessage;
import iped.app.ui.ai.model.Conversation;
import iped.app.ui.ai.util.ConversationPersistence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Singleton manager responsible for maintaining the state of AI conversations 
 * for the currently active IPED case.
 */
public class ConversationManager {

    private static ConversationManager instance;
    
    private final List<Conversation> conversations;
    private Conversation activeConversation;

    private ConversationManager() {
        this.conversations = new ArrayList<>();
        
        // Load from disk
        List<Conversation> loadedChats = ConversationPersistence.loadAllConversations();
        
        if (loadedChats != null && !loadedChats.isEmpty()) {
            this.conversations.addAll(loadedChats);
            // Intentionally leave activeConversation as null so the screen 
            // starts blank, forcing the user to select one or click "+ New Chat"
            this.activeConversation = null; 
        } else {
            // If the folder is empty, start a fresh chat
            startNewConversation();
        }
    }

    public static synchronized ConversationManager getInstance() {
        if (instance == null) {
            instance = new ConversationManager();
        }
        return instance;
    }

    public Conversation getActiveConversation() {
        return activeConversation;
    }

    /**
     * Sets a specific conversation as active (used when clicking a chat in the sidebar).
     */
    public void setActiveConversation(Conversation conversation) {
        this.activeConversation = conversation;
        // Safeguard: don't add null to the list if the active state is wiped
        if (conversation != null && !conversations.contains(conversation)) {
            conversations.add(0, conversation);
        }
    }

    /**
     * Initializes a fresh, empty conversation and sets it as active.
     */
    public Conversation startNewConversation() {
        return startNewConversation(false);
    }

    /**
     * Initializes a fresh conversation with the specified agent flag and sets it as active.
     */
    public Conversation startNewConversation(boolean isAgent) {
        Conversation newConv = new Conversation();
        newConv.setAgentConversation(isAgent);
        setActiveConversation(newConv);
        return newConv;
    }

    public List<Conversation> getConversations() {
        return Collections.unmodifiableList(conversations);
    }

    public void removeConversation(Conversation conversation) {
        conversations.remove(conversation);
    }

    /**
     * Appends a message to the active conversation and auto-generates a title if needed.
     */
    public void addMessageToActive(AIChatMessage message) {
        if (activeConversation != null) {
            activeConversation.getMessages().add(message);
            activeConversation.updateLastModified();
            
            // If this is the first user message, generate the title
            if ("New Conversation".equals(activeConversation.getTitle()) && "user".equals(message.getType())) {
                autoGenerateTitle(activeConversation);
            }
            
            // Save to disk asynchronously
            final Conversation convToSave = activeConversation;
            new Thread(() -> ConversationPersistence.saveConversation(convToSave)).start();
        }
    }

    /**
    * Auto-generates a title based on the first user message if the title is default
    */
    public void autoGenerateTitle(Conversation conversation) {
        if (conversation == null) return;

        if (!"New Conversation".equals(conversation.getTitle())) {
            return;
        }

        for (AIChatMessage msg : conversation.getMessages()) {
            if ("user".equals(msg.getType())) {
                String content = msg.getContent();

                if (content != null && !content.isBlank()) {
                    String title = content.length() > 30
                            ? content.substring(0, 27) + "..."
                            : content;

                    conversation.setTitle(title);
                }
                break;
            }
        }
    }
}
