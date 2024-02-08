package iped.parsers.threema;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * @author André Rodrigues Costa <andre.arc@pf.gov.br>
 */
public class Chat {

    private long id;
    private boolean groupChat = false;
    private String title = null;
    private String subject;
    private byte[] image;
    private ThreemaContact contact;
    private Set<ThreemaContact> participants = new HashSet<>();
    private List<Message> messages = new ArrayList<>();

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

    /**
     * @return the messages
     */
    public List<Message> getMessages() {
        return messages;
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
                title = "Threema Group"; //$NON-NLS-1$ //$NON-NLS-2$
                if (getSubject() != null && !getSubject().isBlank()) {
                    title += " - " + getSubject().strip(); //$NON-NLS-1$
                }
            } else {
                title = "Threema Chat"; //$NON-NLS-1$
                title += " - " + contact.getFullId(); //$NON-NLS-1$
            }
        }
        return title;
    }

    public void setContact(ThreemaContact contact) {
        this.contact = contact;
    }

    public ThreemaContact getContact() {
        return contact;
    }

    public Set<ThreemaContact> getParticipants() {
        return participants;
    }

    public byte[] getImage() {
        return image;
    }

    public void setImage(byte[] image) {
        this.image = image;
    }

    public void setParticipants(Set<ThreemaContact> participants) {
        this.participants = participants;
    }
}
