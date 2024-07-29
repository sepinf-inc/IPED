package iped.engine.config;

import java.io.IOException;
import java.nio.file.Path;

import iped.configuration.Configurable;
import iped.utils.UTF8Properties;

public abstract class AbstractPropertiesConfigurable implements Configurable<UTF8Properties>, IPropertiesConfigurable {

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

    public abstract void processProperties(UTF8Properties properties);

    public UTF8Properties getPropertie() {
        return properties;
    }

    @Override
    public void reset() {
        // TODO Auto-generated method stub
        
    }

}
