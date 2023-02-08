package iped.parsers.evtx.model;

import java.nio.ByteBuffer;

import iped.parsers.evtx.template.TemplateInstance;

public class EvtxOptionalSubstitution {
    private short template_value_index;
    private byte template_value_type;
    private EvtxElement evtxElement;

    public EvtxOptionalSubstitution(EvtxFile evtxFile, ByteBuffer bb, EvtxElement evtxElement) throws EvtxParseException {
        template_value_index = bb.getShort();
        template_value_type = bb.get();
        this.evtxElement = evtxElement;

    }

    public Object getValue() {
        try {
            Object o = evtxElement.getTemplateInstance().getValue(template_value_index);
            if (o instanceof TemplateInstance) {
                ((TemplateInstance) o).getFragment().setTemplateInstance(((TemplateInstance) o));
                return ((TemplateInstance) o).getFragment().getElement();
            }
            if (o instanceof EvtxXmlFragment) {
                return ((EvtxXmlFragment) o).getElement();
            }
            return o;
        } catch (Exception e) {
            return "Index " + template_value_index + " not found";
        }
    }

    @Override
    public String toString() {
        try {
            Object o = evtxElement.getTemplateInstance().getValue(template_value_index);
            return o.toString();
        } catch (Exception e) {
            return "Index " + template_value_index + " not found";
        }
    }
}
