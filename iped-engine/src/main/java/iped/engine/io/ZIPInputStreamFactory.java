package iped.engine.io;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import java.util.zip.CRC32;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.archivers.zip.ZipSplitReadOnlySeekableByteChannel;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import iped.io.SeekableInputStream;
import iped.utils.ReadOnlyRAFSeekableByteChannel;
import iped.utils.SeekableFileInputStream;
import iped.utils.SeekableInputStreamFactory;

public class ZIPInputStreamFactory extends SeekableInputStreamFactory implements Closeable {

    private static final Logger logger = LogManager.getLogger(ZIPInputStreamFactory.class);

    private static final int MAX_BYTES_CACHED = 1 << 27;

    private static final int MAX_FILES_CACHED = 1 << 9;

    private static final int UFDR_BUF_SIZE = 1 << 16;

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
            ArrayList<SeekableByteChannel> channels = new ArrayList<>();
            int num = 0;
            // search for ufdr parts
            while (true) {
                File segment = new File(file.getParentFile(), namePrefix + ".z" + String.format("%02d", ++num));
                if (segment.exists()) {
                    RandomAccessFile raf = new RandomAccessFile(segment, "r");
                    channels.add(new ReadOnlyRAFSeekableByteChannel(raf));
                } else {
                    break;
                }
            }
            // main ufdr should be the last one
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            channels.add(new ReadOnlyRAFSeekableByteChannel(raf));

            if (channels.size() == 1) {
                sbc = channels.get(0);
            } else {
                sbc = ZipSplitReadOnlySeekableByteChannel.forOrderedSeekableByteChannels(channels.toArray(new SeekableByteChannel[0]));
            }

            zip = ZipFile.builder().setFile(file).setSeekableByteChannel(sbc).setCharset(StandardCharsets.UTF_8)
                    .setUseUnicodeExtraFields(true).setIgnoreLocalFileHeader(true).setBufferSize(UFDR_BUF_SIZE).get();
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
            if (tmp != null) {
                try {
                    return new SeekableFileInputStream(tmp.toFile());
                } catch (NoSuchFileException e) {
                    // Could have been deleted by Item.dispose()
                    filesCache.remove(path);
                }
            }
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
                    byte[] buf = new byte[UFDR_BUF_SIZE];
                    CRC32 crc32 = new CRC32();
                    if (zae.getSize() <= MAX_BYTES_CACHED) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        int read;
                        while ((read = is.read(buf, 0, buf.length)) >= 0) {
                            if (canceled.get()) {
                                return null;
                            }
                            baos.write(buf, 0, read);
                            crc32.update(buf, 0, read);
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
                            while (!canceled.get() && (read = is.read(buf, 0, buf.length)) >= 0) {
                                out.write(buf, 0, read);
                                crc32.update(buf, 0, read);
                            }
                        } finally {
                            if (canceled.get()) {
                                Files.delete(tmp);
                                return null;
                            }
                        }
                        synchronized (filesCache) {
                            filesCache.put(path, tmp);
                        }
                    }
                    long value = crc32.getValue();
                    if (value != zae.getCrc()) {
                        logger.error("CRC32 inconsistency! File: " + zae.getName() + ", Length: " + zae.getSize()
                                + ", Original: " + Long.toHexString(zae.getCrc()) + " != Calulated: "
                                + Long.toHexString(value));
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
        // Closing the ZipFile will close the channel, all sub channels and RAFs
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
