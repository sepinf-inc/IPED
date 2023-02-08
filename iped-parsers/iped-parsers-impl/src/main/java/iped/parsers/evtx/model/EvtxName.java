package iped.parsers.evtx.model;

import java.io.IOException;
import java.nio.ByteBuffer;

public class EvtxName {
    int nameOffset;
    int size;
    String value;
    private EvtxFile evtxFile;

    public EvtxName(EvtxFile evtxFile, ByteBuffer bb, int nameOffset) {
        this.evtxFile = evtxFile;
        this.nameOffset = nameOffset;

        try {
            int pos = bb.position();
            bb.position(nameOffset + 6);
            size = (bb.getShort()) * 2;
            byte[] b = new byte[size];
            bb.get(b);
            value = new String(b, "UTF-16LE");
            bb.getShort();
            if (nameOffset == pos) {
                size += 10;
            } else {
                bb.position(pos);
                size = 0;
            }
        } catch (IOException e) {

        }
    }

}
