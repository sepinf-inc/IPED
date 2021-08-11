package dpf.sp.gpinf.discord.cache;

import iped3.io.IItemBase;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

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
            numBlocks = (int) ((address & 0x03000000L) >> 24);
            fileSelector = (int) ((address & 0x00ff0000L) >> 16);
            startBlock = (int) (address & 0x0000FFFFL);
        }
    }

    public InputStream getInputStream(List<IItemBase> dataFiles, List<IItemBase> externalFiles) throws IOException {
        if (!initialized) {
            return null;
        }
        switch (fileType) {
            case 0:
                String fileNameStr = Long.toHexString(fileName);
                fileNameStr = (fileNameStr.length() < 6)
                        ? StringUtils.repeat((char) 0, fileNameStr.length() - 6) + fileNameStr
                        : fileNameStr;
                for (IItemBase extFile : externalFiles)
                    if (extFile.getName().contains("f_" + fileNameStr))
                        return FileUtils.openInputStream(extFile.getFile());
                return null;
            case 2:
            case 3:
            case 4:
                for (int i = 0; i < 4; i++) {
                    if (dataFiles.get(i).getName().equals(("data_" + fileSelector))) {
                        InputStream targetStream = FileUtils.openInputStream(dataFiles.get(i).getFile());
                        targetStream.skip(DataBlockFileHeader.getBlockHeaderSize()
                                + startBlock * (fileType == 2 ? 256 : (fileType == 3 ? 1024 : 4096)));
                        return targetStream;
                    }
                }
        }
        return null;
    }

}
