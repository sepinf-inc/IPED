package iped.bfac.api;

/**
 * Result of a send hash operation.
 */
public class SendHashResult {
    private final boolean success;
    private final int fileId;
    private final String message;
    private final boolean fileExistsInBackend;

    public SendHashResult(boolean success, int fileId, String message, boolean fileExistsInBackend) {
        this.success = success;
        this.fileId = fileId;
        this.message = message;
        this.fileExistsInBackend = fileExistsInBackend;
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
}
