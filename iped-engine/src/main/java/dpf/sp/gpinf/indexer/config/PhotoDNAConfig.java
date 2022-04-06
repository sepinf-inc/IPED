package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.Path;

import dpf.sp.gpinf.indexer.util.UTF8Properties;

public class PhotoDNAConfig extends AbstractTaskPropertiesConfig {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public static final String CONFIG_FILE = "PhotoDNAConfig.txt";

    public static final String ENABLE_PHOTO_DNA = "enablePhotoDNA";

    public static final String USE_THUMBNAIL = "computeFromThumbnail";

    public static final String MIN_FILE_SIZE = "minFileSize";

    public static final String SKIP_HASH_DB_FILES = "skipHashDBFiles";

    public static final String STATUS_HASH_DB_FILTER = "statusHashDBFilter";

    public static final String MAX_SIMILARITY_DISTANCE = "maxSimilarityDistance";

    public static final String TEST_ROTATED_FLIPPED = "searchRotatedAndFlipped";

    private boolean useThumbnail = true;

    private int minFileSize = 10000;

    private boolean skipHashDBFiles = true;

    public boolean isUseThumbnail() {
        return useThumbnail;
    }

    public int getMinFileSize() {
        return minFileSize;
    }

    public boolean isSkipHashDBFiles() {
        return skipHashDBFiles;
    }

    @Override
    public String getTaskEnableProperty() {
        return ENABLE_PHOTO_DNA;
    }

    @Override
    public String getTaskConfigFileName() {
        return CONFIG_FILE;
    }

    @Override
    public void processProperties(UTF8Properties properties) {

        String value = properties.getProperty(USE_THUMBNAIL);
        if (value != null && !value.trim().isEmpty())
            useThumbnail = Boolean.valueOf(value.trim());

        value = properties.getProperty(MIN_FILE_SIZE);
        if (value != null && !value.trim().isEmpty())
            minFileSize = Integer.valueOf(value.trim());

        value = properties.getProperty(SKIP_HASH_DB_FILES);
        if (value != null && !value.trim().isEmpty())
            skipHashDBFiles = Boolean.valueOf(value.trim());

    }

}
