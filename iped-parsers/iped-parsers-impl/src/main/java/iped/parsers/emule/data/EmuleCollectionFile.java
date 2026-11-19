package iped.parsers.emule.data;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;

public class EmuleCollectionFile implements ECollectionFile {

    HashMap<Byte, Tag> tags = new HashMap<Byte, Tag>();

    public static EmuleCollectionFile loadCollectionFile(ByteBuffer data) throws IOException {

        EmuleCollectionFile result = new EmuleCollectionFile();

        int tagcount = data.getInt();
        for (int i = 0; i < tagcount; i++) {
            Tag t = Tag.createTag(data, true);
            result.tags.put(t.getNameID(), t);
        }

        return result;

    }

    public String getName() {
        Tag t = tags.get(Tag.FT_FILENAME);

        return t.getStr();
    }

    public String getHashStr() {
        Tag t = tags.get(Tag.FT_FILE_HASH);

        return hashBytesToStr(t.m_pData);
    }

    public static final String hashBytesToStr(byte[] bytes) {
        final char[] tos = "0123456789ABCDEF".toCharArray();
        char[] c = new char[bytes.length << 1];
        int k = 0;
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xFF;
            c[k++] = tos[b >>> 4];
            c[k++] = tos[b & 15];
        }
        return new String(c);
    }

    @Override
    public long getSize() {
        Tag t = tags.get(Tag.FT_SIZE);
        if(t==null) {
            t = tags.get(Tag.FT_SIZE_HI);
        }
        if (t == null) {
            return 0;
        }
        if (t.getValObject() instanceof Number) {
            try {
                return ((Number) t.getValObject()).longValue();
            } catch (Exception e) {
                // ignores and return 0;
            }
        }
        return 0;
    }

}
