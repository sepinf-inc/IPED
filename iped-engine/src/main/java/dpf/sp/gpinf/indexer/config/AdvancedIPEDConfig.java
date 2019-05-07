package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.DirectoryStream.Filter;

import org.apache.tika.fork.ForkParser2;

import dpf.sp.gpinf.indexer.analysis.LetterDigitTokenizer;
import dpf.sp.gpinf.indexer.io.FastPipedReader;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.RawStringParser;
import dpf.sp.gpinf.indexer.search.SaveStateThread;
import dpf.sp.gpinf.indexer.util.FragmentingReader;

public class AdvancedIPEDConfig extends AbstractPropertiesConfigurable {
    long unallocatedFragSize = 1024 * 1024 * 1024;
    long minItemSizeToFragment = 100 * 1024 * 1024;

    boolean forceMerge;
    int timeOut;
    int timeOutPerMB;
    boolean embutirLibreOffice;
    boolean addFatOrphans;
    long minOrphanSizeToIgnore;
    int searchThreads;
    boolean autoManageCols;
    boolean entropyTest = true;
    boolean storeTermVectors = true;
    boolean filterNonLatinChars = false;
    boolean convertCharsToAscii = true;
    int maxTokenLength = 255;
    boolean preOpenImagesOnSleuth = false;
    boolean openImagesCacheWarmUpEnabled = false;
    int openImagesCacheWarmUpThreads = 255;

    private static int textSplitSize = 100000000;
    private static int textOverlapSize = 10000;

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

    public void processConfig(Path resource) throws IOException {
        super.processConfig(resource);

        String value = null;

        value = properties.getProperty("enableExternalParsing"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            ForkParser2.enabled = Boolean.valueOf(value.trim());
        }

        value = properties.getProperty("numExternalParsers"); //$NON-NLS-1$
        if (value != null && !value.trim().equalsIgnoreCase("auto")) { //$NON-NLS-1$
            ForkParser2.SERVER_POOL_SIZE = Integer.valueOf(value.trim());
        } else
            ForkParser2.SERVER_POOL_SIZE = Runtime.getRuntime().availableProcessors();

        value = properties.getProperty("externalParsingMaxMem"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            ForkParser2.SERVER_MAX_HEAP = value.trim();
        }

        value = properties.getProperty("unallocatedFragSize"); //$NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (value != null && !value.isEmpty()) {
            unallocatedFragSize = Long.valueOf(value);
        }

        value = properties.getProperty("minItemSizeToFragment"); //$NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (value != null && !value.isEmpty()) {
            minItemSizeToFragment = Long.valueOf(value);
        }

        value = properties.getProperty("forceMerge"); //$NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (value != null && value.equalsIgnoreCase("false")) { //$NON-NLS-1$
            forceMerge = false;
        }

        value = properties.getProperty("timeOut"); //$NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (value != null && !value.isEmpty()) {
            timeOut = Integer.valueOf(value);
        }
        FastPipedReader.setTimeout(timeOut);

        value = properties.getProperty("timeOutPerMB"); //$NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (value != null && !value.isEmpty()) {
            timeOutPerMB = Integer.valueOf(value);
        }

        value = properties.getProperty("embutirLibreOffice"); //$NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (value != null && !value.isEmpty()) {
            embutirLibreOffice = Boolean.valueOf(value);
        }

        value = properties.getProperty("extraCharsToIndex"); //$NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (value != null && !value.isEmpty()) {
            LetterDigitTokenizer.load(value);
        }

        value = properties.getProperty("convertCharsToLowerCase"); //$NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (value != null && !value.isEmpty()) {
            LetterDigitTokenizer.convertCharsToLowerCase = Boolean.valueOf(value);
        }

        value = properties.getProperty("storeTermVectors"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            storeTermVectors = Boolean.valueOf(value.trim());
        }

        value = properties.getProperty("maxTokenLength"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            maxTokenLength = Integer.valueOf(value.trim());
        }

        value = properties.getProperty("filterNonLatinChars"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            filterNonLatinChars = Boolean.valueOf(value.trim());
        }

        value = properties.getProperty("convertCharsToAscii"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            convertCharsToAscii = Boolean.valueOf(value.trim());
        }

        value = properties.getProperty("addFatOrphans"); //$NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (value != null && !value.isEmpty()) {
            addFatOrphans = Boolean.valueOf(value);
        }

        value = properties.getProperty("minOrphanSizeToIgnore"); //$NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (value != null && !value.isEmpty()) {
            minOrphanSizeToIgnore = Long.valueOf(value);
        }

        value = properties.getProperty("searchThreads"); //$NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (value != null && !value.isEmpty()) {
            searchThreads = Integer.valueOf(value);
        }

        value = properties.getProperty("maxBackups"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            SaveStateThread.MAX_BACKUPS = Integer.valueOf(value.trim());
        }

        value = properties.getProperty("backupInterval"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            SaveStateThread.BKP_INTERVAL = Long.valueOf(value.trim());
        }

        value = properties.getProperty("autoManageCols"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            autoManageCols = Boolean.valueOf(value.trim());
        }

        value = properties.getProperty("minRawStringSize"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            System.setProperty(RawStringParser.MIN_STRING_SIZE, value.trim());
        }

        value = properties.getProperty("entropyTest"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            entropyTest = Boolean.valueOf(value.trim());
            System.setProperty(IndexerDefaultParser.ENTROPY_TEST_PROP, value.trim());
        }

        value = properties.getProperty("textSplitSize"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            textSplitSize = Integer.valueOf(value.trim());
        }

        FragmentingReader.setTextSplitSize(textSplitSize);
        FragmentingReader.setTextOverlapSize(textOverlapSize);

        value = properties.getProperty("preOpenImagesOnSleuth"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            preOpenImagesOnSleuth = Boolean.valueOf(value.trim());
        }

        value = properties.getProperty("openImagesCacheWarmUpEnabled"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            openImagesCacheWarmUpEnabled = Boolean.valueOf(value.trim());
        }

        value = properties.getProperty("openImagesCacheWarmUpThreads"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            openImagesCacheWarmUpThreads = Integer.parseInt(value.trim());
        }
    }

    public long getUnallocatedFragSize() {
        return unallocatedFragSize;
    }

    public long getMinItemSizeToFragment() {
        return minItemSizeToFragment;
    }

    public boolean isForceMerge() {
        return forceMerge;
    }

    public int getTimeOut() {
        return timeOut;
    }

    public int getTimeOutPerMB() {
        return timeOutPerMB;
    }

    public boolean isEmbutirLibreOffice() {
        return embutirLibreOffice;
    }

    public boolean isAddFatOrphans() {
        return addFatOrphans;
    }

    public long getMinOrphanSizeToIgnore() {
        return minOrphanSizeToIgnore;
    }

    public int getSearchThreads() {
        return searchThreads;
    }

    public boolean isAutoManageCols() {
        return autoManageCols;
    }

    public boolean isEntropyTest() {
        return entropyTest;
    }

    public static int getTextSplitSize() {
        return textSplitSize;
    }

    public static int getTextOverlapSize() {
        return textOverlapSize;
    }

    public boolean isStoreTermVectors() {
        return storeTermVectors;
    }

    public boolean isFilterNonLatinChars() {
        return filterNonLatinChars;
    }

    public boolean isConvertCharsToAscii() {
        return convertCharsToAscii;
    }

    public int getMaxTokenLength() {
        return maxTokenLength;
    }

    public boolean isPreOpenImagesOnSleuth() {
        return preOpenImagesOnSleuth;
    }

    public boolean isOpenImagesCacheWarmUpEnabled() {
        return openImagesCacheWarmUpEnabled;
    }

    public int getOpenImagesCacheWarmUpThreads() {
        return openImagesCacheWarmUpThreads;
    }
}
