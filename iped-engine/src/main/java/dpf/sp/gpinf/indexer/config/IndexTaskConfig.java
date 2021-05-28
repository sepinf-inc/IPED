package dpf.sp.gpinf.indexer.config;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;

public class IndexTaskConfig extends AbstractTaskPropertiesConfig {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static final String ENABLE_PARAM = "indexFileContents";
    private static final String CONFIG_FILE = "IndexTaskConfig.txt";

    private boolean indexFileContents = true;
    private boolean indexCorruptedFiles = true;
    private boolean indexUnknownFiles = true;
    private boolean indexUnallocated = false;
    private boolean convertCharsToLowerCase = false;
    private boolean convertCharsToAscii = false;
    private boolean filterNonLatinChars = false;
    private boolean useNIOFSDirectory = false;
    private boolean forceMerge = false;
    private int textSplitSize = 10485760;
    private int textOverlapSize = 10000;
    private boolean storeTermVectors = true;
    private int maxTokenLength = 255;
    private int[] extraCharsToIndexArray;

    @Override
    public String getTaskEnableProperty() {
        return ENABLE_PARAM;
    }

    @Override
    public String getTaskConfigFileName() {
        return CONFIG_FILE;
    }

    @Override
    public void processTaskConfig(Path resource) throws IOException {

        properties.load(resource.toFile());

        String value = properties.getProperty("extraCharsToIndex"); //$NON-NLS-1$
        if (value != null) {
            extraCharsToIndexArray = convertExtraCharsToIndex(value.trim());
        }

        value = properties.getProperty("convertCharsToLowerCase"); //$NON-NLS-1$
        if (value != null) {
            convertCharsToLowerCase = Boolean.valueOf(value.trim());
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

        value = properties.getProperty("textSplitSize"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            textSplitSize = Integer.valueOf(value.trim());
        }

        value = properties.getProperty("useNIOFSDirectory"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            useNIOFSDirectory = Boolean.valueOf(value.trim());
        }

        value = properties.getProperty("forceMerge"); //$NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (value != null && value.equalsIgnoreCase("false")) { //$NON-NLS-1$
            forceMerge = false;
        }

    }

    private int[] convertExtraCharsToIndex(String chars) throws FileNotFoundException, IOException {

        ArrayList<Integer> codePoints = new ArrayList<Integer>();
        for (char c : chars.toCharArray()) {
            if (c != ' ') {
                codePoints.add((int) c);
            }
        }

        int[] extraCodePoints = null;
        if (codePoints.size() > 0) {
            extraCodePoints = new int[codePoints.size()];
            for (int i = 0; i < extraCodePoints.length; i++) {
                extraCodePoints[i] = codePoints.get(i);
            }
        }
        return extraCodePoints;
    }

    public boolean isIndexFileContents() {
        return indexFileContents;
    }

    public boolean isIndexCorruptedFiles() {
        return indexCorruptedFiles;
    }

    public boolean isIndexUnknownFiles() {
        return indexUnknownFiles;
    }

    public boolean isIndexUnallocated() {
        return indexUnallocated;
    }

    public boolean isConvertCharsToLowerCase() {
        return convertCharsToLowerCase;
    }

    public boolean isConvertCharsToAscii() {
        return convertCharsToAscii;
    }

    public boolean isFilterNonLatinChars() {
        return filterNonLatinChars;
    }

    public boolean isUseNIOFSDirectory() {
        return useNIOFSDirectory;
    }

    public boolean isForceMerge() {
        return forceMerge;
    }

    public int getTextSplitSize() {
        return textSplitSize;
    }

    public int getTextOverlapSize() {
        return textOverlapSize;
    }

    public int[] getExtraCharsToIndex() {
        return extraCharsToIndexArray;
    }

    public boolean isStoreTermVectors() {
        return storeTermVectors;
    }

    public int getMaxTokenLength() {
        return maxTokenLength;
    }

}
