package iped.engine.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Path;

import iped.configuration.EnabledInterface;
import iped.utils.UTF8Properties;

public class EnableTaskProperty extends AbstractPropertiesConfigurable implements EnabledInterface {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private String propertyName;
    private boolean value = false;

    public EnableTaskProperty(String propertyName) {
        this.propertyName = propertyName;
    }

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return new Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
                return entry.endsWith(Configuration.CONFIG_FILE);
            }
        };
    }

    @Override
    public void processProperties(UTF8Properties properties) {
        if (propertyName == null || propertyName.isEmpty()) {
            value = true;
            return;
        }
        String val = properties.getProperty(propertyName);
        if (val != null) {
            value = Boolean.valueOf(val.trim());
        }
    }

    public String getPropertyName() {
        return propertyName;
    }

    @Override
    public boolean isEnabled() {
        return value;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.value = enabled;
    }

    @Override
    public void save(Path resource) {
        try {
            File confFile = new File(resource.toFile(), Configuration.CONFIG_FILE);
            properties.load(confFile);
            properties.setProperty(propertyName, Boolean.toString(value));
            properties.store(confFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}