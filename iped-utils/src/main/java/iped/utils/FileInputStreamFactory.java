package iped.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

import iped.io.SeekableInputStream;

public class FileInputStreamFactory extends SeekableInputStreamFactory {

    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().startsWith("windows");

    public FileInputStreamFactory(Path dataSource) {
        super(dataSource.toUri());
    }

    public File getFile(String subPath) {
        Path source = Paths.get(this.dataSource);
        try {
            return source.resolve(subPath).toFile();

        } catch (InvalidPathException e) {
            File file = new File(subPath);
            if (!file.isAbsolute()) {
                file = new File(source.toFile(), subPath);
            }
            if (IS_WINDOWS) {
                //this is a workaround to support cases where there are folders with trailing spaces on the name
                return new File("\\\\?\\" + file.getAbsolutePath());
            } else {
                return file;
            }
        }
    }

    @Override
    public SeekableInputStream getSeekableInputStream(String subPath) throws IOException {
        File file = getFile(subPath);
        if (file.isFile())
            return new SeekableFileInputStream(file);
        else
            return new EmptyInputStream();
    }

}
