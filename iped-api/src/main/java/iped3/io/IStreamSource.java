package iped3.io;

import java.io.File;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;

public interface IStreamSource {

    @Deprecated
    public SeekableInputStream getStream() throws IOException;

    public SeekableInputStream getSeekableInputStream() throws IOException;

    public SeekableByteChannel getSeekableByteChannel() throws IOException;

    public File getFile();

}
