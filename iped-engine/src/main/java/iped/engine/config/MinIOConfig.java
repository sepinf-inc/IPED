package iped.engine.config;

import iped.utils.UTF8Properties;

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
    private static final String TIME_OUT = "timeOut";
    private static final String RETRIES = "retries";

    private static final int DEFAULT_TIMEOUT = 30;
    private static final int DEFAULT_RETRIES = 1;

    private String host;
    private String port;
    private long timeOut;
    private int retries;
    private boolean updateRefsToMinIO = false;

    private long zipFilesMaxSize;

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

        host = properties.getProperty(HOST_KEY).trim();
        port = properties.getProperty(PORT_KEY).trim();
        timeOut = Long.parseLong(properties.getProperty(TIME_OUT, Integer.toString(DEFAULT_TIMEOUT)).trim());
        retries = Integer.parseInt(properties.getProperty(RETRIES, Integer.toString(DEFAULT_RETRIES)).trim());
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

    public long getTimeOut() {
        return timeOut;
    }

    public int getRetries() {
        return retries;
    }

}
