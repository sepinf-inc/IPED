package dpf.sp.gpinf.indexer.process.task;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.util.SeekableInputStreamFactory;
import dpf.sp.gpinf.indexer.util.UTF8Properties;
import dpf.sp.gpinf.network.util.ProxySever;
import gpinf.dev.data.Item;
import io.minio.BucketExistsArgs;
import io.minio.ErrorCode;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.ObjectStat;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidBucketNameException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import iped3.ICaseData;
import iped3.IItem;
import iped3.io.SeekableInputStream;

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

    private static final int FOLDER_LEVELS = 4;
    private static final String CONF_FILE = "MinIOConfig.txt";
    private static final String ENABLE_KEY = "enable";
    private static final String HOST_KEY = "host";
    private static final String PORT_KEY = "port";
    private static final String CMD_LINE_KEY = "MinioCredentials";
    private static final String ACCESS_KEY = "accesskey";
    private static final String SECRET_KEY = "secretkey";
    private static final String BUCKET_KEY = "bucket";

    private static boolean enabled = false;
    private static String server = "http://127.0.0.1:9000";
    private static String accessKey;
    private static String secretKey;
    private static String bucket = null;

    private static Tika tika;

    private MinioClient minioClient;
    private MinIOInputInputStreamFactory inputStreamFactory;

    @Override
    public void init(Properties confParams, File confDir) throws Exception {

        File config = new File(confDir, CONF_FILE);
        UTF8Properties props = new UTF8Properties();
        props.load(config);

        enabled = Boolean.valueOf(props.getProperty(ENABLE_KEY));

        if (!enabled) {
            return;
        }

        String host = props.getProperty(HOST_KEY);
        String port = props.getProperty(PORT_KEY);

        server = host + ":" + port;

        // case name is default bucket name
        if (bucket == null) {
            bucket = output.getParentFile().getName().toLowerCase();
        }
        loadCredentials(caseData);

        minioClient = MinioClient.builder().endpoint(server).credentials(accessKey, secretKey).build();
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
        return enabled;
    }

    public static boolean isTaskEnabled() {
        return enabled;
    }

    @Override
    public void finish() throws Exception {

    }

    private String insertItem(String hash, InputStream is, long length, String mediatype, boolean preview)
            throws Exception {
        boolean exists = false;
        try {
            ObjectStat stat = minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(hash).build());
            exists = true;

        } catch (ErrorResponseException e) {
            ErrorCode code = e.errorResponse().errorCode();
            if (code != ErrorCode.NO_SUCH_OBJECT && code != ErrorCode.NO_SUCH_KEY) {
                throw e;
            }
        }

        String bucketPath = buildPath(hash);
        // if preview saves in a preview folder
        if (preview) {
            bucketPath = "preview/" + hash;
        }
        String fullPath = bucket + "/" + bucketPath;

        if (exists) {
            return fullPath;
        }

        // create directory structure
        if (FOLDER_LEVELS > 0) {
            String folder = bucketPath.substring(0, FOLDER_LEVELS * 2);
            minioClient.putObject(PutObjectArgs.builder().bucket(bucket).object(folder)
                    .stream(new ByteArrayInputStream(new byte[0]), 0, -1).build());
        }

        try {
            minioClient.putObject(PutObjectArgs.builder().bucket(bucket).object(bucketPath).stream(is, length, -1)
                    .contentType(mediatype).build());

            return fullPath;

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
    protected void process(IItem item) throws Exception {

        if (caseData.isIpedReport() || !item.isToAddToCase())
            return;

        String hash = item.getHash();
        if (hash == null || hash.isEmpty() || item.getLength() == null)
            return;

        // disable blocking proxy possibly enabled by HtmlViewer
        ProxySever.get().disable();

        try (InputStream is = item.getBufferedStream()) {
            String fullPath = insertItem(hash, is, item.getLength(), item.getMediaType().toString(), false);
            if (fullPath != null) {
                updateDataSource(item, fullPath);
            }
        } catch (Exception e) {
            // TODO: handle exception
            logger.error(e.getMessage() + "File " + item.getPath() + " (" + item.getLength() + " bytes)", e);
        }
        if (item.getViewFile() != null) {
            try (InputStream is = new FileInputStream(item.getViewFile())) {
                String fullPath = insertItem(hash, is, item.getViewFile().length(),
                        getMimeType(item.getViewFile().getName()), true);
                if (fullPath != null) {
                    item.getMetadata().add(ElasticSearchIndexTask.PREVIEW_IN_DATASOURCE,
                            "idInDataSource" + ElasticSearchIndexTask.KEY_VAL_SEPARATOR + fullPath);
                    item.getMetadata().add(ElasticSearchIndexTask.PREVIEW_IN_DATASOURCE, "type"
                            + ElasticSearchIndexTask.KEY_VAL_SEPARATOR + getMimeType(item.getViewFile().getName()));
                }
            } catch (Exception e) {
                // TODO: handle exception
                logger.error(e.getMessage() + "Preview " + item.getViewFile().getPath() + " ("
                        + item.getViewFile().length() + " bytes)", e);
            }
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

    private void updateDataSource(IItem item, String id) {
        if (item.isSubItem()) {
            // deletes local sqlite content after sent to minio
            item.setDeleteFile(true);
            ((Item) item).dispose(false);
        }

        item.setInputStreamFactory(inputStreamFactory);
        item.setIdInDataSource(id);
        item.setFile(null);
        item.setExportedFile(null);
        item.setFileOffset(-1);
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
        private long pos = 0, markPos;
        private InputStream is;

        public MinIOSeekableInputStream(MinioClient minioClient, String bucket, String id) {
            this.minioClient = minioClient;
            this.bucket = bucket;
            this.id = id;
            // disable blocking proxy possibly enabled by HtmlViewer
            ProxySever.get().disable();
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
                try {
                    size = minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(id).build()).length();
                } catch (InvalidKeyException | ErrorResponseException | InsufficientDataException | InternalException
                        | InvalidBucketNameException | InvalidResponseException | NoSuchAlgorithmException
                        | ServerException | XmlParserException e) {
                    throw new IOException(e);
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
                return minioClient.getObject(bucket, id, pos);

            } catch (InvalidKeyException | ErrorResponseException | InsufficientDataException | InternalException
                    | InvalidBucketNameException | InvalidResponseException | NoSuchAlgorithmException | ServerException
                    | XmlParserException e) {
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
        public boolean markSupported() {
            return true;
        }

        @Override
        public void mark(int mark) {
            markPos = pos;
        }

        @Override
        public void reset() throws IOException {
            pos = markPos;
            if (is != null) {
                is.close();
                is = getInputStream(pos);
            }
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
