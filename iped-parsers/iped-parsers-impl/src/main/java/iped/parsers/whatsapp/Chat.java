package iped.parsers.whatsapp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@pf.gov.br>
 */
public class Chat {

    private long id;
    private final WAContact remote;
    private String subject;
    private List<Message> messages = new ArrayList<>();
    private String title = null;
    private boolean groupChat = false;
    private boolean deleted = false;

    private String recoveredFrom = null;

    private Set<WAContact> groupmembers = new HashSet<>();

    public Chat(WAContact remote) {
        this.remote = remote;
    }

    /**
     * @return the id
     */
    public long getId() {
        return id;
    }

    /**
     * @param id
     *            the id to set
     */
    public void setId(long id) {
        this.id = id;
    }

    /**
     * @return the subject
     */
    public String getSubject() {
        return subject;
    }

    /**
     * @param subject
     *            the subject to set
     */
    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getPrintId() {
        return remote.getId();
    }

    /**
     * @return the messages
     */
    public List<Message> getMessages() {
        return messages;
    }

    public void add(Message message) {
        messages.add(message);
    }
    
    /**
     * @param messages
     *            the messages to set
     */
    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public boolean isGroupChat() {
        return groupChat;
    }

    public void setGroupChat(boolean groupChat) {
        this.groupChat = groupChat;
    }

    public String getTitle() {
        if (title == null) {
            if (isGroupChat()) {
                title = "WhatsApp Group"; //$NON-NLS-1$ //$NON-NLS-2$
                if (getSubject() != null && !getSubject().isBlank()) {
                    title += " - " + getSubject().strip(); //$NON-NLS-1$
                }
            } else {
                title = "WhatsApp Chat"; //$NON-NLS-1$
                if (remote != null && remote.getDisplayName() != null && !remote.getDisplayName().isBlank()
                        && (getPrintId() == null || !remote.getDisplayName().strip().equals(getPrintId().strip()))) {
                    title += " - " + remote.getDisplayName().strip();
                } else if (remote != null && remote.getName() != null
                        && (getPrintId() == null || !remote.getName().strip().equals(getPrintId().strip()))) {
                    title += " - " + remote.getName().strip(); //$NON-NLS-1$
                }
            }
            if (getPrintId() != null && !getPrintId().isBlank()) {
                title += " - " + getPrintId().strip();
            }
        }
        return title;
    }

    public WAContact getRemote() {
        return remote;
    }

    public String getRecoveredFrom() {
        return recoveredFrom;
    }

    public void setRecoveredFrom(String recoveredFrom) {
        this.recoveredFrom = recoveredFrom;
    }

    public Set<WAContact> getGroupmembers() {
        return groupmembers;
    }

    public void setGroupmembers(Set<WAContact> groupmembers) {
        this.groupmembers = groupmembers;
    }
    
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
    
    public boolean isDeleted() {
        return deleted;
    }
}
