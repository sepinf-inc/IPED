package iped.engine.datasource.ufed;

import org.apache.commons.lang3.StringUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import iped.parsers.ufed.model.BaseModel;

public class UfedModelHandlerListOnly extends UfedModelHandler {

    public UfedModelHandlerListOnly(XMLReader xmlReader, ContentHandler parentHandler,
            iped.engine.datasource.ufed.UfedModelHandler.UfedModelListener listener) {
        super(xmlReader, parentHandler, listener);
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {

        elementValueBuilder.setLength(0); // Clear builder for new element

        if ("model".equalsIgnoreCase(qName) || StringUtils.equalsAnyIgnoreCase(qName, "field", "value") && modelStack.size() == 1) {
            super.startElement(uri, localName, qName, attributes);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {

        if ("model".equalsIgnoreCase(qName) || StringUtils.equalsAnyIgnoreCase(qName, "field", "value") && modelStack.size() == 1 || modelStack.isEmpty()) {
            super.endElement(uri, localName, qName);
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (modelStack.size() == 1 && !fieldNameStack.isEmpty() && fieldNameStack.peek().equals("Source")) {
            super.characters(ch, start, length);
        }
    }

    @Override
    protected void setAttributes(BaseModel model, Attributes attributes) {
        // nothing...
    }

    @Override
    protected void addChildModel(BaseModel parent, BaseModel child, String fieldName) {
        // nothing...
    }
}
