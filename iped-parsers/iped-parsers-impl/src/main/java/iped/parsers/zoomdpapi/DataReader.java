package iped.parsers.zoomdpapi;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Binary data reader with little-endian byte order support
 * for parsing DPAPI structures.
 *
 * @author Calil Khalil (Hakal)
 */
public class DataReader {

    private final byte[] data;
    private int offset;

    public DataReader(byte[] data) {
        this.data = data;
        this.offset = 0;
    }

    public long readDword() {
        ByteBuffer buffer = ByteBuffer.wrap(data, offset, 4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        offset += 4;
        return buffer.getInt() & 0xFFFFFFFFL;
    }

    public long readQword() {
        ByteBuffer buffer = ByteBuffer.wrap(data, offset, 8);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        offset += 8;
        return buffer.getLong();
    }

    public byte[] readBytes(int length) {
        if (offset + length > data.length) {
            throw new RuntimeException("Buffer overflow: requesting " + length +
                " bytes at offset " + offset + ", available: " + (data.length - offset));
        }
        byte[] result = new byte[length];
        System.arraycopy(data, offset, result, 0, length);
        offset += length;
        return result;
    }

    public String readStringUtf16(int byteLength) {
        byte[] bytes = readBytes(byteLength);
        return new String(bytes, StandardCharsets.UTF_16LE).trim();
    }

    public void skip(int bytes) {
        if (offset + bytes > data.length) {
            throw new RuntimeException("Buffer overflow: skipping " + bytes +
                " bytes at offset " + offset + ", available: " + (data.length - offset));
        }
        offset += bytes;
    }

    public int remaining() {
        return data.length - offset;
    }

    public boolean hasRemaining() {
        return offset < data.length;
    }

    public int getOffset() {
        return offset;
    }

    public byte[] getData() {
        return data;
    }
}
