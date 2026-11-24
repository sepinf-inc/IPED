package iped.engine.config;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import iped.engine.task.leapp.UfdrDecodingMode;
import iped.engine.task.leapp.UfedModelRule;
import iped.utils.UTF8Properties;

public class ALeappConfig extends AbstractTaskPropertiesConfig {

    private static final long serialVersionUID = -6174870703965268807L;

    private static final String CONFIG_FILE = "ALeappConfig.txt";

    public static final String UFDR_DECODING_TO_USE = "ufdrDecodingToUse";

    // prefix of the properties telling when a plugin is skipped in a ufdr, by the UFED models equivalent to it:
    // "skipIfUfedHas.<module> = <models>" or "skipIfUfedHas.<module>.<artifact> = <models>"
    public static final String SKIP_IF_UFED_HAS_PREFIX = "skipIfUfedHas.";

    private File aleappFolder;

    ArrayList<String> excludedPlugins = new ArrayList<>();
    ArrayList<String> includedPlugins = new ArrayList<>();

    // when the property is absent (config file of an older version), nothing changes: every plugin runs
    private UfdrDecodingMode ufdrDecodingToUse = UfdrDecodingMode.ALL;

    // "<module>" or "<module>.<artifact>" -> equivalent UFED models
    private Map<String, List<UfedModelRule>> ufedModels = new HashMap<>();

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

        String ufdrDecodingValue = properties.getProperty(UFDR_DECODING_TO_USE);
        if (StringUtils.isNotBlank(ufdrDecodingValue)) {
            ufdrDecodingToUse = UfdrDecodingMode.parse(ufdrDecodingValue);
        }

        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith(SKIP_IF_UFED_HAS_PREFIX)) {
                List<UfedModelRule> models = UfedModelRule.parseList(properties.getProperty(key));
                if (!models.isEmpty()) {
                    ufedModels.put(StringUtils.strip(key.substring(SKIP_IF_UFED_HAS_PREFIX.length())), models);
                }
            }
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

    public UfdrDecodingMode getUfdrDecodingToUse() {
        return ufdrDecodingToUse;
    }

    /**
     * The UFED models holding the same data a plugin decodes. The ones configured for the artifact take precedence
     * over the ones configured for its whole module.
     *
     * @param moduleName the plugin module (python file stem)
     * @param artifactName the artifact of the module (the key in its artifacts declaration)
     */
    public List<UfedModelRule> getUfedModels(String moduleName, String artifactName) {
        List<UfedModelRule> models = ufedModels.get(moduleName + "." + artifactName);
        if (models == null) {
            models = ufedModels.get(moduleName);
        }
        return models != null ? models : List.of();
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
