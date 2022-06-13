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
    private static final String UPDATE_REFS_TO_MINIO = "updateRefsToMinIO";
    private static final String ZIP_FILES_MAX_SIZE = "zipFilesMaxSize";

    private boolean enabled;
    private String host;
    private String port;
    private boolean updateRefsToMinIO = false;

    private long zipFilesMaxSize;

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

    public String getHostAndPort() {
        if (port == null || port.isBlank())
            return host;
        else
            return host + ":" + port;
    }

    @Override
    public void processProperties(UTF8Properties properties) {

        enabled = Boolean.valueOf(properties.getProperty(ENABLE_KEY).trim());
        host = properties.getProperty(HOST_KEY).trim();
        port = properties.getProperty(PORT_KEY).trim();
        setZipFilesMaxSize(Long.parseLong(properties.getProperty(ZIP_FILES_MAX_SIZE)));
        updateRefsToMinIO = Boolean.valueOf(properties.getProperty(UPDATE_REFS_TO_MINIO, "false"));
    }

    @Override
    public String getTaskEnableProperty() {
        return ENABLE_KEY;
    }

    @Override
    public String getTaskConfigFileName() {
        return CONFIG_FILE;
    }

    public long getZipFilesMaxSize() {
        return zipFilesMaxSize;
    }

    public void setZipFilesMaxSize(long zipFilesMaxSize) {
        this.zipFilesMaxSize = zipFilesMaxSize;
    }

    public boolean isToUpdateRefsToMinIO() {
        return this.updateRefsToMinIO;
    }


}
