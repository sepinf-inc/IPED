package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.DirectoryStream.Filter;

import dpf.sp.gpinf.indexer.analysis.LetterDigitTokenizer;
import dpf.sp.gpinf.indexer.io.FastPipedReader;
import dpf.sp.gpinf.indexer.io.ParsingReader;
import dpf.sp.gpinf.indexer.parsers.RawStringParser;
import dpf.sp.gpinf.indexer.search.SaveStateThread;

public class AdvancedIPEDConfig extends AbstractPropertiesConfigurable{
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

	private static int textSplitSize = 100000000;
    private static int textOverlapSize = 10000;

	public static final String CONFIG_FILE = "conf\\AdvancedConfig.txt"; //$NON-NLS-1$

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
	    if (value != null) {
	      value = value.trim();
	    }
	    if (value != null && !value.isEmpty()) {
	      RawStringParser.MIN_SIZE = Integer.valueOf(value);
	    }

	    value = properties.getProperty("entropyTest"); //$NON-NLS-1$
	    if (value != null) {
	      value = value.trim();
	    }
	    if (value != null && !value.isEmpty()) {
	      entropyTest = Boolean.valueOf(value);
	    }

	    value = properties.getProperty("textSplitSize"); //$NON-NLS-1$
	    if (value != null && !value.trim().isEmpty()) {
	    	textSplitSize = Integer.valueOf(value.trim());
	    }

	    ParsingReader.setTextSplitSize(textSplitSize);
	    ParsingReader.setTextOverlapSize(textOverlapSize);
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
}
