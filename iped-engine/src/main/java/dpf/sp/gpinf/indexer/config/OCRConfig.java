package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;

import dpf.sp.gpinf.indexer.util.UTF8Properties;

import java.nio.file.Path;

public class OCRConfig extends AbstractPropertiesConfigurable {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public static final String CONFIG_FILE = "conf/OCRConfig.txt"; //$NON-NLS-1$

    private Boolean enableOCR;
    private String ocrLanguage;
    private String minFileSize2OCR;
    private String maxFileSize2OCR;
    private String pageSegMode;
    private String pdfToImgResolution;
    private String pdfToImgLib;
    private String externalPdfToImgConv;
    private String externalConvMaxMem;
    private String maxPdfTextSize2OCR;

    public static final DirectoryStream.Filter<Path> filter = new Filter<Path>() {
        @Override
        public boolean accept(Path entry) throws IOException {
            return entry.endsWith(CONFIG_FILE);
        }
    };

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return filter;
    }

    @Override
    public void processProperties(UTF8Properties properties) {

        String value = properties.getProperty("enableOCR"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            enableOCR = Boolean.valueOf(value.trim());
        }

        value = properties.getProperty("OCRLanguage"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            ocrLanguage = value.trim();
        }

        value = properties.getProperty("minFileSize2OCR"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            minFileSize2OCR = value.trim();
        }

        value = properties.getProperty("maxFileSize2OCR"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            maxFileSize2OCR = value.trim();
        }

        value = properties.getProperty("pageSegMode"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            pageSegMode = value.trim();
        }

        value = properties.getProperty("pdfToImgResolution"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            pdfToImgResolution = value.trim();
        }

        value = properties.getProperty("pdfToImgLib"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            pdfToImgLib = value.trim();
        }

        value = properties.getProperty("externalPdfToImgConv"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            externalPdfToImgConv = value.trim();
        }

        value = properties.getProperty("externalConvMaxMem"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            externalConvMaxMem = value.trim();
        }

        value = properties.getProperty("maxPDFTextSize2OCR"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            maxPdfTextSize2OCR = value.trim();
        }

    }

    public Boolean isOCREnabled() {
        return enableOCR;
    }

    public String getOcrLanguage() {
        return ocrLanguage;
    }

    public String getMinFileSize2OCR() {
        return minFileSize2OCR;
    }

    public String getMaxFileSize2OCR() {
        return maxFileSize2OCR;
    }

    public String getPageSegMode() {
        return pageSegMode;
    }

    public String getPdfToImgResolution() {
        return pdfToImgResolution;
    }

    public String getPdfToImgLib() {
        return pdfToImgLib;
    }

    public String getExternalPdfToImgConv() {
        return externalPdfToImgConv;
    }

    public String getExternalConvMaxMem() {
        return externalConvMaxMem;
    }

    public String getMaxPdfTextSize2OCR() {
        return maxPdfTextSize2OCR;
    }

}
