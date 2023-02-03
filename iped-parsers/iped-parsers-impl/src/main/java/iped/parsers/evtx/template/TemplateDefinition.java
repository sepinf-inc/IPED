package iped.parsers.evtx.template;

import java.nio.ByteBuffer;

import iped.parsers.evtx.model.EvtxElement;
import iped.parsers.evtx.model.EvtxFile;

public class TemplateDefinition {

    int id;
    int dataOffset;
    int size;
    byte[] guid = new byte[16];
    byte[] data;

    public void TemplateDefinition2(ByteBuffer bb) {
        // template instance
        byte b = bb.get();
        bb.get(this.guid);
        this.size = bb.getInt();
        /*
         * this.id = bb.getInt(); this.dataOffset=bb.getInt();
         * bb.position(bb.position()+4);
         */
    }

    public TemplateDefinition(EvtxFile evtxFile, ByteBuffer bb) {
        // template instance
        bb.position(bb.position() + 1);
        this.id = bb.getInt();
        this.dataOffset = bb.getInt();
        // TemplateData tData = evtxFile.getTemplateData(this.dataOffset);
        if (this.dataOffset == bb.position()) {
            bb.position(bb.position() + 4);
            bb.get(this.guid);
            this.size = bb.getInt();
        }
    }
}
