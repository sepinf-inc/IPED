package iped.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import iped.io.SeekableInputStream;

public class FileInputStreamFactory extends SeekableInputStreamFactory {

    public FileInputStreamFactory(Path dataSource) {
        super(dataSource.toUri());
    }

    public Path getPath(String subPath) {
        Path source = Paths.get(this.dataSource);
        return source.resolve(subPath);
    }

    @Override
    public SeekableInputStream getSeekableInputStream(String subPath) throws IOException {
        File file = getPath(subPath).toFile();
        if (file.isFile())
            return new SeekableFileInputStream(file);
        else
            return new EmptyInputStream();
    }

}
