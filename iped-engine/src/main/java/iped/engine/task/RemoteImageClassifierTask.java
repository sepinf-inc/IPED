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

public class RemoteImageClassifierTask extends AbstractTask {

    private static Logger logger = LoggerFactory.getLogger(RemoteImageClassifierTask.class);

    private RemoteImageClassifierConfig config;

    private String url;
    private int batchSize;
    private int skipSize;
    private int skipDimension;
    private boolean skipHashDBFiles;   
    private boolean validateSSL;

    // Temporary storage to allow for skipping classification of duplicate files
    private static final String AI_STORAGE = "ai/unique_files.db";
    private static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS unique_files(id TEXT PRIMARY KEY);";
    private static final String INSERT_DATA = "INSERT INTO unique_files(id) VALUES(?) ON CONFLICT(id) DO NOTHING";
    private static final String SELECT_EXACT = "SELECT id FROM unique_files WHERE id=?;";
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
        File db = new File(output, AI_STORAGE);
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

    private String getIdFromDb(String id) throws IOException {
        return getIdFromDb(this.conn, id);
    }

    private String getIdFromDb(Connection conn, String id) throws IOException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_EXACT)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
        return null;
    }

    private void storeIdInDb(String id) throws IOException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_DATA)) {
            ps.setString(1, id);
            ps.executeUpdate();
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
            logger.info("Total classifications count: " + totClassifications);
            logger.info("Successful classifications: " + classificationSuccess.intValue());
            logger.info("Failed classifications: " + classificationFail.intValue());
            logger.info("Average classification time (ms/thumb): " + String.format("%.3f", (((float) classificationTime.longValue() / this.worker.manager.getNumWorkers()) / totClassifications)));
            logger.info("Average classification throughput (thumbs/s): " + ((int) (totClassifications / ((float) classificationTime.longValue() / this.worker.manager.getNumWorkers()) * 1000)));
            logger.info("Total bytes sent: " + (sendImageBytes.longValue() + sendVideoBytes.longValue()));
            logger.info("Bytes sent for images: " + sendImageBytes.longValue());
            logger.info("Bytes sent for videos: " + sendVideoBytes.longValue());
            classificationSuccess.set(0);
            classificationFail.set(0);
        }

        // Statistics for skipped classification
        long totSkipCount = skipSizeCount.longValue() + skipDimensionCount.longValue() + skipHashDBFilesCount.longValue() + skipDuplicatesCount.longValue();
        if (totSkipCount != 0) {
            logger.info("Total skipped classification: " + totSkipCount);
            logger.info("Skipped classification by duplicates: " + skipDuplicatesCount.intValue());
            logger.info("Skipped classification by size: " + skipSizeCount.intValue());
            logger.info("Skipped classification by dimension: " + skipDimensionCount.intValue());
            logger.info("Skipped classification by hashDBFiles: " + skipHashDBFilesCount.intValue());
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

            for (String name : results.keySet()) {
                IItem evidence;
                if (name == null || (evidence = queue.get(name)) == null) {
                    logger.warn("No matching item found ");
                    continue;
                }
                ResultItem res = results.get(name);
                for (String classname : res.classes.keySet()) {
                    evidence.setExtraAttribute(classname, res.getClassProb(classname));
                }
                storeIdInDb(evidence.getHash());
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

        // Skip classification of images/videos duplicates
        String hash = getIdFromDb(evidence.getHash());
        if (hash != null) {
            logger.info("- SkipDuplicates: Evidence -> type: '" + evidence.getMediaType().toString() + "'; hash: '" + evidence.getHash() + "'; name: '" + evidence.getName() + "'");
            skipDuplicatesCount.incrementAndGet();
            return;
        }

        // Skip classification of images/videos smaller than a given file size, according to 'skipSize' config property
        if (config.getSkipSize() > 0 && config.getSkipSize() > evidence.getLength()) {
            logger.info("- SkipSize: Evidence -> type: '" + evidence.getMediaType().toString() + "'; hash: '" + evidence.getHash() + "'; name: '" + evidence.getName() + "'; size: '" + evidence.getLength() + "'; skipSize: '" + config.getSkipSize() + "'");
            skipSizeCount.incrementAndGet();
            return;
        }

        // Skip classification of images/videos smaller than a given dimension, i.e. height or width, according to 'skipDimension' config property
        if (config.getSkipDimension() > 0) {
            int width = 0;
            int height = 0;
            String mediaType = evidence.getMediaType().toString();
            if (mediaType.startsWith("image")) {
                width = Integer.parseInt(evidence.getMetadata().get("image:Width"));
                height = Integer.parseInt(evidence.getMetadata().get("image:Height"));
                logger.info("Evidence -> type: '" + mediaType + "'; hash: '" + evidence.getHash() + "'; name: '" + evidence.getName() + "'; dimension: '" + width + "x" + height + "'");
            }
            else if (mediaType.startsWith("video")) {
                width = Integer.parseInt(evidence.getMetadata().get("video:Width"));
                height = Integer.parseInt(evidence.getMetadata().get("video:Height"));
                logger.info("Evidence -> type: '" + mediaType + "'; hash: '" + evidence.getHash() + "'; name: '" + evidence.getName() + "'; dimension: '" + width + "x" + height + "'");
            }
            if ((config.getSkipDimension() > width || config.getSkipDimension() > height) && width > 0 && height > 0) {
                logger.info("- SkipDimension: Evidence -> type: '" + mediaType + "'; hash: '" + evidence.getHash() + "'; name: '" + evidence.getName() + "'; dimension: '" + width + "x" + height + "'; skipDimension: '" + config.getSkipDimension() + "'");
                skipDimensionCount.incrementAndGet();
                return;
            }
        }

        // Skip classification of images/videos with hits on IPED hashesDB database, according to 'skipHashDBFiles' config property
        if (config.isSkipHashDBFiles() && evidence.getExtraAttribute(HashDBLookupTask.STATUS_ATTRIBUTE) != null) {
            logger.info("- SkipHashDBFiles: Evidence -> type: '" + evidence.getMediaType().toString() + "'; hash: '" + evidence.getHash() + "'; name: '" + evidence.getName() + "'; hashDBStatus: '" + evidence.getExtraAttribute(HashDBLookupTask.STATUS_ATTRIBUTE) + "'");
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
