package iped.engine.preview;

import java.nio.ByteBuffer;

import iped.data.IItemReader;
import iped.engine.task.HashTask;
import iped.utils.HashValue;

/**
 * Primary key for storing/retrieving an item's preview. Prioritizes MD5 hash, falls back to item ID. Uses a 16-byte key
 * (if MD5) or 4-byte key (if ID).
 */
public class PreviewKey {

    private final ByteBuffer buffer;

    public PreviewKey(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    public PreviewKey(byte[] bytes) {
        this.buffer = ByteBuffer.wrap(bytes);
    }

    public PreviewKey(int id) {
        this.buffer = ByteBuffer.allocate(Integer.BYTES).putInt(id);
    }

    public static PreviewKey create(IItemReader item) {

        String hashString = (String) item.getExtraAttribute(HashTask.HASH.MD5.toString());
        if (hashString != null) {
            return new PreviewKey(new HashValue(hashString).getBytes());
        }

        // Fallback to item ID
        return new PreviewKey(item.getId());
    }

    public byte[] getBytes() {
        return buffer.array();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PreviewKey) {
            return this.buffer.equals(((PreviewKey) obj).buffer);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return buffer.hashCode();
    }
}
