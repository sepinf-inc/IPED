package dpf.sp.gpinf.indexer.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

import iped3.io.SeekableInputStream;

public class SeekableByteChannelImpl implements SeekableByteChannel{
    
    private SeekableInputStream sis;
    private boolean closed = false;
    
    public SeekableByteChannelImpl(SeekableInputStream sis){
        this.sis = sis;
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public void close() throws IOException {
        sis.close();
        closed = true;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        byte[] buf = new byte[dst.remaining()];
        int read = sis.read(buf);
        if(read == -1)
            return -1;
        dst.put(buf, 0, read);
        return read;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        throw new IOException("Operation not supported"); //$NON-NLS-1$
    }

    @Override
    public long position() throws IOException {
        return sis.position();
    }

    @Override
    public SeekableByteChannel position(long newPosition) throws IOException {
        sis.seek(newPosition);
        return this;
    }

    @Override
    public long size() throws IOException {
        return sis.size();
    }

    @Override
    public SeekableByteChannel truncate(long size) throws IOException {
        throw new IOException("Operation not supported"); //$NON-NLS-1$
    }

}
