package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;

import dpf.sp.gpinf.indexer.parsers.OCRParser;

public class OCRConfig extends AbstractPropertiesConfigurable {
    private static final String OCRLanguage = "OCRLanguage";
    private static final String minFileSize2OCR = "minFileSize2OCR";
    private static final String maxFileSize2OCR = "maxFileSize2OCR";
    private static final String pageSegMode = "pageSegMode";
    private static final String processNonStandard = "processNonStandard";
    private static final String maxConvImageSize = "maxConvImageSize";

    public static final String CONFIG_FILE = "conf/AdvancedConfig.txt"; //$NON-NLS-1$

    private Boolean enableOCR;

    public static final DirectoryStream.Filter<Path> filter = new Filter<Path>() {
        @Override
        public boolean accept(Path entry) throws IOException {
            return entry.endsWith(CONFIG_FILE) || entry.endsWith(IPEDConfig.CONFIG_FILE);
        }
    };

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return filter;
    }

    @Override
    public void processConfig(Path resource) throws IOException {
        super.processConfig(resource);

        String value = null;

        value = properties.getProperty("enableOCR"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            enableOCR = Boolean.valueOf(value.trim());
            System.setProperty(OCRParser.ENABLE_PROP, value.trim());
        }

        value = properties.getProperty(OCRLanguage);
        if (value != null && !value.trim().isEmpty()) {
            System.setProperty(OCRParser.LANGUAGE_PROP, value.trim());
        }

        value = properties.getProperty(minFileSize2OCR);
        if (value != null && !value.trim().isEmpty()) {
            System.setProperty(OCRParser.MIN_SIZE_PROP, value.trim());
        }

        value = properties.getProperty(maxFileSize2OCR);
        if (value != null && !value.trim().isEmpty()) {
            System.setProperty(OCRParser.MAX_SIZE_PROP, value.trim());
        }

        value = properties.getProperty(pageSegMode);
        if (value != null && !value.trim().isEmpty()) {
            System.setProperty(OCRParser.PAGE_SEGMODE_PROP, value.trim());
        }

        value = properties.getProperty(processNonStandard);
        if (value != null && !value.trim().isEmpty()) {
            System.setProperty(OCRParser.PROCESS_NON_STANDARD_FORMATS_PROP, value.trim());
        }

        value = properties.getProperty(maxConvImageSize);
        if (value != null && !value.trim().isEmpty()) {
            System.setProperty(OCRParser.MAX_CONV_IMAGE_SIZE_PROP, value.trim());
        }

    }

    public Boolean isOCREnabled() {
        return enableOCR;
    }

}
