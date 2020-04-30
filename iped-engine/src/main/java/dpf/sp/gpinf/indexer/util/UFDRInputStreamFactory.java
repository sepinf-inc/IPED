package dpf.sp.gpinf.indexer.util;

import java.io.IOException;
import java.nio.file.Path;

import iped3.io.SeekableInputStream;

public class UFDRInputStreamFactory extends ZIPInputStreamFactory{
    
    public static final String UFDR_PATH_PREFIX = "ufdr:///";

    public UFDRInputStreamFactory(Path dataSource) {
        super(dataSource);
    }
    
    @Override
    public SeekableInputStream getSeekableInputStream(String path) throws IOException {
        String internalPath = path.substring(path.indexOf(UFDR_PATH_PREFIX) + UFDR_PATH_PREFIX.length());
        return super.getSeekableInputStream(internalPath);
    }

}
