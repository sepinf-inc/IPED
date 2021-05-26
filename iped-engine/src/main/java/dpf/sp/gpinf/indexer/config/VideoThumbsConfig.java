package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.DirectoryStream.Filter;

import macee.core.EnabledInterface;

import java.nio.file.Path;

public class VideoThumbsConfig extends AbstractPropertiesConfigurable implements EnabledInterface {

    /**
     * Constante com o nome utilizado para o arquivo de propriedades.
     */
    private static final String CONFIG_FILE = "VideoThumbsConfig.txt";

    private static final String ENABLED_PROP = "enableVideoThumbs";

    private static final String LAYOUT = "Layout";

    private static final String VERBOSE = "Verbose";

    private static final String TIMEOUTS = "Timeouts";

    private static final String GALLERY_THUMBS = "GalleryThumbs";

    /**
     * If the task is enabled or not.
     */
    private boolean taskEnabled;

    /**
     * Image width of extracted frame.
     */
    private int width = 200;

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

    private int galleryThumbWidth = -1;
    private int galleryMinThumbs = -1;
    private int galleryMaxThumbs = -1;

    @Override
    public boolean isEnabled() {
        return taskEnabled;
    }

    public int getWidth() {
        return width;
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

    public int getGalleryThumbWidth() {
        return galleryThumbWidth;
    }

    public int getGalleryMinThumbs() {
        return galleryMinThumbs;
    }

    public int getGalleryMaxThumbs() {
        return galleryMaxThumbs;
    }

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return new Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
                return entry.endsWith(CONFIG_FILE) || entry.endsWith(IPEDConfig.CONFIG_FILE);
            }
        };
    }

    @Override
    public void processConfig(Path resource) throws IOException {

        properties.load(resource.toFile());

        if (resource.getFileName().toString().equals(IPEDConfig.CONFIG_FILE)) {
            String value = properties.getProperty(ENABLED_PROP);
            if (value != null) {
                taskEnabled = Boolean.valueOf(value.trim());
            }
        } else {
            // Layout
            String value = properties.getProperty(LAYOUT); // $NON-NLS-1$
            if (value != null) {
                String[] vals = value.trim().split(","); //$NON-NLS-1$
                if (vals.length == 3) {
                    width = Integer.parseInt(vals[0].trim());
                    columns = Integer.parseInt(vals[1].trim());
                    rows = Integer.parseInt(vals[2].trim());
                }
            }

            // Verbose do MPlayer
            value = properties.getProperty(VERBOSE); // $NON-NLS-1$
            if (value != null && value.trim().equalsIgnoreCase("true")) { //$NON-NLS-1$
                verbose = true;
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
                    galleryThumbWidth = Integer.parseInt(vals[0].trim());
                    galleryMinThumbs = Integer.parseInt(vals[1].trim());
                    galleryMaxThumbs = Integer.parseInt(vals[2].trim());
                }
            }
        }

    }

}
