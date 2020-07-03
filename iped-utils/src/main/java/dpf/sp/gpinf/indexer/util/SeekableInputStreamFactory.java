package dpf.sp.gpinf.indexer.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import iped3.io.ISeekableInputStreamFactory;
import iped3.io.SeekableInputStream;

public abstract class SeekableInputStreamFactory implements ISeekableInputStreamFactory {

    protected Path dataSource;

    public SeekableInputStreamFactory(Path dataSource) {
        this.dataSource = dataSource;
        checkIfDataSourceExists();
    }
    
    protected void checkIfDataSourceExists() {
        if(dataSource != null && !Files.exists(dataSource)) {
            SelectImagePathWithDialog siwd = new SelectImagePathWithDialog(dataSource.toFile());
            this.dataSource = siwd.askImagePathInGUI().toPath();
        }
    }

    public abstract SeekableInputStream getSeekableInputStream(String identifier) throws IOException;

    public Path getDataSourcePath() {
        return dataSource;
    }

}
