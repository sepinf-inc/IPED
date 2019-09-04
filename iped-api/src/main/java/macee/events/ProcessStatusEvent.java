package macee.events;

public class ProcessStatusEvent {

    private final String message;

    public ProcessStatusEvent(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
