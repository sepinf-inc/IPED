package iped.bfac.api;

/**
 * Result of a login or token renewal operation.
 */
public class LoginResult {
    private final boolean success;
    private final String message;

    public LoginResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
