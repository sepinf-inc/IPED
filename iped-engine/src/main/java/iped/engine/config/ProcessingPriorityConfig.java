package iped.engine.config;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.DirectoryStream.Filter;

import iped.utils.UTF8Properties;

public class ProcessingPriorityConfig extends AbstractPropertiesConfigurable {
    private static final long serialVersionUID = 1L;

    public static final String CONFIG_FILE = "conf/ProcessingPriorityConfig.txt";

    private boolean randomOrder = true;
    private int maxQueueSize = 0;

    public static final DirectoryStream.Filter<Path> filter = new Filter<Path>() {
        @Override
        public boolean accept(Path entry) throws IOException {
            return entry.endsWith(CONFIG_FILE);
        }
    };

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return filter;
    }

    @Override
    public void processProperties(UTF8Properties properties) {

        String value = properties.getProperty("randomOrder");
        if (value != null && !value.isBlank()) {
            randomOrder = Boolean.valueOf(value.trim());
        }

        value = properties.getProperty("maxQueueSize");
        if (value != null && !value.trim().isEmpty()) {
            if (value.equalsIgnoreCase("auto") || value.equalsIgnoreCase("default")) {
                maxQueueSize = 0;
            } else {
                maxQueueSize = Integer.parseInt(value.trim());
            }
        }
    }

    public boolean isRandomOrder() {
        return randomOrder;
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }
}
