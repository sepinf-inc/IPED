package iped.utils;

import java.io.File;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

import iped.data.IItemReader;
import iped.io.IStreamSource;
import iped.io.SeekableInputStream;

public class PreviewStreamSource implements IStreamSource {

    private IItemReader item;
    private File tempFile;

    public PreviewStreamSource(IItemReader item) {
        this.item = item;
    }

    @Override
    public SeekableInputStream getSeekableInputStream() throws IOException {
        try {
            return item.getPreviewSeekeableInputStream();
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public SeekableByteChannel getSeekableByteChannel() throws IOException {
        return new SeekableByteChannelImpl(getSeekableInputStream());
    }

    @Override
    public File getTempFile() throws IOException {
        if (tempFile == null) {
            try (SeekableInputStream input = item.getPreviewSeekeableInputStream()) {
                if (input instanceof SeekableFileInputStream) {
                    tempFile = ((SeekableFileInputStream) input).getFile();
                } else {
                    Path tmp = Files.createTempFile("preview-", item.getPreviewExt());
                    Files.copy(input, tmp);
                    tempFile = tmp.toFile();
                    tempFile.deleteOnExit();
                }
            } catch (SQLException e) {
                throw new IOException(e);
            }
        }
        return tempFile;
    }
}
