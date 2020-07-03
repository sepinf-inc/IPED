package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.DirectoryStream.Filter;

public class SleuthKitConfig extends AbstractPropertiesConfigurable {
    public static final String CONFIG_FILE = "AdvancedConfig.txt"; //$NON-NLS-1$

    public static final DirectoryStream.Filter<Path> filter = new Filter<Path>() {
        @Override
        public boolean accept(Path entry) throws IOException {
            return entry.endsWith(CONFIG_FILE);
        }
    };

    private boolean robustImageReading;
    private int numImageReaders;

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return filter;
    }

    public void processConfig(Path resource) throws IOException {
        super.processConfig(resource);

        String value;

        value = properties.getProperty("robustImageReading"); //$NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (value != null && !value.isEmpty()) {
            robustImageReading = Boolean.valueOf(value);
        }

        value = properties.getProperty("numImageReaders"); //$NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (value != null && !"auto".equalsIgnoreCase(value)) {
            numImageReaders = Integer.valueOf(value);
        } else
            numImageReaders = (int) Math.ceil((float) Runtime.getRuntime().availableProcessors() / 2);

    }

    public boolean isRobustImageReading() {
        return robustImageReading;
    }

    public int getNumImageReaders() {
        return numImageReaders;
    }
}
