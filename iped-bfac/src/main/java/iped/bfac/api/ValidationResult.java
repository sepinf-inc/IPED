package iped.bfac.api;

/**
 * Result of a session validation operation.
 */
public class ValidationResult {
    private final boolean valid;
    private final int statusCode;
    private final String message;

    public ValidationResult(boolean valid, int statusCode, String message) {
        this.valid = valid;
        this.statusCode = statusCode;
        this.message = message;
    }

    public boolean isValid() {
        return valid;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getMessage() {
        return message;
    }
}
