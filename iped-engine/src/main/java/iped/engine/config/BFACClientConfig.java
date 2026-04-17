package iped.engine.config;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Path;

import iped.utils.UTF8Properties;

/**
 * Configuration for BFAC Client integration.
 * Reads the enableBFACClient property from IPEDConfig.txt
 * and the baseUrl property from conf/BFACConfig.txt.
 */
public class BFACClientConfig extends AbstractPropertiesConfigurable {

    private static final long serialVersionUID = 1L;

    private static final String IPED_CONFIG_FILE = "IPEDConfig.txt"; //$NON-NLS-1$
    private static final String BFAC_CONFIG_FILE = "conf/BFACConfig.txt"; //$NON-NLS-1$
    private static final String DEFAULT_BASE_URL = "http://localhost:8000/"; //$NON-NLS-1$
    private static final int DEFAULT_MAX_CONCURRENT_UPLOADS = 5;

    private boolean enabled = false;
    private String baseUrl = DEFAULT_BASE_URL;
    private int maxConcurrentUploads = DEFAULT_MAX_CONCURRENT_UPLOADS;

    public static final DirectoryStream.Filter<Path> filter = new Filter<Path>() {
        @Override
        public boolean accept(Path entry) throws IOException {
            return entry.endsWith(IPED_CONFIG_FILE) || entry.endsWith(BFAC_CONFIG_FILE);
        }
    };

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return filter;
    }

    @Override
    public void processProperties(UTF8Properties properties) {
        String value = properties.getProperty("enableBFACClient"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            enabled = Boolean.valueOf(value.trim());
        }

        value = properties.getProperty("baseUrl"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            baseUrl = value.trim();
        }

        value = properties.getProperty("maxConcurrentUploads"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            try {
                maxConcurrentUploads = Integer.parseInt(value.trim());
                if (maxConcurrentUploads < 1) {
                    maxConcurrentUploads = DEFAULT_MAX_CONCURRENT_UPLOADS;
                }
            } catch (NumberFormatException e) {
                maxConcurrentUploads = DEFAULT_MAX_CONCURRENT_UPLOADS;
            }
        }
    }

    /**
     * Returns whether BFAC Client is enabled.
     * @return true if enabled, false otherwise (default is false)
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Gets the base URL for the BFAC backend API.
     * @return The base URL loaded from configuration or default value
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Gets the maximum number of concurrent file uploads.
     * @return The max concurrent uploads (default is 5)
     */
    public int getMaxConcurrentUploads() {
        return maxConcurrentUploads;
    }
}
