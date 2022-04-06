package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.DirectoryStream.Filter;

import dpf.sp.gpinf.indexer.util.UTF8Properties;
import iped3.configuration.EnabledInterface;

import java.nio.file.Path;

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

}
