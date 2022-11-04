package iped.engine.config;

import java.io.IOException;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Path;

import iped.utils.UTF8Properties;

public class SplashScreenConfig extends AbstractPropertiesConfigurable {

    private static final long serialVersionUID = 1L;
    public static final String CONFIG_FILE = "conf/SplashScreenConfig.txt";
    public static final String CUSTOM_MESSAGE = "customMessage";

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

        String value = properties.getProperty(CUSTOM_MESSAGE);
        if (value != null && !value.isBlank()) {
            message = value.trim();
        }
    }
}
