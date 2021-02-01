package dpf.sp.gpinf.indexer.util;

import java.io.IOException;
import java.net.URI;

import iped3.io.ISeekableInputStreamFactory;
import iped3.io.SeekableInputStream;

public abstract class SeekableInputStreamFactory implements ISeekableInputStreamFactory {

    protected URI dataSource;

    public SeekableInputStreamFactory(URI dataSource) {
        this.dataSource = dataSource;
    }

    public boolean checkIfDataSourceExists() {
        return true;
    }

    public abstract SeekableInputStream getSeekableInputStream(String identifier) throws IOException;

    public URI getDataSourceURI() {
        return dataSource;
    }

    public void setDataSourceURI(URI dataSource) {
        this.dataSource = dataSource;
    }

}
