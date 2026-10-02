package iped.bfac.api;

/**
 * Result of a submission creation operation.
 */
public class SubmissionResult {
    private final boolean success;
    private final int submissionId;
    private final String message;
    private final int httpStatusCode;

    public SubmissionResult(boolean success, int submissionId, String message) {
        this(success, submissionId, message, 0);
    }

    public SubmissionResult(boolean success, int submissionId, String message, int httpStatusCode) {
        this.success = success;
        this.submissionId = submissionId;
        this.message = message;
        this.httpStatusCode = httpStatusCode;
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

    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    public boolean isUnauthorized() {
        return httpStatusCode == 401;
    }
}
