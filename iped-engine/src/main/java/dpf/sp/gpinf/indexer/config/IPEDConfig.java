package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;

import java.nio.file.Path;

public class IPEDConfig extends AbstractPropertiesConfigurable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    public static final String CONFDIR = "confdir"; //$NON-NLS-1$
    public static final String TOADDUNALLOCATED = "addUnallocated"; //$NON-NLS-1$
    public static final String TOADDFILESLACKS = "addFileSlacks"; //$NON-NLS-1$
    public static final String CONFIG_FILE = "IPEDConfig.txt"; //$NON-NLS-1$

    private boolean toAddUnallocated = false;
    private boolean toAddFileSlacks = false;

    public static final DirectoryStream.Filter<Path> filter = new Filter<Path>() {
        @Override
        public boolean accept(Path entry) throws IOException {
            return entry.endsWith(CONFIG_FILE);
        }
    };

    public String getConfDir() {
        return (String) properties.get(IPEDConfig.CONFDIR);
    }

    public boolean isToAddUnallocated() {
        return toAddUnallocated;
    }

    public boolean isToAddFileSlacks() {
        return toAddFileSlacks;
    }

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return filter;
    }

    @Override
    public void processConfig(Path resource) throws IOException {

        properties.load(resource.toFile());

        String value = null;

        value = properties.getProperty(TOADDUNALLOCATED); // $NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (value != null && !value.isEmpty()) {
            toAddUnallocated = Boolean.valueOf(value);
        }

        value = properties.getProperty(TOADDFILESLACKS); // $NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (value != null && !value.isEmpty()) {
            toAddFileSlacks = Boolean.valueOf(value);
        }

    }

}
