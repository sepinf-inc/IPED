package iped.engine.config;

import java.io.IOException;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Path;

import iped.utils.UTF8Properties;

public class SplashScreenConfig extends AbstractPropertiesConfigurable {

    private static final long serialVersionUID = 1L;
    private static final String CONFIG_FILE = "conf/SplashScreenConfig.txt";

    private String message;

    public String getMessage() {
        return message;
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
    public void processProperties(UTF8Properties properties) {

        String value = properties.getProperty("customMessage"); //$NON-NLS-1$
        if (value != null && !value.isBlank()) {
            message = value.trim();
        }
    }
}
