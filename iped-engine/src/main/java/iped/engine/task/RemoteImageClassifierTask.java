package iped.engine.task;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;
import javax.net.ssl.SSLContext;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.tika.io.TemporaryResources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import iped.configuration.Configurable;
import iped.data.IHashValue;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.RemoteImageClassifierConfig;
import iped.engine.preview.PreviewRepositoryManager;
import iped.engine.task.die.DIETask;
import iped.engine.task.index.IndexItem;
import iped.parsers.util.MetadataUtil;
import iped.utils.ImageUtil;

/**
 * Performs remote classification of image and video files.
 * 
 * @implNote Sends files in batches for improved performance (see 'batchSize' config property).
 *           Stores classification results in evidence's extra attributes.
 *           Attributes' names are controlled by the remote classifier (usually prefixed by 'AI').
 */
public class RemoteImageClassifierTask extends AbstractTask {

    private static Logger logger = LoggerFactory.getLogger(RemoteImageClassifierTask.class);

    private RemoteImageClassifierConfig config;

    // Task enabled status
    private static boolean enabled = true;

    // Configuration parameters
    private String urlZip;
    private String urlVersion;
    private int batchSize;
    private int skipSize;
    private int skipDimension;
    private boolean skipHashDBFiles;   
    private boolean validateSSL;
    private double labelingThreshold;

    // Labeling classes name in priority order
    private static final List<String> classesName = new ArrayList<>();
    private static final Map<String, String> labelNames = new HashMap<>();
    static {
        initLabeling();
    }

    // AI-related attributes prefix
    private static final String aiPrefix = "ai:";
    
    // AI classification extra attributes and values
    private static final String AI_CLASSIFICATION_STATUS_ATTR = aiPrefix + "classificationStatus";
    private static final String AI_CLASSIFICATION_SUCCESS = "success";
    private static final String AI_CLASSIFICATION_FAIL_NO_CLASS = "failNoClass";
    private static final String AI_CLASSIFICATION_FAIL_NO_RESULTS = "failNoResults";
    private static final String AI_CLASSIFICATION_SKIP_SIZE = "skippedSize";
    private static final String AI_CLASSIFICATION_SKIP_DIMENSION = "skippedDimension";
    private static final String AI_CLASSIFICATION_SKIP_HASHDB = "skippedHashDB";
    private static final String AI_CLASSIFICATION_LABEL_ATTR = aiPrefix + "label";

    // Classifications cache (avoids classification of duplicates)
    private static ConcurrentHashMap<IHashValue, String> classifications = new ConcurrentHashMap<>();

    // Queue to store 'name' to 'evidence' mapping
    private Map<String, IItem> queue = new TreeMap<>();
    private LinkedList<IItem> sendToNext = new LinkedList<>();

    // Zip archive holding files to be sent for classification
    private ZipFile zip;

    // Variables related to execution control and information
    private static final AtomicBoolean abortNow = new AtomicBoolean();
    private static final AtomicBoolean finishNow = new AtomicBoolean();
    private static final AtomicInteger lastBatch = new AtomicInteger();

    // Variables related to statistics
    private static final AtomicLong classificationTime = new AtomicLong();
    private static final AtomicInteger classificationSuccess = new AtomicInteger();
    private static final AtomicInteger classificationFail = new AtomicInteger();
    private static final AtomicLong sendImageBytes = new AtomicLong();
    private static final AtomicLong sendVideoBytes = new AtomicLong();
    private static final AtomicInteger countImageThumbs = new AtomicInteger(); // also represents 'countImageFiles' for count purposes
    private static final AtomicInteger countVideoThumbs = new AtomicInteger();
    private static final AtomicInteger countVideoFiles = new AtomicInteger();
    private static final AtomicInteger skipDuplicatesCount = new AtomicInteger();
    private static final AtomicInteger skipSizeCount = new AtomicInteger();
    private static final AtomicInteger skipDimensionCount = new AtomicInteger();
    private static final AtomicInteger skipHashDBFilesCount = new AtomicInteger();
    private static final AtomicInteger activeInstances = new AtomicInteger(0);

    // Number of the current batch of files being processed
    private int currentBatch;

    // Number of retry failed classifications performed
    private int retryCount;

    // Maximum number of attempts to retry failed classifications
    private static final int MAX_RETRY = 10;

    // Wait time (ms) before retry attempts
    private static final int WAIT_BEFORE_RETRY = 100;

    /**
     * Represents the result of a classification.
     */
    private static class ResultItem {
        private final TreeMap<String, List<Double>> classes = new TreeMap<>();
        private final HashMap<String, Double> classesProb = new HashMap<>();

        // Add classification value for 'classname'.
        // It stores classification for images and grouped classification data for video frames and animation image frames
        public void addClass(String classname, double val) {
            if (!classes.containsKey(classname)) {
                classes.put(classname, new ArrayList<>());
            }
            classes.get(classname).add(val);
        }

        // Get probability value for 'classname'.
        public Double getClassProb(String classname) {
            Double value = classesProb.get(classname);
            if (value == null) {
                List<Double> probs = classes.get(classname);
                if (probs != null) {
                    value = DIETask.videoScore(probs);
    
                    // Scale values from [0,1] to [0, 100] and
                    // limit them to 2 decimal digits.
                    value = Math.round(value * 10000) / 100.0;
    
                    classesProb.put(classname, value);
                }
            }
            return value;
        }
    }

    /**
     * Represents a Zip archive holding files to be sent for classification.
     */
    private static class ZipFile {
        TemporaryResources tmp;
        File zipFile;
        FileOutputStream fos;
        ZipOutputStream zos;
        int fileCount;

        public ZipFile() throws IOException {
            tmp = new TemporaryResources();
            zipFile = tmp.createTemporaryFile();
            fos = new FileOutputStream(zipFile);
            zos = new ZipOutputStream(fos);
            fileCount = 0;
        }

        public int getFileCount() {
            return fileCount;
        }

        public void addFileToZip(String filename, byte[] data) throws IOException {
            ZipEntry zipEntry = new ZipEntry(filename);
            zipEntry.setMethod(ZipEntry.STORED);
            zipEntry.setSize(data.length);
            zipEntry.setCompressedSize(data.length);
            CRC32 crc = new CRC32();
            crc.update(data);
            zipEntry.setCrc(crc.getValue());

            zos.putNextEntry(zipEntry);
            zos.write(data, 0, data.length);
            zos.closeEntry();
            fileCount++;
        }

        public File closeAndGetZip() throws IOException {
            zos.close();
            fos.close();
            return zipFile;
        }

        public void clean() throws IOException {
            tmp.close();
        }
    }

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new RemoteImageClassifierConfig());
    }

    public boolean isEnabled() {
        return config.isEnabled() && enabled;
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        config = configurationManager.findObject(RemoteImageClassifierConfig.class);

        if (!isEnabled()) {
            return;
        }
        activeInstances.incrementAndGet();

        urlZip = "https://" + config.getUrl() + "/zip"; // enforces secure communication (required for sensitive data
                                                        // transfer)
        urlVersion = "https://" + config.getUrl() + "/version";
        batchSize = config.getBatchSize();
        skipSize = config.getSkipSize();
        skipDimension = config.getSkipDimension();
        skipHashDBFiles = config.isSkipHashDBFiles();
        validateSSL = config.isValidateSSL();
        labelingThreshold = config.getLabelingThreshold();
        
        try (CloseableHttpClient client = getClient()) {
            HttpGet get = new HttpGet(urlVersion);

            client.execute(get, response -> {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == HttpStatus.SC_OK) {
                    try (InputStream responseStream = response.getEntity().getContent()) {
                        ObjectMapper objectMapper = new ObjectMapper();
                        JsonNode jsonResponse = objectMapper.readTree(responseStream);
                        String protocol_version = jsonResponse.get("protocol_version").asText();
                        String model_version = jsonResponse.get("model_version").asText();
                        logger.info(
                                "RemoteImageClassifierTask: Connected to remote image classifier at '{}' - protocol_version: {} model_version: {}",
                                urlVersion, protocol_version, model_version);
                        if (!protocol_version.startsWith("v1")) {
                            throw new RuntimeException("Incompatible protocol version: " + protocol_version);
                        }
                        if (!model_version.startsWith("v1")) {
                            throw new RuntimeException("Incompatible model version: " + model_version);
                        }
                    }
                } else {
                    throw new HttpResponseStatusException(
                            "Failed to connect to remote image classifier at '" + urlVersion + "'", statusCode, null);
                }
                return null;
            });
        }
        catch (UnknownHostException | HttpHostConnectException e) {
            // Disable task in case of failure to connect to remote image classifier 
            enabled = false;
            logger.error("Task disabled. Failed to connect to remote image classifier at '" + urlVersion + "': " + e.getMessage());
        }

        if (zip == null) {
            zip = new ZipFile();
        }
    }

    public static String formatNumberToKMGUnits(float bytes) {
        if (bytes < 1024 * 1024) {
            return new DecimalFormat("#,##0.##").format((float) bytes / 1024.0) + " KB";
        } else if (bytes < 1024L * 1024 * 1024) {
            return new DecimalFormat("#,##0.##").format((float) bytes / (1024.0 * 1024)) + " MB";
        } else 
            return new DecimalFormat("#,##0.##").format((float) bytes / (1024.0 * 1024 * 1024)) + " GB";
    }

    @Override
    public void finish() throws Exception {
        // "Close" classifications cache
        if (activeInstances.decrementAndGet() == 0){
            classifications.clear();
        }
        // Summary statistics
        if (!finishNow.getAndSet(true)) {
            long totClassifications = classificationSuccess.longValue() + classificationFail.longValue();
            long totSkipCount = skipSizeCount.longValue() + skipDimensionCount.longValue() + skipHashDBFilesCount.longValue() + skipDuplicatesCount.longValue();
            logger.info("Total count of files processed: {}", (totClassifications + totSkipCount));

            // Statistics for files sent for classification
            if (totClassifications != 0) {
                logger.info(" Files sent for classification: {}", totClassifications);
                logger.info("  Successful classifications: {}", classificationSuccess.intValue());
                logger.info("  Failed classifications: {}", classificationFail.intValue());
                if (countImageThumbs.intValue() > 0)
                    logger.info("  Image files: {} (1 thumb/image)", countImageThumbs.intValue());
                if (countVideoFiles.intValue() > 0)
                    logger.info("  Video files: {} ({} thumbs/video)", countVideoFiles.intValue(), (countVideoThumbs.intValue() / countVideoFiles.intValue()));
                long totThumbs = countImageThumbs.intValue() + countVideoThumbs.intValue();
                if (totThumbs != 0) {
                    long totSendBytes = sendImageBytes.longValue() + sendVideoBytes.longValue();
                    float sendThroughput = (float) totSendBytes / ((float) (classificationTime.longValue()) / 1000);
                    logger.info("  Thumbs sent: {} ({}); average throughput: {}/s", totThumbs, formatNumberToKMGUnits(totSendBytes), formatNumberToKMGUnits(sendThroughput));
                    if (countImageThumbs.intValue() > 0)
                        logger.info("   Image thumbs: {} ({}); average thumb size: {}", countImageThumbs.intValue(), formatNumberToKMGUnits(sendImageBytes.longValue()), formatNumberToKMGUnits(sendImageBytes.longValue() / countImageThumbs.intValue()));
                    if (countVideoThumbs.intValue() > 0)
                        logger.info("   Videos thumbs: {} ({}); average thumb size: {}", countVideoThumbs.intValue(), formatNumberToKMGUnits(sendVideoBytes.longValue()), formatNumberToKMGUnits(sendVideoBytes.longValue() / countVideoThumbs.intValue()));
                    logger.info("   Average thumb classification time (ms/thumb): {}", String.format("%.3f", (((float) classificationTime.longValue() / this.worker.manager.getNumWorkers()) / totThumbs)));
                    logger.info("   Average thumb classification throughput (thumbs/s): {}", ((int) (totThumbs / ((float) classificationTime.longValue() / this.worker.manager.getNumWorkers()) * 1000)));
                }
            }

            // Statistics for files with skipped classification
            if (totSkipCount != 0) {
                logger.info(" Files with skipped classification: {}", totSkipCount);
                logger.info("  Skipped classification by size: {}", skipSizeCount.intValue());
                logger.info("  Skipped classification by dimension: {}", skipDimensionCount.intValue());
                logger.info("  Skipped classification by hashDBFiles: {}", skipHashDBFilesCount.intValue());
                logger.info("  Skipped classification by duplicates: {}", skipDuplicatesCount.intValue());
            }
        }
    }

    private void sendItemsToNextTask() throws Exception {
        sendToNext.addAll(queue.values());
        queue.clear();
    }

    protected void sendToNextTask(IItem item) throws Exception {
        if (!isEnabled()) {
            super.sendToNextTask(item);
            return;
        }

        LinkedList<IItem> localList = new LinkedList<IItem>(sendToNext);
        sendToNext.clear();
        for (IItem it : localList) {
            super.sendToNextTask(it);
        }
        if (!queue.containsValue(item) || item.isQueueEnd()) {
            super.sendToNextTask(item);
        }
    }

    private void processResult(InputStream responseStream) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonResponse = objectMapper.readTree(responseStream);
        logger.debug("Server Response: {}", jsonResponse.toPrettyString());
        
        // Queue to store 'name' of failed evidences
        Set<String> queueFail = new HashSet<>();

        // Get 'results' from response
        JsonNode resultArray = jsonResponse.get("results");       
        Map<String,ResultItem> results = new TreeMap<>();
        if (resultArray != null && resultArray.isArray()) {
            // Process 'results'
            for (JsonNode item : resultArray) {
                // Checks result item for 'filename' field
                String name = item.get("filename").asText();
                ResultItem res = null;
                if (name == null) {
                    logger.warn("Invalid/missing 'filename' field");
                    continue;
                }
                // The two 'if' conditions below allow for:
                // - storing classification for images
                // - storing grouped classification data for video frames and animation image frames
                if (name.split("_").length == 2) {
                    name = name.split("_")[1];
                    res = results.get(name);
                }
                if (res == null) {
                    res = new ResultItem();
                    results.put(name, res);
                }

                // Checks result item for 'class' field
                JsonNode classes = item.get("class");
                if (classes != null && classes.isObject()) {
                    // 'class' field exists
                    var iterator = classes.fields();
                    while (iterator.hasNext()) {
                        Map.Entry<String, JsonNode> entry = iterator.next();
                        String key = entry.getKey();
                        // filtering only CSAM related classes to avoid overuse of the model
                        if (key.equals("AI_CSAM") || key.equals("AI_LIKELYCSAM") || key.equals("AI_People")
                                || key.equals("AI_Porn") || key.startsWith("AI_Drawing")) {
                            key = normalize(key);
                            double value = entry.getValue().asDouble();
                            res.addClass(key, value);
                        }
                    }
                } else {
                    // Invalid/missing 'class' field
                    // Server may have encountered a problem during evidence classification (e.g., corrupted image)
                    // Flag classification fail status in evidence attributes
                    // Store failed evidence in case cache

                    // Check for matching evidence
                    IItem evidence = queue.get(name);
                    if (evidence != null) {
                        // Matching evidence found
                        // Add classification fail info, if not already exists
                        // Avoids duplicate counting of failed videos, in case of classification problem with more than one thumb
                        if (!queueFail.contains(name)) {
                            // Add evidence to the fail queue
                            // Avoids signaling evidence classification success later on for videos with classification problem with some, but not all, thumbs
                            queueFail.add(name);
                            // Classification fail
                            classificationFail.incrementAndGet();
                            // Add classification status
                            evidence.setExtraAttribute(AI_CLASSIFICATION_STATUS_ATTR, AI_CLASSIFICATION_FAIL_NO_CLASS);
                            logger.warn("ClassificationFail::EvidenceNoClass: Invalid/missing 'class' field for filename: {}", name);
                        }
                    }
                    else {
                        // No matching evidence found
                        logger.warn("ClassificationFail::EvidenceNotFound: Invalid/missing 'class' field for filename: {}. No matching evidence found.", name);                        
                    }
                }
            }

            // Add classification to evidences
            // Store classification in case cache
            for (String name : results.keySet()) {
                // Find the evidence to which the classification belongs
                IItem evidence;
                if (name == null || (evidence = queue.get(name)) == null) {
                    // No matching evidence found
                    logger.warn("No matching evidence found");
                    continue;
                }
                // Check if evidence is in fail queue
                if (name != null && queueFail.contains(name)) {
                    // Classification fail
                    continue;
                }
                // Check if classes are not missing
                ResultItem res = results.get(name);
                Iterator<String> iterator = res.classes.keySet().iterator();
                if (iterator.hasNext()) {
                    // Classification success
                    classificationSuccess.incrementAndGet();
                    // Add classification status
                    evidence.setExtraAttribute(AI_CLASSIFICATION_STATUS_ATTR, AI_CLASSIFICATION_SUCCESS);
                    
                    // Classification classes as a String holding className=classProb pairs (used
                    // when retrieving cached classifications)
                    StringBuilder classes = new StringBuilder();
                    while (iterator.hasNext()) {
                        String className = iterator.next();
                        double classProb = res.getClassProb(className);
                        // Add classification class to the evidence
                        evidence.setExtraAttribute(className, classProb);
                        classes.append(className).append('=').append(classProb);
                        if (iterator.hasNext()) {
                            classes.append(';');
                        }
                    }

                    // Get the label to be assigned based on classes probabilities
                    String label = getLabel(res);
                    if (label != null) {
                        evidence.setExtraAttribute(AI_CLASSIFICATION_LABEL_ATTR, label);
                        classes.append(';').append(AI_CLASSIFICATION_LABEL_ATTR).append('=').append(label);
                    }

                    // Store classification in classifications cache
                    classifications.put(evidence.getHashValue(), classes.toString());
                }
            }
        } else {
            // 'results' array is missing in JSON response
            // Server malformed response
            // Flag classification fail status in evidence attributes
            // Do not store failed evidences in case cache

            // Store fail information for each evidence in queue
            for (IItem evidence : queue.values()) {
                // Classification fail
                classificationFail.incrementAndGet();                
                // Add classification status
                evidence.setExtraAttribute(AI_CLASSIFICATION_STATUS_ATTR, AI_CLASSIFICATION_FAIL_NO_RESULTS);
            }
            logger.error("ClassificationFail::NoResults: 'results' array is missing in JSON response. Classification fail for a batch of {} files", queue.size());
        }
    }
    
    private CloseableHttpClient getClient() {
        if (!validateSSL) {
            SSLContext sslContext;
            try {
                sslContext = SSLContextBuilder.create().loadTrustMaterial(null, new TrustStrategy() {
                    @Override
                    public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        // Trust all certs
                        return true;
                    }
                }).build();
            } catch (Exception e) {
                throw new RuntimeException("Failed to create a trusting SSL context", e);
            }

            // Use NoopHostnameVerifier to ignore host name verification
            SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext,
                    NoopHostnameVerifier.INSTANCE);
            return HttpClients.custom().setSSLSocketFactory(sslSocketFactory).build();
        } else {
            return HttpClients.createDefault();
        }
    }

    // Custom HttpResponseStatusException exception
    private static class HttpResponseStatusException extends IOException {
        private static final long serialVersionUID = -7955212330143589634L;
        private final int statusCode;
        private final String responseBody;

        public HttpResponseStatusException(String message, int statusCode, String responseBody) {
            super(message);
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        public int getStatusCode() {
            return statusCode;
        }

        @Override
        public String toString() {
            String str = String.format("%s HTTP Status Code: %d", getMessage(), statusCode);
            if (responseBody != null && !responseBody.isEmpty()) {
                str += String.format(" - Response: %s", responseBody);
            }
            return str;
        }
    }
    
    private void sendZipFile(File zipFile) throws IOException {
        currentBatch = lastBatch.incrementAndGet();
        logger.info("Send ZIP file #{} (files: {})", currentBatch, zip.getFileCount());

        try (CloseableHttpClient client = getClient()) {
            HttpPost post = new HttpPost(urlZip);

            HttpEntity entity = MultipartEntityBuilder.create().setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                    .addBinaryBody("file", zipFile, ContentType.APPLICATION_OCTET_STREAM, zipFile.getName()).build();

            post.setEntity(entity);

            long t = System.currentTimeMillis();
            try (CloseableHttpResponse response = client.execute(post)) {
                classificationTime.addAndGet(System.currentTimeMillis() - t);
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == HttpStatus.SC_OK) {
                    try (InputStream responseStream = response.getEntity().getContent()) {
                        processResult(responseStream);
                    }
                } else {
                    // Server response with HTTP related issues (e.g., server is busy, overloaded, or down)
                    // HTTP response might have useful info
                    String errorMessage;
                    try (InputStream errorStream = response.getEntity().getContent()) {
                        errorMessage = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        errorMessage = "Failed to read error message from response.";
                    }
                    String errMsg = String.format("HTTP Status Code: %d - Response:%n%s", statusCode, errorMessage);
                    throw new HttpResponseStatusException(errMsg, statusCode, errorMessage);
                }
            }
        }
    }

    private void sendBatchedFiles() throws Exception {
        // In case of errors, retry, if possible
        while (retryCount <= MAX_RETRY) {
            try {
                sendZipFile(zip.closeAndGetZip());
                break;
            }
            catch (IOException e) {
                String baseMsg = String.format(" Failed to upload ZIP file.");

                // In case of non recoverable errors, abort case processing
                // Abort if unknown host on URL
                if (e instanceof UnknownHostException) {
                    if (!abortNow.getAndSet(true)) {
                        logger.error("ClassificationFail::UnknownHost:{} Unknown host on '{}': {}", baseMsg, urlZip, e.getClass().getName());
                        throw new RuntimeException("Unknown host on '" + urlZip + "': " + e.getMessage(), e);
                    }
                }
                // Abort if HTTP response status code is different from SC_SERVICE_UNAVAILABLE and SC_GATEWAY_TIMEOUT
                if (e instanceof HttpResponseStatusException) {
                    HttpResponseStatusException eHTTP = (HttpResponseStatusException) e;
                    if (eHTTP.getStatusCode() != HttpStatus.SC_SERVICE_UNAVAILABLE && eHTTP.getStatusCode() != HttpStatus.SC_GATEWAY_TIMEOUT) {
                        if (!abortNow.getAndSet(true)) {
                            logger.error("ClassificationFail::HttpStatusNotOK: {}", e.getMessage());
                            throw new RuntimeException("HTTP Status Not OK on '" + urlZip + "': " + e.getMessage(), e);
                        }
                    }
                }

                // In case of recoverable errors, retry sending batched files
                // Log warning/error messages
                baseMsg = String.format(" Failed to upload ZIP file #%d (files: %d).", currentBatch, zip.getFileCount());
                if (retryCount == MAX_RETRY)
                    baseMsg = String.format(" Failed to upload ZIP file.");
                if (retryCount > 0)
                    baseMsg = String.format("retry#%d:", retryCount) + baseMsg; 
                String msg = "";                    
                if (e instanceof HttpResponseStatusException) {
                    HttpResponseStatusException eHTTP = (HttpResponseStatusException) e;
                    if (eHTTP.getStatusCode() == HttpStatus.SC_SERVICE_UNAVAILABLE)
                        msg += String.format("ClassificationFail::HttpStatusNotOK:%s Service unavailable for '%s': %s: %s", baseMsg, urlZip, e.getClass().getName(), e.getMessage());
                    if (eHTTP.getStatusCode() == HttpStatus.SC_GATEWAY_TIMEOUT)
                        msg += String.format("ClassificationFail::HttpStatusNotOK:%s Gateway timeout for '%s': %s: %s", baseMsg, urlZip, e.getClass().getName(), e.getMessage());
                }
                else if (e instanceof HttpHostConnectException)
                    msg += String.format("ClassificationFail::ConnectionProblem:%s Could not connect to host on '%s': %s: %s", baseMsg, urlZip, e.getClass().getName(), e.getMessage());
                else if (e instanceof SocketTimeoutException)
                    msg += String.format("ClassificationFail::ConnectionProblem:%s Socket timeout occurred while connecting or reading from '%s': %s: %s", baseMsg, urlZip, e.getClass().getName(), e.getMessage());
                else if (e instanceof ClientProtocolException)
                    msg += String.format("ClassificationFail::ConnectionProblem:%s HTTP protocol error while communicating with '%s': %s: %s", baseMsg, urlZip, e.getClass().getName(), e.getMessage());
                else 
                    msg += String.format("ClassificationFail::IOProblem:%s I/O error occurred during HTTP request to '%s': %s: %s", baseMsg, urlZip, e.getClass().getName(), e.getMessage());
                if (retryCount < MAX_RETRY) {
                    // Log warning and retry
                    logger.warn(msg);
                } else {
                    // Log error and abort case processing
                    if (!abortNow.getAndSet(true)) {
                        logger.error(msg);
                        logger.error("ClassificationFail::TooManyErrors: Aborting case processing");
                        throw new RuntimeException(
                                "Too many errors while communicating with '" + urlZip + "': " + e.getMessage(), e);
                    }
                }

                // Increment 'retryCount'
                retryCount++;

                // Wait time before retrying
                Thread.sleep(WAIT_BEFORE_RETRY);
            }
        }
    }

    @Override
    protected boolean processQueueEnd() {
        return true;
    }

    @Override
    protected void process(IItem evidence) throws Exception {
        // Send files to the remote classifier if any condition is met
        // (if maximum number of thumbs in current batch has been reached or current batch is the last one)
        if (zip.getFileCount() >= batchSize || (evidence.isQueueEnd() && zip.getFileCount() > 0)) {
            // Send batched files
            sendBatchedFiles();
            zip.clean();
            zip = new ZipFile();
            sendItemsToNextTask();
        }

        // Does not process evidence if any condition is met
        if (!isEnabled() || !evidence.isToAddToCase() || evidence.getHashValue() == null || evidence.getThumb() == null
                || evidence.getThumb().length < 10 || evidence.isQueueEnd()) {
            return;
        }

        // Skip classification of images/videos smaller than a given file size (see 'skipSize' config property)
        if (skipSize > 0 && skipSize > evidence.getLength()) {
            // Add skip classification info
            evidence.setExtraAttribute(AI_CLASSIFICATION_STATUS_ATTR, AI_CLASSIFICATION_SKIP_SIZE);
            skipSizeCount.incrementAndGet();
            return;
        }

        // Skip classification of images/videos smaller than a given dimension, i.e. height or width (see 'skipDimension' config property)
        if (skipDimension > 0) {
            int width = 0;
            int height = 0;
            String mediaType = evidence.getMediaType().toString();
            if (mediaType.startsWith("image")) {
                try {
                    if (evidence.getMetadata().get("image:Width") != null)
                        width = Integer.parseInt(evidence.getMetadata().get("image:Width"));
                    if (evidence.getMetadata().get("image:Height") != null)
                        height = Integer.parseInt(evidence.getMetadata().get("image:Height"));
                }
                catch (NumberFormatException e) {
                    logger.warn("Invalid dimension for image file '{}': width:{}; height:{}", evidence.getName(), evidence.getMetadata().get("image:Width"), evidence.getMetadata().get("image:Height"));
                }
            }
            else if (mediaType.startsWith("video")) {
                try {
                    if (evidence.getMetadata().get("video:Width") != null)
                        width = Integer.parseInt(evidence.getMetadata().get("video:Width"));
                    if (evidence.getMetadata().get("video:Height") != null)
                        height = Integer.parseInt(evidence.getMetadata().get("video:Height"));
                }
                catch (NumberFormatException e) {
                    logger.warn("Invalid dimension for video file '{}': width:{}; height:{}", evidence.getName(), evidence.getMetadata().get("video:Width"), evidence.getMetadata().get("video:Height"));
                }
            }
            if ((skipDimension > width || skipDimension > height) && width > 0 && height > 0) {
                // Add skip classification info
                evidence.setExtraAttribute(AI_CLASSIFICATION_STATUS_ATTR, AI_CLASSIFICATION_SKIP_DIMENSION);
                skipDimensionCount.incrementAndGet();
                return;
            }
        }

        // Skip classification of images/videos with hits on IPED hashesDB database (see 'skipHashDBFiles' config property)
        if (skipHashDBFiles && evidence.getExtraAttribute(HashDBLookupTask.STATUS_ATTRIBUTE) != null) {
            // Add skip classification info
            evidence.setExtraAttribute(AI_CLASSIFICATION_STATUS_ATTR, AI_CLASSIFICATION_SKIP_HASHDB);
            skipHashDBFilesCount.incrementAndGet();
            return;
        }

        // Skip classification of images/videos duplicates if classification exists in classifications cache
        // Retrieve classification from classifications cache
        String classes = classifications.get(evidence.getHashValue());
        if (classes != null) {
            // Classification exists in classifications cache
            // Add classification status
            evidence.setExtraAttribute(AI_CLASSIFICATION_STATUS_ATTR, AI_CLASSIFICATION_SUCCESS);
            // Add classification classes to the evidence 
            String[] classesArray = classes.split(";");
            for (int i = 0; i < classesArray.length; i++) {
                // classParts[0] will hold className and classParts[1] will hold classProb
                String[] classParts = classesArray[i].split("="); 
                // Add classification class to the evidence
                String key = classParts[0];
                if (key.equals(AI_CLASSIFICATION_LABEL_ATTR)) {
                    evidence.setExtraAttribute(key, classParts[1]);
                } else {
                    evidence.setExtraAttribute(key, Double.parseDouble(classParts[1]));
                }
            }
            skipDuplicatesCount.incrementAndGet();
            return;
        }

        // 'name' is a key to map to the evidence
        String name = evidence.getExtraAttribute(IndexItem.TRACK_ID).toString() + ".jpg";
        if (MetadataUtil.isVideoType(evidence.getMediaType()) || MetadataUtil.isAnimationImage(evidence)) {
            // For videos, call the detection method for each extracted frame image (VideoThumbsTask must be enabled)
            File viewFile = evidence.getViewFile();
            List<BufferedImage> frames = null;
            if (viewFile != null && viewFile.exists()) {
                frames = ImageUtil.getFrames(viewFile);
            } else if (evidence.hasPreview()) {
                try (InputStream is = PreviewRepositoryManager.get(output).readPreview(evidence, false)) {
                    frames = ImageUtil.getFrames(is);
                }
            }
            if (frames != null) {
                int i = 0;
                for (BufferedImage frame : frames) {
                    String iName = (++i) + "_" + name;
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(frame, "jpeg", baos);
                    zip.addFileToZip(iName, baos.toByteArray());
                    sendVideoBytes.addAndGet(baos.toByteArray().length);
                }
                countVideoThumbs.addAndGet(i);
            } else {
                countVideoThumbs.incrementAndGet();
                sendVideoBytes.addAndGet(evidence.getThumb().length);
                zip.addFileToZip(name, evidence.getThumb());
            }
            countVideoFiles.incrementAndGet();
        } else {
            countImageThumbs.incrementAndGet();
            sendImageBytes.addAndGet(evidence.getThumb().length);
            zip.addFileToZip(name, evidence.getThumb());
        }

        // Add a new 'name' to 'evidence' mapping to the queue
        queue.put(name, evidence);
    }

    /**
     * Rename class attribute names to use "ai:" prefix and match commonly used
     * naming standards.
     */
    private static final String normalize(String s) {
        if (s.startsWith("AI_")) {
            s = aiPrefix + s.substring(3);
        }
        s = s.toLowerCase();
        for (int i = 3; i < s.length() - 1; i++) {
            if (s.charAt(i) == '_') {
                s = s.substring(0, i) + Character.toUpperCase(s.charAt(i + 1)) + s.substring(i + 2);
            }
        }
        int i = s.indexOf("csam");
        if (i > 3) {
            s = s.substring(0, i) + Character.toUpperCase(s.charAt(i)) + s.substring(i + 1);
        }
        return s;
    }

    /**
     * Return the label to be assigned base on probabilities of each class. Null if
     * no label should be assigned, because no class probability reached the minimum
     * threshold.
     */
    private String getLabel(ResultItem res) {
        for (String className : classesName) {
            Double prob = res.getClassProb(className);
            if (prob != null && prob >= labelingThreshold) {
                return labelNames.get(className);
            }
        }
        return null;
    }

    private static void initLabeling() {
        // Labeling classes names, in priority order
        addLabel("ai:csam", "ChildSexualAbuse");
        addLabel("ai:likelyCsam", "LikelyChildSexualAbuse");
        addLabel("ai:porn", "Pornography");
        addLabel("ai:drawingCsam", "ChildSexualAbuseDrawing");
        addLabel("ai:drawingPorn", "ExplicitDrawing");
        addLabel("ai:drawing", "Drawing");
        addLabel("ai:people", "People");
    }

    private static void addLabel(String className, String label) {
        classesName.add(className);
        labelNames.put(className, label);
    }
}
