package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DocThumbTaskConfig extends AbstractTaskPropertiesConfig {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private static final String ENABLE_PROP = "enableDocThumbs";
    private static final String CONFIG_FILE = "DocThumbsConfig.txt";

    private static final Logger logger = LoggerFactory.getLogger(DocThumbTaskConfig.class);

    private int pdfTimeout = 60;
    private int loTimeout = 180;
    private int timeoutIncPerMB = 2;
    private int thumbSize = 480;
    private boolean externalPdfConversion;
    private int maxPdfExternalMemory = 256;
    private boolean pdfEnabled;
    private boolean loEnabled;

    public int getPdfTimeout() {
        return pdfTimeout;
    }

    public int getLoTimeout() {
        return loTimeout;
    }

    public int getTimeoutIncPerMB() {
        return timeoutIncPerMB;
    }

    public int getThumbSize() {
        return thumbSize;
    }

    public boolean isExternalPdfConversion() {
        return externalPdfConversion;
    }

    public int getMaxPdfExternalMemory() {
        return maxPdfExternalMemory;
    }

    public boolean isPdfEnabled() {
        return pdfEnabled;
    }

    public boolean isLoEnabled() {
        return loEnabled;
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

        String value = properties.getProperty("pdfThumbs");
        if (value != null) {
            value = value.trim();
            if ("external".equalsIgnoreCase(value)) {
                externalPdfConversion = true;
                pdfEnabled = true;
            } else if ("internal".equalsIgnoreCase(value)) {
                externalPdfConversion = false;
                pdfEnabled = true;
            }
        }

        value = properties.getProperty("libreOfficeThumbs");
        if (value != null) {
            value = value.trim();
            if ("external".equalsIgnoreCase(value)) {
                loEnabled = true;
            }
        }

        value = properties.getProperty("pdfTimeout");
        if (value != null && !value.trim().isEmpty()) {
            pdfTimeout = Integer.parseInt(value);
        }

        value = properties.getProperty("libreOfficeTimeout");
        if (value != null && !value.trim().isEmpty()) {
            loTimeout = Integer.parseInt(value);
        }

        value = properties.getProperty("timeoutIncPerMB");
        if (value != null && !value.trim().isEmpty()) {
            timeoutIncPerMB = Integer.parseInt(value);
        }

        value = properties.getProperty("maxPdfExternalMemory");
        if (value != null && !value.trim().isEmpty()) {
            maxPdfExternalMemory = Integer.parseInt(value);
        }

        value = properties.getProperty("thumbSize");
        if (value != null && !value.trim().isEmpty()) {
            thumbSize = Integer.valueOf(value.trim());
        }

        if (!loEnabled && !pdfEnabled) {
            logger.warn("Both PDF and LibreOffice thumb generation disabled!");
            super.setEnabled(false);
        }

    }

}
