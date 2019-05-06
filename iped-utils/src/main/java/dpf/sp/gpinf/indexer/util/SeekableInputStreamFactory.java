package dpf.sp.gpinf.indexer.util;

import java.io.IOException;
import java.nio.file.Path;

import iped3.io.ISeekableInputStreamFactory;
import iped3.io.SeekableInputStream;

public abstract class SeekableInputStreamFactory implements ISeekableInputStreamFactory{
    
    protected Path dataSource;
    
    public SeekableInputStreamFactory(Path dataSource) {
        this.dataSource = dataSource;
    }
    
    public abstract SeekableInputStream getSeekableInputStream(String identifier) throws IOException;
    
    public Path getDataSourcePath() {
        return dataSource;
    }

}
