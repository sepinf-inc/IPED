package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;

import dpf.sp.gpinf.indexer.util.SeekableInputStreamFactory;
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

public class MinIOTask extends AbstractTask {

    private MinioClient minioClient;
    private MinIOInputInputStreamFactory inputStreamFactory;

    private static String server = "http://127.0.0.1:9000";
    private static String accessKey = "minioadmin";
    private static String secretKey = "minioadmin";
    private static String bucket = "dev-test";

    @Override
    public void init(Properties confParams, File confDir) throws Exception {

        minioClient = MinioClient.builder().endpoint(server).credentials(accessKey, secretKey).build();
        inputStreamFactory = new MinIOInputInputStreamFactory(null);

        // Check if the bucket already exists.
        boolean isExist = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!isExist) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }

    }

    @Override
    public void finish() throws Exception {
        
    }

    @Override
    protected void process(IItem item) throws Exception {

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

        if (exists) {
            updateDataSource(item, hash);
            return;
        }

        try {
            // Upload the file to the bucket with putObject
            try (InputStream is = item.getBufferedStream()) {
                minioClient.putObject(PutObjectArgs.builder().bucket(bucket).object(hash)
                        .stream(is, item.getLength(), -1).contentType(item.getMediaType().toString()).build());
            }
            updateDataSource(item, hash);

        } catch (Exception e) {
            System.out.println("Error occurred uploading file " + item.getPath());
            e.printStackTrace();
        }

    }

    private void updateDataSource(IItem item, String id) {
        item.setInputStreamFactory(inputStreamFactory);
        item.setIdInDataSource(id);
    }

    public static class MinIOInputInputStreamFactory extends SeekableInputStreamFactory {

        private MinioClient minioClient = null;

        public MinIOInputInputStreamFactory(Path dataSource) {
            super(Paths.get("minio-test"));
            minioClient = MinioClient.builder().endpoint(server).credentials(accessKey, secretKey).build();
        }

        @Override
        public SeekableInputStream getSeekableInputStream(String identifier) throws IOException {
            return new MinIOSeekableInputStream(minioClient, identifier);
        }

    }

    public static class MinIOSeekableInputStream extends SeekableInputStream {

        private MinioClient minioClient;
        private String id;
        private Long size;
        private long pos = 0, markPos;
        private InputStream is;

        public MinIOSeekableInputStream(MinioClient minioClient, String id) {
            this.minioClient = minioClient;
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
                try {
                    size = minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(id).build()).length();
                } catch (InvalidKeyException | ErrorResponseException | IllegalArgumentException
                        | InsufficientDataException | InternalException | InvalidBucketNameException
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

            } catch (InvalidKeyException | ErrorResponseException | IllegalArgumentException | InsufficientDataException
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
