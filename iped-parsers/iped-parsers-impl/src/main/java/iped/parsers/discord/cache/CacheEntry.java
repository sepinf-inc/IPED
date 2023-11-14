package iped.parsers.discord.cache;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
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

    protected long hash;
    protected CacheAddr nextEntry;
    protected CacheAddr rankingsNode;
    protected int reuseCount;
    protected int refetchCount;
    protected int state;
    protected Date creationTime;
    protected int keyDataSize;
    protected CacheAddr longKeyAddressCacheAddress;
    protected long longKeyAddress;
    protected String key;
    protected int[] dataStreamSize;
    protected CacheAddr[] dataStreamAdresses;
    protected long flags;
    protected int[] paddings;
    protected long selfHash;
    protected byte[] keyData;
    protected List<IItemReader> dataFiles;
    protected List<IItemReader> externalFiles;

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

    public Date getCreationTime() {
        return creationTime;
    }

    public int getKeyDataSize() {
        return keyDataSize;
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
        return dataStreamAdresses[1].getInputStream(dataFiles, externalFiles, dataStreamSize[1]);
    }

    public InputStream getResponseInfo() throws IOException {
        return dataStreamAdresses[0].getInputStream(dataFiles, externalFiles, dataStreamSize[0]);
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
    public CacheEntry(InputStream is, List<IItemReader> dataFiles, List<IItemReader> externalFiles)
            throws IOException {
        this.dataFiles = dataFiles;
        this.externalFiles = externalFiles;

        read(is);

    }

    private void read(InputStream is) throws IOException {
        hash = Index.readUnsignedInt(is);
        nextEntry = new CacheAddr(Index.readUnsignedInt(is));
        rankingsNode = new CacheAddr(Index.readUnsignedInt(is));
        reuseCount = Index.read4bytes(is);
        refetchCount = Index.read4bytes(is);
        state = Index.read4bytes(is);
        
        creationTime = Index.readDate(is);
        keyDataSize = Index.read4bytes(is);
        longKeyAddress = Index.readUnsignedInt(is);
        longKeyAddressCacheAddress = new CacheAddr(longKeyAddress);
        dataStreamSize = new int[4];
        dataStreamSize[0] = Index.read4bytes(is);
        dataStreamSize[1] = Index.read4bytes(is);
        dataStreamSize[2] = Index.read4bytes(is);
        dataStreamSize[3] = Index.read4bytes(is);

        dataStreamAdresses = new CacheAddr[4];
        dataStreamAdresses[0] = new CacheAddr(Index.readUnsignedInt(is));
        dataStreamAdresses[1] = new CacheAddr(Index.readUnsignedInt(is));
        dataStreamAdresses[2] = new CacheAddr(Index.readUnsignedInt(is));
        dataStreamAdresses[3] = new CacheAddr(Index.readUnsignedInt(is));

        flags = Index.readUnsignedInt(is);

        paddings = new int[4];
        paddings[0] = Index.read4bytes(is);
        paddings[1] = Index.read4bytes(is);
        paddings[2] = Index.read4bytes(is);
        paddings[3] = Index.read4bytes(is);

        selfHash = Index.readUnsignedInt(is);
        if (keyDataSize > 0) {
            keyData = IOUtils.readFully(is, Math.min((256 - 24 * 4) + (3 * 256), keyDataSize));

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
        try {

            if (key == null) {
                if (keyDataSize < 0) {
                    return null;
                }

                if (longKeyAddress > 0) {
                    key = new String(longKeyAddressCacheAddress.getInputStream(dataFiles, externalFiles, null)
                            .readNBytes(keyDataSize));
                } else {
                    key = new String(keyData);
                }
            }

            if (key.contains("_dk_")) {
                String[] parts = key.split(" ");
                key = parts[parts.length - 1];
            }

            return key;

        } catch (Exception exe) {
            exe.printStackTrace();
            return "";
        }
    }

    public String getRequestMethod() {
        return "GET";
    }

    public InputStream getResponseDataStream(String contentEncoding) throws Exception, ZipException {

        InputStream bis = getResponseRawDataStream();

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

        try (InputStream is = getResponseInfo()) {

            httpResponse.put("payload_size", String.valueOf(Index.read4bytes(is)));
            httpResponse.put("flags", String.valueOf(Index.read4bytes(is)));
            httpResponse.put("request_time", Long.toString(Index.read8bytes(is)));
            httpResponse.put("response_time", Long.toString(Index.read8bytes(is)));

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

    public String readString(InputStream is) throws IOException {

        int length;
        byte[] data = null;
        int ret;

        length = Index.read4bytes(is);
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
                + ", keyDataSize=" + keyDataSize + ", longKeyAddress=" + longKeyAddress + ", dataStreamSize=" + Arrays.toString(dataStreamSize) + ", dataStreamAdresses[ResponseInfo]=" + dataStreamAdresses[0].toString()
                + ", dataStreamAdresses[ResponseRawDataStream]=" + dataStreamAdresses[1].toString() + ", flags=" + flags + ", paddings=" + Arrays.toString(paddings) + ", selfHash=" + selfHash + ", requestURL=" + getRequestURL() + "]";
    }
}
