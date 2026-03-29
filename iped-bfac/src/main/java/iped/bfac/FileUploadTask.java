package iped.bfac;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.bfac.api.BfacApiClient;
import iped.bfac.api.FileHashInfo;
import iped.bfac.api.FileUploadStatus;
import iped.data.IItem;
import iped.data.IItemId;
import iped.engine.data.IPEDMultiSource;

/**
 * Callable task that uploads a single file to the BFAC backend in segments.
 * Designed to run in a thread pool for parallel file uploads.
 */
public class FileUploadTask implements Callable<FileUploadResult> {

    private static final Logger logger = LoggerFactory.getLogger(FileUploadTask.class);

    private final int fileId;
    private final FileHashInfo hashInfo;
    private final BfacApiClient apiClient;
    private final IPEDMultiSource ipedSource;
    private final AtomicLong globalBytesUploaded;
    private final long totalBytesToUpload;
    private final Consumer<String> logCallback;
    private final Runnable tokenRenewalCallback;
    private final Runnable progressCallback;
    private final FileUploadStatus initialStatus;
    private final boolean[] cancelledFlag;
    private final boolean[] authErrorFlag;

    /**
     * Creates a new file upload task.
     *
     * @param fileId             The file ID in the backend
     * @param hashInfo           The file hash information (includes IItemId for retrieval)
     * @param apiClient          The BFAC API client (thread-safe)
     * @param ipedSource         The IPED multi-source for reading file content
     * @param globalBytesUploaded Shared atomic counter for total bytes uploaded across all tasks
     * @param totalBytesToUpload Total bytes to upload across all files (for progress)
     * @param logCallback        Callback for log messages (thread-safe publish)
     * @param tokenRenewalCallback Callback to trigger token renewal (synchronized externally)
     * @param progressCallback   Callback to update progress bar after each segment
     * @param cancelledFlag      Shared cancellation flag [0]
     * @param authErrorFlag      Shared authentication error flag [0]
     */
    public FileUploadTask(int fileId, FileHashInfo hashInfo, BfacApiClient apiClient,
                          IPEDMultiSource ipedSource, AtomicLong globalBytesUploaded,
                          long totalBytesToUpload, Consumer<String> logCallback,
                          Runnable tokenRenewalCallback, Runnable progressCallback,
                          FileUploadStatus initialStatus,
                          boolean[] cancelledFlag, boolean[] authErrorFlag) {
        this.fileId = fileId;
        this.hashInfo = hashInfo;
        this.apiClient = apiClient;
        this.ipedSource = ipedSource;
        this.globalBytesUploaded = globalBytesUploaded;
        this.totalBytesToUpload = totalBytesToUpload;
        this.logCallback = logCallback;
        this.tokenRenewalCallback = tokenRenewalCallback;
        this.progressCallback = progressCallback;
        this.initialStatus = initialStatus;
        this.cancelledFlag = cancelledFlag;
        this.authErrorFlag = authErrorFlag;
    }

    @Override
    public FileUploadResult call() {
        try {
            if (cancelledFlag[0] || authErrorFlag[0]) {
                return FileUploadResult.error(fileId, hashInfo.getFileName(), 0, "Cancelled");
            }

            // Get upload status from backend
            FileUploadStatus status = initialStatus != null ? initialStatus : apiClient.getUploadStatus(fileId);
            if (!status.isSuccess()) {
                if (status.isUnauthorized()) {
                    authErrorFlag[0] = true;
                    return FileUploadResult.authError(fileId, hashInfo.getFileName(), 0, status.getErrorMessage());
                }
                return FileUploadResult.error(fileId, hashInfo.getFileName(), 0, status.getErrorMessage());
            }

            // If already complete, skip
            if (status.isComplete()) {
                log("  File already uploaded: " + hashInfo.getFileName());
                globalBytesUploaded.addAndGet(hashInfo.getFileSize());
                if (progressCallback != null) {
                    progressCallback.run();
                }
                return FileUploadResult.skipped(fileId, hashInfo.getFileName(), hashInfo.getFileSize());
            }

            // Get the file content from IPED
            IItem item = findItem();
            if (item == null) {
                return FileUploadResult.error(fileId, hashInfo.getFileName(), 0,
                        "Could not find item for file " + hashInfo.getFileName());
            }

            long fileSize = item.getLength() != null ? item.getLength() : 0;
            if (fileSize == 0) {
                log("  Skipping empty file: " + hashInfo.getFileName());
                return FileUploadResult.skipped(fileId, hashInfo.getFileName(), 0);
            }

            int segmentSize = status.getSegmentSize();
            long startOffset = status.getUploadedSize();

            log("  Uploading: " + hashInfo.getFileName() + " (" + formatBytes(fileSize) + ")");

            // Count already-uploaded bytes in global progress
            if (startOffset > 0) {
                globalBytesUploaded.addAndGet(startOffset);
                if (progressCallback != null) {
                    progressCallback.run();
                }
            }

            try (InputStream is = item.getBufferedInputStream()) {
                // Skip to start offset if resuming
                if (startOffset > 0) {
                    is.skip(startOffset);
                }

                long currentOffset = startOffset;
                byte[] buffer = new byte[segmentSize];

                while (currentOffset < fileSize && !cancelledFlag[0] && !authErrorFlag[0]) {
                    // Trigger token renewal check (synchronized externally)
                    tokenRenewalCallback.run();

                    int bytesRead = is.read(buffer);
                    if (bytesRead <= 0) break;

                    byte[] segment = bytesRead == buffer.length ? buffer : Arrays.copyOf(buffer, bytesRead);

                    FileUploadStatus result = apiClient.uploadFileSegment(fileId, currentOffset, segment);
                    if (!result.isSuccess()) {
                        if (result.isUnauthorized()) {
                            authErrorFlag[0] = true;
                            return FileUploadResult.authError(fileId, hashInfo.getFileName(),
                                    currentOffset, result.getErrorMessage());
                        }
                        return FileUploadResult.error(fileId, hashInfo.getFileName(),
                                currentOffset, result.getErrorMessage());
                    }

                    currentOffset += bytesRead;
                    globalBytesUploaded.addAndGet(bytesRead);

                    // Update progress bar after each segment
                    if (progressCallback != null) {
                        progressCallback.run();
                    }

                    if (result.isComplete()) {
                        break;
                    }
                }

                if (cancelledFlag[0]) {
                    return FileUploadResult.error(fileId, hashInfo.getFileName(), currentOffset, "Cancelled");
                }
                if (authErrorFlag[0]) {
                    return FileUploadResult.authError(fileId, hashInfo.getFileName(), currentOffset, "Authentication error");
                }

                return FileUploadResult.success(fileId, hashInfo.getFileName(), currentOffset);

            } catch (IOException e) {
                logger.error("Error reading file {}", hashInfo.getFileName(), e);
                return FileUploadResult.error(fileId, hashInfo.getFileName(), 0, "Error reading file: " + e.getMessage());
            }

        } catch (Exception e) {
            logger.error("Error uploading file {}", hashInfo.getFileName(), e);
            return FileUploadResult.error(fileId, hashInfo.getFileName(), 0, "Error: " + e.getMessage());
        }
    }

    private IItem findItem() {
        if (ipedSource == null) return null;

        IItemId itemId = hashInfo.getItemId();
        if (itemId != null) {
            try {
                return ipedSource.getItemByItemId(itemId);
            } catch (Exception e) {
                logger.warn("Failed to find item by ID for {}", hashInfo.getFileName(), e);
            }
        }
        return null;
    }

    private void log(String message) {
        if (logCallback != null) {
            logCallback.accept(message);
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
