package iped.app.ui.ai.model;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Chat message model used by the AI assistant UI.
 */
public class AIChatMessage {

    private final String sender;
    private String content;
    private final String type;
    private final String time;

    private AIChatMessage(String sender, String content, String type, String time) {
        this.sender = sender;
        this.content = content;
        this.type = type;
        this.time = time;
    }

    public static AIChatMessage create(String sender, String content, String type) {
        String time = new SimpleDateFormat("HH:mm").format(new Date());
        return new AIChatMessage(sender, content, type, time);
    }

    public String getSender() {
        return sender;
    }

    public String getContent() {
        return content;
    }

    public String getType() {
        return type;
    }

    public String getTime() {
        return time;
    }

    public void appendContent(String token) {
        this.content = this.content + token;
    }
}
