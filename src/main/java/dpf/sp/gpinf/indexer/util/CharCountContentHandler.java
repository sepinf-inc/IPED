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
package dpf.sp.gpinf.indexer.util;

import org.apache.tika.sax.EmbeddedContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class CharCountContentHandler extends EmbeddedContentHandler {

	private int charCount = 0;

	public CharCountContentHandler(ContentHandler handler) {
		super(handler);
	}

	@Override
	public void characters(char[] ch, int start, int length) throws SAXException {
		charCount += length;
		super.characters(ch, start, length);
	}

	@Override
	public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
		charCount += length;
		super.ignorableWhitespace(ch, start, length);
	}

	public int getCharCount() {
		return charCount;
	}

}
