package iped.parsers.evtx.model;

import java.nio.ByteBuffer;

import iped.parsers.evtx.template.TemplateInstance;

public class EvtxAttribute {

    private EvtxName name;
    int size = 0;
    Object value;
    EvtxElement evtxElement;

    public EvtxAttribute(EvtxFile evtxFile, ByteBuffer bb, EvtxElement evtxElement) throws EvtxParseException {
        int nameOffset = bb.getInt();
        size += 4;
        this.evtxElement = evtxElement;
        this.name = new EvtxName(evtxFile, bb, nameOffset);
        size += name.size;
        BinXmlToken token = new BinXmlToken(evtxFile, bb);
        size += 1;

        switch (token.type) {
            case BinXmlToken.LIBFWEVT_XML_TOKEN_VALUE:
                EvtxValue v = new EvtxValue(evtxFile, bb);
                size += v.size;
                this.value = v;
                break;
            case BinXmlToken.LIBFWEVT_XML_TOKEN_NORMAL_SUBSTITUTION:
                this.value = new EvtxNormalSubstitution(evtxFile, bb, evtxElement);
                size += 3;
                break;
            case BinXmlToken.LIBFWEVT_XML_TOKEN_OPTIONAL_SUBSTITUTION:
                this.value = new EvtxOptionalSubstitution(evtxFile, bb, evtxElement);
                size += 3;
                break;

            default:
                break;
        }

    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append(name.value);
        b.append("=\"");
        if (value != null) {
            b.append(value.toString());
        }
        b.append("\"");
        return b.toString();
    }

    public String getName() {
        return name.value;
    }

    public Object getValue() {
        return value;
    }

    public String getValueAsString() {
        return value.toString();
    }

    public void setValue(Object value) {
        this.value = value;
    }

}
