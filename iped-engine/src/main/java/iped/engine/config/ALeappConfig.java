package iped.engine.config;

import iped.utils.UTF8Properties;

public class ALeappConfig extends AbstractTaskPropertiesConfig {

    String aleapFolder;

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static final String CONFIG_FILE = "ALeappConfig.txt"; //$NON-NLS-1$

    @Override
    void processProperties(UTF8Properties properties) {
        String value = properties.getProperty("aleapFolder"); //$NON-NLS-1$
        if (value != null) {
            aleapFolder = value.trim();
        }
    }

    @Override
    public String getTaskEnableProperty() {
        return "enableAleapp";
    }

    @Override
    public String getTaskConfigFileName() {
        return CONFIG_FILE;
    }

}
