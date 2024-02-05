package iped.engine.config;

import java.util.ArrayList;

import org.apache.commons.lang3.StringUtils;

import iped.utils.UTF8Properties;

public class ALeappConfig extends AbstractTaskPropertiesConfig {

    String aleapScriptsDir;
    ArrayList<String> excludedPlugins = new ArrayList();
    ArrayList<String> includedPlugins = new ArrayList();

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static final String CONFIG_FILE = "ALeappConfig.txt"; //$NON-NLS-1$

    @Override
    void processProperties(UTF8Properties properties) {
        String value = properties.getProperty("aleapFolder"); //$NON-NLS-1$
        if (value != null) {
            aleapScriptsDir = value.trim();
        }

        String excludePlugins = properties.getProperty("excludePlugins"); //$NON-NLS-1$
        if (excludePlugins != null) {
            String[] aExcludedPlugins = StringUtils.split(excludePlugins, ",");
            for (String excludedPlugin : aExcludedPlugins) {
                excludedPlugins.add(excludePlugins);
            }
        }

        String includePlugins = properties.getProperty("includePlugins"); //$NON-NLS-1$
        if (includePlugins != null) {
            String[] aIncludedPlugins = StringUtils.split(includePlugins, ",");
            for (String includedPlugin : aIncludedPlugins) {
                includedPlugins.add(includedPlugin);
            }
        }
    }

    @Override
    public String getTaskEnableProperty() {
        return "enableAleapp";
    }

    @Override
    public String getTaskConfigFileName() {
        return CONFIG_FILE;
    }

    public String getAleapScriptsDir() {
        return aleapScriptsDir;
    }

    public void setAleapScriptsDir(String aleapScriptsDir) {
        this.aleapScriptsDir = aleapScriptsDir;
    }

    public ArrayList<String> getExcludedPlugins() {
        return excludedPlugins;
    }

    public void setExcludedPlugins(ArrayList<String> excludedPlugins) {
        this.excludedPlugins = excludedPlugins;
    }

    public ArrayList<String> getIncludedPlugins() {
        return includedPlugins;
    }

    public void setIncludedPlugins(ArrayList<String> includedPlugins) {
        this.includedPlugins = includedPlugins;
    }

    public boolean isPluginIncluded(String moduleName) {
        if (!excludedPlugins.isEmpty()) {
            return !excludedPlugins.contains(moduleName);
        }
        if (!includedPlugins.isEmpty()) {
            return includedPlugins.contains(moduleName);
        }
        return true;
    }

}
