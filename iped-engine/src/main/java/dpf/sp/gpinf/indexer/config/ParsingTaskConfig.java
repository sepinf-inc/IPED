package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.Path;

import dpf.sp.gpinf.indexer.parsers.PDFOCRTextParser;
import dpf.sp.gpinf.indexer.parsers.RawStringParser;

public class ParsingTaskConfig extends AbstractTaskPropertiesConfig {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public static final String ENABLE_PARAM = "enableFileParsing";
    private static final String CONF_FILE = "ParsingTaskConfig.xml";

    private boolean enableExternalParsing = false;
    private int numExternalParsers;;
    private String externalParsingMaxMem = "512";
    private boolean parseCorruptedFiles = true;
    private boolean parseUnknownFiles = true;
    private int timeOut = 180;
    private int timeOutPerMB = 2;
    private boolean storeTextCacheOnDisk = true;
    private boolean sortPDFChars;
    private boolean processImagesInPDFs = false;
    private String phoneParsersToUse;

    @Override
    public String getTaskEnableProperty() {
        return ENABLE_PARAM;
    }

    @Override
    public String getTaskConfigFileName() {
        return CONF_FILE;
    }

    @Override
    public void processTaskConfig(Path resource) throws IOException {

        String value = properties.getProperty("parseCorruptedFiles"); //$NON-NLS-1$
        if (value != null) {
            parseCorruptedFiles = Boolean.valueOf(value.trim());
        }

        value = properties.getProperty("parseUnknownFiles"); //$NON-NLS-1$
        if (value != null) {
            parseUnknownFiles = Boolean.valueOf(value.trim());
        }

        value = properties.getProperty("enableExternalParsing"); //$NON-NLS-1$
        if (value != null) {
            enableExternalParsing = Boolean.valueOf(value.trim());
        }

        value = properties.getProperty("numExternalParsers"); //$NON-NLS-1$
        if (value != null && !value.trim().equalsIgnoreCase("auto")) { //$NON-NLS-1$
            numExternalParsers = Integer.valueOf(value.trim());
        } else {
            OCRConfig ocrconfig = ConfigurationManager.findObject(OCRConfig.class);
            if (ocrconfig.isOCREnabled() == null) {
                throw new RuntimeException(OCRConfig.class.getSimpleName() + " must be loaded before "
                        + AdvancedIPEDConfig.class.getSimpleName());
            }
            int div = ocrconfig.isOCREnabled() ? 1 : 2;
            numExternalParsers = (int) Math.ceil((float) Runtime.getRuntime().availableProcessors() / div);
        }

        value = properties.getProperty("externalParsingMaxMem"); //$NON-NLS-1$
        if (value != null) {
            externalParsingMaxMem = value.trim();
        }

        value = properties.getProperty("timeOut"); //$NON-NLS-1$
        if (value != null) {
            timeOut = Integer.valueOf(value.trim());
        }

        value = properties.getProperty("timeOutPerMB"); //$NON-NLS-1$
        if (value != null) {
            timeOutPerMB = Integer.valueOf(value.trim());
        }

        value = properties.getProperty("minRawStringSize"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            System.setProperty(RawStringParser.MIN_STRING_SIZE, value.trim());
        }

        value = properties.getProperty("storeTextCacheOnDisk"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            storeTextCacheOnDisk = Boolean.valueOf(value.trim());
        }

        value = properties.getProperty("sortPDFChars"); //$NON-NLS-1$
        if (value != null) {
            sortPDFChars = Boolean.valueOf(value.trim());
        }

        value = properties.getProperty("processImagesInPDFs"); //$NON-NLS-1$
        if (value != null) {
            processImagesInPDFs = Boolean.valueOf(value.trim());
        }

        value = properties.getProperty("phoneParsersToUse"); //$NON-NLS-1$
        if (value != null) {
            phoneParsersToUse = value.trim();
        }

    }

    public boolean isEnableExternalParsing() {
        return enableExternalParsing;
    }

    public int getNumExternalParsers() {
        return numExternalParsers;
    }

    public String getExternalParsingMaxMem() {
        return externalParsingMaxMem;
    }

    public boolean isParseCorruptedFiles() {
        return parseCorruptedFiles;
    }

    public boolean isParseUnknownFiles() {
        return parseUnknownFiles;
    }

    public int getTimeOut() {
        return timeOut;
    }

    public int getTimeOutPerMB() {
        return timeOutPerMB;
    }

    public boolean isStoreTextCacheOnDisk() {
        return storeTextCacheOnDisk;
    }

    public boolean isSortPDFChars() {
        return sortPDFChars;
    }

    public boolean isProcessImagesInPDFs() {
        return processImagesInPDFs;
    }

    public String getPhoneParsersToUse() {
        return phoneParsersToUse;
    }

}
