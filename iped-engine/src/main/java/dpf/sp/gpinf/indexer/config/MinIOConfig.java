package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.DirectoryStream.Filter;

import macee.core.EnabledInterface;

import java.nio.file.Path;

public class MinIOConfig extends AbstractPropertiesConfigurable implements EnabledInterface {

    private static final String CONFIG_FILE = "MinIOConfig.txt";
    private static final String ENABLE_KEY = "enable";
    private static final String HOST_KEY = "host";
    private static final String PORT_KEY = "port";

    private boolean enabled;
    private String host;
    private String port;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public String getHost() {
        return host;
    }

    public String getPort() {
        return port;
    }

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return new Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
                return entry.endsWith(CONFIG_FILE);
            }
        };
    }

    @Override
    public void processConfig(Path resource) throws IOException {

        properties.load(resource.toFile());

        enabled = Boolean.valueOf(properties.getProperty(ENABLE_KEY).trim());
        host = properties.getProperty(HOST_KEY).trim();
        port = properties.getProperty(PORT_KEY).trim();

    }

}
