package iped3.io;

import java.io.IOException;
import java.net.URI;

public interface ISeekableInputStreamFactory {

    public SeekableInputStream getSeekableInputStream(String identifier) throws IOException;

    public URI getDataSourceURI();

    public default void deleteItemInDataSource(String identifier) throws IOException {
        // do nothing by default
    }

}
