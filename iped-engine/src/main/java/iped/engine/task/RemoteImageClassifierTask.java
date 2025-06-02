package iped.engine.task;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;
import javax.net.ssl.SSLContext;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
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
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.SynchronousMode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.RemoteImageClassifierConfig;
import iped.engine.task.die.DIETask;
import iped.engine.task.index.IndexItem;
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

    // AI classification extra attributes, values, and fail codes
    private static final String AI_CLASSIFICATION_STATUS_ATTR = "AIClassificationStatus";
    private static final String AI_CLASSIFICATION_SUCCESS = "success";
    private static final String AI_CLASSIFICATION_FAIL = "fail";
    private static final String AI_CLASSIFICATION_SKIP_ATTR = "AIClassificationSkip";
    private static final String AI_CLASSIFICATION_SKIP_NO = "no";
    private static final String AI_CLASSIFICATION_SKIP_SIZE = "size";
    private static final String AI_CLASSIFICATION_SKIP_DIMENSION = "dimension";
    private static final String AI_CLASSIFICATION_SKIP_HASHDB = "hashDB";
    private static final String AI_CLASSIFICATION_SKIP_DUPLICATE = "duplicate";
    private static final String AI_CLASSIFICATION_METADATA_ATTR = "AIClassificationMetadata";
    private static final String AI_CLASSIFICATION_FROM_CACHE_ATTR = "AIClassesFromCache";
    private static final String AI_CLASSIFICATION_FROM_CACHE_NO = "no";
    private static final String AI_CLASSIFICATION_FROM_CACHE_CASE = "case";
    private static final int AI_CLASSIFICATION_FAIL_NO_CLASS = 1;
    private static final int AI_CLASSIFICATION_FAIL_NO_RESULTS = 2;
    private static final int AI_CLASSIFICATION_FAIL_OTHER = 3;

    private RemoteImageClassifierConfig config;

    private String url;
    private int batchSize;
    private int skipSize;
    private int skipDimension;
    private boolean skipHashDBFiles;   
    private boolean validateSSL;

    // "case cache" - allows for improved classification management
    private static final String AI_STORAGE = "ai/classifications.db";

    // 'classifications' table - allows for skipping classification of duplicate files
    //   - 'id': evidence HASH; 'classes': classification classes; 'tag': extra info from the remote classifier (e.g., version)
    private static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS classifications (id TEXT PRIMARY KEY, classes TEXT, tag TEXT);";
    private static final String INSERT_DATA = "INSERT INTO classifications (id, classes, tag) VALUES (?,?,?) ON CONFLICT(id) DO NOTHING;";
    private static final String SELECT_FROM_ID = "SELECT classes, tag FROM classifications WHERE id=?;";

    // 'classifications_fail' table - allows for storing and eventually retrying failed classification
    //   - 'id': evidence TRACK_ID; 'code': fail code; 'retry': retries count ('-1': don't retry; useful for non recoverable errors)
    private static final String CREATE_TABLE_FAIL = "CREATE TABLE IF NOT EXISTS classifications_fail (id TEXT PRIMARY KEY, code INTEGER, retry INTEGER);";
    private static final String INSERT_DATA_FAIL = "INSERT INTO classifications_fail (id, code, retry) VALUES (?,?,?) ON CONFLICT(id) DO NOTHING;";
    private static final String SELECT_FROM_RETRY_FAIL = "SELECT id FROM classifications_fail WHERE retry>=0 AND retry<?;";
    private static final String UPDATE_DATA_FAIL = "UPDATE classifications_fail SET code=? AND retry=? WHERE id=?;";
    private static final String DELETE_FROM_ID_FAIL = "DELETE FROM classifications_fail WHERE id=?;";

    // "case cache" database file
    private static File db;

    // "case cache" database connection, lock, and prepared statements (uses a single connection for all task instances)
    private static final Object lock = new Object();
    private static Connection conn;
    private static PreparedStatement psSelect, psInsert, psInsertFail, psSelectFail, psUpdateFail, psDeleteFail;

    // Queue to store 'name' to 'evidence' mapping
    private Map<String, IItem> queue = new TreeMap<>();

    // Zip archive holding files to be sent for classification
    private ZipFile zip;

    // Variables to store statistics
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
    private static final AtomicLong cacheStoreTime = new AtomicLong();
    private static final AtomicInteger cacheStoreCount = new AtomicInteger();
    private static final AtomicLong cacheRetrieveTime = new AtomicLong();
    private static final AtomicInteger cacheRetrieveCount = new AtomicInteger();
    private static final AtomicInteger cacheStoreFailEvidenceCount = new AtomicInteger();

    // Variables for debugging purposes
    private int currentBatch;
    private static int lastBatch;

    /**
     * Represents the result of a classification.
     */
    private static class ResultItem {
        public String name;
        public TreeMap<String, ArrayList<Double>> classes = new TreeMap<>();

        // Add classification value for 'classname'.
        // It stores classification for images and grouped classification data for video frames and animation image frames
        public void addClass(String classname, double val) {
            if (!classes.containsKey(classname)) {
                classes.put(classname, new ArrayList<>());
            }
            classes.get(classname).add(val);
        }

        // Get probability value for 'classname'.
        public double getClassProb(String classname) {
            return DIETask.videoScore(classes.get(classname));
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
        return config.isEnabled();
    }

    private void createConnection() {
        synchronized (lock) {
            if (conn == null) {
                conn = createConnection(output);
            }
        }
    }

    private Connection createConnection(File output) {
        db = new File(output, AI_STORAGE);
        db.getParentFile().mkdirs();
        try {
            SQLiteConfig config = new SQLiteConfig();
            config.setSynchronous(SynchronousMode.OFF);
            config.setBusyTimeout(3600000);
            Connection conn = config.createConnection("jdbc:sqlite:" + db.getAbsolutePath());
            
            // Create tables and prepared statements
            // 'success' table
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(CREATE_TABLE);
            }
            psSelect = conn.prepareStatement(SELECT_FROM_ID);
            psInsert = conn.prepareStatement(INSERT_DATA);
            // 'fail' table
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(CREATE_TABLE_FAIL);
            }
            psInsertFail = conn.prepareStatement(INSERT_DATA_FAIL);
            psSelectFail = conn.prepareStatement(SELECT_FROM_RETRY_FAIL);
            psUpdateFail = conn.prepareStatement(UPDATE_DATA_FAIL);
            psDeleteFail = conn.prepareStatement(DELETE_FROM_ID_FAIL);

            return conn;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    protected static class ClassesAndTag {
        String classes;
        String tag;
    }

    private ClassesAndTag getClassesFromDb(String id) throws IOException {
        ResultSet rs = null;
        try {
            synchronized (lock) {
                psSelect.setString(1, id);
                long t = System.currentTimeMillis();
                rs = psSelect.executeQuery();
                cacheRetrieveTime.addAndGet(System.currentTimeMillis() - t);
                cacheRetrieveCount.incrementAndGet();
                if (rs.next()) {
                    ClassesAndTag result = new ClassesAndTag();
                    result.classes = rs.getString(1);
                    result.tag = rs.getString(2);
                    return result;
                }
            }
        } catch (SQLException e) {
            throw new IOException(e);
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (Exception e) {
                }
            }
        }
        return null;        
    }

    private void storeClassesInDb(String id, String classes, String tag) throws IOException {
        try {
            synchronized (lock) {
                psInsert.setString(1, id);
                psInsert.setString(2, classes);
                psInsert.setString(3, tag);
                long t = System.currentTimeMillis();
                psInsert.executeUpdate();
                cacheStoreTime.addAndGet(System.currentTimeMillis() - t);
                cacheStoreCount.incrementAndGet();
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }        
    }

    private void storeFailEvidenceInDb(String id, int code, int retry) throws IOException {
        try {
            synchronized (lock) {
                psInsertFail.setString(1, id);
                psInsertFail.setInt(2, code);
                psInsertFail.setInt(3, retry);
                psInsertFail.executeUpdate();
                cacheStoreFailEvidenceCount.incrementAndGet();
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }        
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        config = configurationManager.findObject(RemoteImageClassifierConfig.class);

        url = "https://" + config.getUrl(); // enforces secure communication (required for sensitive data transfer)
        batchSize = config.getBatchSize();
        skipSize = config.getSkipSize();
        skipDimension = config.getSkipDimension();
        skipHashDBFiles = config.isSkipHashDBFiles();
        validateSSL = config.isValidateSSL();
        
        if (zip == null) {
            zip = new ZipFile();
        }

        if (conn == null && config.isEnabled()) {
            createConnection();
        }
    }

    public static String formatNumberToKMGUnits(long bytes) {
        if (bytes < 1024 * 1024) {
            return new DecimalFormat("#,##0.##").format(bytes / 1024.0) + " KB";
        } else if (bytes < 1024L * 1024 * 1024) {
            return new DecimalFormat("#,##0.##").format(bytes / (1024.0 * 1024)) + " MB";
        } else 
            return new DecimalFormat("#,##0.##").format(bytes / (1024.0 * 1024 * 1024)) + " GB";
    }

    @Override
    public void finish() throws Exception {
        // Close DB connection
        synchronized (lock) {
            if (conn != null) {
                if (psInsert != null) {
                    psInsert.close();
                }
                if (psSelect != null) {
                    psSelect.close();
                }
                conn.close();
                conn = null;
            }
        }

        // Summary statistics
        long totClassifications = classificationSuccess.longValue() + classificationFail.longValue();
        long totSkipCount = skipSizeCount.longValue() + skipDimensionCount.longValue() + skipHashDBFilesCount.longValue() + skipDuplicatesCount.longValue();
        logger.info("Total count of files processed: " + (totClassifications + totSkipCount));

        // Statistics for files sent for classification
        if (totClassifications != 0) {
            logger.info(" Files sent for classification: " + totClassifications);
            logger.info("  Successful classifications: " + classificationSuccess.intValue());
            if (cacheStoreFailEvidenceCount.intValue() == 0)
                logger.info("  Failed classifications: " + classificationFail.intValue());
            else
                logger.info("  Failed classifications: " + classificationFail.intValue() + " (failed cache: " + cacheStoreFailEvidenceCount.intValue() + ")");
            if (countImageThumbs.intValue() > 0)
                logger.info("  Image files: " + countImageThumbs.intValue() + " (1 thumb/image)");
            if (countVideoFiles.intValue() > 0)
                logger.info("  Video files: " + countVideoFiles.intValue() + " (" + (countVideoThumbs.intValue() / countVideoFiles.intValue()) + " thumbs/video)");
            long totThumbs = countImageThumbs.intValue() + countVideoThumbs.intValue();
            if (totThumbs != 0) {
                logger.info("  Thumbs sent: " + totThumbs + " (" + formatNumberToKMGUnits(sendImageBytes.longValue() + sendVideoBytes.longValue()) + ")");
                if (countImageThumbs.intValue() > 0)
                    logger.info("   Image thumbs: " + countImageThumbs.intValue() + " (" + formatNumberToKMGUnits(sendImageBytes.longValue()) + "); average thumb size: " + formatNumberToKMGUnits(sendImageBytes.longValue() / countImageThumbs.intValue()));
                if (countVideoThumbs.intValue() > 0)
                    logger.info("   Videos thumbs: " + countVideoThumbs.intValue() + " (" + formatNumberToKMGUnits(sendVideoBytes.longValue()) + "); average thumb size: " + formatNumberToKMGUnits(sendVideoBytes.longValue() / countVideoThumbs.intValue()));
                logger.info("   Average thumb classification time (ms/thumb): " + String.format("%.3f", (((float) classificationTime.longValue() / this.worker.manager.getNumWorkers()) / totThumbs)));
                logger.info("   Average thumb classification throughput (thumbs/s): " + ((int) (totThumbs / ((float) classificationTime.longValue() / this.worker.manager.getNumWorkers()) * 1000)));
            }
            classificationSuccess.set(0);
            classificationFail.set(0);
        }

        // Statistics for files with skipped classification
        if (totSkipCount != 0) {
            logger.info(" Files with skipped classification: " + totSkipCount);
            logger.info("  Skipped classification by size: " + skipSizeCount.intValue());
            logger.info("  Skipped classification by dimension: " + skipDimensionCount.intValue());
            logger.info("  Skipped classification by hashDBFiles: " + skipHashDBFilesCount.intValue());
            logger.info("  Skipped classification by duplicates: " + skipDuplicatesCount.intValue());
            logger.info("   Cache file size: " + formatNumberToKMGUnits(db.length()));
            if (cacheStoreCount.intValue() > 0)
                logger.info("   Cache store operations: " + cacheStoreCount.intValue() + "; average operation time (ms): " + String.format("%.3f", ((float) cacheStoreTime.longValue() / cacheStoreCount.intValue())));
            if (cacheRetrieveCount.intValue() > 0)
                logger.info("   Cache retrieve operations: " + cacheRetrieveCount.intValue() + "; average operation time (ms): " + String.format("%.3f", ((float) cacheRetrieveTime.longValue() / cacheRetrieveCount.intValue())));
            skipSizeCount.set(0);
            skipDimensionCount.set(0);
            skipHashDBFilesCount.set(0);
            skipDuplicatesCount.set(0);
        }
    }

    private void sendItemsToNextTask() throws Exception {
        for (IItem item : queue.values()) {
            if (item != null) {
                super.sendToNextTask(item);
            }
        }
        queue.clear();
    }

    protected void sendToNextTask(IItem item) throws Exception {
        if (!isEnabled()) {
            super.sendToNextTask(item);
            return;
        }

        if (!queue.containsValue(item) || item.isQueueEnd()) {
            super.sendToNextTask(item);
        }
    }

    private void processResult(InputStream responseStream) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonResponse = objectMapper.readTree(responseStream);
        logger.debug("Server Response: {}", jsonResponse.toPrettyString());
        // Get 'tag' metadata from response
        JsonNode metadataNode = jsonResponse.get("metadata");
        String tag = "";
        if (metadataNode != null && metadataNode.get("tag") != null) {
            tag = metadataNode.get("tag").asText();
        }
        // Get 'results' from response
        JsonNode resultArray = jsonResponse.get("results");       
        Map<String,ResultItem> results = new TreeMap<>();
        if (resultArray != null && resultArray.isArray()) {
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
                        double value = entry.getValue().asDouble();
                        res.addClass(key, value);
                    }
                } else {
                    // Invalid/missing 'class' field

                    // Check for matching evidence
                    IItem evidence = queue.get(name);
                    if (evidence != null) {
                        // Matching evidence found

                        // Classification fail
                        classificationFail.incrementAndGet();

                        // Add classification status
                        evidence.setExtraAttribute(AI_CLASSIFICATION_STATUS_ATTR, AI_CLASSIFICATION_FAIL);

                        // Store failed evidence in case cache
                        storeFailEvidenceInDb(evidence.getExtraAttribute(IndexItem.TRACK_ID).toString(), AI_CLASSIFICATION_FAIL_NO_CLASS, 0);

                        logger.error("Invalid/missing 'class' field for filename: {}", name);
                    }
                    else {
                        // No matching evidence found
                        logger.warn("Invalid/missing 'class' field for filename: {}. No matching evidence found.", name);                        
                    }
                }
            }

            // Add classification to evidences
            for (String name : results.keySet()) {
                // Find the evidence to which the classification belongs
                IItem evidence;
                if (name == null || (evidence = queue.get(name)) == null) {
                    // No matching evidence found
                    logger.warn("No matching evidence found");
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
                    // Add classification metadata
                    evidence.setExtraAttribute(AI_CLASSIFICATION_METADATA_ATTR, tag);
                    // Add classification cache info
                    evidence.setExtraAttribute(AI_CLASSIFICATION_FROM_CACHE_ATTR, AI_CLASSIFICATION_FROM_CACHE_NO);
                    // Add skip classification info
                    evidence.setExtraAttribute(AI_CLASSIFICATION_SKIP_ATTR, AI_CLASSIFICATION_SKIP_NO);

                    String className;
                    double classProb;
                    // Classification classes as a String holding className=classProb pairs (used when retrieving cached classifications)
                    StringBuilder classes = new StringBuilder();
                    while (iterator.hasNext()) {
                        className = iterator.next();
                        classProb = res.getClassProb(className);
                        // Add classification class to the evidence 
                        evidence.setExtraAttribute(className, classProb);
                        classes.append(className + "=" + classProb);
                        if (iterator.hasNext())
                            classes.append(";");
                    }

                    // Store classification classes in case cache
                    storeClassesInDb(evidence.getHash(), classes.toString(), tag);
                }
            }
        } else {
            // Store fail information for each evidence in queue
            for (IItem evidence : queue.values()) {
                // Classification fail
                classificationFail.incrementAndGet();
                
                // Add classification status
                evidence.setExtraAttribute(AI_CLASSIFICATION_STATUS_ATTR, AI_CLASSIFICATION_FAIL);

                // Store failed evidence in case cache
                storeFailEvidenceInDb(evidence.getExtraAttribute(IndexItem.TRACK_ID).toString(), AI_CLASSIFICATION_FAIL_NO_RESULTS, 0);
            }                    

            logger.error("'results' array is missing in JSON response. Classification fail for a batch of {} files", queue.size());
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

    private void sendZipFile(File zipFile) throws IOException {
        currentBatch = ++lastBatch;
        logger.info("Send ZIP file #{} with {} files", currentBatch, zip.getFileCount());

        try (CloseableHttpClient client = getClient()) {
            HttpPost post = new HttpPost(url);

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
                    // Classification fail
                    classificationFail.addAndGet(zip.getFileCount());
                    
                    // Store fail information for each evidence in queue
                    for (IItem evidence : queue.values()) {
                        // Add classification status
                        evidence.setExtraAttribute(AI_CLASSIFICATION_STATUS_ATTR, AI_CLASSIFICATION_FAIL);

                        // Store failed evidence in case cache
                        storeFailEvidenceInDb(evidence.getExtraAttribute(IndexItem.TRACK_ID).toString(), statusCode, 0);
                    }                    

                    // HTTP response may have useful info
                    String errorMessage;
                    try (InputStream errorStream = response.getEntity().getContent()) {
                        errorMessage = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        errorMessage = "Failed to read error message from response.";
                    }
                    logger.error("Failed to upload ZIP file #{}. HTTP Code: {} - Response: {}", currentBatch, statusCode, errorMessage);
                }
            }
        }
        catch (IOException e) {
            // Classification fail
            classificationFail.addAndGet(zip.getFileCount());
            
            // Store fail information for each evidence in queue
            for (IItem evidence : queue.values()) {
                // Add classification status
                evidence.setExtraAttribute(AI_CLASSIFICATION_STATUS_ATTR, AI_CLASSIFICATION_FAIL);

                // Store failed evidence in case cache
                storeFailEvidenceInDb(evidence.getExtraAttribute(IndexItem.TRACK_ID).toString(), AI_CLASSIFICATION_FAIL_OTHER, 0);
            }                    

            logger.error("Failed to upload ZIP file #{}. {}", currentBatch, e.getMessage());
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
            sendZipFile(zip.closeAndGetZip());
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
            evidence.setExtraAttribute(AI_CLASSIFICATION_SKIP_ATTR, AI_CLASSIFICATION_SKIP_SIZE);
            skipSizeCount.incrementAndGet();
            return;
        }

        // Skip classification of images/videos smaller than a given dimension, i.e. height or width (see 'skipDimension' config property)
        if (skipDimension > 0) {
            int width = 0;
            int height = 0;
            String mediaType = evidence.getMediaType().toString();
            if (mediaType.startsWith("image")) {
                width = Integer.parseInt(evidence.getMetadata().get("image:Width"));
                height = Integer.parseInt(evidence.getMetadata().get("image:Height"));
            }
            else if (mediaType.startsWith("video")) {
                width = Integer.parseInt(evidence.getMetadata().get("video:Width"));
                height = Integer.parseInt(evidence.getMetadata().get("video:Height"));
            }
            if ((skipDimension > width || skipDimension > height) && width > 0 && height > 0) {
                // Add skip classification info
                evidence.setExtraAttribute(AI_CLASSIFICATION_SKIP_ATTR, AI_CLASSIFICATION_SKIP_DIMENSION);
                skipDimensionCount.incrementAndGet();
                return;
            }
        }

        // Skip classification of images/videos with hits on IPED hashesDB database (see 'skipHashDBFiles' config property)
        if (skipHashDBFiles && evidence.getExtraAttribute(HashDBLookupTask.STATUS_ATTRIBUTE) != null) {
            // Add skip classification info
            evidence.setExtraAttribute(AI_CLASSIFICATION_SKIP_ATTR, AI_CLASSIFICATION_SKIP_HASHDB);
            skipHashDBFilesCount.incrementAndGet();
            return;
        }

        // Skip classification of images/videos duplicates (if classification exists in case cache)
        ClassesAndTag classesAndTag = getClassesFromDb(evidence.getHash());
        if (classesAndTag != null && classesAndTag.classes != null) {
            // Classification success (exists in case cache)

            // Add classification status
            evidence.setExtraAttribute(AI_CLASSIFICATION_STATUS_ATTR, AI_CLASSIFICATION_SUCCESS);
            // Add classification metadata
            evidence.setExtraAttribute(AI_CLASSIFICATION_METADATA_ATTR, classesAndTag.tag);
            // Add classification cache info
            evidence.setExtraAttribute(AI_CLASSIFICATION_FROM_CACHE_ATTR, AI_CLASSIFICATION_FROM_CACHE_CASE);
            // Add skip classification info
            evidence.setExtraAttribute(AI_CLASSIFICATION_SKIP_ATTR, AI_CLASSIFICATION_SKIP_DUPLICATE);

            // Add classification classes to the evidence 
            String[] classesArray = classesAndTag.classes.split(";");
            String[] classParts;
            for (int i = 0; i < classesArray.length; i++) {
                // classParts[0] will hold className and classParts[1] will hold classProb
                classParts = classesArray[i].split("="); 
                // Add classification class to the evidence 
                evidence.setExtraAttribute(classParts[0], Double.parseDouble(classParts[1]));
            }

            skipDuplicatesCount.incrementAndGet();
            return;
        }

        // 'name' is a key to map to the evidence
        String name = evidence.getExtraAttribute(IndexItem.TRACK_ID).toString() + ".jpg";
        if (DIETask.isVideoType(evidence.getMediaType()) || DIETask.isAnimationImage(evidence)) {
            // For videos, call the detection method for each extracted frame image (VideoThumbsTask must be enabled)
            File viewFile = evidence.getViewFile();
            List<BufferedImage> frames;
            if (viewFile != null && viewFile.exists() && (frames = ImageUtil.getFrames(viewFile)) != null) {
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

}
