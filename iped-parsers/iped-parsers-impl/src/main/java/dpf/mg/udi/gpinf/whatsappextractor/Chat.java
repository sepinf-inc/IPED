package dpf.mg.udi.gpinf.whatsappextractor;

import java.util.List;

/**
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@dpf.gov.br>
 */
public class Chat {

    private long id;
    private final WAContact remote;
    private String subject;
    private List<Message> messages;
    private String title = null;
    private boolean groupChat = false;

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
     * @param id the id to set
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
     * @param subject the subject to set
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

    /**
     * @param messages the messages to set
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
                if (getSubject() != null && getSubject().trim().length() != 0) {
                    title = "WhatsApp Group - " + getSubject(); //$NON-NLS-1$
                } else {
                    title = "WhatsApp Group - " + getPrintId(); //$NON-NLS-1$
                }
            } else {
                title = "WhatsApp Chat - "; //$NON-NLS-1$
                if(remote != null && !remote.getName().trim().equals(getPrintId()))
                	title += remote.getName() + " - "; //$NON-NLS-1$
                title += getPrintId();
            }
        }
        return title;
    }

	public WAContact getRemote() {
		return remote;
	}
}
