package iped3.io;

import java.io.IOException;
import java.io.InputStream;

public abstract class SeekableInputStream extends InputStream {

    public abstract void seek(long pos) throws IOException;

    public abstract long position() throws IOException;

    public abstract long size() throws IOException;

}
