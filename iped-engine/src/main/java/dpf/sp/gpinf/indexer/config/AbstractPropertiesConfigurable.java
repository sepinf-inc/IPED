package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.Path;

import dpf.sp.gpinf.indexer.util.UTF8Properties;
import macee.core.Configurable;

public abstract class AbstractPropertiesConfigurable implements Configurable<UTF8Properties> {

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

    @Override
    public void processConfig(Path resource) throws IOException {
        properties.load(resource.toFile());
        processProperties(properties);
    }

    abstract void processProperties(UTF8Properties properties);

}
