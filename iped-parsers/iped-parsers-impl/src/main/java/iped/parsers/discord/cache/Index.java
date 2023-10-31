package iped.parsers.discord.cache;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IItemReader;
import iped.parsers.discord.cache.CacheAddr.InputStreamNotAvailable;

/**
 * 
 * Class referring to the index file <br />
 * <b>Magic Number:</b> C3CA03C1 <br />
 * <b>Version:</b> 01000200
 * 
 * @author Campanini
 * 
 *
 */
public class Index {

    private static Logger logger = LoggerFactory.getLogger(Index.class);

    private final long magicNumber;
    private final long version;
    private final int entriesCont;
    private final int bytesCont;
    private final int lastFile;
    private final int id;
    private final CacheAddr stats;
    private final int tableLength;
    private final int crash;
    private final int experiment;
    private final long createTime;
    private final int[] padding = new int[52];
    private int[] pad1 = new int[2];
    private int filled;
    private int[] sizes = new int[5];
    private CacheAddr[] heads = new CacheAddr[5];
    private CacheAddr[] tails = new CacheAddr[5];
    private CacheAddr transaction;
    private int operation;
    private int operation_list;
    private int[] pad2 = new int[7];
    private List<CacheEntry> lst = new ArrayList<>();

    public long getMagicNumber() {
        return magicNumber;
    }

    public long getVersion() {
        return version;
    }

    public int getEntriesCont() {
        return entriesCont;
    }

    public int getBytesCont() {
        return bytesCont;
    }

    public int getLastFile() {
        return lastFile;
    }

    public int getId() {
        return id;
    }

    public CacheAddr getStats() {
        return stats;
    }

    public int getTableLength() {
        return tableLength;
    }

    public int getCrash() {
        return crash;
    }

    public int getExperiment() {
        return experiment;
    }

    public long getCreateTime() {
        return createTime;
    }

    public int[] getPadding() {
        return padding;
    }

    public int[] getPad1() {
        return pad1;
    }

    public int getFilled() {
        return filled;
    }

    public int[] getSizes() {
        return sizes;
    }

    public CacheAddr[] getHeads() {
        return heads;
    }

    public CacheAddr[] getTails() {
        return tails;
    }

    public CacheAddr getTransaction() {
        return transaction;
    }

    public int getOperation() {
        return operation;
    }

    public int getOperation_list() {
        return operation_list;
    }

    public int[] getPad2() {
        return pad2;
    }

    public List<CacheEntry> getLst() {
        return lst;
    }

    public void setLst(List<CacheEntry> lst) {
        this.lst = lst;
    }

    public Index(InputStream is, String path, List<IItemReader> dataFiles, List<IItemReader> externalFiles) throws IOException {

        magicNumber = readUnsignedInt(is);
        version = readUnsignedInt(is);
        entriesCont = read4bytes(is);
        bytesCont = read4bytes(is);
        lastFile = read4bytes(is);
        id = read4bytes(is);
        stats = new CacheAddr(readUnsignedInt(is));
        tableLength = read4bytes(is);
        crash = read4bytes(is);
        experiment = read4bytes(is);
        createTime = read8bytes(is);

        for (int i = 0; i < 52; i++) {
            padding[i] = (int) readUnsignedInt(is);
        }

        pad1 = new int[2];
        pad1[0] = (int) readUnsignedInt(is);
        pad1[1] = (int) readUnsignedInt(is);
        filled = read4bytes(is);
        sizes = new int[5];
        for (int i = 0; i < 5; i++) {
            sizes[i] = read4bytes(is);
        }

        heads = new CacheAddr[5];
        for (int i = 0; i < 5; i++) {
            heads[i] = new CacheAddr(readUnsignedInt(is));
        }
        tails = new CacheAddr[5];
        for (int i = 0; i < 5; i++) {
            tails[i] = new CacheAddr(readUnsignedInt(is));
        }
        transaction = new CacheAddr(readUnsignedInt(is));
        operation = read4bytes(is);
        operation_list = read4bytes(is);
        pad2 = new int[7];
        for (int i = 0; i < 7; i++) {
            pad2[i] = read4bytes(is);
        }

        int tsize = 0x10000;

        if (tableLength > 0) {
            tsize = tableLength;
        }

        CacheAddr table[] = new CacheAddr[tsize];

        for (int i = 0; i < tsize; i++) {
            table[i] = new CacheAddr(readUnsignedInt(is));
        }

        for (CacheAddr ea : table) {
            try (InputStream eaIS = ea.getInputStream(dataFiles, externalFiles, null)) {
                lst.add(new CacheEntry(eaIS, dataFiles, externalFiles));
            } catch (InputStreamNotAvailable e) {
                continue;
            } catch (IOException e) {
                logger.warn("Exception reading CacheEntry of Discord Index " + path, e);
            }
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
