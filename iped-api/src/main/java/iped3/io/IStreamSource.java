package iped3.io;

import java.io.File;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;

public interface IStreamSource {

    public SeekableInputStream getStream() throws IOException;

    public SeekableByteChannel getSeekableByteChannel() throws IOException;

    public File getFile();

}
