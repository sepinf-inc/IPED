package iped.parsers.evtx.model;

import java.nio.ByteBuffer;

import iped.parsers.evtx.template.TemplateInstance;

public class EvtxXmlFragment {

    Object fragObj;
    public TemplateInstance templateInstance;
    Object parent;

    public EvtxXmlFragment(EvtxFile evtxFile, Object parent, ByteBuffer bb) throws EvtxParseException {
        bb.position(bb.position() + 3);
        this.parent = parent;

        BinXmlToken token = new BinXmlToken(evtxFile, bb);
        switch (token.type & 0xbf) {
            case BinXmlToken.LIBFWEVT_XML_TOKEN_OPEN_START_ELEMENT_TAG:
                int elemOffset = bb.position() - 29;
                EvtxElement e;
                e = new EvtxElement(evtxFile, bb, token.type, null);
                fragObj = e;
                break;
            case BinXmlToken.LIBFWEVT_XML_TOKEN_TEMPLATE_INSTANCE:
                this.templateInstance = new TemplateInstance(evtxFile, bb);
                fragObj = this.templateInstance;
                break;
            default:
                break;
        }

    }

    public EvtxElement getElement() {
        if (templateInstance != null) {
            EvtxElement el = templateInstance.getFragment().getElement();
            el.setTemplateInstance(templateInstance);
            return el;
        } else {
            return (EvtxElement) fragObj;
        }
    }

    @Override
    public String toString() {
        if (fragObj != null) {
            return fragObj.toString();
        } else {
            return "null";
        }
    }

    public void setTemplateInstance(TemplateInstance templateInstance2) {
        this.templateInstance = templateInstance;
        if (fragObj instanceof EvtxElement) {
            ((EvtxElement) fragObj).setTemplateInstance(templateInstance2);
        }
    }

}
