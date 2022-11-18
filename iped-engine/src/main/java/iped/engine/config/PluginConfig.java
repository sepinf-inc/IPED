package iped.engine.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Path;

import org.apache.commons.lang.SystemUtils;

import iped.configuration.IConfigurationDirectory;
import iped.exception.IPEDException;
import iped.utils.UTF8Properties;

public class PluginConfig extends AbstractPropertiesConfigurable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private String relativePluginFolder;
    private String tskJarPath;
    private String value;
    final static private String tskJarPathpropName = "tskJarPath";
    final static private String propertyName = "pluginFolder";
    

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
        String appRoot = System.getProperty(IConfigurationDirectory.IPED_APP_ROOT);
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

        value = properties.getProperty(propertyName); //$NON-NLS-1$
        if (value != null) {
            relativePluginFolder = value.trim();
        }

        tskJarPath = properties.getProperty(tskJarPathpropName); //$NON-NLS-1$
        if (tskJarPath != null && !tskJarPath.isEmpty())
            tskJarPath = tskJarPath.trim();
        else if (!SystemUtils.IS_OS_WINDOWS) {
            throw new IPEDException("You must set tskJarPath on LocalConfig.txt!"); //$NON-NLS-1$
        }
        if (!SystemUtils.IS_OS_WINDOWS && !new File(tskJarPath).exists()) {
            throw new IPEDException("File not found " + tskJarPath + ". Set tskJarPath on LocalConfig.txt!"); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    @Override
    public void save(Path resource) {
        try {
            File confDir = new File(resource.toFile(), Configuration.CONF_DIR);
            confDir.mkdirs();
            File confFile = new File(confDir, LOCAL_CONFIG);            
            properties.load(confFile);
            properties.setProperty(propertyName , value);
            properties.setProperty(tskJarPathpropName , tskJarPath);
            properties.store(confFile);
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

}
