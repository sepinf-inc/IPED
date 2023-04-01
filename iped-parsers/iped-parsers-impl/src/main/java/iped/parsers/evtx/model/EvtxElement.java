package iped.parsers.evtx.model;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;

import iped.parsers.evtx.template.TemplateInstance;

public class EvtxElement {
    byte mode;
    private short depId;
    private int dataSize;
    private int nameOffset;
    private EvtxName name;
    private EvtxFile evtxFile;
    private EvtxAttributes attributes;

    ArrayList<Object> children = new ArrayList<Object>();
    ArrayList<EvtxElement> childElements = new ArrayList<EvtxElement>();
    private EvtxElement parent;
    private TemplateInstance templateInstance;

    public EvtxElement(EvtxFile evtxFile, ByteBuffer bb, byte mode, EvtxElement parent) throws EvtxParseException {
        this.evtxFile = evtxFile;
        this.parent = parent;
        this.mode = mode;

        this.depId = bb.getShort();
        this.dataSize = bb.getInt();
        this.nameOffset = bb.getInt();
        this.name = new EvtxName(evtxFile, bb, nameOffset);

        int attrsSize = 0;
        if (mode == 0x41) {
            attrsSize = bb.getInt();
            if (attrsSize > 0) {
                this.attributes = new EvtxAttributes(evtxFile, bb, attrsSize, this);
            }
        }

        BinXmlToken token = new BinXmlToken(evtxFile, bb);// end token expected
        if ((token.type != BinXmlToken.LIBFWEVT_XML_TOKEN_CLOSE_START_ELEMENT_TAG) && (token.type != BinXmlToken.LIBFWEVT_XML_TOKEN_CLOSE_EMPTY_ELEMENT_TAG)) {
            throw new EvtxParseException("Attributes end tag not found");
        }

        if ((token.type & 0xbf) == BinXmlToken.LIBFWEVT_XML_TOKEN_CLOSE_START_ELEMENT_TAG) {
            while (true) {
                token = new BinXmlToken(evtxFile, bb);
                switch (token.type & 0xbf) {
                    case BinXmlToken.LIBFWEVT_XML_TOKEN_CLOSE_EMPTY_ELEMENT_TAG:
                    case BinXmlToken.LIBFWEVT_XML_TOKEN_END_ELEMENT_TAG:
                        return;
                    case BinXmlToken.LIBFWEVT_XML_TOKEN_OPEN_START_ELEMENT_TAG:
                        EvtxElement element = new EvtxElement(evtxFile, bb, token.type, this);
                        children.add(element);
                        childElements.add(element);
                        break;
                    case BinXmlToken.LIBFWEVT_XML_TOKEN_CDATA_SECTION:
                        EvtxCDATASegment cdata = new EvtxCDATASegment(evtxFile, bb);
                        children.add(cdata);
                        break;
                    case BinXmlToken.LIBFWEVT_XML_TOKEN_VALUE:
                        EvtxValue value = new EvtxValue(evtxFile, bb);
                        children.add(value);
                        break;
                    case BinXmlToken.LIBFWEVT_XML_TOKEN_PI_TARGET:
                        EvtxPITarget pitarget = new EvtxPITarget(evtxFile, bb);
                        children.add(pitarget);
                        break;
                    case BinXmlToken.LIBFWEVT_XML_TOKEN_CHARACTER_REFERENCE:
                        EvtxCharacterReference cr = new EvtxCharacterReference(evtxFile, bb);
                        children.add(cr);
                        break;
                    case BinXmlToken.LIBFWEVT_XML_TOKEN_ENTITY_REFERENCE:
                        EvtxTokenEntityReference teref = new EvtxTokenEntityReference(evtxFile, bb);
                        children.add(teref);
                        break;
                    case BinXmlToken.LIBFWEVT_XML_TOKEN_NORMAL_SUBSTITUTION:
                        EvtxNormalSubstitution subs = new EvtxNormalSubstitution(evtxFile, bb, this);
                        if (subs.getValue() instanceof TemplateInstance) {
                            TemplateInstance ti = ((TemplateInstance) subs.getValue());
                            ti.getFragment().setTemplateInstance(ti);
                            childElements.add(ti.getFragment().getElement());
                        }
                        children.add(subs);
                        break;
                    case BinXmlToken.LIBFWEVT_XML_TOKEN_OPTIONAL_SUBSTITUTION:
                        EvtxOptionalSubstitution osubs = new EvtxOptionalSubstitution(evtxFile, bb, this);
                        if (osubs.getValue() instanceof TemplateInstance) {
                            TemplateInstance ti = ((TemplateInstance) osubs.getValue());
                            ti.getFragment().setTemplateInstance(ti);
                            childElements.add(ti.getFragment().getElement());
                        }
                        children.add(osubs);
                        break;
                    default:
                        break;
                }
            }
        }
    }

    @Override
    public String toString() {
        StringBuffer b = new StringBuffer();

        int level = 0;
        EvtxElement curLevel = parent;
        while (curLevel != null) {
            level++;
            curLevel = curLevel.parent;
        }

        b.append("\t".repeat(level));
        b.append("<");
        b.append(name.value);
        if (attributes != null) {
            for (Iterator iterator = attributes.attributes.iterator(); iterator.hasNext();) {
                EvtxAttribute object = (EvtxAttribute) iterator.next();
                b.append(" ");
                b.append(object.toString());
            }
        }

        if (children.size() > 0) {
            if (childElements.size() > 0) {
                b.append(">\n");
            } else {
                b.append(">");
            }
            for (Iterator iterator = children.iterator(); iterator.hasNext();) {
                Object child = (Object) iterator.next();
                b.append(child.toString());
            }

            if (childElements.size() > 0) {
                b.append("\t".repeat(level));
            }
            b.append("</");
            b.append(name.value);
            b.append(">\n");
        } else {
            b.append("/>\n");
        }

        return b.toString();
    }

    public void setTemplateInstance(TemplateInstance curTemplateInstance) {
        this.templateInstance = curTemplateInstance;
    }

    public TemplateInstance getTemplateInstance() {
        if (this.templateInstance == null) {
            if (this.parent != null) {
                return this.parent.getTemplateInstance();
            }
        }
        return this.templateInstance;
    }

    public String getName() {
        return name.value;
    }

    public ArrayList<EvtxAttribute> getAttributes() {
        if (attributes != null) {
            return attributes.attributes;
        }
        return null;
    }

    public Object getAttributeByName(String name) {
        ArrayList<EvtxAttribute> list = attributes.attributes;
        for (Iterator iterator = list.iterator(); iterator.hasNext();) {
            EvtxAttribute evtxAttribute = (EvtxAttribute) iterator.next();
            if (evtxAttribute.getName().equals(name)) {
                return evtxAttribute.value;
            }
        }
        return null;
    }

}
