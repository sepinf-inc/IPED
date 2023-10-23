package iped.parsers.evtx.template;

import java.nio.ByteBuffer;

import iped.parsers.evtx.model.EvtxFile;

public class TemplateDefinitionData {

    public int valuesCount;
    protected TemplateValueDescriptor[] vds;

    public TemplateDefinitionData(EvtxFile evtxFile, ByteBuffer bb) {
        int valuesCount = bb.getInt();

        for (int i = 0; i < valuesCount; i++) {
            TemplateValueDescriptor vd = new TemplateValueDescriptor();
            short shortVal = bb.getShort();
            vd.size = shortVal >= 0 ? shortVal : 0x10000 + shortVal;
            byte bval = bb.get();
            vd.type = bval >= (short) 0 ? (short) bval : (short) ((0x100) + bval);
            bb.get();
            vds[i] = vd;
        }
    }

}
