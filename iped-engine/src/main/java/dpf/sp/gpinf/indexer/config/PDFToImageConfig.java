package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

import org.apache.tika.fork.ForkParser2;

import java.nio.file.DirectoryStream.Filter;

import dpf.sp.gpinf.indexer.parsers.PDFOCRTextParser;
import dpf.sp.gpinf.indexer.parsers.util.PDFToImage;

public class PDFToImageConfig extends AbstractPropertiesConfigurable {
    public static final String MAXPDFTextSize2OCR = "maxPDFTextSize2OCR";
    public static final String PDFToImgResolution = "pdfToImgResolution";
    public static final String PDFToImgLib = "pdfToImgLib";
    public static final String ExternalPdfToImgConv = "externalPdfToImgConv";
    public static final String ExternalConvMaxMem = "externalConvMaxMem";
    public static final String ProcessImagesInPDFs = "processImagesInPDFs";
    public static final String CONFIG_FILE = "conf/AdvancedConfig.txt"; //$NON-NLS-1$

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
    public void processConfigs(List<Path> resources) throws IOException {
        for (Iterator<Path> iterator = resources.iterator(); iterator.hasNext();) {
            Path path = iterator.next();
            processConfig(path);
        }
    }

    public void processConfig(Path resource) throws IOException {
        super.processConfig(resource);

        String value = null;

        value = properties.getProperty("pdfToImgResolution"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            System.setProperty(PDFToImage.RESOLUTION_PROP, value.trim());
        }

        value = properties.getProperty("pdfToImgLib"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            System.setProperty(PDFToImage.PDFLIB_PROP, value.trim());
        }

        value = properties.getProperty("externalPdfToImgConv"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            System.setProperty(PDFToImage.EXTERNAL_CONV_PROP, value.trim());
        }
        // do not open extra processes for OCR if forkParser is enabled
        if (ForkParser2.enabled) {
            System.setProperty(PDFToImage.EXTERNAL_CONV_PROP, "false");
        }

        value = properties.getProperty("externalConvMaxMem"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            System.setProperty(PDFToImage.EXTERNAL_CONV_MAXMEM_PROP, value.trim());
        }

        value = properties.getProperty("maxPDFTextSize2OCR"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            System.setProperty(PDFOCRTextParser.MAX_CHARS_TO_OCR, value.trim());
        }

        value = properties.getProperty("processImagesInPDFs"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            System.setProperty(PDFOCRTextParser.PROCESS_INLINE_IMAGES, value.trim());
        }

        value = properties.getProperty("sortPDFChars"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            System.setProperty(PDFOCRTextParser.SORT_PDF_CHARS, value.trim());
        }

    }
}
