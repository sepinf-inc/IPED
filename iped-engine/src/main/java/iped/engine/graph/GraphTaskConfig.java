package iped.engine.graph;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import iped.engine.config.AbstractTaskConfig;
import iped.engine.config.Configuration;

public class GraphTaskConfig extends AbstractTaskConfig<GraphConfiguration> {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public static final String ENABLE_PARAM = "enableGraphGeneration";

    public static final String CONFIG_FILE = "GraphConfig.json";

    private GraphConfiguration graphConfig;
    private String json;

    @Override
    public GraphConfiguration getConfiguration() {
        return graphConfig;
    }

    @Override
    public void setConfiguration(GraphConfiguration config) {
        graphConfig = config;
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
    public void processTaskConfig(Path resource) throws IOException {
        json = Files.readString(resource);
        graphConfig = GraphConfiguration.loadFrom(resource.toFile());
    }

    @Override
    public void save(Path resource) {
        try {
            File confDir = new File(resource.toFile(), Configuration.CONF_DIR);
            confDir.mkdirs();
            File confFile = new File(confDir, CONFIG_FILE);
            Files.write(confFile.toPath(),json.getBytes(StandardCharsets.UTF_8));
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

}
