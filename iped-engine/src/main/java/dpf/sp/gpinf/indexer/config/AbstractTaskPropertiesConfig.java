package dpf.sp.gpinf.indexer.config;

import dpf.sp.gpinf.indexer.util.UTF8Properties;

public abstract class AbstractTaskPropertiesConfig extends AbstractTaskConfig<UTF8Properties> {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    protected UTF8Properties properties = new UTF8Properties();

    @Override
    public UTF8Properties getConfiguration() {
        return properties;
    }

    @Override
    public void setConfiguration(UTF8Properties config) {
        this.properties = config;
    }

}
