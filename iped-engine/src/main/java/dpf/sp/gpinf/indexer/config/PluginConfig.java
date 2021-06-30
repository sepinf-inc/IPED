package dpf.sp.gpinf.indexer.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;

import org.apache.commons.lang.SystemUtils;

import java.nio.file.DirectoryStream.Filter;

import dpf.sp.gpinf.indexer.util.IPEDException;
import dpf.sp.gpinf.indexer.util.UTF8Properties;
import iped3.configuration.IConfigurationDirectory;

public class PluginConfig extends AbstractPropertiesConfigurable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private String relativePluginFolder;
    private String tskJarPath;

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

    public File getTskJarFile() {
        if (tskJarPath == null || SystemUtils.IS_OS_WINDOWS) {
            return null;
        } else {
            return new File(tskJarPath);
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

        tskJarPath = properties.getProperty("tskJarPath"); //$NON-NLS-1$
        if (tskJarPath != null && !tskJarPath.isEmpty())
            tskJarPath = tskJarPath.trim();
        else if (!SystemUtils.IS_OS_WINDOWS) {
            throw new IPEDException("You must set tskJarPath on LocalConfig.txt!"); //$NON-NLS-1$
        }
        if (!SystemUtils.IS_OS_WINDOWS && !new File(tskJarPath).exists()) {
            throw new IPEDException("File not found " + tskJarPath + ". Set tskJarPath on LocalConfig.txt!"); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

}
