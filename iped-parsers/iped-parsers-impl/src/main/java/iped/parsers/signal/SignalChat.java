package iped.parsers.signal;

import java.util.ArrayList;
import java.util.List;

public class SignalChat {

    private long id;
    private SignalContact contact;
    private String groupTitle;
    private List<SignalMessage> messages = new ArrayList<>();
    private List<SignalContact> participants = new ArrayList<>();

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public SignalContact getContact() { return contact; }
    public void setContact(SignalContact contact) { this.contact = contact; }

    public String getGroupTitle() { return groupTitle; }
    public void setGroupTitle(String groupTitle) { this.groupTitle = groupTitle; }

    public List<SignalMessage> getMessages() { return messages; }
    public void setMessages(List<SignalMessage> messages) { this.messages = messages; }

    public List<SignalContact> getParticipants() { return participants; }

    public boolean isGroupChat() {
        return contact != null && contact.isGroup();
    }

    public String getTitle() {
        if (isGroupChat()) {
            String title = "Signal Group";
            if (groupTitle != null && !groupTitle.isBlank())
                title += " - " + groupTitle.strip();
            return title;
        }
        return "Signal Chat - " + (contact != null ? contact.getFullId() : "Unknown");
    }
}
