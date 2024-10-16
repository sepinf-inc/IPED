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
    String pluginFolder;
    private String tskJarPath;
    final static public String TSK_JAR_PATH = "tskJarPath";
    final static private String PLUGIN_FOLDER = "pluginFolder";


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

    public String getRelativePluginFolder(){
        return relativePluginFolder;
    }

    public File getPluginFolder() {
        String appRoot = System.getProperty(IConfigurationDirectory.IPED_APP_ROOT);
        try {
            return new File(appRoot, relativePluginFolder).getCanonicalFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setPluginFolder(String pluginFolder){
        properties.setProperty(PLUGIN_FOLDER, pluginFolder);
        relativePluginFolder = pluginFolder;
    }

    public File getTskJarFile() {
        if (tskJarPath == null || SystemUtils.IS_OS_WINDOWS) {
            return null;
        } else {
            return new File(tskJarPath);
        }
    }

    public void setTskJarFile(String tskJarPath){
        properties.setProperty(TSK_JAR_PATH, tskJarPath);
        this.tskJarPath = tskJarPath;
    }

    public boolean isTskJarPathEnabled(){
        return properties.containsKey(TSK_JAR_PATH);
    }

    @Override
    public void processProperties(UTF8Properties properties) {

        pluginFolder = properties.getProperty(PLUGIN_FOLDER); //$NON-NLS-1$
        if (pluginFolder != null) {
            relativePluginFolder = pluginFolder.trim();
        }

        tskJarPath = properties.getProperty(TSK_JAR_PATH); //$NON-NLS-1$
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
            properties.setProperty(PLUGIN_FOLDER, pluginFolder);
            properties.setProperty(TSK_JAR_PATH, tskJarPath);
            properties.store(confFile);
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

}
