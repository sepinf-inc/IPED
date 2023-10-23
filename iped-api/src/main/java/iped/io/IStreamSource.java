package iped.io;

import java.io.File;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;

public interface IStreamSource {

    public SeekableInputStream getSeekableInputStream() throws IOException;

    public SeekableByteChannel getSeekableByteChannel() throws IOException;

    public File getTempFile() throws IOException;

}
