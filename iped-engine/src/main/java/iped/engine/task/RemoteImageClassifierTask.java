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
 * Stores classification results in evidence's extra attributes.
 * Note: attributes' names are controlled by the remote classifier (usually prefixed by 'AI').
 */
public class RemoteImageClassifierTask extends AbstractTask {

    private static Logger logger = LoggerFactory.getLogger(RemoteImageClassifierTask.class);

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
    private static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS classifications (id TEXT PRIMARY KEY, classes TEXT);";
    private static final String INSERT_DATA = "INSERT INTO classifications (id, classes) VALUES (?,?) ON CONFLICT(id) DO NOTHING;";
    private static final String SELECT_EXACT = "SELECT classes FROM classifications WHERE id=?;";
    // "case cache" database file
    private static File db;
    // "case cache" database connection
    private Connection conn;

    private Map<String, IItem> queue = new TreeMap<>();
    private ZipFile zip;

    // Variables to store statistics
    private static final AtomicLong classificationTime = new AtomicLong();
    private static final AtomicInteger classificationSuccess = new AtomicInteger();
    private static final AtomicInteger classificationFail = new AtomicInteger();
    private static final AtomicLong sendImageBytes = new AtomicLong();
    private static final AtomicLong sendVideoBytes = new AtomicLong();
    private static final AtomicInteger skipDuplicatesCount = new AtomicInteger();
    private static final AtomicInteger skipSizeCount = new AtomicInteger();
    private static final AtomicInteger skipDimensionCount = new AtomicInteger();
    private static final AtomicInteger skipHashDBFilesCount = new AtomicInteger();
    private static final AtomicLong cacheStoreTime = new AtomicLong();
    private static final AtomicInteger cacheStoreCount = new AtomicInteger();
    private static final AtomicLong cacheRetrieveTime = new AtomicLong();
    private static final AtomicInteger cacheRetrieveCount = new AtomicInteger();

    private static class ResultItem {
        public String name;
        public TreeMap<String, ArrayList<Double>> classes = new TreeMap<>();

        public void addClass(String classname, double val) {
            if (!classes.containsKey(classname)) {
                classes.put(classname, new ArrayList<>());
            }
            classes.get(classname).add(val);
        }

        public double getClassProb(String classname) {
            return DIETask.videoScore(classes.get(classname));
        }
    }

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
        this.conn = createConnection(output);
    }

    private Connection createConnection(File output) {
        db = new File(output, AI_STORAGE);
        db.getParentFile().mkdirs();
        try {
            SQLiteConfig config = new SQLiteConfig();
            config.setSynchronous(SynchronousMode.OFF);
            config.setBusyTimeout(3600000);
            Connection conn = config.createConnection("jdbc:sqlite:" + db.getAbsolutePath());

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(CREATE_TABLE);
            }
            return conn;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private String getClassesFromDb(String id) throws IOException {
        return getClassesFromDb(this.conn, id);
    }

    private String getClassesFromDb(Connection conn, String id) throws IOException {
        long t = System.currentTimeMillis();
        try (PreparedStatement ps = conn.prepareStatement(SELECT_EXACT)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            cacheRetrieveTime.addAndGet(System.currentTimeMillis() - t);
            cacheRetrieveCount.incrementAndGet();
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
        return null;
    }

    private void storeClassesInDb(String id, String classes) throws IOException {
        long t = System.currentTimeMillis();
        try (PreparedStatement ps = conn.prepareStatement(INSERT_DATA)) {
            ps.setString(1, id);
            ps.setString(2, classes);
            ps.executeUpdate();
            cacheStoreTime.addAndGet(System.currentTimeMillis() - t);
            cacheStoreCount.incrementAndGet();
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

    @Override
    public void finish() throws Exception {
        // Close DB connection
        if (conn != null) {
            conn.close();
            conn = null;
        }

        // Statistics for classification requests 
        long totClassifications = classificationSuccess.longValue() + classificationFail.longValue();
        if (totClassifications != 0) {
            logger.info("Total count of thumbs sent for classification: " + totClassifications);
            logger.info(" Successful classifications: " + classificationSuccess.intValue());
            logger.info(" Failed classifications: " + classificationFail.intValue());
            logger.info(" Average classification time (ms/thumb): " + String.format("%.3f", (((float) classificationTime.longValue() / this.worker.manager.getNumWorkers()) / totClassifications)));
            logger.info(" Average classification throughput (thumbs/s): " + ((int) (totClassifications / ((float) classificationTime.longValue() / this.worker.manager.getNumWorkers()) * 1000)));
            logger.info("Total bytes sent: " + (sendImageBytes.longValue() + sendVideoBytes.longValue()));
            logger.info(" Bytes sent for images: " + sendImageBytes.longValue());
            logger.info(" Bytes sent for videos: " + sendVideoBytes.longValue());
            classificationSuccess.set(0);
            classificationFail.set(0);
        }

        // Statistics for skipped classification
        long totSkipCount = skipSizeCount.longValue() + skipDimensionCount.longValue() + skipHashDBFilesCount.longValue() + skipDuplicatesCount.longValue();
        if (totSkipCount != 0) {
            logger.info("Total count of files with skipped classification: " + totSkipCount);
            logger.info(" Skipped classification by duplicates: " + skipDuplicatesCount.intValue());
            logger.info("  Cache file size: ~" + Math.ceil((float) db.length() / 1024) + "KB");
            logger.info("  Cache store operations: " + cacheStoreCount.intValue() + "; average operation time (ms): " + String.format("%.6f", ((float) cacheStoreTime.longValue() / cacheStoreCount.intValue())));
            logger.info("  Cache retrieve operations: " + cacheRetrieveCount.intValue() + "; average operation time (ms): " + String.format("%.6f", ((float) cacheRetrieveTime.longValue() / cacheRetrieveCount.intValue())));
            logger.info(" Skipped classification by size: " + skipSizeCount.intValue());
            logger.info(" Skipped classification by dimension: " + skipDimensionCount.intValue());
            logger.info(" Skipped classification by hashDBFiles: " + skipHashDBFilesCount.intValue());
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
        JsonNode resultArray = jsonResponse.get("results");
        Map<String,ResultItem> results = new TreeMap<>();

        if (resultArray != null && resultArray.isArray()) {
            for (JsonNode item : resultArray) {
                String name = item.get("filename").asText();
                ResultItem res = null;
                if (name == null) {
                    logger.warn("Invalid name ");
                    continue;
                }
                if (name.split("_").length == 2) {
                    name = name.split("_")[1];
                    res = results.get(name);
                }
                if (res == null) {
                    res = new ResultItem();
                    results.put(name, res);
                }

                JsonNode classes = item.get("class");
                if (classes != null && classes.isObject()) {
                    classificationSuccess.incrementAndGet();
                    var iterator = classes.fields();
                    while (iterator.hasNext()) {
                        Map.Entry<String, JsonNode> entry = iterator.next();
                        String key = entry.getKey();
                        double value = entry.getValue().asDouble();
                        res.addClass(key, value);
                    }
                } else {
                    classificationFail.incrementAndGet();
                    logger.warn("'class' field is missing for filename: {}", name);
                }
            }

            // Add classification classes to evidence's extra attributes related to each result
            for (String name : results.keySet()) {
                IItem evidence;
                if (name == null || (evidence = queue.get(name)) == null) {
                    logger.warn("No matching item found ");
                    continue;
                }

                ResultItem res = results.get(name);
                Iterator<String> iterator = res.classes.keySet().iterator();
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
                // Add extra attribute to signal that classification was NOT retrieved from cache
                evidence.setExtraAttribute("AIClassesFromCache","no");

                // Store classification classes in case cache
                storeClassesInDb(evidence.getHash(), classes.toString());
            }
        } else {
            classificationFail.incrementAndGet();
            logger.warn("'results' array is missing in JSON response.");
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
        logger.info("Send ZIP file with {} files", zip.getFileCount());
        try (CloseableHttpClient client = getClient()) {
            HttpPost post = new HttpPost(url);

            HttpEntity entity = MultipartEntityBuilder.create().setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                    .addBinaryBody("file", zipFile, ContentType.APPLICATION_OCTET_STREAM, zipFile.getName()).build();

            post.setEntity(entity);

            long t = System.currentTimeMillis();
            try (CloseableHttpResponse response = client.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();
                classificationTime.addAndGet(System.currentTimeMillis() - t);
                if (statusCode == 200) {
                    try (InputStream responseStream = response.getEntity().getContent()) {
                        processResult(responseStream);
                    }
                } else {
                    classificationFail.incrementAndGet();
                    String errorMessage;
                    try (InputStream errorStream = response.getEntity().getContent()) {
                        errorMessage = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        errorMessage = "Failed to read error message from response.";
                    }
                    logger.error("Failed to upload ZIP file. HTTP Code: {} - Response: {}", statusCode, errorMessage);
                }
            }
        }
    }

    @Override
    protected boolean processQueueEnd() {
        return true;
    }

    @Override
    protected void process(IItem evidence) throws Exception {
        if (zip.getFileCount() >= batchSize || (evidence.isQueueEnd() && zip.getFileCount() > 0)) {
            sendZipFile(zip.closeAndGetZip());
            zip.clean();
            zip = new ZipFile();
            sendItemsToNextTask();
        }

        if (!isEnabled() || !evidence.isToAddToCase() || evidence.getHashValue() == null || evidence.getThumb() == null
                || evidence.getThumb().length < 10 || evidence.isQueueEnd()) {
            return;
        }

        // Skip classification of images/videos duplicates (if classification exists in case cache)
        String classes = getClassesFromDb(evidence.getHash());
        if (classes != null) {
            // Add classification classes to the evidence 
            String[] classesArray = classes.split(";");
            String[] classParts;
            for (int i = 0; i < classesArray.length; i++) {
                // classParts[0] will hold className and classParts[1] will hold classProb
                classParts = classesArray[i].split("="); 
                // Add classification class to the evidence 
                evidence.setExtraAttribute(classParts[0], Double.parseDouble(classParts[1]));
            }
            // Add extra attribute to signal that classification was retrieved from cache
            evidence.setExtraAttribute("AIClassesFromCache","case");

            skipDuplicatesCount.incrementAndGet();
            return;
        }

        // Skip classification of images/videos smaller than a given file size, according to 'skipSize' config property
        if (skipSize > 0 && skipSize > evidence.getLength()) {
            skipSizeCount.incrementAndGet();
            return;
        }

        // Skip classification of images/videos smaller than a given dimension, i.e. height or width, according to 'skipDimension' config property
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
                skipDimensionCount.incrementAndGet();
                return;
            }
        }

        // Skip classification of images/videos with hits on IPED hashesDB database, according to 'skipHashDBFiles' config property
        if (skipHashDBFiles && evidence.getExtraAttribute(HashDBLookupTask.STATUS_ATTRIBUTE) != null) {
            skipHashDBFilesCount.incrementAndGet();
            return;
        }

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
            } else {
                sendVideoBytes.addAndGet(evidence.getThumb().length);
                zip.addFileToZip(name, evidence.getThumb());
            }
        } else {
            sendImageBytes.addAndGet(evidence.getThumb().length);
            zip.addFileToZip(name, evidence.getThumb());
        }
        queue.put(name, evidence);
    }

}
