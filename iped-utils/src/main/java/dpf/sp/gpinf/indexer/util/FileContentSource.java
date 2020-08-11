package dpf.sp.gpinf.indexer.util;

import java.io.File;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;

import iped3.io.IStreamSource;
import iped3.io.SeekableInputStream;

public class FileContentSource implements IStreamSource {

    private File file;

    public FileContentSource(File file) {
        this.file = file;
    }

    @Deprecated
    public SeekableInputStream getStream() throws IOException {
        return getSeekableInputStream();
    }

    @Override
    public SeekableInputStream getSeekableInputStream() throws IOException {
        return new SeekableFileInputStream(file);
    }

    @Override
    public SeekableByteChannel getSeekableByteChannel() throws IOException {
        return new SeekableByteChannelImpl(getSeekableInputStream());
    }

    @Override
    public File getFile() {
        return file;
    }

}
