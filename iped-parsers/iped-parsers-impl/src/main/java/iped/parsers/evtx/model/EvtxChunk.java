package iped.parsers.evtx.model;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;

import org.apache.lucene.util.ArrayUtil;

public class EvtxChunk {
    byte[] src;
    int headerSize;
    long firstEventRecordId;
    long firstEventRecordNum;
    long lastEventRecordId;
    long lastEventRecordNum;
    int lastEventRecordOffset;

    int CHUNK_HEADER_SIZE = 512;
    private EvtxFile evtxFile;

    public EvtxChunk(EvtxFile evtxFile, byte[] src) {
        this.evtxFile = evtxFile;
        this.src = src;
    }

    public void processChunk() throws EvtxParseException {
        String sig = new String(ArrayUtil.copyOfSubArray(src, 0, 8));

        if (!sig.equals("ElfChnk\0")) {
            throw new EvtxInvalidChunkHeaderException(sig);
        }

        ByteBuffer bb = ByteBuffer.wrap(src);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.position(8);

        firstEventRecordNum = bb.getLong();
        lastEventRecordNum = bb.getLong();
        firstEventRecordId = bb.getLong();
        lastEventRecordId = bb.getLong();
        headerSize = bb.getInt();
        lastEventRecordOffset = bb.getInt();
        bb.position(CHUNK_HEADER_SIZE);

        int curPos = CHUNK_HEADER_SIZE;

        while (curPos <= lastEventRecordOffset) {
            byte[] recSig = new byte[4];
            bb.get(recSig);
            if (!((recSig[0] == 0x2a) && (recSig[1] == 0x2a) && (recSig[2] == 0x00) && (recSig[3] == 0x00))) {
                break;
            }
            evtxFile.totalCount++;
            EvtxRecord evtxRec = new EvtxRecord(evtxFile, bb);

            EvtxRecordConsumer c = evtxFile.getEvtxRecordConsumer();
            if (c != null) {
                c.accept(evtxRec);
            }

            curPos += evtxRec.size;
            bb.position(curPos);
        }
    }

}
