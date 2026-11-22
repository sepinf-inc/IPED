package iped.parsers.telegram;

import java.util.Date;

public class MessageMultiMedia {
    private long id;
    private Chat chat;
    private Message message;
    private boolean isDeleted;
    private Contact from;
    private boolean fromMe;

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

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public Date getTimeStamp() {
        return message == null ? null : message.getTimeStamp();
    }

    public String getLocation() {
        return message == null || message.getLatitude() == null || message.getLongitude() == null ? null : message.getLatitude() + ";" + message.getLongitude();
    }

    public Long getToId() {
        return message == null ? null : message.getToId();
    }

    public String getData() {
        return message == null ? null : message.getData();
    }

    public boolean isPhoneCall() {
        return message == null ? null : message.isPhoneCall();
    }

    public String getType() {
        return message == null ? null : message.getType();
    }
}
