package iped.parsers.evtx.template;

import java.nio.ByteBuffer;

import iped.parsers.evtx.model.BinXmlToken;
import iped.parsers.evtx.model.EvtxFile;
import iped.parsers.evtx.model.EvtxParseException;
import iped.parsers.evtx.model.EvtxXmlFragment;

public class TemplateInstance {
    TemplateDefinition tdefinition;
    TemplateData tdata;
    EvtxXmlFragment fragment;

    public TemplateInstance(EvtxFile evtxFile, ByteBuffer bb) throws EvtxParseException {
        int pos = bb.position();
        this.tdefinition = new TemplateDefinition(evtxFile, bb);

        int tdoffset = tdefinition.dataOffset;

        int pos2 = bb.position();

        bb.position(pos + 9);
        if (this.tdefinition.size <= 2) {
        } else {
            bb.position(bb.position() + this.tdefinition.size + 24);
        }

        this.tdata = new TemplateData(evtxFile, bb, this);
        evtxFile.addTemplateData(tdoffset, this.tdata);

        fragment = evtxFile.getTemplateXml(tdoffset);
        if (fragment == null) {
            bb.position(tdoffset + 24);
            BinXmlToken b = new BinXmlToken(evtxFile, bb);
            fragment = new EvtxXmlFragment(evtxFile, this, bb);
            evtxFile.addTemplateXml(tdoffset, fragment);
        }
    }

    public int getValuesCount() {
        return tdata.tds.size();
    }

    public Object getValue(int i) {
        return tdata.tds.get(i);
    }

    @Override
    public String toString() {
        fragment.setTemplateInstance(this);
        return fragment.toString();
    }

    public EvtxXmlFragment getFragment() {
        fragment.setTemplateInstance(this);
        return fragment;
    }

}
