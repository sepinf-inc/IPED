package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream.Filter;

import org.apache.commons.lang3.tuple.Pair;

import dpf.sp.gpinf.indexer.util.UTF8Properties;

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
                return entry.endsWith(IPEDConfig.CONFIG_FILE) || entry.endsWith(CONFIG_FILE)
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

}
