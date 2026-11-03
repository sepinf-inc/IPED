package iped.bfac.api;

/**
 * Result of a send hash operation.
 */
public class SendHashResult {
    private final boolean success;
    private final int fileId;
    private final String message;
    private final boolean fileExistsInBackend;
    private final int httpStatusCode;

    public SendHashResult(boolean success, int fileId, String message, boolean fileExistsInBackend) {
        this(success, fileId, message, fileExistsInBackend, 0);
    }

    public SendHashResult(boolean success, int fileId, String message, boolean fileExistsInBackend, int httpStatusCode) {
        this.success = success;
        this.fileId = fileId;
        this.message = message;
        this.fileExistsInBackend = fileExistsInBackend;
        this.httpStatusCode = httpStatusCode;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getFileId() {
        return fileId;
    }

    public String getMessage() {
        return message;
    }

    public boolean isFileExistsInBackend() {
        return fileExistsInBackend;
    }

    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    public boolean isUnauthorized() {
        return httpStatusCode == 401;
    }
}
