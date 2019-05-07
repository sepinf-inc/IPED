package dpf.sp.gpinf.indexer.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

public class SequenceSeekableByteChannel implements SeekableByteChannel {

    private SeekableByteChannel[] channels;
    private long pos = 0;
    private int channel = 0;
    private boolean closed = false;

    public SequenceSeekableByteChannel(SeekableByteChannel... channels) {
        this.channels = channels;
    }

    @Override
    public boolean isOpen() {
        return closed;
    }

    @Override
    public void close() throws IOException {
        for (SeekableByteChannel c : channels)
            c.close();
        closed = true;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        int read = channels[channel].read(dst);
        if (read == -1) {
            if (channel == channels.length - 1)
                return -1;
            else {
                channel++;
                return 0;
            }
        } else
            pos += read;

        return read;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        throw new IOException("Unsupported Operation");
    }

    @Override
    public long position() throws IOException {
        return pos;
    }

    @Override
    public SeekableByteChannel position(long newPosition) throws IOException {
        pos = newPosition;
        long prevSize = 0;
        channel = 0;
        while ((prevSize += channels[channel].size()) <= pos)
            channel++;
        prevSize -= channels[channel].size();
        channels[channel].position(newPosition - prevSize);
        return this;
    }

    @Override
    public long size() throws IOException {
        long total = 0;
        for (SeekableByteChannel c : channels)
            total += c.size();
        return total;
    }

    @Override
    public SeekableByteChannel truncate(long size) throws IOException {
        throw new IOException("Unsupported Operation");
    }

}
