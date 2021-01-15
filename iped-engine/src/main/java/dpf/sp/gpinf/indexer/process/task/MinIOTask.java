package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.util.SeekableInputStreamFactory;
import dpf.sp.gpinf.indexer.util.UTF8Properties;
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

    private static final String CONF_FILE = "MinIOConfig.txt";
    private static final String ENABLE_KEY = "enable";
    private static final String HOST_KEY = "host";
    private static final String PORT_KEY = "port";
    private static final String CMD_LINE_KEY = "minio";
    private static final String ACCESS_KEY = "accesskey";
    private static final String SECRET_KEY = "secretkey";
    private static final String BUCKET_KEY = "bucket";

    private static boolean enabled = false;
    private static String server = "http://127.0.0.1:9000";
    private static String accessKey = "minioadmin";
    private static String secretKey = "minioadmin";
    private static String bucket;

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
        bucket = output.getParentFile().getName().toLowerCase();

        CmdLineArgs args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
        String cmdFields = args.getExtraParams().get(CMD_LINE_KEY);

        if (cmdFields != null) {
            parseCmdLineFields(cmdFields);
        }

        minioClient = MinioClient.builder().endpoint(server).credentials(accessKey, secretKey).build();
        inputStreamFactory = new MinIOInputInputStreamFactory(null);

        // Check if the bucket already exists.
        boolean isExist = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!isExist) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }

    }

    private void parseCmdLineFields(String cmdFields) {
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

    @Override
    protected void process(IItem item) throws Exception {

        if (caseData.isIpedReport() || !item.isToAddToCase())
            return;

        String hash = item.getHash();
        if (hash == null || hash.isEmpty())
            return;

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

        String uri = buildUri(server, bucket, hash);

        if (exists) {
            updateDataSource(item, uri);
            return;
        }

        try {
            // Upload the file to the bucket with putObject
            try (InputStream is = item.getBufferedStream()) {
                minioClient.putObject(PutObjectArgs.builder().bucket(bucket).object(hash)
                        .stream(is, item.getLength(), -1).contentType(item.getMediaType().toString()).build());

            }
            updateDataSource(item, uri);

        } catch (Exception e) {
            logger.error("Error when uploading object " + item.getPath(), e);
        }

    }

    private static String buildUri(String server, String bucket, String hash) {
        return server + "/" + bucket + "/" + hash;
    }

    private static String[] parseUri(String uri) {
        ArrayList<String> parts = new ArrayList<>();
        int end = uri.length();
        for (int i = uri.length() - 1; i > 0; i--) {
            if (uri.charAt(i) == '/') {
                parts.add(0, uri.substring(i + 1, end));
                end = i;
            }
            if (parts.size() == 2) {
                parts.add(0, uri.substring(0, i));
                break;
            }
        }
        return parts.toArray(new String[3]);
    }

    private void updateDataSource(IItem item, String id) {
        if (item.isSubItem()) {
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

        public MinIOInputInputStreamFactory(Path dataSource) {
            super(Paths.get("minio-storage"));
        }

        public boolean checkIfDataSourceExists() {
            return false;
        }

        @Override
        public SeekableInputStream getSeekableInputStream(String identifier) throws IOException {
            String[] parts = parseUri(identifier);
            String server = parts[0];
            String bucket = parts[1];
            String hash = parts[2];

            MinioClient minioClient = map.get(server);
            if (minioClient == null) {
                minioClient = MinioClient.builder().endpoint(server).credentials(accessKey, secretKey).build();
                map.put(server, minioClient);
            }
            return new MinIOSeekableInputStream(minioClient, bucket, hash);
        }

    }

    public static class MinIOSeekableInputStream extends SeekableInputStream {

        private MinioClient minioClient;
        private String bucket, id;
        private Long size;
        private long pos = 0, markPos;
        private InputStream is;

        public MinIOSeekableInputStream(MinioClient minioClient, String bucket, String hash) {
            this.minioClient = minioClient;
            this.bucket = bucket;
            this.id = hash;
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
                        | InvalidBucketNameException
                        | InvalidResponseException | NoSuchAlgorithmException | ServerException
                        | XmlParserException e) {
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

            } catch (InvalidKeyException | ErrorResponseException | InsufficientDataException
                    | InternalException | InvalidBucketNameException | InvalidResponseException
                    | NoSuchAlgorithmException | ServerException | XmlParserException e) {
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
