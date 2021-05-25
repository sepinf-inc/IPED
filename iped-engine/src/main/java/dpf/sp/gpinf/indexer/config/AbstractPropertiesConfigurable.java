package dpf.sp.gpinf.indexer.config;

import dpf.sp.gpinf.indexer.util.UTF8Properties;
import macee.core.Configurable;

public abstract class AbstractPropertiesConfigurable implements Configurable<UTF8Properties> {

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
