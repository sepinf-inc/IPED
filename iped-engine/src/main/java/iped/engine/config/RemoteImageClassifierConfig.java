package iped.engine.config;

import iped.utils.UTF8Properties;

public class RemoteImageClassifierConfig extends AbstractTaskPropertiesConfig {

    private static final long serialVersionUID = 1L;

    /**
     * Config file name and enable/disable property.
     */
    private static final String CONFIG_FILE = "RemoteImageClassifierConfig.txt";
    private static final String ENABLE_PROP = "enableRemoteImageClassifier";

    /**
     * Constants mapping to properties name in config file.
     */
    private static final String URL = "url";
    private static final String BATCH_SIZE = "batchSize";
    private static final String LABELING_THRESHOLD = "labelingThreshold";
    private static final String SKIP_SIZE = "skipSize";
    private static final String SKIP_DIMENSION = "skipDimension";
    private static final String SKIP_HASH_DB_FILES = "skipHashDBFiles";
    private static final String VALIDATE_SSL = "validateSSL";

    // URL of the service/central node used by the RemoteImageClassifier implementation
    private String url;

    // Maximum number of thumbs to be included in the zip file to send to the server
    private int batchSize = 50;

    // Threshold used to decide if an image is labeled with one class
    private double labelingThreshold = 60;

    // Skip classification of images/videos smaller than a given file size (in bytes; '0' = do not skip)
    private int skipSize = 0;

    // Skip classification of images/videos smaller than a given dimension, i.e. height or width (in pixels; '0' = do not skip)
    private int skipDimension = 0;

    // Skip classification of images/videos with hits on IPED hashesDB database (if 'hashesDB' is not configured in 'LocalConfig.txt' or 'false', do not skip)
    private boolean skipHashDBFiles = true;   

    // Validate server SSL certificate
    private boolean validateSSL = false;

    @Override
    public String getTaskEnableProperty() {
        return ENABLE_PROP;
    }

    @Override
    public String getTaskConfigFileName() {
        return CONFIG_FILE;
    }

    public String getUrl() {
        return url;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public double getLabelingThreshold() {
        return labelingThreshold;
    }

    public int getSkipSize() {
        return skipSize;
    }

    public int getSkipDimension() {
        return skipDimension;
    }

    public boolean isSkipHashDBFiles() {
        return skipHashDBFiles;
    }

    public boolean isValidateSSL() {
        return validateSSL;
    }

    @Override
    void processProperties(UTF8Properties properties) {

        String value = properties.getProperty(URL);
        if (value != null && !value.trim().isEmpty())
            url = value.trim();

        value = properties.getProperty(BATCH_SIZE);
        if (value != null && !value.trim().isEmpty()) {
            batchSize = Integer.valueOf(value.trim());
            // enforce minimum value
            if (batchSize < 1)
                batchSize = 1;
        }

        value = properties.getProperty(LABELING_THRESHOLD);
        if (value != null && !value.trim().isEmpty()) {
            labelingThreshold = Double.parseDouble(value.trim());
            // enforce range [0, 100]
            if (labelingThreshold < 0) {
                labelingThreshold = 0;
            } else if (labelingThreshold > 100) {
                labelingThreshold = 100;
            }
        }

        value = properties.getProperty(SKIP_SIZE);
        if (value != null && !value.trim().isEmpty()) {
            skipSize = Integer.valueOf(value.trim());
            // enforce minimum value
            if (skipSize < 0)
                skipSize = 0;
        }

        value = properties.getProperty(SKIP_DIMENSION);
        if (value != null && !value.trim().isEmpty()) {
            skipDimension = Integer.valueOf(value.trim());
            // enforce minimum value
            if (skipDimension < 0)
                skipDimension = 0;
        }

        value = properties.getProperty(SKIP_HASH_DB_FILES);
        if (value != null && !value.trim().isEmpty())
            skipHashDBFiles = Boolean.valueOf(value.trim());

        value = properties.getProperty(VALIDATE_SSL);
        if (value != null && !value.trim().isEmpty())
            validateSSL = Boolean.valueOf(value.trim());
    
    }

}
