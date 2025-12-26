package iped.bfac;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.SwingWorker;

import iped.bfac.api.BfacApiClient;
import iped.bfac.api.FileHashInfo;
import iped.bfac.api.FileUploadStatus;
import iped.bfac.api.LoginResult;
import iped.bfac.api.SendHashResult;
import iped.bfac.api.SubmissionResult;
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

    private volatile boolean cancelled = false;
    private int totalItems = 0;
    private int processedItems = 0;
    private int successCount = 0;
    private int errorCount = 0;
    private long lastTokenRenewalTime = 0;

    /**
     * Callback interface for communication with the dialog.
     */
    public interface SubmissionCallback {
        void onLogMessage(String message);
        void onComplete(boolean success);
    }

    public SubmissionWorker(BfacApiClient apiClient, IIPEDSource ipedSource, SubmissionCallback callback,
            boolean isNewSubmission, int submissionId, String submissionName,
            String comment, String categoryName, Set<String> bookmarkNames, boolean uploadFiles) {
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
                    return false;
                }

                submissionId = result.getSubmissionId();
                publish("Submission created with ID: " + submissionId);
            } else {
                publish("Using existing submission: " + submissionName + " (ID: " + submissionId + ")");
            }

            if (cancelled) return false;

            // Step 2: Collect items from bookmarks
            publish("");
            publish("Collecting items from selected bookmarks...");

            List<FileHashInfo> hashInfos = collectHashInfoFromBookmarks(bookmarkNames);

            totalItems = hashInfos.size();
            publish("Found " + totalItems + " items to process");

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
            Map<Integer, FileHashInfo> filesToUpload = new HashMap<>();

            // Process in batches
            for (int i = 0; i < hashInfos.size() && !cancelled; i += BATCH_SIZE) {
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
                    }
                    processedItems++;
                }

                // Update progress
                int progress = (processedItems * 100) / totalItems;
                setProgress(progress);
                publish("Processed " + processedItems + " of " + totalItems + " items...");
            }

            if (cancelled) {
                publish("");
                publish("Operation cancelled by user.");
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

            // Step 5: Upload files if requested
            if (uploadFiles && !filesToUpload.isEmpty()) {
                publish("");
                publish("=== Starting File Upload ===");
                publish("Files to upload: " + filesToUpload.size());

                // Renew token before starting upload (uploads can take days)
                renewTokenIfNeeded(true);

                // Reset progress bar for upload phase
                setProgress(0);

                int uploadedCount = 0;
                int uploadErrorCount = 0;
                long totalBytesUploaded = 0;
                int totalFilesToUpload = filesToUpload.size();

                // Calculate total bytes to upload for accurate progress
                long totalBytesToUpload = 0;
                for (FileHashInfo info : filesToUpload.values()) {
                    totalBytesToUpload += info.getFileSize();
                }

                long bytesUploadedSoFar = 0;

                for (Map.Entry<Integer, FileHashInfo> entry : filesToUpload.entrySet()) {
                    if (cancelled) break;

                    int fileId = entry.getKey();
                    FileHashInfo hashInfo = entry.getValue();

                    long[] bytesUploaded = new long[1]; // Array to allow modification in lambda-like callback
                    boolean success = uploadFile(fileId, hashInfo, totalBytesToUpload, bytesUploadedSoFar, bytesUploaded);
                    if (success) {
                        uploadedCount++;
                        totalBytesUploaded += hashInfo.getFileSize();
                        bytesUploadedSoFar += bytesUploaded[0];
                    } else {
                        uploadErrorCount++;
                        bytesUploadedSoFar += bytesUploaded[0]; // Count partial uploads too
                    }

                    publish("Uploaded " + (uploadedCount + uploadErrorCount) + " of " + totalFilesToUpload + " files...");
                }

                publish("");
                publish("=== File Upload Complete ===");
                publish("Files uploaded: " + uploadedCount);
                publish("Total data: " + formatBytes(totalBytesUploaded));
                if (uploadErrorCount > 0) {
                    publish("Upload errors: " + uploadErrorCount);
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
                if (success) {
                    callback.onLogMessage("");
                    callback.onLogMessage("Operation completed successfully!");
                }
                callback.onComplete(success);
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
        cancel(false);
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
     * Uploads a file to the backend in segments.
     * @param fileId The file ID in the backend
     * @param hashInfo The file hash information
     * @param totalBytesToUpload Total bytes across all files to upload (for progress calculation)
     * @param bytesUploadedBefore Bytes already uploaded from previous files
     * @param bytesUploadedOut Output parameter: bytes uploaded for this file
     * @return true if upload was successful
     */
    private boolean uploadFile(int fileId, FileHashInfo hashInfo, long totalBytesToUpload,
                               long bytesUploadedBefore, long[] bytesUploadedOut) {
        bytesUploadedOut[0] = 0;

        try {
            // Get upload status from backend
            FileUploadStatus status = apiClient.getUploadStatus(fileId);
            if (status == null) {
                publish("  Error: Could not get upload status for file " + hashInfo.getFileName());
                return false;
            }

            // If already complete, skip
            if (status.isComplete()) {
                publish("  File already uploaded: " + hashInfo.getFileName());
                bytesUploadedOut[0] = hashInfo.getFileSize();
                return true;
            }

            // Get the file content from IPED
            IItem item = findItemByHash(hashInfo);
            if (item == null) {
                publish("  Error: Could not find item for file " + hashInfo.getFileName());
                return false;
            }

            long fileSize = item.getLength() != null ? item.getLength() : 0;
            if (fileSize == 0) {
                publish("  Skipping empty file: " + hashInfo.getFileName());
                return true;
            }

            int segmentSize = status.getSegmentSize();
            long startOffset = status.getUploadedSize();

            publish("  Uploading: " + hashInfo.getFileName() + " (" + formatBytes(fileSize) + ")");

            try (InputStream is = item.getBufferedInputStream()) {
                // Skip to start offset if resuming
                if (startOffset > 0) {
                    is.skip(startOffset);
                    bytesUploadedOut[0] = startOffset; // Count already uploaded bytes
                }

                long currentOffset = startOffset;
                byte[] buffer = new byte[segmentSize];

                while (currentOffset < fileSize && !cancelled) {
                    // Renew token periodically (every 12 hours) during long uploads
                    renewTokenIfNeeded(false);

                    int bytesRead = is.read(buffer);
                    if (bytesRead <= 0) break;

                    byte[] segment = bytesRead == buffer.length ? buffer : Arrays.copyOf(buffer, bytesRead);

                    FileUploadStatus result = apiClient.uploadFileSegment(fileId, currentOffset, segment);
                    if (result == null) {
                        publish("    Error uploading segment at offset " + currentOffset);
                        return false;
                    }

                    currentOffset += bytesRead;
                    bytesUploadedOut[0] = currentOffset;

                    // Update progress after each segment
                    if (totalBytesToUpload > 0) {
                        long totalUploaded = bytesUploadedBefore + currentOffset;
                        int progress = (int) ((totalUploaded * 100) / totalBytesToUpload);
                        setProgress(Math.min(progress, 100));
                    }

                    if (result.isComplete()) {
                        break;
                    }
                }

                return true;

            } catch (IOException e) {
                publish("    Error reading file: " + e.getMessage());
                return false;
            }

        } catch (Exception e) {
            publish("  Error uploading file " + hashInfo.getFileName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Finds an IPED item by its stored ItemId or hash information.
     */
    private IItem findItemByHash(FileHashInfo hashInfo) {
        if (ipedSource == null) return null;

        // Use stored ItemId if available (much faster)
        if (hashInfo.getItemId() != null && ipedSource instanceof IPEDMultiSource) {
            try {
                return ((IPEDMultiSource) ipedSource).getItemByItemId(hashInfo.getItemId());
            } catch (Exception e) {
                // Fall back to hash search
            }
        }

        return null;
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
