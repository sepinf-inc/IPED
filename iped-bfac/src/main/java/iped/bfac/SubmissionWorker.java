package iped.bfac;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.SwingWorker;

import iped.bfac.api.BfacApiClient;
import iped.bfac.api.BfacApiClient.BatchUploadSegmentInput;
import iped.bfac.api.BfacApiClient.BatchUploadSegmentResult;
import iped.bfac.api.FileHashInfo;
import iped.bfac.api.LoginResult;
import iped.bfac.api.SendHashResult;
import iped.bfac.api.SubmissionResult;
import iped.bfac.api.FileUploadStatus;
import iped.data.IIPEDSource;
import iped.data.IItem;
import iped.data.IItemId;
import iped.data.IMultiBookmarks;
import iped.engine.data.IPEDMultiSource;
import iped.engine.search.IPEDSearcher;
import iped.engine.search.MultiSearchResult;
import iped.search.IMultiSearchResult;

/**
 * SwingWorker for handling BFAC submission in background.
 * Collects hashes from IPED items, sends them to the server, and optionally uploads files.
 */
public class SubmissionWorker extends SwingWorker<Boolean, String> {

    private static final int BATCH_SIZE = 50;
    private static final long TOKEN_RENEWAL_INTERVAL_MS = 12 * 60 * 60 * 1000; // 12 hours in milliseconds

    private final BfacApiClient apiClient;
    private final IIPEDSource ipedSource;
    private final SubmissionCallback callback;

    private final boolean isNewSubmission;
    private int submissionId;
    private final String submissionName;
    private final String comment;
    private final String categoryName;
    private final Set<String> bookmarkNames;
    private final boolean uploadFiles;
    private final int maxConcurrentUploads;

    private volatile boolean cancelled = false;
    private volatile boolean authenticationError = false;
    private int totalItems = 0;
    private int processedItems = 0;
    private int successCount = 0;
    private int errorCount = 0;
    private long lastTokenRenewalTime = 0;
    private ExecutorService uploadExecutor;
    private final boolean[] cancelledFlag = new boolean[] { false };
    private final boolean[] authErrorFlag = new boolean[] { false };
    private volatile boolean uploadHadErrors = false;

    /**
     * Callback interface for communication with the dialog.
     */
    public interface SubmissionCallback {
        void onLogMessage(String message);
        void onComplete(boolean success);

        /**
         * Called when an authentication error (401) is received from the server.
         * The dialog should return to the login screen.
         */
        default void onAuthenticationError() {
            // Default implementation does nothing
        }
    }

    public SubmissionWorker(BfacApiClient apiClient, IIPEDSource ipedSource, SubmissionCallback callback,
            boolean isNewSubmission, int submissionId, String submissionName,
            String comment, String categoryName, Set<String> bookmarkNames, boolean uploadFiles,
            int maxConcurrentUploads) {
        this.apiClient = apiClient;
        this.ipedSource = ipedSource;
        this.callback = callback;
        this.isNewSubmission = isNewSubmission;
        this.submissionId = submissionId;
        this.submissionName = submissionName;
        this.comment = comment;
        this.categoryName = categoryName;
        this.bookmarkNames = bookmarkNames;
        this.uploadFiles = uploadFiles;
        this.maxConcurrentUploads = maxConcurrentUploads;
    }

    @Override
    protected Boolean doInBackground() {
        try {
            // Step 1: Create or use existing submission
            if (isNewSubmission) {
                publish("Creating submission '" + submissionName + "'...");
                SubmissionResult result = apiClient.createSubmission(submissionName, comment, categoryName);

                if (!result.isSuccess()) {
                    publish("ERROR: " + result.getMessage());
                    if (result.isUnauthorized()) {
                        authenticationError = true;
                    }
                    return false;
                }

                submissionId = result.getSubmissionId();
                publish("Submission created with ID: " + submissionId);
            } else {
                publish("Using existing submission: " + submissionName + " (ID: " + submissionId + ")");
            }

            if (cancelled || authenticationError) return false;

            // Step 2: Collect items from bookmarks
            publish("");
            publish("Collecting items from selected bookmarks...");

            List<FileHashInfo> hashInfos = collectHashInfoFromBookmarks(bookmarkNames);

            List<FileHashInfo> validHashInfos = new ArrayList<>();
            int invalidHashInfoCount = 0;
            for (FileHashInfo hashInfo : hashInfos) {
                boolean hasValidName = hashInfo.getFileName() != null && !hashInfo.getFileName().trim().isEmpty();
                boolean hasValidSize = hashInfo.getFileSize() > 0;
                if (hasValidName && hasValidSize) {
                    validHashInfos.add(hashInfo);
                } else {
                    invalidHashInfoCount++;
                }
            }
            hashInfos = validHashInfos;

            totalItems = hashInfos.size();
            publish("Found " + totalItems + " items to process");
            if (invalidHashInfoCount > 0) {
                publish("Skipped " + invalidHashInfoCount + " item(s) with invalid metadata for hash submission (empty name or size <= 0).");
            }

            if (totalItems == 0) {
                publish("");
                publish("No items found in selected bookmarks.");
                return true;
            }

            if (cancelled) return false;

            // Step 3: Send hashes in batches
            publish("");
            publish("Sending hashes to server...");

            // Track files that need upload (fileId -> hashInfo)
            Map<Integer, FileHashInfo> filesToUpload = new LinkedHashMap<>();

            // Process in batches
            for (int i = 0; i < hashInfos.size() && !cancelled && !authenticationError; i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, hashInfos.size());
                List<FileHashInfo> batch = hashInfos.subList(i, end);

                List<SendHashResult> results = apiClient.sendHashesBatch(submissionId, batch);

                for (int j = 0; j < results.size(); j++) {
                    SendHashResult result = results.get(j);
                    if (result.isSuccess()) {
                        successCount++;
                        // If file doesn't exist in backend and upload is requested, track it
                        if (uploadFiles && !result.isFileExistsInBackend() && result.getFileId() > 0) {
                            filesToUpload.put(result.getFileId(), batch.get(j));
                        }
                    } else {
                        errorCount++;
                        // Check for authentication error
                        if (result.isUnauthorized()) {
                            publish("ERROR: Authentication failed. Session expired.");
                            authenticationError = true;
                            break;
                        }
                    }
                    processedItems++;
                }

                // Update progress
                int progress = (processedItems * 100) / totalItems;
                setProgress(progress);
                publish("Processed " + processedItems + " of " + totalItems + " items...");
            }

            if (cancelled || authenticationError) {
                if (cancelled) {
                    publish("");
                    publish("Operation cancelled by user.");
                }
                return false;
            }

            // Step 4: Hash summary
            publish("");
            publish("=== Hash Upload Complete ===");
            publish("Total items: " + totalItems);
            publish("Successfully sent: " + successCount);
            if (errorCount > 0) {
                publish("Errors: " + errorCount);
            }

            // Step 5: Upload files if requested (parallel)
            if (uploadFiles && !filesToUpload.isEmpty()) {
                publish("");
                publish("=== Starting File Upload ===");
                publish("Files to upload: " + filesToUpload.size());
                publish("Concurrent uploads: " + maxConcurrentUploads);

                // Renew token before starting upload (uploads can take days)
                renewTokenIfNeeded(true);

                // Reset progress bar for upload phase
                setProgress(0);

                int totalFilesToUpload = filesToUpload.size();

                // Calculate total bytes to upload for accurate progress
                long totalBytesToUpload = 0;
                for (FileHashInfo info : filesToUpload.values()) {
                    totalBytesToUpload += info.getFileSize();
                }

                // Shared state for parallel uploads
                final AtomicLong globalBytesUploaded = new AtomicLong(0);
                final long finalTotalBytesToUpload = totalBytesToUpload;

                // Reset shared flags for this upload phase
                cancelledFlag[0] = false;
                authErrorFlag[0] = false;

                // Token renewal callback (synchronized in BfacApiClient.renewToken)
                Runnable tokenRenewalCallback = () -> renewTokenIfNeeded(false);

                // Thread-safe log callback via SwingWorker.publish
                java.util.function.Consumer<String> logCallback = msg -> publish(msg);

                // Progress callback: updates progress bar after each segment upload
                Runnable progressCallback = () -> {
                    if (finalTotalBytesToUpload > 0) {
                        int progress = (int) ((globalBytesUploaded.get() * 100) / finalTotalBytesToUpload);
                        setProgress(Math.min(progress, 100));
                    }
                };

                // Create thread pool
                uploadExecutor = Executors.newFixedThreadPool(maxConcurrentUploads);

                // Ensure ipedSource is IPEDMultiSource for file retrieval
                IPEDMultiSource multiSource = (ipedSource instanceof IPEDMultiSource)
                        ? (IPEDMultiSource) ipedSource : null;

                List<Map.Entry<Integer, FileHashInfo>> pendingUploads = new ArrayList<>(filesToUpload.entrySet());
                List<Map.Entry<Integer, FileHashInfo>> uploadsForTasks = new ArrayList<>();
                List<Integer> pendingFileIds = new ArrayList<>();
                for (Map.Entry<Integer, FileHashInfo> entry : pendingUploads) {
                    pendingFileIds.add(entry.getKey());
                }
                Map<Integer, BfacApiClient.BatchFileUploadStatusItem> statusByFileId = apiClient.getUploadStatusBatch(pendingFileIds);

                int batchUploadedCount = 0;
                int batchSkippedCount = 0;
                int batchErrorCount = 0;
                long batchBytesUploaded = 0;

                int uploadIndex = 0;
                while (uploadIndex < pendingUploads.size() && !cancelledFlag[0] && !authErrorFlag[0]) {
                    Map.Entry<Integer, FileHashInfo> currentEntry = pendingUploads.get(uploadIndex);
                    int currentFileId = currentEntry.getKey();
                    FileHashInfo currentHashInfo = currentEntry.getValue();

                    BfacApiClient.BatchFileUploadStatusItem currentStatusItem = statusByFileId.get(currentFileId);
                    if (currentStatusItem == null || !currentStatusItem.isFound() || currentStatusItem.getStatus() == null) {
                        if (currentStatusItem != null && currentStatusItem.isUnauthorized()) {
                            authErrorFlag[0] = true;
                            authenticationError = true;
                            break;
                        }
                        uploadsForTasks.add(currentEntry);
                        uploadIndex++;
                        continue;
                    }
                    FileUploadStatus currentStatus = currentStatusItem.getStatus();

                    long currentSize = Math.max(0, currentHashInfo.getFileSize());
                    long groupedLimit = Math.max(1, currentStatus.getSegmentSize());
                    if (currentStatus.isComplete() || currentStatus.getUploadedSize() > 0 || currentSize == 0 || currentSize > groupedLimit) {
                        uploadsForTasks.add(currentEntry);
                        uploadIndex++;
                        continue;
                    }

                    List<Integer> groupedIndices = new ArrayList<>();
                    groupedIndices.add(uploadIndex);
                    long groupedTotal = currentSize;
                    int cursor = uploadIndex + 1;

                    while (cursor < pendingUploads.size()) {
                        Map.Entry<Integer, FileHashInfo> nextEntry = pendingUploads.get(cursor);
                        BfacApiClient.BatchFileUploadStatusItem nextStatusItem = statusByFileId.get(nextEntry.getKey());
                        if (nextStatusItem == null || !nextStatusItem.isFound() || nextStatusItem.getStatus() == null) {
                            break;
                        }
                        FileUploadStatus nextStatus = nextStatusItem.getStatus();
                        if (nextStatus.isComplete() || nextStatus.getUploadedSize() > 0) {
                            break;
                        }

                        long nextSize = Math.max(0, nextEntry.getValue().getFileSize());
                        long nextLimit = Math.max(1, nextStatus.getSegmentSize());
                        if (nextSize == 0 || nextSize > nextLimit) {
                            break;
                        }

                        groupedLimit = Math.min(groupedLimit, nextLimit);
                        if (groupedTotal + nextSize > groupedLimit) {
                            break;
                        }

                        groupedIndices.add(cursor);
                        groupedTotal += nextSize;
                        cursor++;
                    }

                    if (groupedIndices.size() > 1 && multiSource != null) {
                        List<BatchUploadSegmentInput> segments = new ArrayList<>();
                        boolean segmentBuildFailed = false;

                        for (Integer groupedIndex : groupedIndices) {
                            Map.Entry<Integer, FileHashInfo> groupedEntry = pendingUploads.get(groupedIndex);
                            IItem groupedItem = null;
                            try {
                                IItemId itemId = groupedEntry.getValue().getItemId();
                                if (itemId != null) {
                                    groupedItem = multiSource.getItemByItemId(itemId);
                                }
                                if (groupedItem == null) {
                                    segmentBuildFailed = true;
                                    break;
                                }
                                long groupedSize = Math.max(0, groupedEntry.getValue().getFileSize());
                                if (groupedSize > Integer.MAX_VALUE) {
                                    segmentBuildFailed = true;
                                    break;
                                }
                                byte[] segmentData;
                                try (java.io.InputStream groupedStream = groupedItem.getBufferedInputStream()) {
                                    segmentData = groupedStream.readAllBytes();
                                }
                                if (segmentData.length != groupedSize) {
                                    segmentBuildFailed = true;
                                    break;
                                }
                                segments.add(new BatchUploadSegmentInput(
                                        groupedEntry.getKey(),
                                        0,
                                        segmentData.length,
                                        segmentData));
                            } catch (Exception e) {
                                segmentBuildFailed = true;
                                break;
                            }
                        }

                        if (!segmentBuildFailed) {
                            List<BatchUploadSegmentResult> batchResults = apiClient.uploadFileSegmentsBatch(segments);
                            Map<Integer, BatchUploadSegmentResult> byFileId = new HashMap<>();
                            for (BatchUploadSegmentResult item : batchResults) {
                                byFileId.put(item.getFileId(), item);
                            }

                            boolean fatalBatchAuthError = false;
                            for (Integer groupedIndex : groupedIndices) {
                                Map.Entry<Integer, FileHashInfo> groupedEntry = pendingUploads.get(groupedIndex);
                                BatchUploadSegmentResult batchResult = byFileId.get(groupedEntry.getKey());
                                if (batchResult != null && batchResult.isUnauthorized()) {
                                    authErrorFlag[0] = true;
                                    authenticationError = true;
                                    fatalBatchAuthError = true;
                                    break;
                                }
                            }
                            if (fatalBatchAuthError) {
                                break;
                            }

                            for (Integer groupedIndex : groupedIndices) {
                                Map.Entry<Integer, FileHashInfo> groupedEntry = pendingUploads.get(groupedIndex);
                                BatchUploadSegmentResult batchResult = byFileId.get(groupedEntry.getKey());
                                long groupedSize = Math.max(0, groupedEntry.getValue().getFileSize());
                                if (batchResult != null && batchResult.isSuccess()) {
                                    batchUploadedCount++;
                                    batchBytesUploaded += groupedSize;
                                    globalBytesUploaded.addAndGet(groupedSize);
                                    if (progressCallback != null) {
                                        progressCallback.run();
                                    }
                                } else {
                                    uploadsForTasks.add(groupedEntry);
                                }
                            }

                            uploadIndex += groupedIndices.size();
                            continue;
                        }
                    }

                    uploadsForTasks.add(currentEntry);
                    uploadIndex++;
                }

                // Submit all upload tasks via CompletionService (results in completion order)
                CompletionService<FileUploadResult> completionService =
                        new ExecutorCompletionService<>(uploadExecutor);
                int submittedCount = 0;
                for (Map.Entry<Integer, FileHashInfo> entry : uploadsForTasks) {
                    BfacApiClient.BatchFileUploadStatusItem statusItem = statusByFileId.get(entry.getKey());
                    FileUploadStatus initialStatus = statusItem != null ? statusItem.getStatus() : null;
                    FileUploadTask task = new FileUploadTask(
                            entry.getKey(), entry.getValue(), apiClient, multiSource,
                            globalBytesUploaded, finalTotalBytesToUpload,
                            logCallback, tokenRenewalCallback, progressCallback, initialStatus,
                            cancelledFlag, authErrorFlag);
                    completionService.submit(task);
                    submittedCount++;
                }

                // Collect results in completion order (not submission order)
                uploadExecutor.shutdown();

                int uploadedCount = batchUploadedCount;
                int uploadErrorCount = batchErrorCount;
                int skippedCount = batchSkippedCount;
                long totalBytesUploaded = batchBytesUploaded;

                for (int i = 0; i < submittedCount; i++) {
                    try {
                        Future<FileUploadResult> future = completionService.take();
                        FileUploadResult result = future.get();

                        if (result.isSuccess()) {
                            if (result.isSkipped()) {
                                skippedCount++;
                            } else {
                                uploadedCount++;
                            }
                            totalBytesUploaded += result.getBytesUploaded();
                        } else {
                            uploadErrorCount++;
                            if (result.isAuthenticationError()) {
                                authenticationError = true;
                                // Propagate to shared flags so other tasks stop
                                authErrorFlag[0] = true;
                            }
                        }

                        int processed = uploadedCount + uploadErrorCount + skippedCount;
                        publish("Processed " + processed + " of " + totalFilesToUpload + " files...");

                    } catch (Exception e) {
                        uploadErrorCount++;
                        publish("  Error processing upload result: " + e.getMessage());
                    }
                }

                publish("");
                publish("=== File Upload Complete ===");
                publish("Files uploaded: " + uploadedCount);
                if (skippedCount > 0) {
                    publish("Files already in backend (skipped): " + skippedCount);
                }
                publish("Total data: " + formatBytes(totalBytesUploaded));
                if (uploadErrorCount > 0) {
                    uploadHadErrors = true;
                    publish("Upload errors: " + uploadErrorCount);
                    if (uploadedCount == 0 && skippedCount == 0) {
                        publish("ERROR: All file uploads failed!");
                    } else {
                        publish("WARNING: " + uploadErrorCount + " of " + totalFilesToUpload + " file(s) failed to upload.");
                    }
                }
            } else if (uploadFiles) {
                publish("");
                publish("No files need to be uploaded (all already exist in backend).");
            }

            return true;

        } catch (Exception e) {
            publish("ERROR: " + e.getMessage());
            return false;
        }
    }

    @Override
    protected void process(List<String> chunks) {
        for (String message : chunks) {
            if (callback != null) {
                callback.onLogMessage(message);
            }
        }
    }

    @Override
    protected void done() {
        try {
            boolean success = get();
            if (callback != null) {
                // Check if there was an authentication error
                if (authenticationError) {
                    callback.onLogMessage("");
                    callback.onLogMessage("ERROR: Authentication failed. Please log in again.");
                    callback.onAuthenticationError();
                    callback.onComplete(false);
                    return;
                }

                if (success) {
                    callback.onLogMessage("");
                    if (uploadHadErrors) {
                        callback.onLogMessage("Operation completed with errors. Some files failed to upload.");
                    } else {
                        callback.onLogMessage("Operation completed successfully!");
                    }
                }
                callback.onComplete(success && !uploadHadErrors);
            }
        } catch (Exception e) {
            if (callback != null) {
                callback.onLogMessage("ERROR: " + e.getMessage());
                callback.onComplete(false);
            }
        }
    }

    public void cancelOperation() {
        cancelled = true;
        cancelledFlag[0] = true;
        cancel(false);
        if (uploadExecutor != null && !uploadExecutor.isShutdown()) {
            uploadExecutor.shutdownNow();
        }
    }

    /**
     * Renews the access token if needed.
     * Should be called periodically during long-running operations.
     * @param force If true, renews the token regardless of time elapsed
     * @return true if token is valid (either already valid or successfully renewed)
     */
    private boolean renewTokenIfNeeded(boolean force) {
        long currentTime = System.currentTimeMillis();

        // Check if we need to renew (first time, forced, or interval elapsed)
        if (!force && lastTokenRenewalTime > 0 &&
            (currentTime - lastTokenRenewalTime) < TOKEN_RENEWAL_INTERVAL_MS) {
            return true; // Token still valid, no need to renew
        }

        publish("Renewing authentication token...");
        LoginResult result = apiClient.renewToken();

        if (result.isSuccess()) {
            lastTokenRenewalTime = currentTime;
            publish("Token renewed successfully.");
            return true;
        } else {
            publish("WARNING: Token renewal failed: " + result.getMessage());
            return false;
        }
    }

    /**
     * Collects hash information from items in the selected bookmarks.
     * Uses IPEDSearcher and filterBookmarks for efficient retrieval.
     */
    private List<FileHashInfo> collectHashInfoFromBookmarks(Set<String> bookmarkNames) {
        List<FileHashInfo> hashInfos = new ArrayList<>();

        if (ipedSource == null) {
            publish("WARNING: No IPED source configured. Cannot collect items.");
            publish("This is a test/demo mode.");
            return hashInfos;
        }

        if (!(ipedSource instanceof IPEDMultiSource)) {
            publish("WARNING: IPED source is not a multi-source. Cannot collect items.");
            return hashInfos;
        }

        IPEDMultiSource multiSource = (IPEDMultiSource) ipedSource;

        try {
            IMultiBookmarks multiBookmarks = multiSource.getMultiBookmarks();
            if (multiBookmarks == null) {
                publish("WARNING: No bookmarks available.");
                return hashInfos;
            }

            // Use IPEDSearcher to get all items, then filter by bookmarks
            publish("Searching for items in selected bookmarks...");

            IPEDSearcher searcher = new IPEDSearcher(multiSource, "*:*");
            searcher.setNoScoring(true);
            MultiSearchResult allResults = searcher.multiSearch();

            // Filter by selected bookmarks
            IMultiSearchResult filteredResults = multiBookmarks.filterBookmarks(allResults, bookmarkNames);

            int totalItems = filteredResults.getLength();
            publish("Found " + totalItems + " items in selected bookmarks.");

            // Iterate over filtered results
            int processed = 0;
            for (IItemId itemId : filteredResults.getIterator()) {
                if (cancelled) break;

                try {
                    IItem item = multiSource.getItemByItemId(itemId);
                    if (item == null || item.isDir()) {
                        continue;
                    }

                    // Extract hash info from item
                    FileHashInfo hashInfo = extractHashInfo(item);
                    if (hashInfo != null && hasValidHash(hashInfo)) {
                        hashInfo.setItemId(itemId);
                        hashInfos.add(hashInfo);
                    }

                    processed++;
                    if (processed % 1000 == 0) {
                        publish("Collected " + processed + " of " + totalItems + " items...");
                    }

                } catch (Exception e) {
                    // Skip items that cause errors
                }
            }

        } catch (Exception e) {
            publish("ERROR collecting items: " + e.getMessage());
            e.printStackTrace();
        }

        return hashInfos;
    }

    /**
     * Extracts hash information from an IPED item.
     */
    private FileHashInfo extractHashInfo(IItem item) {
        FileHashInfo hashInfo = new FileHashInfo();

        hashInfo.setFileName(item.getName());
        hashInfo.setFileSize(item.getLength() != null ? item.getLength() : 0);
        hashInfo.setFilePath(item.getPath());
        hashInfo.setMimeType(item.getMediaType() != null ? item.getMediaType().toString() : null);

        // Get MD5 hash
        Object md5 = item.getExtraAttribute("md5");
        if (md5 != null) {
            hashInfo.setMd5(md5.toString().toLowerCase());
        }

        // Get SHA-1 hash
        Object sha1 = item.getExtraAttribute("sha-1");
        if (sha1 != null) {
            hashInfo.setSha1(sha1.toString().toLowerCase());
        } else if (item.getHash() != null && !item.getHash().isEmpty()) {
            // Default hash is usually the first configured hash
            hashInfo.setSha1(item.getHash().toLowerCase());
        }

        // Get SHA-256 hash
        Object sha256 = item.getExtraAttribute("sha-256");
        if (sha256 != null) {
            hashInfo.setSha256(sha256.toString().toLowerCase());
        }

        // Get edonkey hash
        Object edonkey = item.getExtraAttribute("edonkey");
        if (edonkey != null) {
            hashInfo.setEd2k(edonkey.toString().toLowerCase());
        }

        return hashInfo;
    }

    /**
     * Checks if the hash info has at least one valid hash.
     */
    private boolean hasValidHash(FileHashInfo hashInfo) {
        return (hashInfo.getMd5() != null && !hashInfo.getMd5().isEmpty()) ||
               (hashInfo.getSha1() != null && !hashInfo.getSha1().isEmpty()) ||
               (hashInfo.getSha256() != null && !hashInfo.getSha256().isEmpty());
    }

    /**
     * Formats bytes to human-readable string.
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
