package iped.engine.config;

import iped.utils.UTF8Properties;

public class ALeappConfig extends AbstractTaskPropertiesConfig {

    String aleapScriptsDir;

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static final String CONFIG_FILE = "ALeappConfig.txt"; //$NON-NLS-1$

    @Override
    void processProperties(UTF8Properties properties) {
        String value = properties.getProperty("aleapFolder"); //$NON-NLS-1$
        if (value != null) {
            aleapScriptsDir = value.trim();
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

    public String getAleapScriptsDir() {
        return aleapScriptsDir;
    }

    public void setAleapScriptsDir(String aleapScriptsDir) {
        this.aleapScriptsDir = aleapScriptsDir;
    }

}
