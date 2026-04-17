package iped.bfac.api;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import iped.bfac.config.BfacConfig;
import iped.engine.Version;
import iped.engine.config.BFACClientConfig;
import iped.engine.config.ConfigurationManager;

/**
 * HTTP client for communicating with the BFAC (Base Federal de Arquivos Conhecidos) backend API.
 * Handles authentication, submission creation, and file/hash uploads.
 */
public class BfacApiClient {

    static {
        // Must be set before HttpClient class is loaded to disable hostname verification
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
    }

    private static final Logger logger = LoggerFactory.getLogger(BfacApiClient.class);
    private static final Gson GSON = new Gson();
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final String USER_AGENT = "IPED-BFAC-Client/" + Version.APP_VERSION + " (" + Version.APP_EXT + ")";
    private static final int MAX_STATUS_BATCH_FILE_IDS = 50;

    public static class BatchUploadSegmentInput {
        private final int fileId;
        private final long offset;
        private final int segmentSize;
        private final byte[] segmentData;

        public BatchUploadSegmentInput(int fileId, long offset, int segmentSize, byte[] segmentData) {
            this.fileId = fileId;
            this.offset = offset;
            this.segmentSize = segmentSize;
            this.segmentData = segmentData;
        }

        public int getFileId() {
            return fileId;
        }

        public long getOffset() {
            return offset;
        }

        public int getSegmentSize() {
            return segmentSize;
        }

        public byte[] getSegmentData() {
            return segmentData;
        }
    }

    public static class BatchUploadSegmentResult {
        private final int fileId;
        private final boolean success;
        private final FileUploadStatus status;
        private final String error;
        private final int statusCode;

        public BatchUploadSegmentResult(int fileId, boolean success, FileUploadStatus status, String error, int statusCode) {
            this.fileId = fileId;
            this.success = success;
            this.status = status;
            this.error = error;
            this.statusCode = statusCode;
        }

        public int getFileId() {
            return fileId;
        }

        public boolean isSuccess() {
            return success;
        }

        public FileUploadStatus getStatus() {
            return status;
        }

        public String getError() {
            return error;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public boolean isUnauthorized() {
            return statusCode == 401;
        }
    }

    public static class BatchFileUploadStatusItem {
        private final int fileId;
        private final boolean found;
        private final FileUploadStatus status;
        private final String error;
        private final int statusCode;

        public BatchFileUploadStatusItem(int fileId, boolean found, FileUploadStatus status, String error, int statusCode) {
            this.fileId = fileId;
            this.found = found;
            this.status = status;
            this.error = error;
            this.statusCode = statusCode;
        }

        public int getFileId() {
            return fileId;
        }

        public boolean isFound() {
            return found;
        }

        public FileUploadStatus getStatus() {
            return status;
        }

        public String getError() {
            return error;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public boolean isUnauthorized() {
            return statusCode == 401;
        }
    }

    private String baseUrl;
    private String accessToken;
    private HttpClient httpClient;
    private BfacConfig config;

    public BfacApiClient() {
        this.config = BfacConfig.getInstance();
        // Load base URL from IPED configuration
        BFACClientConfig bfacClientConfig = ConfigurationManager.get().findObject(BFACClientConfig.class);
        this.baseUrl = bfacClientConfig != null ? bfacClientConfig.getBaseUrl() : "http://localhost:8000/";
        if (!this.baseUrl.endsWith("/")) {
            this.baseUrl += "/";
        }
        this.httpClient = createHttpClient();

        // Load existing access token if available
        this.accessToken = config.getAccessToken();
    }

    public BfacApiClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.httpClient = createHttpClient();
        this.config = BfacConfig.getInstance();

        // Load existing access token if available
        this.accessToken = config.getAccessToken();
    }

    /**
     * Creates an HttpClient that accepts all SSL certificates, including self-signed ones.
     * This is useful for development/testing environments with self-signed certificates.
     * @return HttpClient configured to trust all certificates
     */
    private HttpClient createHttpClient() {
        try {
            // Create a trust manager that accepts all certificates
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
            };

            // Install the all-trusting trust manager
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            return HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT)
                    .version(HttpClient.Version.HTTP_1_1)
                    .sslContext(sslContext)
                    .build();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            logger.warn("Failed to create insecure SSL context, falling back to default: {}", e.getMessage());
            // Fallback to default HTTP client if SSL configuration fails
            return HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT)
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();
        }
    }

    /**
     * Creates a new HttpRequest.Builder with common headers (User-Agent).
     * @param url The URL for the request
     * @return HttpRequest.Builder with common headers set
     */
    private HttpRequest.Builder newRequestBuilder(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("User-Agent", USER_AGENT);
    }

    /**
     * Creates a new HttpRequest.Builder with common headers including Authorization.
     * @param url The URL for the request
     * @return HttpRequest.Builder with common headers and Authorization set
     */
    private HttpRequest.Builder newAuthenticatedRequestBuilder(String url) {
        return newRequestBuilder(url)
                .header("Authorization", "Bearer " + accessToken);
    }

    /**
     * Authenticates with the BFAC server.
     * @param username The username
     * @param password The password
     * @return LoginResult with success status and message
     */
    public LoginResult login(String username, String password) {
        try {
            // Build form data (application/x-www-form-urlencoded)
            String formData = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8) +
                              "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);

            HttpRequest request = newRequestBuilder(baseUrl + "api/auth/token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formData))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject tokenResponse = GSON.fromJson(response.body(), JsonObject.class);
                this.accessToken = tokenResponse.get("access_token").getAsString();

                // Save credentials to shared config file
                config.saveCredentials(tokenResponse);
                config.saveUsername(username);

                logger.info("Login successful for user: {}", username);
                return new LoginResult(true, "Login successful");

            } else if (response.statusCode() == 401) {
                logger.warn("Login failed: Invalid credentials for user: {}", username);
                return new LoginResult(false, "Invalid username or password");

            } else {
                String errorMsg = "Login failed with status " + response.statusCode() + ": " + response.body();
                logger.error(errorMsg);
                return new LoginResult(false, errorMsg);
            }

        } catch (IOException e) {
            String errorMsg = "Connection error: " + e.getMessage();
            logger.error("Login failed due to connection error", e);
            return new LoginResult(false, errorMsg);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new LoginResult(false, "Login interrupted");

        } catch (Exception e) {
            String errorMsg = "Unexpected error: " + e.getMessage();
            logger.error("Login failed due to unexpected error", e);
            return new LoginResult(false, errorMsg);
        }
    }

    /**
     * Renews the access token using the current token.
     * @return LoginResult with success status and message
     */
    public synchronized LoginResult renewToken() {
        if (accessToken == null || accessToken.isEmpty()) {
            return new LoginResult(false, "No access token available");
        }

        try {
            HttpRequest request = newAuthenticatedRequestBuilder(baseUrl + "api/auth/users/renew_token")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject tokenResponse = GSON.fromJson(response.body(), JsonObject.class);
                this.accessToken = tokenResponse.get("access_token").getAsString();

                // Save renewed credentials
                config.saveCredentials(tokenResponse);

                logger.info("Token renewed successfully");
                return new LoginResult(true, "Token renewed");

            } else {
                String errorMsg = "Token renewal failed with status " + response.statusCode();
                logger.warn(errorMsg);
                return new LoginResult(false, errorMsg);
            }

        } catch (IOException e) {
            String errorMsg = "Connection error: " + e.getMessage();
            logger.error("Token renewal failed", e);
            return new LoginResult(false, errorMsg);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new LoginResult(false, "Token renewal interrupted");
        }
    }

    /**
     * Creates a new submission in the BFAC backend.
     * @param name Submission name
     * @param comment Optional comment (can be null)
     * @param categoryName Category name
     * @return SubmissionResult with submission ID if successful
     */
    public SubmissionResult createSubmission(String name, String comment, String categoryName) {
        if (accessToken == null || accessToken.isEmpty()) {
            return new SubmissionResult(false, -1, "Not authenticated");
        }

        try {
            JsonObject submissionData = new JsonObject();
            submissionData.addProperty("name", name);
            submissionData.addProperty("category_name", categoryName);
            if (comment != null && !comment.isEmpty()) {
                submissionData.addProperty("comment", comment);
            }

            HttpRequest request = newAuthenticatedRequestBuilder(baseUrl + "api/v1/submissions/")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(submissionData)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201) {
                JsonObject responseObj = GSON.fromJson(response.body(), JsonObject.class);
                JsonObject data = responseObj.getAsJsonObject("data");
                int submissionId = data.get("id").getAsInt();
                logger.info("Created submission {} with ID {}", name, submissionId);
                return new SubmissionResult(true, submissionId, "Submission created successfully");

            } else {
                String errorMsg = "Failed to create submission: " + response.statusCode();
                try {
                    JsonObject errorObj = GSON.fromJson(response.body(), JsonObject.class);
                    if (errorObj.has("detail")) {
                        errorMsg = errorObj.get("detail").getAsString();
                    }
                } catch (Exception e) {
                    // Use default error message
                }
                logger.error(errorMsg);
                return new SubmissionResult(false, -1, errorMsg, response.statusCode());
            }

        } catch (IOException e) {
            String errorMsg = "Connection error: " + e.getMessage();
            logger.error("Failed to create submission", e);
            return new SubmissionResult(false, -1, errorMsg, -1);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SubmissionResult(false, -1, "Operation interrupted", -1);
        }
    }

    /**
     * Sends file hashes to the BFAC backend for a submission (single hash).
     * @param submissionId The submission ID
     * @param hashInfo Hash information for a single file
     * @return SendHashResult with file ID if successful
     */
    public SendHashResult sendHash(int submissionId, FileHashInfo hashInfo) {
        if (accessToken == null || accessToken.isEmpty()) {
            return new SendHashResult(false, -1, "Not authenticated", false);
        }

        try {
            JsonObject hashData = new JsonObject();
            JsonArray filenames = new JsonArray();
            filenames.add(hashInfo.getFileName());
            hashData.add("filenames", filenames);
            hashData.addProperty("file_size", hashInfo.getFileSize());
            if (hashInfo.getMd5() != null) hashData.addProperty("md5", hashInfo.getMd5());
            if (hashInfo.getSha1() != null) hashData.addProperty("sha1", hashInfo.getSha1());
            if (hashInfo.getSha256() != null) hashData.addProperty("sha256", hashInfo.getSha256());

            HttpRequest request = newAuthenticatedRequestBuilder(baseUrl + "api/v1/submissions/" + submissionId + "/files/hashes")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(hashData)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                JsonObject responseObj = GSON.fromJson(response.body(), JsonObject.class);
                JsonObject data = responseObj.getAsJsonObject("data");
                int fileId = data.has("id") ? data.get("id").getAsInt() : -1;
                boolean hasFile = data.has("has_file") && data.get("has_file").getAsBoolean();
                return new SendHashResult(true, fileId, "Hash sent successfully", hasFile);

            } else {
                String errorMsg = "Failed to send hash: " + response.statusCode();
                logger.error(errorMsg);
                return new SendHashResult(false, -1, errorMsg, false, response.statusCode());
            }

        } catch (IOException e) {
            logger.error("Failed to send hash", e);
            return new SendHashResult(false, -1, "Connection error: " + e.getMessage(), false, -1);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SendHashResult(false, -1, "Operation interrupted", false, -1);
        }
    }

    /**
     * Sends multiple file hashes to the BFAC backend in a batch.
     * @param submissionId The submission ID
     * @param hashInfos List of hash information
     * @return List of SendHashResult for each hash
     */
    public List<SendHashResult> sendHashesBatch(int submissionId, List<FileHashInfo> hashInfos) {
        List<SendHashResult> results = new ArrayList<>();

        if (accessToken == null || accessToken.isEmpty()) {
            for (int i = 0; i < hashInfos.size(); i++) {
                results.add(new SendHashResult(false, -1, "Not authenticated", false));
            }
            return results;
        }

        if (hashInfos.isEmpty()) {
            return results;
        }

        try {
            // Build batch request
            JsonArray fileDataList = new JsonArray();
            for (FileHashInfo hashInfo : hashInfos) {
                JsonObject hashData = new JsonObject();
                JsonArray filenames = new JsonArray();
                filenames.add(hashInfo.getFileName());
                hashData.add("filenames", filenames);
                hashData.addProperty("file_size", hashInfo.getFileSize());
                if (hashInfo.getMd5() != null) hashData.addProperty("md5", hashInfo.getMd5());
                if (hashInfo.getSha1() != null) hashData.addProperty("sha1", hashInfo.getSha1());
                if (hashInfo.getSha256() != null) hashData.addProperty("sha256", hashInfo.getSha256());
                if (hashInfo.getEd2k() != null) hashData.addProperty("ed2k", hashInfo.getEd2k());
                fileDataList.add(hashData);
            }

            JsonObject batchData = new JsonObject();
            batchData.add("file_data", fileDataList);

            HttpRequest request = newAuthenticatedRequestBuilder(baseUrl + "api/v1/submissions/" + submissionId + "/files/hashes_batched")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(batchData)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                JsonObject responseObj = GSON.fromJson(response.body(), JsonObject.class);
                JsonArray dataArray = responseObj.getAsJsonArray("data");

                for (int i = 0; i < dataArray.size(); i++) {
                    JsonObject data = dataArray.get(i).getAsJsonObject();
                    int fileId = data.has("id") ? data.get("id").getAsInt() : -1;
                    boolean hasFile = data.has("has_file") && data.get("has_file").getAsBoolean();
                    results.add(new SendHashResult(true, fileId, "Hash sent successfully", hasFile));
                }

                logger.info("Sent {} hashes in batch to submission {}", results.size(), submissionId);
                return results;

            } else {
                String errorMsg = "Failed to send hashes batch: " + response.statusCode();
                logger.error(errorMsg);
                for (int i = 0; i < hashInfos.size(); i++) {
                    results.add(new SendHashResult(false, -1, errorMsg, false, response.statusCode()));
                }
                return results;
            }

        } catch (IOException e) {
            logger.error("Failed to send hashes batch", e);
            for (int i = 0; i < hashInfos.size(); i++) {
                results.add(new SendHashResult(false, -1, "Connection error: " + e.getMessage(), false, -1));
            }
            return results;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            for (int i = 0; i < hashInfos.size(); i++) {
                results.add(new SendHashResult(false, -1, "Operation interrupted", false, -1));
            }
            return results;
        }
    }

    /**
     * Gets the upload status for a file from the backend.
     * @param fileId The file ID in the backend
     * @return FileUploadStatus with success or error information
     */
    public FileUploadStatus getUploadStatus(int fileId) {
        if (accessToken == null || accessToken.isEmpty()) {
            return FileUploadStatus.error(fileId, 401, "Not authenticated");
        }

        try {
            HttpRequest request = newAuthenticatedRequestBuilder(baseUrl + "api/v1/file-transfer/" + fileId + "/status")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject data = GSON.fromJson(response.body(), JsonObject.class);
                return new FileUploadStatus(
                    fileId,
                    data.has("total_size") ? data.get("total_size").getAsLong() : 0,
                    data.has("uploaded_size") ? data.get("uploaded_size").getAsLong() : 0,
                    data.has("segment_size") ? data.get("segment_size").getAsInt() : 1024 * 1024,
                    data.has("is_complete") && data.get("is_complete").getAsBoolean()
                );
            }
            String errorMsg = "Failed to get upload status: " + response.statusCode();
            logger.error(errorMsg);
            return FileUploadStatus.error(fileId, response.statusCode(), errorMsg);

        } catch (Exception e) {
            logger.error("Error getting upload status for file {}", fileId, e);
            return FileUploadStatus.error(fileId, -1, "Connection error: " + e.getMessage());
        }
    }

    public java.util.Map<Integer, BatchFileUploadStatusItem> getUploadStatusBatch(List<Integer> fileIds) {
        java.util.Map<Integer, BatchFileUploadStatusItem> byFileId = new java.util.HashMap<>();

        if (fileIds == null || fileIds.isEmpty()) {
            return byFileId;
        }

        if (accessToken == null || accessToken.isEmpty()) {
            for (Integer fileId : fileIds) {
                byFileId.put(fileId, new BatchFileUploadStatusItem(fileId, false, null, "Not authenticated", 401));
            }
            return byFileId;
        }

        for (int start = 0; start < fileIds.size(); start += MAX_STATUS_BATCH_FILE_IDS) {
            int end = Math.min(start + MAX_STATUS_BATCH_FILE_IDS, fileIds.size());
            List<Integer> chunk = fileIds.subList(start, end);

            try {
                JsonObject payload = new JsonObject();
                JsonArray fileIdsArray = new JsonArray();
                for (Integer fileId : chunk) {
                    fileIdsArray.add(fileId);
                }
                payload.add("file_ids", fileIdsArray);

                HttpRequest request = newAuthenticatedRequestBuilder(baseUrl + "api/v1/file-transfer/status-batch")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    String errorMsg = "Failed to get batch upload status: " + response.statusCode();
                    logger.error(errorMsg + " - " + response.body());
                    for (Integer fileId : chunk) {
                        byFileId.put(fileId, new BatchFileUploadStatusItem(fileId, false, null, errorMsg, response.statusCode()));
                    }
                    continue;
                }

                JsonObject responseObj = GSON.fromJson(response.body(), JsonObject.class);
                JsonArray items = responseObj.getAsJsonArray("items");
                for (JsonElement element : items) {
                    JsonObject item = element.getAsJsonObject();
                    int fileId = item.has("file_id") ? item.get("file_id").getAsInt() : -1;
                    boolean found = item.has("found") && item.get("found").getAsBoolean();
                    String error = item.has("error") && !item.get("error").isJsonNull()
                            ? item.get("error").getAsString()
                            : null;
                    int statusCode = item.has("status_code") && !item.get("status_code").isJsonNull()
                            ? item.get("status_code").getAsInt()
                            : (found ? 200 : 404);
                    FileUploadStatus status = null;
                    if (item.has("status") && item.get("status").isJsonObject()) {
                        JsonObject statusObj = item.getAsJsonObject("status");
                        status = new FileUploadStatus(
                                fileId,
                                statusObj.has("total_size") ? statusObj.get("total_size").getAsLong() : 0,
                                statusObj.has("uploaded_size") ? statusObj.get("uploaded_size").getAsLong() : 0,
                                statusObj.has("segment_size") ? statusObj.get("segment_size").getAsInt() : 1024 * 1024,
                                statusObj.has("is_complete") && statusObj.get("is_complete").getAsBoolean());
                    }
                    byFileId.put(fileId, new BatchFileUploadStatusItem(fileId, found, status, error, statusCode));
                }

                for (Integer fileId : chunk) {
                    byFileId.putIfAbsent(fileId, new BatchFileUploadStatusItem(fileId, false, null, "Missing status item", 500));
                }
            } catch (Exception e) {
                logger.error("Error getting batch upload status", e);
                for (Integer fileId : chunk) {
                    byFileId.put(fileId, new BatchFileUploadStatusItem(fileId, false, null, "Connection error: " + e.getMessage(), -1));
                }
            }
        }

        return byFileId;
    }

    /**
     * Uploads a file segment to the BFAC backend.
     * @param fileId The file ID in the backend
     * @param offset Byte offset for this segment
     * @param segmentData The segment data
     * @return FileUploadStatus with success or error information
     */
    public FileUploadStatus uploadFileSegment(int fileId, long offset, byte[] segmentData) {
        if (accessToken == null || accessToken.isEmpty()) {
            return FileUploadStatus.error(fileId, 401, "Not authenticated");
        }

        try {
            String url = baseUrl + "api/v1/file-transfer/" + fileId + "/upload-segment" +
                         "?offset=" + offset + "&segment_size=" + segmentData.length;

            HttpRequest request = newAuthenticatedRequestBuilder(url)
                    .header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(segmentData))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject responseData = GSON.fromJson(response.body(), JsonObject.class);
                return new FileUploadStatus(
                    fileId,
                    responseData.has("total_size") ? responseData.get("total_size").getAsLong() : 0,
                    responseData.has("uploaded_size") ? responseData.get("uploaded_size").getAsLong() : 0,
                    responseData.has("segment_size") ? responseData.get("segment_size").getAsInt() : 1024 * 1024,
                    responseData.has("is_complete") && responseData.get("is_complete").getAsBoolean()
                );
            } else {
                String errorMsg = "Error uploading segment: " + response.statusCode();
                logger.error(errorMsg + " - " + response.body());
                return FileUploadStatus.error(fileId, response.statusCode(), errorMsg);
            }

        } catch (Exception e) {
            logger.error("Error uploading segment for file {}", fileId, e);
            return FileUploadStatus.error(fileId, -1, "Connection error: " + e.getMessage());
        }
    }

    public List<BatchUploadSegmentResult> uploadFileSegmentsBatch(List<BatchUploadSegmentInput> segments) {
        List<BatchUploadSegmentResult> results = new ArrayList<>();

        if (accessToken == null || accessToken.isEmpty()) {
            for (BatchUploadSegmentInput item : segments) {
                results.add(new BatchUploadSegmentResult(
                        item.getFileId(),
                        false,
                        null,
                        "Not authenticated",
                        401));
            }
            return results;
        }

        if (segments.isEmpty()) {
            return results;
        }

        try {
            byte[] payload = buildBatchPayload(segments);
            String manifestJson = buildBatchManifestJson(segments);
            String boundary = "----IPEDBfacBatch" + UUID.randomUUID().toString().replace("-", "");
            byte[] multipartBody = buildMultipartBody(boundary, manifestJson, payload);

            HttpRequest request = newAuthenticatedRequestBuilder(baseUrl + "api/v1/file-transfer/upload-segments-batch")
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                String errorMsg = "Failed to upload batch segments: " + response.statusCode();
                logger.error(errorMsg + " - " + response.body());
                for (BatchUploadSegmentInput item : segments) {
                    results.add(new BatchUploadSegmentResult(item.getFileId(), false, null, errorMsg, response.statusCode()));
                }
                return results;
            }

            JsonObject responseObj = GSON.fromJson(response.body(), JsonObject.class);
            JsonArray items = responseObj.getAsJsonArray("items");
            for (JsonElement element : items) {
                JsonObject item = element.getAsJsonObject();
                int fileId = item.has("file_id") ? item.get("file_id").getAsInt() : -1;
                boolean success = item.has("success") && item.get("success").getAsBoolean();
                String error = item.has("error") && !item.get("error").isJsonNull()
                        ? item.get("error").getAsString()
                        : null;
                int statusCode = item.has("status_code") && !item.get("status_code").isJsonNull()
                        ? item.get("status_code").getAsInt()
                        : (success ? 200 : -1);
                FileUploadStatus status = null;
                if (item.has("status") && item.get("status").isJsonObject()) {
                    JsonObject statusObj = item.getAsJsonObject("status");
                    status = new FileUploadStatus(
                            fileId,
                            statusObj.has("total_size") ? statusObj.get("total_size").getAsLong() : 0,
                            statusObj.has("uploaded_size") ? statusObj.get("uploaded_size").getAsLong() : 0,
                            statusObj.has("segment_size") ? statusObj.get("segment_size").getAsInt() : 1024 * 1024,
                            statusObj.has("is_complete") && statusObj.get("is_complete").getAsBoolean());
                }
                results.add(new BatchUploadSegmentResult(fileId, success, status, error, statusCode));
            }

            return results;
        } catch (Exception e) {
            logger.error("Error uploading batch segments", e);
            for (BatchUploadSegmentInput item : segments) {
                results.add(new BatchUploadSegmentResult(
                        item.getFileId(),
                        false,
                        null,
                        "Connection error: " + e.getMessage(),
                        -1));
            }
            return results;
        }
    }

    private byte[] buildBatchPayload(List<BatchUploadSegmentInput> segments) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        for (BatchUploadSegmentInput item : segments) {
            payload.writeBytes(item.getSegmentData());
        }
        return payload.toByteArray();
    }

    private String buildBatchManifestJson(List<BatchUploadSegmentInput> segments) {
        JsonArray items = new JsonArray();
        int payloadOffset = 0;
        for (BatchUploadSegmentInput item : segments) {
            JsonObject manifestItem = new JsonObject();
            manifestItem.addProperty("file_id", item.getFileId());
            manifestItem.addProperty("offset", item.getOffset());
            manifestItem.addProperty("length", item.getSegmentData().length);
            manifestItem.addProperty("payload_offset", payloadOffset);
            manifestItem.addProperty("segment_size", item.getSegmentSize());
            items.add(manifestItem);
            payloadOffset += item.getSegmentData().length;
        }
        JsonObject manifest = new JsonObject();
        manifest.add("items", items);
        return GSON.toJson(manifest);
    }

    private byte[] buildMultipartBody(String boundary, String manifestJson, byte[] payload) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] newline = "\r\n".getBytes(StandardCharsets.UTF_8);
        body.writeBytes(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes("Content-Disposition: form-data; name=\"manifest\"\r\n".getBytes(StandardCharsets.UTF_8));
        body.writeBytes("Content-Type: application/json\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        body.writeBytes(manifestJson.getBytes(StandardCharsets.UTF_8));
        body.writeBytes(newline);
        body.writeBytes(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(
                "Content-Disposition: form-data; name=\"payload\"; filename=\"payload.bin\"\r\n"
                        .getBytes(StandardCharsets.UTF_8));
        body.writeBytes("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        body.writeBytes(payload);
        body.writeBytes(newline);
        body.writeBytes(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return body.toByteArray();
    }

    /**
     * Gets the list of available categories from the backend.
     * @return List of Category objects, or empty list on error
     */
    public List<Category> getCategories() {
        List<Category> categories = new ArrayList<>();

        if (accessToken == null || accessToken.isEmpty()) {
            logger.warn("Cannot get categories: not authenticated");
            return categories;
        }

        try {
            HttpRequest request = newAuthenticatedRequestBuilder(baseUrl + "api/v1/categories/")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject responseObj = GSON.fromJson(response.body(), JsonObject.class);
                JsonArray data = responseObj.getAsJsonArray("data");

                for (JsonElement element : data) {
                    JsonObject catObj = element.getAsJsonObject();
                    Category category = new Category(
                            catObj.get("id").getAsInt(),
                            catObj.get("name").getAsString(),
                            catObj.has("description") && !catObj.get("description").isJsonNull()
                                ? catObj.get("description").getAsString() : "",
                            catObj.has("ignorable") && catObj.get("ignorable").getAsBoolean(),
                            catObj.has("alert") && catObj.get("alert").getAsBoolean()
                    );
                    categories.add(category);
                }
                logger.info("Retrieved {} categories from server", categories.size());

            } else if (response.statusCode() == 401) {
                logger.warn("Authentication failed when getting categories");
            } else {
                logger.error("Failed to get categories: status {}", response.statusCode());
            }

        } catch (IOException e) {
            logger.error("Connection error getting categories", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Error parsing categories response", e);
        }

        return categories;
    }

    /**
     * Gets the list of submissions from the backend using cursor-based pagination.
     * @param closed If true, get closed submissions; if false, get open submissions
     * @param cursor Cursor for pagination (null for first page)
     * @param limit Maximum number of records to return
     * @return PaginationResult containing submissions and pagination metadata
     */
    public PaginationResult<Submission> getSubmissions(boolean closed, String cursor, int limit) {
        PaginationResult<Submission> result = new PaginationResult<>();
        List<Submission> submissions = new ArrayList<>();

        if (accessToken == null || accessToken.isEmpty()) {
            logger.warn("Cannot get submissions: not authenticated");
            return result;
        }

        try {
            StringBuilder urlBuilder = new StringBuilder(baseUrl + "api/v1/submissions/?closed=" + closed);
            if (cursor != null && !cursor.isEmpty()) {
                urlBuilder.append("&cursor=").append(URLEncoder.encode(cursor, StandardCharsets.UTF_8));
            }
            urlBuilder.append("&limit=").append(limit);

            HttpRequest request = newAuthenticatedRequestBuilder(urlBuilder.toString())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject responseObj = GSON.fromJson(response.body(), JsonObject.class);
                JsonArray data = responseObj.getAsJsonArray("data");

                for (JsonElement element : data) {
                    JsonObject subObj = element.getAsJsonObject();
                    Submission submission = new Submission();
                    submission.setId(subObj.get("id").getAsInt());
                    submission.setName(subObj.get("name").getAsString());

                    if (subObj.has("comment") && !subObj.get("comment").isJsonNull()) {
                        submission.setComment(subObj.get("comment").getAsString());
                    }
                    if (subObj.has("category_id") && !subObj.get("category_id").isJsonNull()) {
                        submission.setCategoryId(subObj.get("category_id").getAsInt());
                    }
                    if (subObj.has("category_name") && !subObj.get("category_name").isJsonNull()) {
                        submission.setCategoryName(subObj.get("category_name").getAsString());
                    }
                    if (subObj.has("vote_count")) {
                        submission.setVoteCount(subObj.get("vote_count").getAsInt());
                    }
                    if (subObj.has("closed")) {
                        submission.setClosed(subObj.get("closed").getAsBoolean());
                    }
                    if (subObj.has("created_at") && !subObj.get("created_at").isJsonNull()) {
                        String createdAtStr = subObj.get("created_at").getAsString();
                        try {
                            // Parse ISO 8601 format
                            LocalDateTime createdAt = LocalDateTime.parse(createdAtStr,
                                    DateTimeFormatter.ISO_DATE_TIME);
                            submission.setCreatedAt(createdAt);
                        } catch (Exception e) {
                            logger.debug("Could not parse date: {}", createdAtStr);
                        }
                    }

                    submissions.add(submission);
                }

                // Parse pagination metadata
                PaginationResult.PaginationMeta paginationMeta = null;
                if (responseObj.has("meta") && responseObj.getAsJsonObject("meta").has("pagination")) {
                    JsonObject paginationObj = responseObj.getAsJsonObject("meta").getAsJsonObject("pagination");
                    paginationMeta = new PaginationResult.PaginationMeta();
                    paginationMeta.setLimit(paginationObj.has("limit") ? paginationObj.get("limit").getAsInt() : limit);
                    paginationMeta.setHasMore(paginationObj.has("has_more") && paginationObj.get("has_more").getAsBoolean());
                    paginationMeta.setNextCursor(paginationObj.has("next_cursor") && !paginationObj.get("next_cursor").isJsonNull()
                            ? paginationObj.get("next_cursor").getAsString() : null);
                    paginationMeta.setCursorField(paginationObj.has("cursor_field") ? paginationObj.get("cursor_field").getAsString() : "created_at");
                }

                result.setData(submissions);
                result.setPagination(paginationMeta);
                logger.info("Retrieved {} submissions from server", submissions.size());

            } else if (response.statusCode() == 401) {
                logger.warn("Authentication failed when getting submissions");
            } else {
                logger.error("Failed to get submissions: status {}", response.statusCode());
            }

        } catch (IOException e) {
            logger.error("Connection error getting submissions", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Error parsing submissions response", e);
        }

        return result;
    }

    /**
     * Gets open submissions from the backend.
     * @return List of open Submission objects
     */
    public List<Submission> getOpenSubmissions() {
        PaginationResult<Submission> result = getSubmissions(false, null, 100);
        return result.getData();
    }

    /**
     * Gets closed submissions from the backend.
     * @return List of closed Submission objects
     */
    public List<Submission> getClosedSubmissions() {
        PaginationResult<Submission> result = getSubmissions(true, null, 100);
        return result.getData();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    public boolean isAuthenticated() {
        return accessToken != null && !accessToken.isEmpty();
    }

    /**
     * Validates the current session by making a request to the server.
     * This is useful to check if a cached token is still valid.
     * @return ValidationResult with success status and HTTP status code
     */
    public ValidationResult validateSession() {
        if (accessToken == null || accessToken.isEmpty()) {
            return new ValidationResult(false, 0, "No access token available");
        }

        try {
            // Use the renew_token endpoint to validate and refresh the token
            HttpRequest request = newAuthenticatedRequestBuilder(baseUrl + "api/auth/users/renew_token")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject tokenResponse = GSON.fromJson(response.body(), JsonObject.class);
                this.accessToken = tokenResponse.get("access_token").getAsString();

                // Save renewed credentials
                config.saveCredentials(tokenResponse);

                logger.info("Session validated and token renewed");
                return new ValidationResult(true, 200, "Session valid");

            } else if (response.statusCode() == 401) {
                logger.warn("Session validation failed: token expired or invalid");
                // Clear invalid credentials
                this.accessToken = null;
                config.clearCredentials();
                return new ValidationResult(false, 401, "Session expired");

            } else {
                String errorMsg = "Session validation failed with status " + response.statusCode();
                logger.warn(errorMsg);
                return new ValidationResult(false, response.statusCode(), errorMsg);
            }

        } catch (IOException e) {
            String errorMsg = "Connection error: " + e.getMessage();
            logger.error("Session validation failed", e);
            return new ValidationResult(false, -1, errorMsg);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ValidationResult(false, -1, "Validation interrupted");
        }
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void logout() {
        this.accessToken = null;
        config.clearCredentials();
        logger.info("Logged out");
    }

    /**
     * Gets the stored username from configuration.
     * @return The stored username or null
     */
    public String getStoredUsername() {
        return config.getStoredUsername();
    }
}
