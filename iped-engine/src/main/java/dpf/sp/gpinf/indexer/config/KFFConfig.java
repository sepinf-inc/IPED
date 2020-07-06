package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.DirectoryStream.Filter;

public class KFFConfig extends AbstractPropertiesConfigurable {
    public static final String CONFIG_FILE = "LocalConfig.txt"; //$NON-NLS-1$
    public static final String IPEDCONFIGFILE = "IPEDConfig.txt"; //$NON-NLS-1$

    public static final DirectoryStream.Filter<Path> filter = new Filter<Path>() {
        @Override
        public boolean accept(Path entry) throws IOException {
            return entry.endsWith(CONFIG_FILE) || entry.endsWith(IPEDCONFIGFILE);
        }
    };

    String kffDb;
    boolean enableKff;

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return filter;
    }

    public void processConfig(Path resource) throws IOException {
        super.processConfig(resource);

        String value;

        if (resource.endsWith(IPEDCONFIGFILE)) {
            value = properties.getProperty("enableKff"); //$NON-NLS-1$
            if (value != null) {
                value = value.trim();
            }
            if (value != null && !value.isEmpty()) {
                enableKff = Boolean.valueOf(value);
            }
        }

        if (resource.endsWith(CONFIG_FILE)) {
            value = properties.getProperty("kffDb"); //$NON-NLS-1$
            if (value != null) {
                value = value.trim();
            }
            if (value != null && !value.isEmpty()) {
                kffDb = value;
            }
        }
    }

}
