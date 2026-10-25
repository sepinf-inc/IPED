package iped.bfac;

/**
 * Result of a single file upload operation.
 * Used to communicate results from parallel upload tasks back to the coordinator.
 */
public class FileUploadResult {

    private final int fileId;
    private final String fileName;
    private final boolean success;
    private final boolean skipped;
    private final long bytesUploaded;
    private final boolean authenticationError;
    private final String errorMessage;

    private FileUploadResult(int fileId, String fileName, boolean success, boolean skipped,
                             long bytesUploaded, boolean authenticationError, String errorMessage) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.success = success;
        this.skipped = skipped;
        this.bytesUploaded = bytesUploaded;
        this.authenticationError = authenticationError;
        this.errorMessage = errorMessage;
    }

    public static FileUploadResult success(int fileId, String fileName, long bytesUploaded) {
        return new FileUploadResult(fileId, fileName, true, false, bytesUploaded, false, null);
    }

    public static FileUploadResult skipped(int fileId, String fileName, long fileSize) {
        return new FileUploadResult(fileId, fileName, true, true, fileSize, false, null);
    }

    public static FileUploadResult error(int fileId, String fileName, long bytesUploaded, String errorMessage) {
        return new FileUploadResult(fileId, fileName, false, false, bytesUploaded, false, errorMessage);
    }

    public static FileUploadResult authError(int fileId, String fileName, long bytesUploaded, String errorMessage) {
        return new FileUploadResult(fileId, fileName, false, false, bytesUploaded, true, errorMessage);
    }

    public int getFileId() {
        return fileId;
    }

    public String getFileName() {
        return fileName;
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public long getBytesUploaded() {
        return bytesUploaded;
    }

    public boolean isAuthenticationError() {
        return authenticationError;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
