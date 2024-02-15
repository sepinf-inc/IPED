package iped.parsers.evtx.model;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import iped.parsers.evtx.template.TemplateInstance;

public class EvtxAttributes {

    private EvtxFile evtxFile;
    int size;
    private byte type;
    ArrayList<EvtxAttribute> attributes = new ArrayList<EvtxAttribute>();

    public EvtxAttributes(EvtxFile evtxFile, ByteBuffer bb, int size, EvtxElement evtxElement) throws EvtxParseException {
        this.evtxFile = evtxFile;
        this.size = size;

        int curPos = bb.position();
        int startSize = bb.position();
        while (curPos < size + startSize) {
            BinXmlToken token = new BinXmlToken(evtxFile, bb);
            curPos += 1;
            EvtxAttribute attr = new EvtxAttribute(evtxFile, bb, evtxElement);
            curPos += attr.size;
            this.attributes.add(attr);
        }
    }

}
