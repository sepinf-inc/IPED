package iped.app.ui.ai.model;

import java.util.ArrayList;
import java.util.List;

public class StandardConversation extends Conversation {
    private List<Integer> contextIds;
    private List<String> chatHashes;

    public StandardConversation() {
        super();
        this.contextIds = new ArrayList<>();
        this.chatHashes = new ArrayList<>();
    }

    @Override
    public boolean isAgentConversation() {
        return false;
    }

    public List<Integer> getContextIds() {
        if (contextIds == null) contextIds = new ArrayList<>();
        return contextIds;
    }

    public void setContextIds(List<Integer> contextIds) {
        this.contextIds = contextIds;
    }

    public List<String> getChatHashes() {
        if (chatHashes == null) chatHashes = new ArrayList<>();
        return chatHashes;
    }

    public void setChatHashes(List<String> chatHashes) {
        this.chatHashes = chatHashes;
    }
}
