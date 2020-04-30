package dpf.mt.gpinf.skype.parser;

import java.util.Date;
import java.util.List;

import dpf.sp.gpinf.indexer.parsers.util.Messages;

/**
 * Classe que representa uma conversa registrada no arquivo main.db.
 *
 * @author Patrick Dalla Bernardina patrick.pdb@dpf.gov.br
 */

public class SkypeConversation {
    String id;
    Date creationDate;
    Date lastActivity;
    String chatName;
    String displayName;
    List<SkypeMessage> messages;
    List<String> participantes;

    public List<String> getParticipantes() {
        return participantes;
    }

    public void setParticipantes(List<String> participantes) {
        this.participantes = participantes;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    public Date getLastActivity() {
        return lastActivity;
    }

    public void setLastActivity(Date lastActivity) {
        this.lastActivity = lastActivity;
    }

    public String getChatName() {
        return chatName;
    }

    public void setChatName(String chatName) {
        this.chatName = chatName;
    }

    public List<SkypeMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<SkypeMessage> messages) {
        this.messages = messages;
    }

    public String getTitle() {
        if (chatName != null) {
            return Messages.getString("SkypeConversation.SkypeChat") + id + "-" + chatName; //$NON-NLS-1$ //$NON-NLS-2$
        } else if (displayName != null) {
            return Messages.getString("SkypeConversation.SkypeChat") + id + "-" + displayName; //$NON-NLS-1$ //$NON-NLS-2$
        } else
            return Messages.getString("SkypeConversation.SkypeChat") + id; //$NON-NLS-1$
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

}
