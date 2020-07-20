package dpf.sp.gpinf.indexer.util;

import java.io.File;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;

import iped3.io.SeekableInputStream;

public interface StreamSourceOldDelete {

    public SeekableInputStream getStream() throws IOException;

    public SeekableByteChannel getSeekableByteChannel() throws IOException;

    public File getFile();

}
