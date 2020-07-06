package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.DirectoryStream.Filter;

public class UFEDReaderConfig extends AbstractPropertiesConfigurable {
    public static final String CONFIG_FILE = "conf/AdvancedConfig.txt"; //$NON-NLS-1$

    public static final DirectoryStream.Filter<Path> filter = new Filter<Path>() {
        @Override
        public boolean accept(Path entry) throws IOException {
            return entry.endsWith(CONFIG_FILE);
        }
    };

    String phoneParsersToUse = "external"; //$NON-NLS-1$ ;

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return filter;
    }

    public void processConfig(Path resource) throws IOException {
        super.processConfig(resource);

        String value;

        value = properties.getProperty("phoneParsersToUse"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            phoneParsersToUse = value.trim();
        }
    }

    public String getPhoneParsersToUse() {
        return phoneParsersToUse;
    }

}
