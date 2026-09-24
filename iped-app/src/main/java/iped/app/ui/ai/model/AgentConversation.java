package iped.app.ui.ai.model;

public class AgentConversation extends Conversation {
    private String sessionId;

    public AgentConversation() {
        super();
    }

    @Override
    public boolean isAgentConversation() {
        return true;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
