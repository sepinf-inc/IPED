package iped.engine.config;

import iped.utils.UTF8Properties;

public abstract class DefaultTaskPropertiesConfig extends AbstractTaskPropertiesConfig {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private String enabledPropName;
    private String configFileName;

    public DefaultTaskPropertiesConfig(String enabledPropName, String configFileName) {
        this.enabledPropName = enabledPropName;
        this.configFileName = configFileName;
    }

    @Override
    public String getTaskEnableProperty() {
        return enabledPropName;
    }

    @Override
    public String getTaskConfigFileName() {
        return configFileName;
    }

    @Override
    public void processProperties(UTF8Properties properties) {
        // no op
    }

}
