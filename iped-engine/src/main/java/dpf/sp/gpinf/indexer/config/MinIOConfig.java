package dpf.sp.gpinf.indexer.config;

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

    private static final String TAR_MAX_FILES = "tarMaxFiles";
    private static final String TAR_MAX_LENGTH = "tarMaxLength";

    private boolean enabled;
    private String host;
    private String port;

    private long tarMaxFiles, tarMaxLength;

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

    public long getTarMaxFiles() {
        return tarMaxFiles;
    }

    public long getTarMaxLength() {
        return tarMaxLength;
    }

    @Override
    public void processProperties(UTF8Properties properties) {

        enabled = Boolean.valueOf(properties.getProperty(ENABLE_KEY).trim());
        host = properties.getProperty(HOST_KEY).trim();
        port = properties.getProperty(PORT_KEY).trim();
        tarMaxFiles = Long.parseLong(properties.getProperty(TAR_MAX_FILES));
        tarMaxLength = (long) (1024 * 1024 * Double.parseDouble(properties.getProperty(TAR_MAX_LENGTH)));

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
