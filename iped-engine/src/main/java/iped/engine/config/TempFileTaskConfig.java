package iped.engine.config;

import iped.utils.UTF8Properties;

public class TempFileTaskConfig extends AbstractTaskPropertiesConfig {

    private static final long serialVersionUID = 1L;
    private static final String CONFIG_FILE = "TempFileTaskConfig.txt";

    private long maxFileSize = 1L << 30;

    @Override
    public void processProperties(UTF8Properties properties) {
        String value = properties.getProperty("maxFileSize");
        if (value != null && !value.isBlank()) {
            maxFileSize = Long.valueOf(value.trim());
        }
    }

    @Override
    public String getTaskEnableProperty() {
        return "";
    }

    @Override
    public String getTaskConfigFileName() {
        return CONFIG_FILE;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }
}
