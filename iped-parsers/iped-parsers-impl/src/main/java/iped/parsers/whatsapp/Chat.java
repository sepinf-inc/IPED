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
    private String title;
    private boolean isGroupChat;
    private boolean isChannelChat;
    private boolean isDeleted;

    private String recoveredFrom;

    private Set<WAContact> groupMembers = new HashSet<>();
    private Set<WAContact> groupAdmins = new HashSet<>();

    private WAAccount account;
    private boolean isOwnerAdmin;

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
        return isGroupChat;
    }

    public void setGroupChat(boolean isGroupChat) {
        this.isGroupChat = isGroupChat;
    }

    public boolean isChannelChat() {
        return isChannelChat;
    }

    public void setChannelChat(boolean isChannelChat) {
        this.isChannelChat = isChannelChat;
    }

    public boolean isGroupOrChannelChat() {
        return isGroupChat || isChannelChat;
    }
    
    public String getTitle() {
        if (title == null) {
            if (isChannelChat()) {
                title = "WhatsApp Channel";
                if (getSubject() != null && !getSubject().isBlank()) {
                    title += " - " + getSubject().strip();
                }
            } else if (isGroupChat()) {
                title = "WhatsApp Group";
                if (getSubject() != null && !getSubject().isBlank()) {
                    title += " - " + getSubject().strip();
                }
            } else {
                title = "WhatsApp Chat"; //$NON-NLS-1$
                if (remote != null && remote.getName() != null
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

    public Set<WAContact> getGroupMembers() {
        return groupMembers;
    }

    public Set<WAContact> getGroupAdmins() {
        return groupAdmins;
    }

    public WAAccount getAccount() {
        return account;
    }

    public void setAccount(WAAccount account) {
        this.account = account;
    }

    public boolean isOwnerAdmin() {
        return isOwnerAdmin;
    }

    public void setOwnerAdmin(boolean isOwnerAdmin) {
        this.isOwnerAdmin = isOwnerAdmin;
    }

    public void setDeleted(boolean isDeleted) {
        this.isDeleted = isDeleted;
    }
    
    public boolean isDeleted() {
        return isDeleted;
    }
}
