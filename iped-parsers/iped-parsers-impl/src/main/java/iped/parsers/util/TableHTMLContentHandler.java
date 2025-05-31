package iped.parsers.util;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class TableHTMLContentHandler extends XHTMLContentHandler {

    public TableHTMLContentHandler(ContentHandler handler, Metadata metadata) {
        super(handler, metadata);
    }

    public void startTable() throws SAXException {
        startElement("table");
    }

    public void endTable() throws SAXException {
        endElement("table");
    }

    public void addHeaderRow(String... columnNames) throws SAXException {
        startElement("thead");
        startElement("tr");
        for (String column : columnNames) {
            startElement("th");
            characters(column);
            endElement("th");
        }
        endElement("tr");
        endElement("thead");

        // Start tbody for subsequent data rows
        super.startElement("tbody");
    }

    public void addRow(boolean firstHead, String... values) throws SAXException {
        startElement("tr");

        int i = 0;
        for (String value : values) {
            String elem = firstHead && (i++ == 0) ? "th" : "td";
            startElement(elem);
            characters(value != null ? value : "");
            endElement(elem);
        }

        super.endElement("tr");
    }

    /**
     * Call this method if you added a header row, to properly close the tbody. Must be called before endTable() if a header
     * was added.
     * 
     * @throws SAXException
     */
    public void endTableBody() throws SAXException {
        super.endElement("tbody");
    }
}