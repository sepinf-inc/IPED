package dpf.sp.gpinf.indexer.io;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.ClosedChannelException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.archivers.zip.ZipSplitReadOnlySeekableByteChannel;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.commons.io.IOUtils;

import dpf.sp.gpinf.indexer.util.SeekableFileInputStream;
import dpf.sp.gpinf.indexer.util.SeekableInputStreamFactory;
import iped3.io.SeekableInputStream;

public class ZIPInputStreamFactory extends SeekableInputStreamFactory implements Closeable {

    private static final int MAX_BYTES_CACHED = 1 << 27;

    private static final int MAX_FILES_CACHED = 1 << 9;

    private volatile ZipFile zip;

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

    private synchronized void init() throws IOException {
        if (zip == null) {
            File file = Paths.get(this.dataSource).toFile();
            int idx = file.getName().lastIndexOf('.');
            if (idx == -1) {
                throw new IOException("ZIP file must have extension!");
            }
            String namePrefix = file.getName().substring(0, idx);
            ArrayList<File> list = new ArrayList<>();
            int num = 0;
            while (true) {
                File segment = new File(file.getParentFile(), namePrefix + ".z" + String.format("%02d", ++num));
                if (segment.exists()) {
                    list.add(segment);
                } else {
                    break;
                }
            }
            if (list.isEmpty()) {
                zip = new ZipFile(file, "UTF-8", true, true);
            } else {
                zip = new ZipFile(ZipSplitReadOnlySeekableByteChannel.forFiles(file, list), file.getAbsolutePath(),
                        "UTF-8", true, true);
            }
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

        ZipArchiveEntry zae;
        try {
            if (zip == null) {
                init();
            }
            zae = zip.getEntry(path);
        } catch (IOException e1) {
            throw e1;
        }
        if (zae == null) {
            return new SeekableFileInputStream(new SeekableInMemoryByteChannel(new byte[0]));
        }
        InputStream is;
        // ZipFile.getInputStream(ze) isn't thread safe in commons compress 1.21 if
        // ignoreLocalFileHeader constructor flag is enabled
        synchronized (zip) {
            is = zip.getInputStream(zae);
        }
        try {
            if (zae.getSize() <= MAX_BYTES_CACHED) {
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
            if (zip != null) {
                zip.close();
                zip = null;
            }
            if (tmp != null)
                Files.delete(tmp);
            throw e;
        } finally {
            is.close();
        }
        if (bytes != null) {
            return new SeekableFileInputStream(new SeekableInMemoryByteChannel(bytes));
        }
        return new SeekableFileInputStream(tmp.toFile());
    }

    @Override
    public void close() throws IOException {
        if (zip != null) {
            zip.close();
            zip = null;
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
