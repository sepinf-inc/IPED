package dpf.sp.gpinf.indexer.config;

import java.io.IOException;

import java.nio.file.Path;

public class ImageThumbTaskConfig extends AbstractTaskPropertiesConfig {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static final String ENABLE_PROP = "enableImageThumbs"; //$NON-NLS-1$

    private static final String CONFIG_FILE = "ImageThumbsConfig.txt"; //$NON-NLS-1$

    private boolean useGraphicsMagick = false;
    private boolean extractThumb = true;
    private boolean logGalleryRendering = false;
    private int minTimeout = 20;
    private int timeoutPerMB = 2;
    private int thumbSize = 160;
    private int galleryThreads = 1;

    public boolean isUseGraphicsMagick() {
        return useGraphicsMagick;
    }

    public boolean isExtractThumb() {
        return extractThumb;
    }

    public boolean isLogGalleryRendering() {
        return logGalleryRendering;
    }

    public int getMinTimeout() {
        return minTimeout;
    }

    public int getTimeoutPerMB() {
        return timeoutPerMB;
    }

    public int getThumbSize() {
        return thumbSize;
    }

    public int getGalleryThreads() {
        return galleryThreads;
    }

    @Override
    public String getTaskEnableProperty() {
        return ENABLE_PROP;
    }

    @Override
    public String getTaskConfigFileName() {
        return CONFIG_FILE;
    }

    @Override
    public void processTaskConfig(Path resource) throws IOException {

        properties.load(resource.toFile());

        String value = properties.getProperty("externalConversionTool"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            if (value.trim().equals("graphicsmagick")) { //$NON-NLS-1$
                useGraphicsMagick = true;
            }
        } else {
            useGraphicsMagick = false;
        }

        value = properties.getProperty("imgConvTimeout"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            minTimeout = Integer.valueOf(value.trim());
        }

        value = properties.getProperty("imgConvTimeoutPerMB"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            timeoutPerMB = Integer.valueOf(value.trim());
        }

        value = properties.getProperty("galleryThreads"); //$NON-NLS-1$
        if (value != null && !value.trim().equalsIgnoreCase("default")) { //$NON-NLS-1$
            galleryThreads = Integer.valueOf(value.trim());
        } else {
            galleryThreads = Runtime.getRuntime().availableProcessors();
        }

        value = properties.getProperty("imgThumbSize"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            thumbSize = Integer.valueOf(value.trim());
        }

        value = properties.getProperty("extractThumb"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            extractThumb = Boolean.valueOf(value.trim());
        }

        value = properties.getProperty("logGalleryRendering"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            logGalleryRendering = Boolean.valueOf(value.trim());
        }

    }

}
