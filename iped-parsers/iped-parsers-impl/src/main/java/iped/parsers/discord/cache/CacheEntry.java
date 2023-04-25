package iped.parsers.discord.cache;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;

import org.apache.commons.io.IOUtils;
import org.brotli.dec.BrotliInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IItemReader;
import iped.parsers.discord.cache.CacheAddr.InputStreamNotAvailable;

/**
 * @author PCF Campanini
 *
 */
public class CacheEntry {

    private static Logger logger = LoggerFactory.getLogger(CacheEntry.class);

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
    private List<IItemReader> dataFiles;
    private List<IItemReader> externalFiles;

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
    public CacheEntry(InputStream is, List<IItemReader> dataFiles, List<IItemReader> externalFiles) throws IOException {
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
        if (keyDataSize > 0) {
            keyData = IOUtils.readFully(is, Math.min(256 - 24 * 4, keyDataSize));
        } else {
            keyData = new byte[0];
        }

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
        return new String(keyData);
    }

    public String getRequestMethod() {
        return "GET";
    }

    public InputStream getResponseDataStream(String contentEncoding) throws Exception, ZipException {

        BufferedInputStream bis = new BufferedInputStream(getResponseRawDataStream());

        if (contentEncoding == null) {
            return bis;
        }

        switch (contentEncoding) {
            case "br":
                try {
                    return new BrotliInputStream(bis);
                } catch (IOException e) {
                    logger.warn("Brotli decoder failed, trying Gzip", e);
                }
            case "gzip":
                try {
                    return new GZIPInputStream(bis);
                } catch (IOException e) {
                    logger.warn("Gzip decoder failed, trying default", e);
                }
            default:
                return bis;
        }

    }

    /**
     * This method gets the HTTP response data for the cache entry
     * 
     * @return Returns HTTP response data organized in Map<String, String>
     * @throws IOException
     */
    public Map<String, String> getHttpResponse() {

        Map<String, String> httpResponse = new HashMap<>();

        try (InputStream is = dataStreamAdresses[0].getInputStream(dataFiles, externalFiles)) {

            httpResponse.put("payload_size", String.valueOf(read4bytes(is)));
            httpResponse.put("flags", String.valueOf(read4bytes(is)));
            httpResponse.put("request_time", Long.toString(read8bytes(is)));
            httpResponse.put("response_time", Long.toString(read8bytes(is)));

            Pattern NULL_SEPARATOR = Pattern.compile("\\u0000");
            String[] lines = NULL_SEPARATOR.split(readString(is));

            for (String line : lines) {

                String[] value = line.split(":");

                switch (value.length) {
                    case 0:
                        continue;
                    case 1:
                        httpResponse.put(value[0], value[0]);
                        break;
                    case 2:
                        httpResponse.put(value[0], value[1]);
                        break;
                    default:
                        String[] mult_values = line.split(":", 2);
                        httpResponse.put(mult_values[0], mult_values[1]);
                        break;

                }
            }
        } catch (InputStreamNotAvailable e) {
            // ignore
        } catch (IOException e) {
            e.printStackTrace();
        }

        return httpResponse;
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

    public String readString(InputStream is) throws IOException {

        int length;
        byte[] data = null;
        int ret;

        length = read4bytes(is);
        data = new byte[length];
        ret = is.read(data);

        // Checking if the reading occurred correctly
        if (ret != length && ret != -1)
            throw new IOException();

        return new String(data);
    }

    @Override
    public String toString() {
        return "CacheEntry [hash=" + hash + ", nextEntry=" + nextEntry + ", rankingsNode=" + rankingsNode + ", reuseCount=" + reuseCount + ", refetchCount=" + refetchCount + ", state=" + state + ", creationTime=" + creationTime
                + ", keyDataSize=" + keyDataSize + ", longKeyAddress=" + longKeyAddress + ", dataStreamSize=" + Arrays.toString(dataStreamSize) + ", dataStreamAdresses=" + Arrays.toString(dataStreamAdresses) + ", flags=" + flags
                + ", paddings=" + Arrays.toString(paddings) + ", selfHash=" + selfHash + ", getKey()=" + getKey() + ", keyData=" + Arrays.toString(keyData) + "]";
    }

}
