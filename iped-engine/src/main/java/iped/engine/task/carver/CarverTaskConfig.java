package iped.engine.task.carver;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream.Filter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import iped.engine.config.AbstractTaskConfig;
import iped.engine.config.Configuration;

public class CarverTaskConfig extends AbstractTaskConfig<XMLCarverConfiguration> {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public static final String ENABLE_PARAM = "enableCarving"; //$NON-NLS-1$
    public static final String GLOBAL_CARVER_CONFIG = "CarverConfig.xml"; //$NON-NLS-1$
    public static final String CARVER_CONFIG_PREFIX = "carver-"; //$NON-NLS-1$
    public static final String CARVER_CONFIG_SUFFIX = ".xml"; //$NON-NLS-1$

    private XMLCarverConfiguration carverConfiguration = new XMLCarverConfiguration();

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return new Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
                return entry.endsWith(Configuration.CONFIG_FILE) || entry.endsWith(getTaskConfigFileName())
                        || (entry.getFileName() != null && entry.getFileName().startsWith(CARVER_CONFIG_PREFIX)
                                && entry.getFileName().startsWith(CARVER_CONFIG_SUFFIX));
            }
        };
    }

    @Override
    public XMLCarverConfiguration getConfiguration() {
        return carverConfiguration;
    }

    @Override
    public void setConfiguration(XMLCarverConfiguration config) {
        this.carverConfiguration = config;
    }

    @Override
    public String getTaskEnableProperty() {
        return ENABLE_PARAM;
    }

    @Override
    public String getTaskConfigFileName() {
        return GLOBAL_CARVER_CONFIG;
    }

    @Override
    public void processTaskConfig(Path resource) throws IOException {

        if (resource.endsWith(GLOBAL_CARVER_CONFIG)) {
            carverConfiguration.loadXMLConfigFile(resource.toFile());
        }
        if (resource.getFileName().startsWith(CARVER_CONFIG_PREFIX)
                && resource.getFileName().startsWith(CARVER_CONFIG_SUFFIX)) {
            carverConfiguration.loadXMLConfigFile(resource.toFile());
        }

    }

    @Override
    public void save(Path resource) {
        try {
            String output = carverConfiguration.getXMLString();
            File confDir = new File(resource.toFile(), Configuration.CONF_DIR);
            confDir.mkdirs();
            File confFile = new File(confDir, GLOBAL_CARVER_CONFIG);
            Files.write(confFile.toPath(),output.toString().getBytes(StandardCharsets.UTF_8));
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

}
