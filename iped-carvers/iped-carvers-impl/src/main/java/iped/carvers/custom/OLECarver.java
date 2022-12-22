package iped.carvers.custom;

import java.io.IOException;

import org.apache.commons.codec.DecoderException;

import iped.carvers.api.CarverType;
import iped.carvers.api.Hit;
import iped.carvers.standard.AbstractCarver;
import iped.data.IItem;
import iped.io.SeekableInputStream;

public class OLECarver extends AbstractCarver {

    public OLECarver() throws DecoderException {
        carverTypes = new CarverType[1];
        carverTypes[0] = new CarverType();
        carverTypes[0].addHeader("\\d0\\cf\\11\\e0\\a1\\b1\\1a\\e1");
        carverTypes[0].setName("OLE");
    }

    @Override
    public long getLengthFromHit(IItem parentEvidence, Hit header) throws IOException {
        try (SeekableInputStream is = parentEvidence.getSeekableInputStream()) {
            is.seek(header.getOffset());
            byte buf[] = new byte[512];
            is.readNBytes(buf, 0, buf.length);

            int blockSizeOff = 0x1E;

            int blockSize = (int) Math.pow(2, ((buf[blockSizeOff] & 0xff) << 0 | (buf[blockSizeOff + 1] & 0xff) << 8));

            int numBatBlocksOff = 0x2C;

            int numBatBlocks = (buf[numBatBlocksOff] & 0xff) << 0 | (buf[numBatBlocksOff + 1] & 0xff) << 8
                    | (buf[numBatBlocksOff + 2] & 0xff) << 16 | (buf[numBatBlocksOff + 3] & 0xff) << 24;

            // TODO melhorar detecção de tamanho para OLE
            /*
             * int lastBatOffPos = i + 0x4C + (batSize - 1) * 4; int lastBatOffP =
             * (buf[lastBatOffPos] & 0xff) << 0 | (buf[lastBatOffPos + 1] & 0xff) << 8 |
             * (buf[lastBatOffPos + 2] & 0xff) << 16 | (buf[lastBatOffPos + 3] & 0xff) <<
             * 24;
             */
            int len = (numBatBlocks * blockSize / 4 + 1) * blockSize;

            return len;
        }

    }

}
