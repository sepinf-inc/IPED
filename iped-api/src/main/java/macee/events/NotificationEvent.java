package macee.events;

public class NotificationEvent {

    public static final String DEFAULT_TYPE = "default";
    private static final String DEFAULT_TITLE = "MACEE";
    private final String title;
    private final String message;
    private String type = "";

    public NotificationEvent(String title, String message, String type) {
        this(title, message);
        this.type = type;
    }

    public NotificationEvent(String title, String message) {
        this.title = title;
        this.message = message;
    }

    public NotificationEvent(String message) {
        this(DEFAULT_TITLE, message);
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getType() {
        return type;
    }
}
