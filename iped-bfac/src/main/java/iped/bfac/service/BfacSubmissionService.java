package iped.bfac.service;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import iped.bfac.api.BfacApiClient;
import iped.bfac.api.FileHashInfo;
import iped.data.IItemId;

/**
 * Service class that handles the submission workflow.
 * Coordinates between the UI, IPED data, and BFAC API.
 */
public class BfacSubmissionService {

    private BfacApiClient apiClient;
    private Consumer<ProgressInfo> progressCallback;
    private volatile boolean cancelled = false;

    public BfacSubmissionService(BfacApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Sets the callback for progress updates.
     * @param callback Consumer that receives progress information
     */
    public void setProgressCallback(Consumer<ProgressInfo> callback) {
        this.progressCallback = callback;
    }

    /**
     * Creates a submission and uploads hashes/files for the selected bookmarks.
     *
     * @param submissionName Name of the submission
     * @param comment Optional comment
     * @param categoryName Category name
     * @param bookmarkNames Set of bookmark names to include
     * @param uploadFiles Whether to upload actual files (not just hashes)
     * @return The submission ID if successful, -1 otherwise
     */
    public int createAndUploadSubmission(
            String submissionName,
            String comment,
            String categoryName,
            Set<String> bookmarkNames,
            boolean uploadFiles) {

        cancelled = false;

        // Step 1: Create submission on backend
        reportProgress("Creating submission on server...", 0, 0);
        BfacApiClient.SubmissionResult result = apiClient.createSubmission(submissionName, comment, categoryName);

        if (!result.isSuccess()) {
            reportProgress("Failed to create submission: " + result.getMessage(), 0, 0);
            return -1;
        }

        int submissionId = result.getSubmissionId();
        reportProgress("Submission created with ID: " + submissionId, 0, 0);

        // Step 2: Collect items from bookmarks
        // TODO: Implement actual item collection from IPED bookmarks
        // This would use IMultiBookmarks.filterBookmarks() to get items
        reportProgress("Collecting items from selected bookmarks...", 0, 0);

        // Step 3: Extract hash information from items
        // TODO: Implement actual hash extraction from IPED items
        // Items have metadata like MD5, SHA-1, SHA-256 already calculated

        // Step 4: Send hashes to backend
        reportProgress("Sending hashes to server...", 0, 0);
        // TODO: apiClient.sendHashes(submissionId, hashes);

        // Step 5: If uploadFiles is true, upload actual file contents
        if (uploadFiles && !cancelled) {
            reportProgress("Uploading files...", 0, 0);
            // TODO: Implement file upload using segments
        }

        if (cancelled) {
            reportProgress("Operation cancelled", 0, 0);
            return -1;
        }

        reportProgress("Upload completed successfully!", 100, 100);
        return submissionId;
    }

    /**
     * Cancels the current operation.
     */
    public void cancel() {
        this.cancelled = true;
    }

    private void reportProgress(String message, int current, int total) {
        if (progressCallback != null) {
            progressCallback.accept(new ProgressInfo(message, current, total));
        }
    }

    /**
     * Progress information holder.
     */
    public static class ProgressInfo {
        private final String message;
        private final int current;
        private final int total;

        public ProgressInfo(String message, int current, int total) {
            this.message = message;
            this.current = current;
            this.total = total;
        }

        public String getMessage() {
            return message;
        }

        public int getCurrent() {
            return current;
        }

        public int getTotal() {
            return total;
        }

        public int getPercentage() {
            if (total <= 0) return 0;
            return (int) ((current * 100.0) / total);
        }
    }
}
