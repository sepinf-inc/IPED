package iped.engine.config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.lang3.tuple.Pair;

import iped.utils.UTF8Properties;

public class HashDBLookupConfig extends AbstractTaskConfig<Pair<Boolean, String>> {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static final String CONFIG_FILE = "HashDBLooupConfig.txt";
    public static final String NSRL_CONFIG_FILE = "NSRLConfig.json";
    private static final String ENABLE_PARAM = "enableHashDBLookup";
    private static final String EXCLUDE_KNOWN = "excludeKnown";

    private boolean excludeKnown;
    private String nsrlConfig;

    public String getNsrlConfig() {
        return nsrlConfig;
    }

    public void setNsrlConfig(String nsrlConfig) {
        this.nsrlConfig = nsrlConfig;
    }

    @Override
    public String getTaskEnableProperty() {
        return ENABLE_PARAM;
    }

    @Override
    public String getTaskConfigFileName() {
        return CONFIG_FILE;
    }

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return new Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
                return entry.endsWith(Configuration.CONFIG_FILE) || entry.endsWith(CONFIG_FILE)
                        || entry.endsWith(NSRL_CONFIG_FILE);
            }
        };
    }

    @Override
    public void processTaskConfig(Path resource) throws IOException {
        if (resource.endsWith(CONFIG_FILE)) {
            UTF8Properties properties = new UTF8Properties();
            properties.load(resource.toFile());
            String value = properties.getProperty(EXCLUDE_KNOWN);
            if (value != null) {
                setExcludeKnown(Boolean.valueOf(value.trim()));
            }
        } else if (resource.endsWith(NSRL_CONFIG_FILE)) {
            this.nsrlConfig = new String(Files.readAllBytes(resource), StandardCharsets.UTF_8);
        }
    }

    public boolean isExcludeKnown() {
        return excludeKnown;
    }

    public void setExcludeKnown(boolean excludeKnown) {
        this.excludeKnown = excludeKnown;
    }

    @Override
    public Pair<Boolean, String> getConfiguration() {
        return Pair.of(excludeKnown, nsrlConfig);
    }

    @Override
    public void setConfiguration(Pair<Boolean, String> config) {
        this.excludeKnown = config.getLeft();
        this.nsrlConfig = config.getRight();
    }

    @Override
    public void save(Path resource) {
        try {
            File confDir = new File(resource.toFile(), Configuration.CONF_DIR);
            confDir.mkdirs();

            if (resource.endsWith(CONFIG_FILE)) {
                File confFile = new File(confDir, CONFIG_FILE);            
                UTF8Properties properties = new UTF8Properties();
                properties.load(resource.toFile());
                properties.setProperty(EXCLUDE_KNOWN, Boolean.toString(excludeKnown));
                properties.store(confFile);
            } else if (resource.endsWith(NSRL_CONFIG_FILE)) {
                File confFile = new File(confDir, NSRL_CONFIG_FILE);            
                Files.write(confFile.toPath(),this.nsrlConfig.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
