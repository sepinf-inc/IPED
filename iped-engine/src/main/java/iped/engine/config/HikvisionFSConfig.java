package iped.engine.config;

import iped.utils.UTF8Properties;

public class HikvisionFSConfig extends AbstractTaskPropertiesConfig {

    private static final long serialVersionUID = 1L;


    private static final String CONFIG_FILE = "HikvisionFSConfig.txt";

    private static final String ENABLED_PROP = "enableHikvisionFS";

    private static final String EXTRACT_DATA_BLOCK = "extractDataBlockVideo";

    private static final String EXTRACT_SYSTEM_LOG = "extractSystemLogs";


    private boolean extractDataBlock = false;

    private boolean extractSystemLog = false;


    public Boolean getExtractDataBlock() {
        return extractDataBlock;
    }

    public Boolean getExtractSystemLog() {
        return extractSystemLog;
    }


    @Override
    public String getTaskEnableProperty() {
        return ENABLED_PROP;
    }

    @Override
    public String getTaskConfigFileName() {
        return CONFIG_FILE;
    }

    @Override
    public void processProperties(UTF8Properties properties) {

        String value = properties.getProperty(EXTRACT_DATA_BLOCK);
        if (value != null && value.trim().equalsIgnoreCase("true")) {
            this.extractDataBlock = true;
        }

        value = properties.getProperty(EXTRACT_SYSTEM_LOG);
        if (value != null && value.trim().equalsIgnoreCase("true")) {
            this.extractSystemLog = true;
        }


    }

}