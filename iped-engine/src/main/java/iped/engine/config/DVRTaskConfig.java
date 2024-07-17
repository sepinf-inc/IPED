package iped.engine.config;

import iped.utils.UTF8Properties;

public class DVRTaskConfig extends AbstractTaskPropertiesConfig {

    private static final long serialVersionUID = 1L;


    private static final String CONFIG_FILE = "DVRConfig.txt";

    private static final String ENABLED_PROP = "enableDVR";

    private static final String EXTRACT_DATA_BLOCK_HVFS = "extractDataBlockHVFS";

    private static final String EXTRACT_SYSTEM_LOG_HVFS = "extractSystemLogsHVFS";


    private boolean extractDataBlockHVFS = false;

    private boolean extractSystemLogsHVFS = false;


    public Boolean getExtractDataBlockHVFS() {
        return extractDataBlockHVFS;
    }

    public Boolean getExtractSystemLogsHVFS() {
        return extractSystemLogsHVFS;
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

        String value = properties.getProperty(EXTRACT_DATA_BLOCK_HVFS);
        if (value != null && value.trim().equalsIgnoreCase("true")) {
            this.extractDataBlockHVFS = true;
        }

        value = properties.getProperty(EXTRACT_SYSTEM_LOG_HVFS);
        if (value != null && value.trim().equalsIgnoreCase("true")) {
            this.extractSystemLogsHVFS = true;
        }


    }

}