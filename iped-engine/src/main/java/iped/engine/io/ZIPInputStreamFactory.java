package iped.engine.io;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.archivers.zip.ZipSplitReadOnlySeekableByteChannel;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.commons.lang3.tuple.Pair;

import iped.io.SeekableInputStream;
import iped.utils.SeekableFileInputStream;
import iped.utils.SeekableInputStreamFactory;

public class ZIPInputStreamFactory extends SeekableInputStreamFactory implements Closeable {

    private static final int MAX_BYTES_CACHED = 1 << 27;

    private static final int MAX_FILES_CACHED = 1 << 9;

    private volatile ZipFile zip;

    private SeekableByteChannel sbc;

    private int bytesCached = 0;

    private Map<String, byte[]> bytesCache = new LinkedHashMap<String, byte[]>(128, 0.75f, true);

    private final ExecutorService executor = Executors.newCachedThreadPool();

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
                sbc = Files.newByteChannel(file.toPath(), StandardOpenOption.READ);
            } else {
                sbc = ZipSplitReadOnlySeekableByteChannel.forFiles(file, list);
            }
            zip = new ZipFile(sbc, file.getAbsolutePath(), "UTF-8", true, true);
        }
    }

    public boolean entryExists(String path) {
        if (zip == null) {
            try {
                init();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return zip.getEntry(path) != null;
    }

    public long getEntrySize(String path) {
        if (zip == null) {
            try {
                init();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        ZipArchiveEntry zae = zip.getEntry(path);
        if (zae != null) {
            return zae.getSize();
        }
        return -1;
    }

    @SuppressWarnings("resource")
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
        AtomicBoolean canceled = new AtomicBoolean(false);
        // see #1199: call in background to avoid closing the channel if interrupted
        Future<Pair<Path, byte[]>> future = executor.submit(new Callable<Pair<Path, byte[]>>() {
            @Override
            public Pair<Path, byte[]> call() throws Exception {
                Path tmp = null;
                byte[] bytes = null;
                InputStream is = null;
                try {
                    // ZipFile.getInputStream(ze) isn't thread safe as of COMPRESS 1.21 if ZipFile
                    // 'ignoreLocalFileHeader' constructor flag is enabled. We must synchronize on
                    // SeekableByteChannel used in constructor (COMPRESS 1.21 specific!), otherwise
                    // this won't work with splitted archives with COMPRESS 1.21, see COMPRESS-618
                    synchronized (sbc) {
                        is = zip.getInputStream(zae);
                    }
                    if (zae.getSize() <= MAX_BYTES_CACHED) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        int read;
                        byte[] buf = new byte[8192];
                        while (!canceled.get() && (read = is.read(buf, 0, buf.length)) >= 0) {
                            baos.write(buf, 0, read);
                        }
                        bytes = baos.toByteArray();
                        baos = null;
                        synchronized (bytesCache) {
                            bytesCache.put(path, bytes);
                            bytesCached += bytes.length;
                            removeEldestBytes();
                        }
                    } else {
                        tmp = Files.createTempFile("zip-stream", null);
                        try (OutputStream out = Files.newOutputStream(tmp)) {
                            int read;
                            byte[] buf = new byte[8192];
                            while (!canceled.get() && (read = is.read(buf, 0, buf.length)) >= 0) {
                                out.write(buf, 0, read);
                            }
                        }
                        synchronized (filesCache) {
                            filesCache.put(path, tmp);
                        }
                    }
                    return Pair.of(tmp, bytes);

                } catch (ClosedChannelException e) {
                    if (zip != null) {
                        zip.close();
                        zip = null;
                    }
                    if (tmp != null) {
                        Files.delete(tmp);
                    }
                    throw e;
                } finally {
                    if (is != null) {
                        is.close();
                    }
                }
            }

        });
        Pair<Path, byte[]> result;
        try {
            result = future.get();
        } catch (InterruptedException | ExecutionException e) {
            canceled.set(true);
            throw new IOException(e);
        }
        bytes = result.getRight();
        if (bytes != null) {
            return new SeekableFileInputStream(new SeekableInMemoryByteChannel(bytes));
        }
        tmp = result.getLeft();
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
        executor.shutdown();
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
