package iped.engine.config;

import iped.utils.UTF8Properties;

public class VideoThumbsConfig extends AbstractTaskPropertiesConfig {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    /**
     * Constante com o nome utilizado para o arquivo de propriedades.
     */
    private static final String CONFIG_FILE = "VideoThumbsConfig.txt";

    private static final String ENABLED_PROP = "enableVideoThumbs";

    private static final String LAYOUT = "Layout";

    private static final String VERBOSE = "Verbose";

    private static final String TIMEOUTS = "Timeouts";

    private static final String GALLERY_THUMBS = "GalleryThumbs";

    private static final String ORIGINAL_DIMENSION = "enableVideoThumbsOriginalDimension";

    private static final String THUMBS_SUBITEMS = "enableVideoThumbsSubitems";

    private static final String MAX_DIMENSION_SIZE = "maxDimensionSize";

    private static final String NUM_FRAMES_EQUATION = "numFramesEquation";

    private static final String COMPRESSION = "compression";

    /**
     * Maximum size (width/height) of extracted frames.
     */
    private int size = 240;

    /**
     * Number of image columns.
     */
    private int columns = 3;

    /**
     * Number of image rows.
     */
    private int rows = 6;

    /**
     * Redirects mplayer output to log for debugging purposes.
     */
    private boolean verbose = false;

    /**
     * Timeout for first mplayer run, can take long because of font loading.
     */
    private int timeoutFirst = 180000;

    /**
     * Timeout to get video properties.
     */
    private int timeoutInfo = 10000;

    /**
     * Timeout to extract video frames.
     */
    private int timeoutProcess = 15000;

    private int galleryThumbSize = -1;
    private int galleryMinThumbs = -1;
    private int galleryMaxThumbs = -1;

    private int compression = 60;

    /**
     * Extracts video frames using original video resolution.
     */
    private Boolean videoThumbsOriginalDimension = false;

    /**
     * Extracts video frames as video subitems, for video frame pipelining purpouse
     * .
     */
    private Boolean videoThumbsSubitems = false;

    /**
     * Max dimension size to use when extracting frames. Currently this has
     * precedence over other options.
     */
    private int maxDimensionSize = 1024;

    /**
     * Javascript equation to compute number of extracted thumbs from video duration
     * in seconds.
     */
    private String numFramesEquation;

    public int getSize() {
        return size;
    }

    public void setSize(int value) {
        size = value;
    }

    public int getColumns() {
        return columns;
    }

    public int getRows() {
        return rows;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public int getTimeoutFirst() {
        return timeoutFirst;
    }

    public int getTimeoutInfo() {
        return timeoutInfo;
    }

    public int getTimeoutProcess() {
        return timeoutProcess;
    }

    public int getGalleryThumbSize() {
        return galleryThumbSize;
    }

    public int getGalleryMinThumbs() {
        return galleryMinThumbs;
    }

    public int getGalleryMaxThumbs() {
        return galleryMaxThumbs;
    }

    public Boolean getVideoThumbsOriginalDimension() {
        return videoThumbsOriginalDimension;
    }

    public Boolean getVideoThumbsSubitems() {
        return videoThumbsSubitems;
    }

    public int getMaxDimensionSize() {
        return maxDimensionSize;
    }

    public int getCompression() {
        return compression;
    }

    public String getNumFramesEquation() {
        return this.numFramesEquation;
    }

    @Override
    public String getTaskEnableProperty() {
        return ENABLED_PROP;
    }

    @Override
    public String getTaskConfigFileName() {
        return CONFIG_FILE;
    }

    @Override
    public void processProperties(UTF8Properties properties) {

        // Layout
        String value = properties.getProperty(LAYOUT); // $NON-NLS-1$
        if (value != null) {
            String[] vals = value.trim().split(","); //$NON-NLS-1$
            if (vals.length == 3) {
                size = Integer.parseInt(vals[0].trim());
                columns = Integer.parseInt(vals[1].trim());
                rows = Integer.parseInt(vals[2].trim());
            }
        }

        // Verbose do MPlayer
        value = properties.getProperty(VERBOSE); // $NON-NLS-1$
        if (value != null && value.trim().equalsIgnoreCase("true")) { //$NON-NLS-1$
            verbose = true;
        }

        // VideoThumbsOriginalDimension
        value = properties.getProperty(ORIGINAL_DIMENSION); // $NON-NLS-1$
        if (value != null && value.trim().equalsIgnoreCase("true")) { //$NON-NLS-1$
            videoThumbsOriginalDimension = true;
        }

        // VideoThumbsSubitems
        value = properties.getProperty(THUMBS_SUBITEMS); // $NON-NLS-1$
        if (value != null && value.trim().equalsIgnoreCase("true")) { //$NON-NLS-1$
            videoThumbsSubitems = true;
        }

        // Timeouts
        value = properties.getProperty(TIMEOUTS); // $NON-NLS-1$
        if (value != null) {
            String[] vals = value.trim().split(","); //$NON-NLS-1$
            if (vals.length == 3) {
                timeoutFirst = 1000 * Integer.parseInt(vals[0].trim());
                timeoutInfo = 1000 * Integer.parseInt(vals[1].trim());
                timeoutProcess = 1000 * Integer.parseInt(vals[2].trim());
            }
        }

        // Gallery Thumbs Configuration
        value = properties.getProperty(GALLERY_THUMBS); // $NON-NLS-1$
        if (value != null) {
            String[] vals = value.trim().split(","); //$NON-NLS-1$
            if (vals.length == 3) {
                galleryThumbSize = Integer.parseInt(vals[0].trim());
                galleryMinThumbs = Integer.parseInt(vals[1].trim());
                galleryMaxThumbs = Integer.parseInt(vals[2].trim());
            }
        }

        value = properties.getProperty(MAX_DIMENSION_SIZE);
        if (value != null) {
            maxDimensionSize = Integer.parseInt(value.trim());
        }

        value = properties.getProperty(COMPRESSION);
        if (value != null) {
            compression = Integer.parseInt(value.trim());
            if (compression < 0) {
                compression = 0;
            } else if (compression > 100) {
                compression = 100;
            }
        }

        value = properties.getProperty(NUM_FRAMES_EQUATION);
        if (value != null) {
            numFramesEquation = value.trim();
        }

    }

}
