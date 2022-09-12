package iped.parsers.discord.cache;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

import iped3.IItemBase;

/**
 * @author PCF Campanini
 *
 */
public class CacheAddr {

    private boolean initialized;
	private int fileType;
	private int numBlocks;
	private int fileSelector;
	private int startBlock;
	private int fileName;
	private String fileNameStr;

	public boolean isInitialized() {
		return initialized;
	}

    public int getFileType() {
        return fileType;
    }

    public int getNumBlocks() {
        return numBlocks;
    }

	public int getFileSelector() {
		return fileSelector;
	}

	public int getStartBlock() {
		return startBlock;
	}

	public int getFileName() {
		return fileName;
	}

	public String getFileNameStr() {
		return fileNameStr;
	}
	
	public static class InputStreamNotAvailable extends IOException {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		private InputStreamNotAvailable() {
			super("Cannot open InputStream for this CacheAddr.");
		}
	}

	/**
	 * Creation of Cache Address as defined in:
	 * https://forensicswiki.xyz/wiki/index.php?title=Chrome_Disk_Cache_Format
	 * 
	 * @param address
	 * 
	 */
	public CacheAddr(long address) throws IOException {

		initialized = (address & 0x80000000L) == 0x80000000L;
		fileType = (int) ((address & 0x70000000L) >> 28);

		if (fileType == 0) {
			fileName = (int) (address & 0x0FFFFFFFL);
		} else {
			numBlocks = (int) ((address & 0x03000000L) >> 24) + 1;
			fileSelector = (int) ((address & 0x00ff0000L) >> 16);
			startBlock = (int) (address & 0x0000FFFFL);
		}

		if (fileType == 0) {
			fileNameStr = Long.toHexString(fileName);
			if (fileNameStr.length() < 6) {
				fileNameStr = StringUtils.repeat('0', 6 - fileNameStr.length()) + fileNameStr;
			}

			fileNameStr = "f_" + fileNameStr;
		} else {
			fileNameStr = "data_" + fileSelector;
		}

	}

	private int getBlockSize() {
		return fileType == 2 ? 256 : (fileType == 3 ? 1024 : 4096);
	}
	
	public InputStream getInputStream(List<IItemBase> dataFiles, List<IItemBase> externalFiles) throws IOException {
		if (!initialized) {
			throw new InputStreamNotAvailable();
		}
		
		RandomAccessFile raf;
		
		switch (fileType) {
		case 0:
             for (IItemBase extFile : externalFiles)
 				if (extFile.getName().equals(fileNameStr)) {
                        raf = new RandomAccessFile(extFile.getTempFile(), "r");
 					return new RandomInputStream(raf, raf.getFilePointer());
 				}
			break;
		case 2:
		case 3:
		case 4:
			for (IItemBase dataFile : dataFiles)
				if (dataFile.getName().equals(("data_" + fileSelector))) {
                        raf = new RandomAccessFile(dataFile.getTempFile(), "r");
					raf.seek(8192 + startBlock * getBlockSize());
		            return new RandomInputStream(raf, raf.getFilePointer());
				}
		}
		
		throw new InputStreamNotAvailable();
	}

}
