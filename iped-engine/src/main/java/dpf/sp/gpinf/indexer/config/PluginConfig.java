package dpf.sp.gpinf.indexer.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.DirectoryStream.Filter;

import dpf.sp.gpinf.indexer.util.UTF8Properties;

public class PluginConfig extends AbstractPropertiesConfigurable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private String optional_jars;
    private File optionalJarDir;

    public static final String OPTIONAL_JARS = "optional_jars";
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

    public File[] getOptionalJars(String appRoot) {
        if (optionalJarDir == null && optional_jars != null) {
            optionalJarDir = new File(appRoot + "/" + optional_jars.trim()); //$NON-NLS-1$
        }
        File[] jars = null;
        if (optionalJarDir != null) {
            jars = optionalJarDir.listFiles();
        }
        return jars != null ? jars : new File[0];
    }

    @Override
    public void processProperties(UTF8Properties properties) {
        optional_jars = properties.getProperty(OPTIONAL_JARS);
    }

}
