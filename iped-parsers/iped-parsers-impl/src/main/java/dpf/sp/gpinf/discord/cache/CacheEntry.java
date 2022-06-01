package dpf.sp.gpinf.discord.cache;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;

import org.apache.commons.io.IOUtils;

import iped3.IItemBase;

/**
 * @author PCF Campanini
 *
 */
public class CacheEntry {

    private long hash;
    private CacheAddr nextEntry;
    private CacheAddr rankingsNode;
    private int reuseCount;
    private int refetchCount;
    private int state;
    private long creationTime;
    private int keyDataSize;
    private CacheAddr longKeyAddress;
    private int[] dataStreamSize;
    private CacheAddr[] dataStreamAdresses;
    private long flags;
    private int[] paddings;
    private long selfHash;
    private byte[] keyData;
    private List<IItemBase> dataFiles;
    private List<IItemBase> externalFiles;

    public long getHash() {
        return hash;
    }

    public CacheAddr getNextEntry() {
        return nextEntry;
    }

    public CacheAddr getRankingsNode() {
        return rankingsNode;
    }

    public int getReuseCount() {
        return reuseCount;
    }

    public int getRefetchCount() {
        return refetchCount;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public int getKeyDataSize() {
        return keyDataSize;
    }

    public CacheAddr getLongKeyAddress() {
        return longKeyAddress;
    }

    public int[] getDataStreamSize() {
        return dataStreamSize;
    }

    public CacheAddr[] getDataStreamAdresses() {
        return dataStreamAdresses;
    }

    public long getFlags() {
        return flags;
    }

    public int[] getPaddings() {
        return paddings;
    }

    public long getSelfHash() {
        return selfHash;
    }

    public byte[] getKeyData() {
        return keyData;
    }

    public InputStream getResponseRawDataStream() throws IOException {
        return dataStreamAdresses[1].getInputStream(dataFiles, externalFiles);
    }

    public InputStream getResponseInfo() throws IOException {
        return dataStreamAdresses[0].getInputStream(dataFiles, externalFiles);
    }

    /**
     * Creation of Cache Entry as defined in:
     * https://forensicswiki.xyz/wiki/index.php?title=Chrome_Disk_Cache_Format
     * 
     * @param is
     * @param dataFiles
     * @param externalFiles
     * @throws IOException
     */
    public CacheEntry(InputStream is, List<IItemBase> dataFiles, List<IItemBase> externalFiles) throws IOException {
        this.dataFiles = dataFiles;
        this.externalFiles = externalFiles;
        hash = readUnsignedInt(is);
        nextEntry = new CacheAddr(readUnsignedInt(is));
        rankingsNode = new CacheAddr(readUnsignedInt(is));
        reuseCount = read4bytes(is);
        refetchCount = read4bytes(is);
        state = read4bytes(is);
        creationTime = read8bytes(is);
        keyDataSize = read4bytes(is);
        longKeyAddress = new CacheAddr(readUnsignedInt(is));
        dataStreamSize = new int[4];
        dataStreamSize[0] = read4bytes(is);
        dataStreamSize[1] = read4bytes(is);
        dataStreamSize[2] = read4bytes(is);
        dataStreamSize[3] = read4bytes(is);

        dataStreamAdresses = new CacheAddr[4];
        dataStreamAdresses[0] = new CacheAddr(readUnsignedInt(is));
        dataStreamAdresses[1] = new CacheAddr(readUnsignedInt(is));
        dataStreamAdresses[2] = new CacheAddr(readUnsignedInt(is));
        dataStreamAdresses[3] = new CacheAddr(readUnsignedInt(is));

        flags = readUnsignedInt(is);

        paddings = new int[4];
        paddings[0] = read4bytes(is);
        paddings[1] = read4bytes(is);
        paddings[2] = read4bytes(is);
        paddings[3] = read4bytes(is);

        selfHash = readUnsignedInt(is);
        keyData = IOUtils.readFully(is, 256 - 24 * 4);
        
    }

    public int getResponseDataSize() {
        return dataStreamSize[1];
    }

    /**
     * 
     * @return state (NORMAL = 0; EVICTED = 1; DOOMED = 2;)
     */
    public int getState() {
        return state;
    }

    public String getName() {
    	return dataStreamAdresses[1].getFileNameStr();
    }
    
    public String getRequestURL() {
        return getKey();
    }

    public String getKey() {
        if (keyDataSize < 0) {
            return null;
        }
        return new String(keyData, 0, keyDataSize > keyData.length ? keyData.length : keyDataSize);
    }

    public String getRequestMethod() {
        return "GET";
    }

    public InputStream getResponseDataStream() throws IOException {
        BufferedInputStream bis = new BufferedInputStream(getResponseRawDataStream());
        bis.mark(1 << 14);
        try {
            return new GZIPInputStream(bis);
        } catch (ZipException e) {
            // is it possible to find a non gzip stream? Or this exception should always be thrown?
            if (e.getMessage().contains("Not in GZIP format")) {
                bis.reset();
                return bis;
            }
            throw e;
        }
    }

    public static int read2bytes(InputStream is) throws IOException {
        return (is.read() + (is.read() << 8));
    }

    public static int read4bytes(InputStream is) throws IOException {
        return read2bytes(is) | (read2bytes(is) << 16);
    }

    public static long readUnsignedInt(InputStream is) throws IOException {
        return (read2bytes(is) | (read2bytes(is) << 16)) & 0xffffffffL;
    }

    public static long read8bytes(InputStream is) throws IOException {
        return read4bytes(is) | (readUnsignedInt(is) << 32);
    }
}
