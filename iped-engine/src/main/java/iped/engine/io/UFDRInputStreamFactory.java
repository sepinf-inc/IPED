package iped.engine.io;

import java.io.IOException;
import java.nio.file.Path;

import iped.io.SeekableInputStream;

public class UFDRInputStreamFactory extends ZIPInputStreamFactory {

    public static final String UFDR_PATH_PREFIX = "ufdr:///";

    public UFDRInputStreamFactory(Path dataSource) {
        super(dataSource);
    }

    @Override
    public SeekableInputStream getSeekableInputStream(String path) throws IOException {
        int idx = path.indexOf(UFDR_PATH_PREFIX);
        if (idx != -1) {
            path = path.substring(path.indexOf(UFDR_PATH_PREFIX) + UFDR_PATH_PREFIX.length());
        }
        return super.getSeekableInputStream(path);
    }

}
