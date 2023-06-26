package iped.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SeekableByteChannel;

/**
 * Read only implementation of SeekableByteChannel over a RandomAccessFile.
 * 
 * @author Nassif
 *
 */
public class ReadOnlyRAFSeekableByteChannel implements SeekableByteChannel {

    private RandomAccessFile raf;

    public ReadOnlyRAFSeekableByteChannel(File file) throws FileNotFoundException {
        this.raf = new RandomAccessFile(file, "r");
    }

    public ReadOnlyRAFSeekableByteChannel(RandomAccessFile raf) {
        this.raf = raf;
    }

    @Override
    public synchronized boolean isOpen() {
        return raf != null;
    }

    private void checkIfOpen() throws ClosedChannelException {
        if (!isOpen()) {
            throw new ClosedChannelException();
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (raf != null) {
            raf.close();
            raf = null;
        }
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        checkIfOpen();
        if (dst.hasArray()) {
            int pos = dst.position();
            int read = raf.read(dst.array(), dst.arrayOffset() + pos, dst.remaining());
            if (read > 0) {
                dst.position(pos + read);
            }
            return read;
        } else {
            byte[] buf = new byte[dst.remaining()];
            int read = raf.read(buf);
            if (read > 0) {
                dst.put(buf, 0, read);
            }
            return read;
        }
    }

    @Override
    public long position() throws IOException {
        checkIfOpen();
        return raf.getFilePointer();
    }

    @Override
    public SeekableByteChannel position(long newPosition) throws IOException {
        checkIfOpen();
        if (newPosition < 0) {
            throw new IllegalArgumentException("New position must be non-negative, it was " + newPosition);
        }
        raf.seek(newPosition);
        return this;
    }

    @Override
    public long size() throws IOException {
        checkIfOpen();
        return raf.length();
    }

    @Override
    public int write(ByteBuffer src) {
        throw new UnsupportedOperationException("Write operation not supported.");
    }

    @Override
    public SeekableByteChannel truncate(long size) {
        throw new UnsupportedOperationException("Truncate operation not supported.");
    }

}
