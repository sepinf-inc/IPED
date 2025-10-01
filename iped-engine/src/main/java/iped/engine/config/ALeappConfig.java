package iped.engine.config;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import iped.utils.UTF8Properties;

public class ALeappConfig extends AbstractTaskPropertiesConfig {

    private static final long serialVersionUID = -6174870703965268807L;

    private static final String CONFIG_FILE = "ALeappConfig.txt";

    private File aleappFolder;

    ArrayList<String> excludedPlugins = new ArrayList<>();
    ArrayList<String> includedPlugins = new ArrayList<>();

    @Override
    public String getTaskEnableProperty() {
        return "enableAleapp";
    }

    @Override
    public String getTaskConfigFileName() {
        return CONFIG_FILE;
    }

    @Override
    void processProperties(UTF8Properties properties) {

        String aleapFolderValue = properties.getProperty("aleapFolder");
        if (StringUtils.isNotBlank(aleapFolderValue)) {
            Path aleappPath = Paths.get(aleapFolderValue.trim());
            if (aleappPath.isAbsolute()) {
                aleappFolder = aleappPath.toFile();
            } else {
                aleappFolder = new File(Configuration.getInstance().appRoot, aleappPath.toString());
            }
        } else {
            aleappFolder = new File(Configuration.getInstance().appRoot, "tools/aleapp");
        }
        if (!aleappFolder.exists() || !aleappFolder.isDirectory()) {
            throw new IllegalArgumentException("Invalid Aleapp folder: " + aleappFolder);
        }

        String excludePlugins = properties.getProperty("excludePlugins");
        if (StringUtils.isNotBlank(excludePlugins)) {
            Arrays.stream(StringUtils.split(excludePlugins, ','))
                .map(StringUtils::strip)
                .collect(Collectors.toCollection(() -> excludedPlugins));
        }

        String includePlugins = properties.getProperty("includePlugins");
        if (StringUtils.isNotBlank(includePlugins)) {
            Arrays.stream(StringUtils.split(includePlugins, ','))
                .map(StringUtils::strip)
                .collect(Collectors.toCollection(() -> includedPlugins));
        }
    }

    public File getAleappFolder() {
        return aleappFolder;
    }

    public ArrayList<String> getExcludedPlugins() {
        return excludedPlugins;
    }

    public ArrayList<String> getIncludedPlugins() {
        return includedPlugins;
    }

    public boolean isPluginIncluded(String moduleName) {

        boolean include = true;

        if (!includedPlugins.isEmpty()) {
            include &= includedPlugins.contains(moduleName);
        }

        if (!excludedPlugins.isEmpty()) {
            include &= !excludedPlugins.contains(moduleName);
        }

        return include;
    }
}
