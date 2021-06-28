package br.gov.pf.labld.graph;

import java.io.IOException;
import java.nio.file.Path;

import dpf.sp.gpinf.indexer.config.AbstractTaskConfig;

public class GraphTaskConfig extends AbstractTaskConfig<GraphConfiguration> {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public static final String ENABLE_PARAM = "enableGraphGeneration";

    public static final String CONFIG_FILE = "GraphConfig.json";

    private GraphConfiguration graphConfig;

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
        graphConfig = GraphConfiguration.loadFrom(resource.toFile());
    }

}
