package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.DirectoryStream.Filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import macee.core.EnabledInterface;

import java.nio.file.Path;

public class DocThumbTaskConfig extends AbstractPropertiesConfigurable implements EnabledInterface {

    private static final String ENABLE_PROP = "enableDocThumbs";
    private static final String CONFIG_FILE = "DocThumbsConfig.txt";

    private static final Logger logger = LoggerFactory.getLogger(DocThumbTaskConfig.class);

    private boolean enabled;
    private int pdfTimeout = 60;
    private int loTimeout = 180;
    private int timeoutIncPerMB = 2;
    private int thumbSize = 480;
    private boolean externalPdfConversion;
    private int maxPdfExternalMemory = 256;
    private boolean pdfEnabled;
    private boolean loEnabled;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

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
            String value = properties.getProperty(ENABLE_PROP);
            if(value != null) {
                enabled = Boolean.valueOf(value.trim());
            }
        }

        if (resource.getFileName().toString().equals(CONFIG_FILE)) {
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
        }

        if (!loEnabled && !pdfEnabled) {
            logger.warn("Both PDF and LibreOffice thumb generation disabled!");
            enabled = false;
        }

    }

}
