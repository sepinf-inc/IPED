package dpf.sp.gpinf.indexer.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.ClosedChannelException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.commons.io.IOUtils;

import iped3.io.SeekableInputStream;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.FileHeader;

public class ZIPInputStreamFactory extends SeekableInputStreamFactory implements Closeable {

    private static final int MAX_BYTES_CACHED = 1 << 27;

    private static final int MAX_FILES_CACHED = 1 << 9;

    private ZipFile4j zip;

    private int bytesCached = 0;

    private Map<String, byte[]> bytesCache = new LinkedHashMap<String, byte[]>(128, 0.75f, true);

    private void removeEldestBytes() {
        synchronized (bytesCache) {
            if (bytesCached > MAX_BYTES_CACHED) {
                Iterator<byte[]> i = bytesCache.values().iterator();
                while (bytesCached > MAX_BYTES_CACHED && i.hasNext()) {
                    byte[] eldest = i.next();
                    i.remove();
                    bytesCached -= eldest.length;
                }
            }
        }
    }

    private Map<String, Path> filesCache = new LinkedHashMap<String, Path>(128, 0.75f, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Path> eldest) {
            boolean remove = this.size() > MAX_FILES_CACHED;
            if (remove) {
                try {
                    Files.deleteIfExists(eldest.getValue());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return remove;
        }
    };

    public ZIPInputStreamFactory(Path dataSource) {
        super(dataSource.toUri());
    }

    private synchronized void init() throws ZipException {
        if (zip == null) {
            zip = new ZipFile4j(Paths.get(this.dataSource).toFile());
        }
    }

    @Override
    public SeekableInputStream getSeekableInputStream(String path) throws IOException {
        Path tmp = null;
        byte[] bytes = null;
        synchronized (bytesCache) {
            bytes = bytesCache.get(path);
        }
        if (bytes != null) {
            return new SeekableFileInputStream(new SeekableInMemoryByteChannel(bytes));
        }
        synchronized (filesCache) {
            tmp = filesCache.get(path);
        }
        if (tmp != null) {
            return new SeekableFileInputStream(tmp.toFile());
        }

        FileHeader zae;
        try {
            if (zip == null)
                init();
            zae = zip.getFileHeader(path);
        } catch (ZipException e1) {
            throw new IOException(e1);
        }
        if (zae == null) {
            return new SeekableFileInputStream(new SeekableInMemoryByteChannel(new byte[0]));
        }
        try (InputStream is = zip.getInputStream(zae)) {
            if (zae.getUncompressedSize() <= MAX_BYTES_CACHED) {
                bytes = IOUtils.toByteArray(is);
                synchronized (bytesCache) {
                    bytesCache.put(path, bytes);
                    bytesCached += bytes.length;
                    removeEldestBytes();
                }
            } else {
                tmp = Files.createTempFile("zip-stream", null);
                Files.copy(is, tmp, StandardCopyOption.REPLACE_EXISTING);
                synchronized (filesCache) {
                    filesCache.put(path, tmp);
                }
            }
        } catch (ClosedChannelException e) {
            // if(zip != null) zip.close();
            zip = null;
            if (tmp != null)
                Files.delete(tmp);
            throw e;
        } catch (ZipException e1) {
            throw new IOException(e1);
        }
        if (bytes != null) {
            return new SeekableFileInputStream(new SeekableInMemoryByteChannel(bytes));
        }
        return new SeekableFileInputStream(tmp.toFile());
    }

    @Override
    public void close() throws IOException {
        if (zip != null) {
            // is not closeable...
            // zip.close();
        }
        synchronized (bytesCache) {
            bytesCache.clear();
        }
        Path[] paths;
        synchronized (filesCache) {
            paths = filesCache.values().toArray(new Path[0]);
            filesCache.clear();
        }
        IOException exception = null;
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                if (exception == null) {
                    exception = new IOException("Fail to delete file(s)");
                }
                exception.addSuppressed(e);
            }
        }
        if (exception != null) {
            throw exception;
        }
    }

}
