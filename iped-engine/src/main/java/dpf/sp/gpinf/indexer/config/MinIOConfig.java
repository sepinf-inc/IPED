package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.Path;

import dpf.sp.gpinf.indexer.util.UTF8Properties;

public class MinIOConfig extends AbstractTaskPropertiesConfig {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private static final String CONFIG_FILE = "MinIOConfig.txt";
    private static final String ENABLE_KEY = "enableMinIO";
    private static final String HOST_KEY = "host";
    private static final String PORT_KEY = "port";

    private boolean enabled;
    private String host;
    private String port;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean value) {
        enabled = value;
    }

    public String getHost() {
        return host;
    }

    public String getPort() {
        return port;
    }

    @Override
    public void processProperties(UTF8Properties properties) {

        enabled = Boolean.valueOf(properties.getProperty(ENABLE_KEY).trim());
        host = properties.getProperty(HOST_KEY).trim();
        port = properties.getProperty(PORT_KEY).trim();

    }

    @Override
    public String getTaskEnableProperty() {
        return ENABLE_KEY;
    }

    @Override
    public String getTaskConfigFileName() {
        return CONFIG_FILE;
    }

}
