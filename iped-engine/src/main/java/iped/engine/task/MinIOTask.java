package iped.engine.task;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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

    private static String accessKey;
    private static String secretKey;
    private static String bucket = null;

    private static Tika tika;

    private MinIOConfig minIOConfig;
    private MinioClient minioClient;
    private MinIOInputInputStreamFactory inputStreamFactory;

    private static long zipFilesMaxSize = 0;
    private static final long zipMaxSize = 8 * 1024 * 1024;
    private static final long zipMaxFiles = 10000;

    private TemporaryResources tmp = null;
    private ZipArchiveOutputStream out = null;
    private CountingOutputStream cos = null;
    private File zipfile = null;
    private long zipLength = 0;
    private long zipFiles = 0;
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

        // case name is default bucket name
        if (bucket == null) {
            bucket = output.getParentFile().getName().toLowerCase();
        }
        loadCredentials(caseData);

        minioClient = MinioClient.builder().endpoint(server).credentials(accessKey, secretKey).build();
        minioClient.setTimeout(timeout, timeout, timeout);
        inputStreamFactory = new MinIOInputInputStreamFactory(URI.create(server));

        // Check if the bucket already exists.
        boolean isExist = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!isExist) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }

    }

    private static void loadCredentials(ICaseData caseData) {
        if (accessKey != null && secretKey != null) {
            return;
        }
        String cmdFields = null;
        if (caseData != null) {
            CmdLineArgs args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
            cmdFields = args.getExtraParams().get(CMD_LINE_KEY);
        }
        if (cmdFields == null) {
            cmdFields = System.getProperty(CMD_LINE_KEY);
        }
        if (cmdFields == null) {
            cmdFields = System.getenv(CMD_LINE_KEY);
        }
        if (cmdFields == null) {
            throw new RuntimeException("'MinioCredentials' not set by ENV var, sys prop or cmd line param.");
        }
        String[] entries = cmdFields.split(";");
        for (String entry : entries) {
            String[] pair = entry.split(":", 2);
            if (ACCESS_KEY.equals(pair[0]))
                accessKey = pair[1];
            else if (SECRET_KEY.equals(pair[0]))
                secretKey = pair[1];
            else if (BUCKET_KEY.equals(pair[0]))
                bucket = pair[1];
        }
    }

    @Override
    public boolean isEnabled() {
        return minIOConfig.isEnabled();
    }

    public List<Configurable<?>> getConfigurables() {
        MinIOConfig result = ConfigurationManager.get().findObject(MinIOConfig.class);
        if(result == null) {
            result = new MinIOConfig();
        }
        return Arrays.asList(result);
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
        if (zipfile != null) {
            logger.info("Flushing MinIOTask " + worker.id + " Sending zip containing " + zipFiles + " files");
            sendZipFile();
        }
    }

    private String getZipName() {
        return DigestUtils.md5Hex(zipfile.getName()).toUpperCase() + ".zip";
    }

    private String insertInZipFile(String hash, SeekableInputStream is, IItem i, boolean preview)
            throws Exception {
        if (out == null) {
            zipLength = 0;
            tmp = new TemporaryResources();
            zipfile = tmp.createTemporaryFile();
            cos = new CountingOutputStream(new BufferedOutputStream(new FileOutputStream(zipfile)));
            out = new ZipArchiveOutputStream(cos);
            out.setLevel(Deflater.NO_COMPRESSION);
        }
        String fullpath = bucket + "/zips/" + getZipName() + "/" + hash;

        // insert into the queue of files waiting to be sent
        if (!preview) {
            queue.put(i.getId(), new QueueItem(i, fullpath));
        }

        zipFiles++;
        ZipArchiveEntry entry = new ZipArchiveEntry(hash);
        entry.setSize(is.size());
        out.putArchiveEntry(entry);
        IOUtils.copy(is, out);
        out.closeArchiveEntry();
        zipLength = cos.getByteCount();

        return fullpath;

    }

    private String insertWithZip(IItem i, String hash, SeekableInputStream is, String mediatype,
            boolean preview) throws Exception {

        String bucketPath = buildPath(hash);
        // if preview saves in a preview folder
        if (preview) {
            bucketPath = "preview/" + hash;
        }
        String fullPath = bucket + "/" + bucketPath;

        // if empty or already exists do not continue
        if (is.size() <= 0) {
            return null;
        }

        if (checkIfExists(bucketPath)) {
            updateDataSource(i, fullPath);
            return fullPath;
        }

        if (is.size() > zipFilesMaxSize) {
            insertItem(hash, is, mediatype, bucketPath);
            if (!preview) {
                updateDataSource(i, fullPath);
            }
        } else {
            fullPath = insertInZipFile(hash, is, i, preview);
        }

        return fullPath;

    }

    private void sendZipFile() throws Exception {
        out.close();

        if (zipFiles > 0) {
            try (SeekableFileInputStream fi = new SeekableFileInputStream(zipfile)) {

                sendFile(PutObjectArgs.builder().bucket(bucket).object("/zips/" + getZipName())
                                .userMetadata(Collections.singletonMap("x-minio-extrac", "true")),
                        fi, zipfile.length(), Math.max(zipfile.length(), 1024 * 1024 * 5));

            }
        }
        // mark to send items to next tasks
        sendQueue = true;

        tmp.close();
        zipFiles = 0;
        zipLength = 0;
        out = null;
        cos = null;
        tmp = null;
        zipfile = null;
    }

    private void sendZipItemsToNextTask() throws Exception {
        for (QueueItem i : queue.values()) {
            if (i != null) {
                updateDataSource(i.item, i.fullpath);
                super.sendToNextTask(i.item);
            }
        }
        queue.clear();
        sendQueue = false;
    }

    private boolean checkIfExists(String hash) throws Exception {
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
        for (int i = 0; i <= retries; i++) {
            try {
                is.seek(0);
                minioClient.putObject(builder.stream(new BufferedInputStream(is), size, partSize).build());
                return;
            } catch (Exception e) {
                // save the Exception to be throwed after all retries;
                ex = e;
            }
        }
        throw ex;
    }

    private void insertItem(String hash, SeekableInputStream is, String mediatype, String bucketPath)
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
            // TODO: handle exception
            logger.error(e.getMessage() + "File " + item.getPath() + " (" + item.getLength() + " bytes)", e);
        }
        if (item.getViewFile() != null && item.getViewFile().length() > 0) {
            try (SeekableFileInputStream is = new SeekableFileInputStream(item.getViewFile())) {
                String mime = getMimeType(item.getViewFile().getName());
                String fullPath = insertWithZip(item, hash, is, mime, true);
                if (fullPath != null) {
                    item.getMetadata().add(ElasticSearchIndexTask.PREVIEW_IN_DATASOURCE,
                            "idInDataSource" + ElasticSearchIndexTask.KEY_VAL_SEPARATOR + fullPath);
                    item.getMetadata().add(ElasticSearchIndexTask.PREVIEW_IN_DATASOURCE,
                            "type" + ElasticSearchIndexTask.KEY_VAL_SEPARATOR + mime);
                }
            } catch (Exception e) {
                // TODO: handle exception
                logger.error(e.getMessage() + "Preview " + item.getViewFile().getPath() + " ("
                        + item.getViewFile().length() + " bytes)", e);
            }
        }

        if (zipFiles >= zipMaxFiles || zipLength >= zipMaxSize) {
            sendZipFile();
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
                return b[0];
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
