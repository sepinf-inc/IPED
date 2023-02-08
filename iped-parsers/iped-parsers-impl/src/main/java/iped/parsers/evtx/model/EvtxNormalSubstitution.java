package iped.parsers.evtx.model;

import java.nio.ByteBuffer;

import iped.parsers.evtx.template.TemplateInstance;

public class EvtxNormalSubstitution extends EvtxOptionalSubstitution {

    public EvtxNormalSubstitution(EvtxFile evtxFile, ByteBuffer bb, EvtxElement evtxElement) throws EvtxParseException {
        super(evtxFile, bb, evtxElement);
    }

}
