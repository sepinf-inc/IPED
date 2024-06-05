package iped.utils;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.StandardOpenOption;

import iped.io.SeekableInputStream;

public class SeekableFileInputStream extends SeekableInputStream {

    private File file;
    private SeekableByteChannel sbc;
    private boolean closed = false;

    public SeekableFileInputStream(File file) throws IOException {
        this.file = file;
        this.sbc = FileChannel.open(file.toPath(), StandardOpenOption.READ);
    }

    public SeekableFileInputStream(SeekableByteChannel channel) {
        this.sbc = channel;
    }

    public File getFile() {
        return this.file;
    }

    @Override
    public int read(byte b[]) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte b[], int off, int len) throws IOException {
        checkIfClosed();
        ByteBuffer bb = ByteBuffer.wrap(b, off, len);
        return sbc.read(bb);
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
            return b[0] & 0xFF;
    }

    @Override
    public int available() throws IOException {
        checkIfClosed();
        long avail = sbc.size() - sbc.position();
        return (int) Math.min(avail, Integer.MAX_VALUE);
    }

    @Override
    public long skip(long n) throws IOException {
        checkIfClosed();
        long pos = sbc.position();
        long newPos = pos + n;
        long len = sbc.size();

        if (newPos > len)
            newPos = len;
        else if (newPos < 0)
            newPos = 0;

        sbc.position(newPos);

        return newPos - pos;
    }

    public void seek(long pos) throws IOException {
        checkIfClosed();
        sbc.position(pos);
    }

    private void checkIfClosed() throws IOException {
        if (closed) {
            throw new IOException("Stream already closed.");
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        sbc.close();
        // clear reference, possibly heavy one with bytes kept in memory
        this.sbc = null;
    }

    @Override
    public long position() throws IOException {
        checkIfClosed();
        return sbc.position();
    }

    @Override
    public long size() throws IOException {
        checkIfClosed();
        return sbc.size();
    }

}
