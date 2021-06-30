package dpf.sp.gpinf.indexer.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.DirectoryStream.Filter;

import dpf.sp.gpinf.indexer.util.UTF8Properties;
import iped3.configuration.IConfigurationDirectory;

public class PluginConfig extends AbstractPropertiesConfigurable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private String relativePluginFolder;

    public static final String LOCAL_CONFIG = "LocalConfig.txt"; //$NON-NLS-1$

    public static final DirectoryStream.Filter<Path> filter = new Filter<Path>() {
        @Override
        public boolean accept(Path entry) throws IOException {
            return entry.endsWith(LOCAL_CONFIG);
        }
    };

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return filter;
    }

    public File[] getPluginJars() {
        File[] jars = null;
        if (getPluginFolder() != null) {
            jars = getPluginFolder().listFiles();
        }
        return jars != null ? jars : new File[0];
    }

    public File getPluginFolder() {
        String appRoot = System.getProperty(IConfigurationDirectory.IPED_ROOT);
        try {
            return new File(appRoot, relativePluginFolder).getCanonicalFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void processProperties(UTF8Properties properties) {

        String value = properties.getProperty("pluginFolder"); //$NON-NLS-1$
        if (value == null) {
            value = properties.getProperty("optional_jars"); //$NON-NLS-1$
        }
        if (value != null) {
            relativePluginFolder = value.trim();
        }
    }

}
