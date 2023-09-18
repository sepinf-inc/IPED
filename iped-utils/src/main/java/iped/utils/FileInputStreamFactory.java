package iped.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import iped.io.SeekableInputStream;

public class FileInputStreamFactory extends SeekableInputStreamFactory {

    public FileInputStreamFactory(Path dataSource) {
        super(dataSource.toUri());
    }

    public File getFile(String subPath) {
        if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
            //this is a workaround to support cases where there are folders with trailing spaces on the name
            return new File("\\\\?\\" + new File(subPath).getAbsolutePath());
        } else {
            return new File(subPath);
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
