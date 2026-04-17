package iped.parsers.telegram;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

public class MessageMultiMedia {
    private long id;
    private Chat chat = null;
    private ArrayList<Message> messages = null;
    private boolean isDeleted = false;
    private Contact from = null;
    private boolean fromMe = false;

    public MessageMultiMedia(long id, Chat chat) {
        this.id = id;
        this.chat = chat;
    }
    public long getId() {
        return id;
    }
    public void setId(long id) {
        this.id = id;
    }
    public Chat getChat() {
        return chat;
    }
    public void setChat(Chat chat) {
        this.chat = chat;
    }
    public boolean isDeleted() {
        return isDeleted;
    }
    public void setDeleted(boolean isDeleted) {
        this.isDeleted = isDeleted;
    }
    public Contact getFrom() {
        return from;
    }
    public void setFrom(Contact from) {
        this.from = from;
    }
    public boolean isFromMe() {
        return fromMe;
    }
    public void setFromMe(boolean fromMe) {
        this.fromMe = fromMe;
    }

    public ArrayList<Message> getMessages() {
        return messages;
    }

    public void addMessage(Message message) {
        if (this.messages == null) {
            this.messages = new ArrayList<>();
        }
        this.messages.add(message);
    }

    public Date getTimeStamp() {
        if (messages != null && !messages.isEmpty()) {
            return messages.get(0).getTimeStamp();
        }
        return null;
    }

    public List<String> getMediaHashes() {
        List<String> mediaHashes = new ArrayList<>();
        if (messages != null) {
            for (Message message : messages) {
                if (message.getMediaHash() != null) {
                    mediaHashes.add(message.getMediaHash());
                }
            }
        }
        return mediaHashes;
    }

    public List<String> getMediaMimes() {
        List<String> mediaMimes = new ArrayList<>();
        if (messages != null) {
            for (Message message : messages) {
                if (message.getMediaMime() != null) {
                    mediaMimes.add(message.getMediaMime());
                }
            }
        }
        return mediaMimes;
    }

    public List<String> getLocations() {
        HashSet<String> locations = new HashSet<String>();
        if (messages != null) {
            for (Message m : messages) {
                if (m.getLatitude() != null && m.getLongitude() != null) {
                    locations.add(m.getLatitude() + ";" + m.getLongitude());
                }
            }
        }
        return new ArrayList<>(locations);
    }

    public Long getToId() {
        if (messages != null && !messages.isEmpty()) {
            return messages.get(0).getToId();
        }
        return null;
    }

    public String getData() {
        if (messages != null && !messages.isEmpty()) {
            return messages.get(0).getData();
        }
        return null;
    }

    public boolean isPhoneCall() {
        if (messages != null && !messages.isEmpty()) {
            return messages.get(0).isPhoneCall();
        }
        return false;
    }

    public String getType() {
        if (messages != null && !messages.isEmpty()) {
            return messages.get(0).getType();
        }
        return null;
    }

}
