package dpf.sp.gpinf.indexer.parsers.util;

import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import org.apache.tika.sax.ToTextContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class ToCSVContentHandler extends ToTextContentHandler {

    private boolean inTable = false, inCell = false;

    public ToCSVContentHandler(OutputStream stream, String encoding) throws UnsupportedEncodingException {
        super(stream, encoding);
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        if (qName.equals("table")) //$NON-NLS-1$
            inTable = true;

        if (inTable && (qName.equals("td") || qName.equals("th"))) { //$NON-NLS-1$ //$NON-NLS-2$
            inCell = true;
            super.characters("\"".toCharArray(), 0, 1); //$NON-NLS-1$
        }
    }

    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (qName.equals("table")) //$NON-NLS-1$
            inTable = false;

        if (inTable) {
            if (qName.equals("tr")) { //$NON-NLS-1$
                super.characters("\r\n".toCharArray(), 0, 2); //$NON-NLS-1$

            } else if (qName.equals("td") || qName.equals("th")) { //$NON-NLS-1$ //$NON-NLS-2$
                super.characters("\",".toCharArray(), 0, 2); //$NON-NLS-1$
                inCell = false;
            }
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (inCell) {
            String value = new String(ch, start, length);
            value = value.trim().replace("\"", "\"\""); //$NON-NLS-1$ //$NON-NLS-2$
            super.characters(value.toCharArray(), 0, value.length());
        }
    }

}
