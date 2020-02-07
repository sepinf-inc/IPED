package macee.events;

public class CloseCaseEvent {

    private final String caseId;

    public CloseCaseEvent(String id) {
        this.caseId = id;
    }

    public String getCaseId() {
        return caseId;
    }

}
