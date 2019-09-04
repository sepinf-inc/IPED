package dpf.sp.gpinf.indexer.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.ClosedChannelException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.commons.io.IOUtils;

import iped3.io.SeekableInputStream;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.FileHeader;

public class ZIPInputStreamFactory extends SeekableInputStreamFactory implements Closeable {

    private static int MAX_MEM_BYTES = 1 << 24;

    private ZipFile4j zip;

    public ZIPInputStreamFactory(Path dataSource) {
        super(dataSource);
    }

    private synchronized void init() throws ZipException {
        if (zip == null) {
            zip = new ZipFile4j(this.dataSource.toFile());
            /*
             * File file = dataSource.toFile(); File part1 = new
             * File(file.getAbsolutePath().substring(0,
             * file.getAbsolutePath().lastIndexOf('.')) + ".z01");
             * SequenceSeekableByteChannel ssbc = new
             * SequenceSeekableByteChannel(Files.newByteChannel(part1.toPath()),
             * Files.newByteChannel(dataSource)); zip = new ZipFile(ssbc);
             */
        }
    }

    @Override
    public SeekableInputStream getSeekableInputStream(String path) throws IOException {
        Path tmp = null;
        byte[] bytes = null;
        FileHeader zae;
        try {
            if (zip == null)
                init();
            zae = zip.getFileHeader(path);
        } catch (ZipException e1) {
            throw new IOException(e1);
        }
        try (InputStream is = zip.getInputStream(zae)) {
            if (zae.getUncompressedSize() <= MAX_MEM_BYTES) {
                bytes = IOUtils.toByteArray(is);
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
