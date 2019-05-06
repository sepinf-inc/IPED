package iped3.io;

import java.io.IOException;
import java.nio.file.Path;

public interface ISeekableInputStreamFactory {
    
    public SeekableInputStream getSeekableInputStream(String identifier) throws IOException;
    
    public Path getDataSourcePath();

}
