package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.Path;

import dpf.sp.gpinf.indexer.util.UTF8Properties;

public class PhotoDNALookupConfig extends AbstractTaskPropertiesConfig {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public static final String CONFIG_FILE = "PhotoDNALookupConfig.txt";

    public static final String ENABLE_PHOTO_DNA = "enablePhotoDNALookup";

    public static final String STATUS_HASH_DB_FILTER = "statusHashDBFilter";

    public static final String MAX_SIMILARITY_DISTANCE = "maxSimilarityDistance";

    public static final String TEST_ROTATED_FLIPPED = "searchRotatedAndFlipped";

    private int maxDistance = 40000;

    private boolean rotateAndFlip = true;

    private String statusHashDBFilter = "";

    public int getMaxDistance() {
        return maxDistance;
    }

    public boolean isRotateAndFlip() {
        return rotateAndFlip;
    }

    public String getStatusHashDBFilter() {
        return statusHashDBFilter;
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

        String value = properties.getProperty(STATUS_HASH_DB_FILTER);
        if (value != null && !value.trim().isEmpty())
            statusHashDBFilter = value.trim();

        value = properties.getProperty(MAX_SIMILARITY_DISTANCE);
        if (value != null && !value.trim().isEmpty())
            maxDistance = Integer.valueOf(value.trim());

        value = properties.getProperty(TEST_ROTATED_FLIPPED);
        if (value != null && !value.trim().isEmpty())
            rotateAndFlip = Boolean.valueOf(value.trim());

    }

}
