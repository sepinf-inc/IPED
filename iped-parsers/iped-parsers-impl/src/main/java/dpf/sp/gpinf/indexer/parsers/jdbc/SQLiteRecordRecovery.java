package dpf.sp.gpinf.indexer.parsers.jdbc;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.BitSet;

import dpf.sp.gpinf.indexer.util.SeekableFileInputStream;
import iped3.io.SeekableInputStream;

public class SQLiteRecordRecovery {

    private int pageSize;
    private int reservedSpace;
    private long numPages;
    private long freeListTrunkPage;
    private long numFreeLists;
    private BitSet freePages = new BitSet();
    private BufferedWriter writer;

    public static void main(String[] args) {
        // File file = new
        // File("F:\\teste-files\\whatsapp\\tada-huge\\ChatStorage.sqlite");
        File file = new File("F:\\teste-files\\whatsapp\\android\\Nova pasta\\msgstore.db");
        // File file = new File("E:/sqlite-test.db");
        new SQLiteRecordRecovery().process(file);
    }

    private void process(File file) {

        try (FileChannel fc = FileChannel.open(file.toPath(), StandardOpenOption.READ);
                SeekableInputStream is = new SeekableFileInputStream(fc);
                BufferedWriter writer = new BufferedWriter(new FileWriter("e:\\deleted-records.txt"))) {

            this.writer = writer;

            // fc.map(MapMode.READ_ONLY, 0, file.length()).load();

            pageSize = readUnsignedShort(is, 16);
            if (pageSize == 1)
                pageSize = 65536;

            if ((pageSize & (pageSize - 1)) != 0)
                throw new IllegalArgumentException("Sqlite page size not a power of 2");

            is.seek(20);
            reservedSpace = 0xFF & (byte) is.read();

            numPages = readUnsignedInt(is, 28);

            freeListTrunkPage = readUnsignedInt(is, 32) - 1;

            numFreeLists = readUnsignedInt(is, 36);

            if (freeListTrunkPage > -1)
                parseFreeListTrunkPage(is, freeListTrunkPage * pageSize);

            for (int page = 1; page < numPages; page++) {
                if (freePages.get(page))
                    continue;
                parsePage(is, page * pageSize);
            }

        } catch (Exception e) {
            e.printStackTrace();

        }

    }

    private void parsePage(SeekableInputStream is, long offset) throws IOException {

        System.out.println("Parsing page " + offset);
        writer.write("Parsing page " + offset);
        writer.newLine();

        is.seek(offset);
        int pageType = is.read();

        // check if it is a leaf data page
        if (pageType != 13)
            return;

        int firstFreeBlock = readUnsignedShort(is, offset + 1);
        int numCells = readUnsignedShort(is, offset + 3);
        int cellContentArea = readUnsignedShort(is, offset + 5);
        if (cellContentArea == 0)
            cellContentArea = 65536;

        long unallocStart = 8 + 2 * numCells;
        byte[] data = readBytes(is, offset + unallocStart, (int) (cellContentArea - unallocStart));
        printUnalloc(data);
        
        // read allocated cells
        int k = 0;
        while(k < numCells) {
            int cellPos = readUnsignedShort(is, offset + 8 + 2 * k++);
            writer.write("cellPos=" + cellPos + " ");
            long cellStart = offset + cellPos;
            is.seek(cellStart);

            int payloadLen = (int) decodeVarInt(is);
            int rowId = (int) decodeVarInt(is);
            long payloadStart = is.position();
            ByteArrayOutputStream payload = new ByteArrayOutputStream();
            
            int U = pageSize - reservedSpace;
            int P = payloadLen;
            int X = U - 35;
            int M = ((U-12)*32/255)-23;
            int K = M+((P-M)%(U-4));
            
            if (P <= X) {
                payload.write(readBytes(is, payloadStart, P));
            } else if (K <= X) {
                payload.write(readBytes(is, payloadStart, K));
                long overflowPage = readUnsignedInt(is, is.position());
                readOverflowPages(is, payload, P - K, overflowPage);
            } else {
                payload.write(readBytes(is, payloadStart, M));
                long overflowPage = readUnsignedInt(is, is.position());
                readOverflowPages(is, payload, P - M, overflowPage);
            }

            // printCellPayload(payload.toByteArray(), new IntRef());
        }

        int nextFreeBlock = firstFreeBlock;
        while (nextFreeBlock != 0) {
            writer.write("freeBlock " + nextFreeBlock);
            writer.newLine();
            int len = readUnsignedShort(is, offset + nextFreeBlock + 2);
            data = readBytes(is, offset + nextFreeBlock, len);
            // writer.write(new String(data, StandardCharsets.UTF_8));
            printUnalloc(data);
            writer.newLine();

            // nextFreeBlock = readUnsignedShort(is, offset + nextFreeBlock);
            nextFreeBlock = (data[0] & 0xFF) << 8 | (data[1] & 0xFF);
        }

        /*
         * int pageType = is.read(); int numCells = readUnsignedShort(is, offset + 5);
         * int contentPos = readUnsignedShort(is, offset + 7); if (contentPos == 0)
         * contentPos = 65536;
         */
    }

    private void readOverflowPages(SeekableInputStream is, ByteArrayOutputStream payload, int remaining,
            long nextOverflowPage) throws IOException {
        is.seek((nextOverflowPage - 1) * pageSize);
        nextOverflowPage = readUnsignedInt(is, is.position());

        int maxSize = pageSize - reservedSpace - 4;
        int size = Math.min(maxSize, remaining);
        payload.write(readBytes(is, is.position(), size));
        remaining -= size;

        if (nextOverflowPage > 0 && remaining > 0) {
            readOverflowPages(is, payload, remaining, nextOverflowPage);
        }
    }

    private void printUnalloc(byte[] data) throws IOException {
        writer.write("unallocated:");
        writer.newLine();
        //writer.write(new String(data, StandardCharsets.UTF_8));
        IntRef pos = new IntRef();

        while (pos.val < data.length) {
            // skip zeros
            boolean end = false;
            while (!(end = pos.val == data.length) && data[pos.val] == 0)
                pos.val++;
            if (end)
                break;

            // TODO payloadLen could be overwritten, mainly in freeblocks, we should use
            // some heuristic to guess it and properly load payload data (with overflows)
            // before processing it
            int payloadLen = (int) decodeVarInt(data, pos);
            int rowId = (int) decodeVarInt(data, pos);

            printCellPayload(data, pos);
        }
    }

    private void printCellPayload(byte[] data, IntRef pos) throws IOException {

        // writer.write("payload: " + new String(data, StandardCharsets.UTF_8));
        // writer.newLine();

        int headerStart = pos.val;
        long headerLen = decodeVarInt(data, pos);
        if (headerLen > data.length - headerStart)
            return;

        writer.write("cell: ");
        // writer.write("rowid " + rowId + " ;");

        ArrayList<Integer> serialTypes = new ArrayList<>();
        while (pos.val < headerStart + headerLen) {
            int serialType = (int) decodeVarInt(data, pos);
            serialTypes.add(serialType);
        }
        for (int type : serialTypes) {
            if (pos.val >= data.length)
                break;
            switch (type) {
                case 0:
                    writer.write("NULL; ");
                    break;
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                    int len = type;
                    if (type == 5 || type == 7)
                        len++;
                    if (type == 6)
                        len += 2;
                    if (pos.val + len > data.length)
                        break;
                    byte[] array = new byte[len];
                    System.arraycopy(data, pos.val, array, 0, len);
                    long l = readSignedLong(array);
                    pos.val += len;
                    if (type < 7)
                        writer.write(l + "; ");
                    else {
                        double d = Double.longBitsToDouble(l);
                        writer.write(d + "; ");
                    }
                    break;
                case 8:
                    writer.write("0; ");
                    break;
                case 9:
                    writer.write("1; ");
                    break;
                default:
                    if (type >= 12) {
                        if (type % 2 == 0) {
                            len = (type - 12) / 2;
                            len = Math.min(len, data.length - pos.val);
                            array = new byte[len];
                            System.arraycopy(data, pos.val, array, 0, len);
                            if (array.length > 3 && array[0] == (byte) 0xFF && array[1] == (byte) 0xD8
                                    && array[2] == (byte) 0xFF) {
                                File blob = new File("e:/blobs/blob-" + blobNum++ + ".jpg");
                                blob.getParentFile().mkdir();
                                Files.write(blob.toPath(), array);
                            }
                            writer.write("BLOB len=" + len);
                            writer.write("; ");
                        } else {
                            len = (type - 13) / 2;
                            len = Math.min(len, data.length - pos.val);
                            writer.write(new String(data, pos.val, len, StandardCharsets.UTF_8));
                            writer.write("; ");
                        }
                        pos.val += len;
                    }
            }
        }
        writer.newLine();
        
    }

    private int blobNum = 0;

    private void parseFreeListTrunkPage(SeekableInputStream is, long offset) throws IOException {

        long nextFreeListTrunk = readUnsignedInt(is, offset);
        offset += 4;
        long numFreeLists = readUnsignedInt(is, offset);

        int freeListNum = 0;
        while (freeListNum++ < numFreeLists) {
            offset += 4;
            int freeListPage = (int) readUnsignedInt(is, offset) - 1;
            freePages.set(freeListPage);
            parseFreePage(is, freeListPage * pageSize);
        }

        if (nextFreeListTrunk > 0) {
            nextFreeListTrunk--;
            parseFreeListTrunkPage(is, nextFreeListTrunk * pageSize);
        }

    }

    private void parseFreePage(SeekableInputStream is, long offset) throws IOException {
        
        byte[] data = readBytes(is, offset, pageSize);

        writer.write("free page " + offset);
        writer.newLine();
        // writer.write(new String(data, StandardCharsets.UTF_8));
        printUnalloc(data);
        writer.newLine();
    }

    private int readUnsignedShort(SeekableInputStream is, long offset) throws IOException {

        byte[] data = readBytes(is, offset, 2);

        return (data[0] & 0xff) << 8 | (data[1] & 0xff);
    }

    private long readUnsignedInt(SeekableInputStream is, long offset) throws IOException {
        byte[] data = readBytes(is, offset, 4);
        return (long) (data[0] & 0xff) << 24 | (data[1] & 0xff) << 16 | (data[2] & 0xff) << 8 | (data[3] & 0xff);
    }

    private long readSignedLong(byte[] data) throws IOException {

        long result = 0;
        for (int i = 0; i < data.length; i++) {
            result |= (data[i] & (long) 0xff) << ((data.length - 1 - i) * 8);
        }

        return result;
    }

    private byte[] readBytes(SeekableInputStream is, long pos, int size) throws IOException {

        is.seek(pos);
        byte[] data = new byte[size];
        int i = 0, off = 0;
        while (i != -1 && (off += i) < data.length)
            i = is.read(data, off, data.length - off);

        return data;
    }

    private long decodeVarInt(byte[] data, IntRef pos) throws IOException {

        long result = 0;
        int b;
        do {
            if (pos.val >= data.length)
                break;// throw new IOException("End of stream reached while decoding varint");
            b = data[pos.val++];
            result = result << 7;
            result |= (b & 0x7F);

        } while ((b & 0x80) != 0);

        return result;
    }

    private long decodeVarInt(SeekableInputStream is) throws IOException {

        long result = 0;
        int b;
        do {
            b = is.read();
            if (b == -1)
                break;// throw new IOException("End of stream reached while decoding varint");
            result = result << 7;
            result |= (b & 0x7F);

        } while ((b & 0x80) != 0);

        return result;
    }

    private class IntRef {
        int val = 0;
    }

}
