package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;

import dpf.sp.gpinf.indexer.util.UTF8Properties;

import java.nio.file.Path;

/**
 * TODO remove this class
 */
public class IPEDConfig extends AbstractPropertiesConfigurable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public static final String CONFDIR = "confdir"; //$NON-NLS-1$
    public static final String CONFIG_FILE = "IPEDConfig.txt"; //$NON-NLS-1$

    public static final DirectoryStream.Filter<Path> filter = new Filter<Path>() {
        @Override
        public boolean accept(Path entry) throws IOException {
            return entry.endsWith(CONFIG_FILE);
        }
    };

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return filter;
    }

    @Override
    public void processProperties(UTF8Properties properties) {
        // no op
    }

}
