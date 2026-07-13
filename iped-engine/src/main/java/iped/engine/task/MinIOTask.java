package iped.engine.task;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.Deflater;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.CountingOutputStream;
import org.apache.tika.Tika;
import org.apache.tika.io.TemporaryResources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.PutObjectArgs.Builder;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import iped.configuration.Configurable;
import iped.data.ICaseData;
import iped.data.IItem;
import iped.engine.CmdLineArgs;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.MinIOConfig;
import iped.engine.preview.PreviewRepositoryManager;
import iped.engine.task.index.ElasticSearchIndexTask;
import iped.io.SeekableInputStream;
import iped.utils.SeekableFileInputStream;
import iped.utils.SeekableInputStreamFactory;

/**
 * Task to export files to MinIO object storage service.
 * 
 * TODO: This and @ExportFileTask should extend a common abstract class, and the
 * implementation should be chosen depending on configuration.
 * 
 * @author Nassif
 *
 */
public class MinIOTask extends AbstractTask {

    private static Logger logger = LoggerFactory.getLogger(MinIOTask.class);

    private static final int FOLDER_LEVELS = 2;
    private static final String CMD_LINE_KEY = "MinioCredentials";
    private static final String ACCESS_KEY = "accesskey";
    private static final String SECRET_KEY = "secretkey";
    private static final String BUCKET_KEY = "bucket";

    private static AtomicBoolean credentialsLoaded = new AtomicBoolean(false);

    private static String paramBucket = null;

    private static String accessKey = null;
    private static String secretKey = null;

    private static Tika tika;

    private MinIOConfig minIOConfig;
    private MinioClient minioClient;
    private MinIOInputInputStreamFactory inputStreamFactory;

    private static long zipFilesMaxSize = 0;
    private static final long zipMaxSize = 8 * 1024 * 1024;
    private static final long zipMaxFiles = 10000;

    private static class ZipRequest {
        public TemporaryResources tmp = null;
        public ZipArchiveOutputStream out = null;
        public CountingOutputStream cos = null;
        public File zipfile = null;
        public long zipLength = 0;
        public long zipFiles = 0;
    }

    private Map<String, ZipRequest> zipRequests = new HashMap<>();
    private static Set<String> existBucket = new HashSet<>();


    private long timeout;
    private int retries;

    private Map<Integer, QueueItem> queue = new TreeMap<>();
    private boolean sendQueue = false;

    private static class QueueItem {
        public IItem item = null;
        public String fullpath = null;

        public QueueItem(IItem i, String fullpath) {
            this.item = i;
            this.fullpath = fullpath;
        }
    }

    // Workaround for class loader issues with minio-8.3.8 and our custom class
    // loader. Without this, reading streams from minio throws:
    // NoClassDefFoundError: Could not initialize class
    // org.simpleframework.xml.stream.NodeBuilder
    static {
        try {
            new org.simpleframework.xml.stream.NodeBuilder();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {

        minIOConfig = configurationManager.findObject(MinIOConfig.class);

        if (!minIOConfig.isEnabled()) {
            return;
        }

        timeout = TimeUnit.SECONDS.toMillis(minIOConfig.getTimeOut());
        retries = minIOConfig.getRetries();

        zipFilesMaxSize = minIOConfig.getZipFilesMaxSize();

        String server = minIOConfig.getHost() + ":" + minIOConfig.getPort();
        if (!credentialsLoaded.getAndSet(true)) {
            loadCredentials(caseData);
            if (paramBucket != null) {
                logger.error("Passing the bucket as a parameter may prevent removing the evidence from the case.");
            }
        }


        minioClient = MinioClient.builder().endpoint(server).credentials(accessKey, secretKey).build();
        minioClient.setTimeout(timeout, timeout, timeout);
        inputStreamFactory = new MinIOInputInputStreamFactory(URI.create(server));

        checkDependency(HashTask.class);
    }

    private static void parseFields(String cmdFields) {
        if (cmdFields == null)
            return;
        String[] entries = cmdFields.split(";");
        for (String entry : entries) {
            String[] pair = entry.split(":", 2);
            if (ACCESS_KEY.equals(pair[0]))
                accessKey = pair[1];
            else if (SECRET_KEY.equals(pair[0]))
                secretKey = pair[1];
            else if (BUCKET_KEY.equals(pair[0]))
                paramBucket = pair[1];
        }
    }
    private static void loadCredentials(ICaseData caseData) {
        if (accessKey != null && secretKey != null) {
            return;
        }

        parseFields(System.getenv(CMD_LINE_KEY));
        parseFields(System.getProperty(CMD_LINE_KEY));

        if (caseData != null) {
            CmdLineArgs args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
            parseFields(args.getExtraParams().get(CMD_LINE_KEY));

        }

        if (accessKey == null || secretKey == null) {
            throw new RuntimeException("'MinioCredentials' not set by ENV var, sys prop or cmd line param.");
        }

    }

    @Override
    public boolean isEnabled() {
        return minIOConfig.isEnabled();
    }

    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new MinIOConfig());
    }

    public static boolean isTaskEnabled() {
        MinIOConfig minIOConfig = ConfigurationManager.get().findObject(MinIOConfig.class);
        return minIOConfig.isEnabled();
    }

    @Override
    public void finish() throws Exception {
        flushZipFile();
    }

    private void flushZipFile() throws Exception {
        for (String bucket : zipRequests.keySet()) {
            ZipRequest zp = zipRequests.get(bucket);
            if (zp.zipfile != null) {
                logger.info("Flushing MinIOTask " + worker.id + " Sending zip containing " + zp.zipFiles + " files");
                sendZipFile(bucket);
            }
        }
    }

    private String getZipName(ZipRequest zp) {
        return DigestUtils.md5Hex(zp.zipfile.getName()).toUpperCase() + ".zip";
    }

    private String getBucket(IItem i) throws Exception {
        String bucket = paramBucket == null ? i.getDataSource().getUUID() : paramBucket;
        if (!zipRequests.containsKey(bucket)) {
            zipRequests.put(bucket, new ZipRequest());
        }
        // Check if the bucket already exists.
        synchronized (existBucket) {
            if (!existBucket.contains(bucket)) {
                existBucket.add(bucket);
                boolean isExist = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
                if (!isExist) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                }
            }
        }
        return bucket;
    }

    private String insertInZipFile(String hash, SeekableInputStream is, IItem i, boolean preview)
            throws Exception {
        ZipRequest zp = zipRequests.get(getBucket(i));
        if (zp == null) {
            zp = new ZipRequest();
            zipRequests.put(getBucket(i), zp);
        }
        if (zp.out == null) {
            zp.zipLength = 0;
            zp.tmp = new TemporaryResources();
            zp.zipfile = zp.tmp.createTemporaryFile();
            zp.cos = new CountingOutputStream(new BufferedOutputStream(new FileOutputStream(zp.zipfile)));
            zp.out = new ZipArchiveOutputStream(zp.cos);
            zp.out.setLevel(Deflater.NO_COMPRESSION);
        }

        String fullpath = getBucket(i) + "/zips/" + getZipName(zp) + "/" + hash;

        // insert into the queue of files waiting to be sent
        if (!preview) {
            queue.put(i.getId(), new QueueItem(i, fullpath));
        }

        zp.zipFiles++;
        ZipArchiveEntry entry = new ZipArchiveEntry(hash);
        entry.setSize(is.size());
        zp.out.putArchiveEntry(entry);
        IOUtils.copy(is, zp.out);
        zp.out.closeArchiveEntry();
        zp.zipLength = zp.cos.getByteCount();

        return fullpath;

    }

    private String insertWithZip(IItem i, String hash, SeekableInputStream is, String mediatype,
            boolean preview) throws Exception {

        String bucketPath = buildPath(hash);
        // if preview saves in a preview folder
        if (preview) {
            bucketPath = "preview/" + hash;
        }
        String fullPath = getBucket(i) + "/" + bucketPath;

        // if empty or already exists do not continue
        if (is.size() <= 0) {
            return null;
        }
        if (checkIfExists(getBucket(i), bucketPath)) {
            if (!preview) {
                updateDataSource(i, fullPath);
            }
            return fullPath;
        }

        if (is.size() > zipFilesMaxSize) {
            insertItem(hash, is, mediatype, getBucket(i), bucketPath);
            if (!preview) {
                updateDataSource(i, fullPath);
            }
        } else {
            fullPath = insertInZipFile(hash, is, i, preview);
        }

        return fullPath;

    }

    private void sendZipFile(String bucket) throws Exception {

        ZipRequest zp = zipRequests.get(bucket);

        zp.out.close();

        if (zp.zipFiles > 0) {
            try (SeekableFileInputStream fi = new SeekableFileInputStream(zp.zipfile)) {

                sendFile(PutObjectArgs.builder().bucket(bucket).object("/zips/" + getZipName(zp))
                                .userMetadata(Collections.singletonMap("x-minio-extrac", "true")),
                        fi, zp.zipfile.length(), Math.max(zp.zipfile.length(), 1024 * 1024 * 5));

            }
        }
        // mark to send items to next tasks
        sendQueue = true;

        zp.tmp.close();
        zp.zipFiles = 0;
        zp.zipLength = 0;
        zp.out = null;
        zp.cos = null;
        zp.tmp = null;
        zp.zipfile = null;
    }

    private void sendZipItemsToNextTask() throws Exception {
        ArrayList<QueueItem> values = new ArrayList<>(queue.values());
        queue.clear();
        sendQueue = false;
        for (QueueItem i : values) {
            if (i != null) {
                updateDataSource(i.item, i.fullpath);
                super.sendToNextTask(i.item);
            }
        }
    }

    private boolean checkIfExists(String bucket, String hash) throws Exception {
        boolean exists = false;
        try {
            minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(hash).build());
            exists = true;

        } catch (ErrorResponseException e) {
            int code = e.response().code();
            if (code != 404) {
                throw e;
            }
        }

        return exists;
    }

    private void sendFile(Builder builder, SeekableInputStream is, long size, long partSize) throws Exception {
        Exception ex = null;
        for (int i = 0; i <= retries || retries == -1; i++) {
            try {
                is.seek(0);
                minioClient.putObject(builder.stream(new BufferedInputStream(is), size, partSize).build());
                return;
            } catch (Exception e) {
                // save the Exception to be throwed after all retries;
                ex = e;
            }
        }
        if (ex != null) {
            throw ex;
        }
    }

    private void insertItem(String hash, SeekableInputStream is, String mediatype, String bucket, String bucketPath)
            throws Exception {

        try {
            sendFile(PutObjectArgs.builder().bucket(bucket).object(bucketPath).contentType(mediatype), is, -1, 10 << 20);

        } catch (Exception e) {
            throw new Exception("Error when uploading object ", e);
        }

    }

    private static String getMimeType(String name) {
        if (tika == null) {
            synchronized (MinIOTask.class) {
                if (tika == null) {
                    tika = new Tika();
                }
            }
        }
        return tika.detect(name);
    }

    @Override
    protected boolean processQueueEnd() {
        // TODO Auto-generated method stub
        return true;
    }

    @Override
    protected void sendToNextTask(IItem item) throws Exception {
        if(!isEnabled()) {
            super.sendToNextTask(item);
            return;
        }

        if (item.isQueueEnd() && !queue.isEmpty()) {
            flushZipFile();
        }
        // if queue contains the item it will be sent when the zipfile is sent;
        if (queue.get(item.getId()) == null) {
            super.sendToNextTask(item);

        }
        if (sendQueue) {
            sendZipItemsToNextTask();
        }

    }

    @Override
    protected void process(IItem item) throws Exception {

        if (item.isQueueEnd()) {
            flushZipFile();
            return;
        }

        if (caseData.isIpedReport() || !item.isToAddToCase())
            return;

        String hash = item.getHash();
        if (hash == null || hash.isEmpty() || item.getLength() == null)
            return;

        try (SeekableInputStream is = item.getSeekableInputStream()) {
            insertWithZip(item, hash, is, item.getMediaType().toString(), false);

        } catch (Exception e) {
            logger.error(e.getMessage() + "File " + item.getPath() + " (" + item.getLength() + " bytes)", e);
            throw e;
        }

        SeekableFileInputStream is = null;
        String mime = null;
        try {
            if (item.getViewFile() != null && item.getViewFile().length() > 0) {
                is = new SeekableFileInputStream(item.getViewFile());
                mime = getMimeType(item.getViewFile().getName());
            } else if (item.hasPreview()) {
                is = PreviewRepositoryManager.get(output).readPreview(item, false);
                mime = getMimeType("file." + item.getPreviewExt());
            }

            if (is != null) {
                String fullPath = insertWithZip(item, hash, is, mime, true);
                if (fullPath != null) {
                    item.getMetadata().add(ElasticSearchIndexTask.PREVIEW_IN_DATASOURCE,
                            "idInDataSource" + ElasticSearchIndexTask.KEY_VAL_SEPARATOR + fullPath);
                    item.getMetadata().add(ElasticSearchIndexTask.PREVIEW_IN_DATASOURCE,
                            "type" + ElasticSearchIndexTask.KEY_VAL_SEPARATOR + mime);
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage() + "Preview " + item, e);
            throw e;
        } finally {
            IOUtils.closeQuietly(is);
        }

        String bucket = getBucket(item);
        ZipRequest zp = zipRequests.get(bucket);

        if (zp.zipFiles >= zipMaxFiles || zp.zipLength >= zipMaxSize) {
            sendZipFile(bucket);
        }

    }

    private static String buildPath(String hash) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < FOLDER_LEVELS; i++) {
            sb.append(hash.charAt(i)).append("/");
        }
        sb.append(hash);
        return sb.toString();
    }

    private static String[] parseBucketAndPath(String path) {
        return path.split("/", 2);
    }

    public static class MinIODataRef {

        public final SeekableInputStreamFactory inputStreamFactory;
        public final String idInDataSource;

        MinIODataRef(SeekableInputStreamFactory inputStreamFactory, String id) {
            this.inputStreamFactory = inputStreamFactory;
            this.idInDataSource = id;
        }
    }

    private void updateDataSource(IItem item, String id) {
        if (id == null || item == null) {
            return;
        }
        MinIODataRef minIORef = new MinIODataRef(inputStreamFactory, id);
        item.setTempAttribute(MinIODataRef.class.getName(), minIORef);

        if (minIOConfig.isToUpdateRefsToMinIO()) {
            item.setInputStreamFactory(inputStreamFactory);
            item.setIdInDataSource(id);
            item.setFileOffset(-1);
        }
    }

    public static class MinIOInputInputStreamFactory extends SeekableInputStreamFactory {

        private static Map<String, MinioClient> map = new ConcurrentHashMap<>();

        public MinIOInputInputStreamFactory(URI dataSource) {
            super(dataSource);
        }

        public boolean checkIfDataSourceExists() {
            return false;
        }

        @Override
        public SeekableInputStream getSeekableInputStream(String identifier) throws IOException {
            String server = dataSource.toString();
            String[] parts = parseBucketAndPath(identifier);
            String bucket = parts[0];
            String path = parts[1];

            MinioClient minioClient = map.get(server);
            if (minioClient == null) {
                loadCredentials(null);
                minioClient = MinioClient.builder().endpoint(server).credentials(accessKey, secretKey).build();
                map.put(server, minioClient);
            }
            return new MinIOSeekableInputStream(minioClient, bucket, path);
        }

    }

    public static class MinIOSeekableInputStream extends SeekableInputStream {

        private MinioClient minioClient;
        private String bucket, id;
        private Long size;
        private long pos = 0;
        private InputStream is;

        public MinIOSeekableInputStream(MinioClient minioClient, String bucket, String id) {
            this.minioClient = minioClient;
            this.bucket = bucket;
            this.id = id;
        }

        @Override
        public void seek(long pos) throws IOException {
            this.pos = pos;
            if (is != null) {
                is.close();
                is = getInputStream(pos);
            }
        }

        @Override
        public long position() throws IOException {
            return pos;
        }

        @Override
        public long size() throws IOException {
            if (size == null) {
                if (id.contains(".zip/")) {
                    try (GetObjectResponse res = minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(id)
                            .offset(0L).extraHeaders(Collections.singletonMap("x-minio-extract", "true")).build())) {
                        size = Long.valueOf(res.headers().get("Content-Length"));

                    } catch (Exception e) {
                        if (e instanceof IOException) {
                            throw (IOException) e;
                        }
                        throw new IOException(e);
                    }
                } else {
                    try {
                        size = minioClient
                                .statObject(StatObjectArgs.builder().bucket(bucket).object(id)
                                        .extraHeaders(Collections.singletonMap("x-minio-extract", "true")).build())
                                .size();
                    } catch (Exception e) {
                        if (e instanceof IOException) {
                            throw (IOException) e;
                        }
                        throw new IOException(e);
                    }
                }
            }
            return size;
        }

        @Override
        public int available() throws IOException {
            long avail = size() - pos;
            return (int) Math.min(avail, Integer.MAX_VALUE);
        }

        @Override
        public int read() throws IOException {
            byte[] b = new byte[1];
            int i;
            do {
                i = read(b, 0, 1);
            } while (i == 0);

            if (i == -1)
                return -1;
            else
                return b[0] & 0xFF;
        }

        @Override
        public int read(byte b[], int off, int len) throws IOException {

            if (is == null) {
                is = getInputStream(pos);
            }

            int read = is.read(b, off, len);
            if (read != -1) {
                pos += read;
            }
            return read;
        }

        private InputStream getInputStream(long pos) throws IOException {
            try {
                return minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(id).offset(pos)
                        .extraHeaders(Collections.singletonMap("x-minio-extract", "true")).build());

            } catch (Exception e) {
                if (e instanceof IOException) {
                    throw (IOException) e;
                }
                throw new IOException(e);
            }
        }

        @Override
        public long skip(long n) throws IOException {

            long oldPos = pos;
            pos += n;

            if (pos > size())
                pos = size();
            else if (pos < 0)
                pos = 0;

            if (is != null) {
                is.close();
                is = getInputStream(pos);
            }

            return pos - oldPos;

        }

        @Override
        public void close() throws IOException {
            if (is != null) {
                is.close();
                is = null;
            }
        }

    }

}
