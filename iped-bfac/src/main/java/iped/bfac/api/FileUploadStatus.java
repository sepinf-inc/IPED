package iped.bfac.api;

/**
 * Status of a file upload operation.
 */
public class FileUploadStatus {
    private final int fileId;
    private final long totalSize;
    private final long uploadedSize;
    private final int segmentSize;
    private final boolean complete;
    private final boolean success;
    private final int httpStatusCode;
    private final String errorMessage;

    public FileUploadStatus(int fileId, long totalSize, long uploadedSize, int segmentSize, boolean complete) {
        this.fileId = fileId;
        this.totalSize = totalSize;
        this.uploadedSize = uploadedSize;
        this.segmentSize = segmentSize;
        this.complete = complete;
        this.success = true;
        this.httpStatusCode = 200;
        this.errorMessage = null;
    }

    /**
     * Creates an error result.
     */
    public static FileUploadStatus error(int fileId, int httpStatusCode, String errorMessage) {
        return new FileUploadStatus(fileId, 0, 0, 0, false, false, httpStatusCode, errorMessage);
    }

    private FileUploadStatus(int fileId, long totalSize, long uploadedSize, int segmentSize,
                             boolean complete, boolean success, int httpStatusCode, String errorMessage) {
        this.fileId = fileId;
        this.totalSize = totalSize;
        this.uploadedSize = uploadedSize;
        this.segmentSize = segmentSize;
        this.complete = complete;
        this.success = success;
        this.httpStatusCode = httpStatusCode;
        this.errorMessage = errorMessage;
    }

    public int getFileId() {
        return fileId;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public long getUploadedSize() {
        return uploadedSize;
    }

    public int getSegmentSize() {
        return segmentSize;
    }

    public boolean isComplete() {
        return complete;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isUnauthorized() {
        return httpStatusCode == 401;
    }
}
