/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.sp.gpinf.indexer.parsers.util;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 * ContentHandler que simplesmente ignora todos os eventos.
 * 
 * @author Nassif
 *
 */
public class IgnoreContentHandler implements ContentHandler {

    @Override
    public void characters(char[] arg0, int arg1, int arg2) throws SAXException {
        // TODO Auto-generated method stub

    }

    @Override
    public void endDocument() throws SAXException {
        // TODO Auto-generated method stub

    }

    @Override
    public void endElement(String arg0, String arg1, String arg2) throws SAXException {
        // TODO Auto-generated method stub

    }

    @Override
    public void endPrefixMapping(String arg0) throws SAXException {
        // TODO Auto-generated method stub

    }

    @Override
    public void ignorableWhitespace(char[] arg0, int arg1, int arg2) throws SAXException {
        // TODO Auto-generated method stub

    }

    @Override
    public void processingInstruction(String arg0, String arg1) throws SAXException {
        // TODO Auto-generated method stub

    }

    @Override
    public void setDocumentLocator(Locator arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void skippedEntity(String arg0) throws SAXException {
        // TODO Auto-generated method stub

    }

    @Override
    public void startDocument() throws SAXException {
        // TODO Auto-generated method stub

    }

    @Override
    public void startElement(String arg0, String arg1, String arg2, Attributes arg3) throws SAXException {
        // TODO Auto-generated method stub

    }

    @Override
    public void startPrefixMapping(String arg0, String arg1) throws SAXException {
        // TODO Auto-generated method stub

    }

}
