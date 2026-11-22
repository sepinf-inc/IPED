package iped.carvers.custom;

import java.io.IOException;
import java.util.Arrays;

import iped.carvers.api.Hit;
import iped.carvers.standard.DefaultCarver;
import iped.data.IItem;
import iped.io.SeekableInputStream;
import iped.utils.IOUtil;

public class OpusCarver extends DefaultCarver {
    private static final char[] keyOggs = { 'O', 'g', 'g', 'S', 0 };
    private static final char[] keyOpus = "OpusHead".toCharArray();

    @Override
    public long getLengthFromHit(IItem parentEvidence, Hit header) throws IOException {
        SeekableInputStream is = null;
        Long parentLen = parentEvidence.getLength();
        try {
            is = parentEvidence.getSeekableInputStream();
            is.seek(header.getOffset());
            long len = 0;
            for (int i = 0; i < 1000; i++) {
                byte[] bytes = is.readNBytes(256);
                if (bytes.length < 27) {
                    break;
                }
                if (i == 0 && !check(bytes, keyOpus)) {
                    return -1;
                }
                byte[] head = Arrays.copyOf(bytes, 5);
                if (!check(head, keyOggs)) {
                    break;
                }
                int numSegs = bytes[26] & 0xFF;
                int headerSize = numSegs + 27;
                int pageSize = headerSize;
                for (int j = 0; j < numSegs; j++) {
                    if (27 + j >= bytes.length) {
                        return -1;
                    }
                    pageSize += bytes[27 + j] & 0xFF;
                }
                if (pageSize < 27) {
                    return -1;
                }
                len += pageSize;
                if (parentLen != null && header.getOffset() + len > parentLen) {
                    break;
                }
                is.seek(header.getOffset() + len);
            }
            if (parentLen != null && header.getOffset() + len > parentLen) {
                len = parentLen - header.getOffset();
            }
            return len;
        } catch (IOException e) {
        } finally {
            IOUtil.closeQuietly(is);
        }
        return -1;
    }

    private static boolean check(byte[] bytes, char[] key) {
        NEXT: for (int i = 0; i <= bytes.length - key.length; i++) {
            for (int j = 0; j < key.length; j++) {
                if ((bytes[i + j] & 0xFF) != key[j]) {
                    continue NEXT;
                }
            }
            return true;
        }
        return false;
    }
}
