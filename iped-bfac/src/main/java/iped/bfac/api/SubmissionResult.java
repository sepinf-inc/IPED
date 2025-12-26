package iped.bfac.api;

/**
 * Result of a submission creation operation.
 */
public class SubmissionResult {
    private final boolean success;
    private final int submissionId;
    private final String message;

    public SubmissionResult(boolean success, int submissionId, String message) {
        this.success = success;
        this.submissionId = submissionId;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getSubmissionId() {
        return submissionId;
    }

    public String getMessage() {
        return message;
    }
}
