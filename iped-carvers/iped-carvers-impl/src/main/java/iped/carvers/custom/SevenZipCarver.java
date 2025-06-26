package iped.carvers.custom;

import java.io.IOException;

import iped.carvers.api.Hit;
import iped.carvers.standard.AbstractCarver;
import iped.data.IItem;
import iped.io.SeekableInputStream;

public class SevenZipCarver extends AbstractCarver {

    @Override
    public long getLengthFromHit(IItem parentEvidence, Hit header) throws IOException {

        try (SeekableInputStream is = parentEvidence.getSeekableInputStream()) {
            is.seek(header.getOffset());
            byte buf[] = new byte[32];
            int read = is.readNBytes(buf, 0, buf.length);
            if (read < buf.length)
                return -1;
            
            long nextHeaderOffset = (long) (buf[19] & 0xff) << 56 | (long) (buf[18] & 0xff) << 48
                    | (long) (buf[17] & 0xff) << 40 | (long) (buf[16] & 0xff) << 32
                    | (long) (buf[15] & 0xff) << 24 | (buf[14] & 0xff) << 16
                    | (buf[13] & 0xff) << 8 | (buf[12] & 0xff);

            long nextHeaderSize = (long) (buf[27] & 0xff) << 56 | (long) (buf[26] & 0xff) << 48
                    | (long) (buf[25] & 0xff) << 40 | (long) (buf[24] & 0xff) << 32
                    | (long) (buf[23] & 0xff) << 24 | (buf[22] & 0xff) << 16
                    | (buf[21] & 0xff) << 8 | (buf[20] & 0xff);

            return nextHeaderOffset + nextHeaderSize + 32;
        }
    }

}
