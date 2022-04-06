package iped3.io;

import java.io.IOException;
import java.io.InputStream;

public abstract class SeekableInputStream extends InputStream {

    private long markedPos = -1;

    public abstract void seek(long pos) throws IOException;

    public abstract long position() throws IOException;

    public abstract long size() throws IOException;

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public void mark(int readLimit) {
        try {
            markedPos = this.position();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void reset() throws IOException {
        if (markedPos == -1) {
            throw new IOException("stream must be marked before reset");
        }
        this.seek(markedPos);
    }

}
