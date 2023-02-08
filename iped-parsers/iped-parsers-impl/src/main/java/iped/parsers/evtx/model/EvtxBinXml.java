package iped.parsers.evtx.model;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;

import iped.parsers.evtx.template.TemplateInstance;

public class EvtxBinXml {
    private EvtxFile evtxFile;
    private ByteBuffer bb;
    private ArrayList<EvtxXmlFragment> fragments = new ArrayList<EvtxXmlFragment>();
    private ArrayList<EvtxElement> elements;
    EvtxRecord record;

    public EvtxBinXml(EvtxFile evtxFile, EvtxRecord record, ByteBuffer bb) throws EvtxParseExeption {
        this.evtxFile = evtxFile;
        this.bb = bb;
        this.record = record;

        int startOffset = bb.position();
        int curOffset = startOffset;

        BinXmlToken b = new BinXmlToken(evtxFile, bb);
        while (curOffset < 64 * 1024) {
            String tstr;
            switch (b.type & 0xbf) {
                case BinXmlToken.LIBFWEVT_XML_TOKEN_END_OF_FILE:
                    return;
                case BinXmlToken.LIBFWEVT_XML_TOKEN_FRAGMENT_HEADER:
                    EvtxXmlFragment frag = new EvtxXmlFragment(evtxFile, this, bb);
                    fragments.add(frag);
                    break;
                case BinXmlToken.LIBFWEVT_XML_TOKEN_TEMPLATE_INSTANCE:
                    TemplateInstance templateInstance = new TemplateInstance(evtxFile, bb);
                    tstr = templateInstance.toString();
                    if (tstr.startsWith("<RenderingInfo")) {
                        // rendering info. does not contains adittional data
                    } else {
                        System.out.println("Unexpected token template instance:");
                        System.out.println(tstr);
                    }
                    break;
                default:
                    System.out.print("Unexpected token in file:" + evtxFile.getName());
                    System.out.println(b.type);
                    return;
            }

            b = new BinXmlToken(evtxFile, bb);
        }
    }

    public ArrayList<EvtxElement> getElements() {
        if (elements == null) {
            elements = new ArrayList<EvtxElement>();
            for (Iterator<EvtxXmlFragment> iterator = fragments.iterator(); iterator.hasNext();) {
                EvtxXmlFragment frag = iterator.next();
                if (frag.getElement() != null) {
                    elements.add(frag.getElement());
                }
            }
        }
        return elements;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        for (Iterator iterator = fragments.iterator(); iterator.hasNext();) {
            EvtxXmlFragment evtxXmlFragment = (EvtxXmlFragment) iterator.next();
            b.append(evtxXmlFragment.toString());
        }

        return b.toString();
    }
}
