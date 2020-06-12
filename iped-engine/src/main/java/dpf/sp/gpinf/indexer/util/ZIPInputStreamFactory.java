package dpf.sp.gpinf.indexer.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.ClosedChannelException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.commons.io.IOUtils;

import iped3.io.SeekableInputStream;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.FileHeader;

public class ZIPInputStreamFactory extends SeekableInputStreamFactory implements Closeable {

    private static int MAX_MEM_BYTES = 1 << 24;
    
    private static int MAX_CACHE = 1 << 28;
    
    private static int cacheSize = 0;
    
    //TODO cache must be per evience/ufdr to not mix content
    private static Map<String, byte[]> cache = new LinkedHashMap<String, byte[]>(128, 0.75f, true) {

        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            //return this.size() > MAX_CACHE;
            return false;
        }
    };

    private ZipFile4j zip;

    public ZIPInputStreamFactory(Path dataSource) {
        super(dataSource);
    }

    private synchronized void init() throws ZipException {
        if (zip == null) {
            zip = new ZipFile4j(this.dataSource.toFile());
        }
    }

    @Override
    public SeekableInputStream getSeekableInputStream(String path) throws IOException {
        Path tmp = null;
        byte[] bytes = null;
        synchronized(cache) {
            bytes = cache.get(path);
        }
        if(bytes != null) return new SeekableFileInputStream(new SeekableInMemoryByteChannel(bytes));
        
        FileHeader zae;
        try {
            if (zip == null)
                init();
            zae = zip.getFileHeader(path);
        } catch (ZipException e1) {
            throw new IOException(e1);
        }
        if(zae == null) {
            return  new SeekableFileInputStream(new SeekableInMemoryByteChannel(new byte[0]));
        }
        try (InputStream is = zip.getInputStream(zae)) {
            if (zae.getUncompressedSize() <= MAX_MEM_BYTES) {
                bytes = IOUtils.toByteArray(is);
                synchronized(cache) {
                    cache.put(path, bytes);
                    cacheSize += bytes.length;
                    if(cacheSize > MAX_CACHE) {
                        Iterator<byte[]> i = cache.values().iterator();
                        while(cacheSize > MAX_CACHE && i.hasNext()){
                            byte[] eldest = i.next();
                            i.remove();
                            cacheSize -= eldest.length;
                        }
                    }
                }
            } else {
                tmp = Files.createTempFile("zip-stream", null);
                Files.copy(is, tmp, StandardCopyOption.REPLACE_EXISTING);
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
        final Path finalTmp = tmp;
        return new SeekableFileInputStream(finalTmp.toFile()) {
            @Override
            public void close() throws IOException {
                super.close();
                Files.delete(finalTmp);
            }
        };
    }

    @Override
    public void close() throws IOException {
        // if(zip != null) zip.close();
    }

}
