package iped.io;

import java.io.IOException;
import java.net.URI;

public interface ISeekableInputStreamFactory {

    public SeekableInputStream getSeekableInputStream(String identifier) throws IOException;

    public URI getDataSourceURI();

    public default boolean returnsEmptyInputStream() {
        return false;
    }

}
