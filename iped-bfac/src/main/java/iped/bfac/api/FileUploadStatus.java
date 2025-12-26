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

    public FileUploadStatus(int fileId, long totalSize, long uploadedSize, int segmentSize, boolean complete) {
        this.fileId = fileId;
        this.totalSize = totalSize;
        this.uploadedSize = uploadedSize;
        this.segmentSize = segmentSize;
        this.complete = complete;
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
}
