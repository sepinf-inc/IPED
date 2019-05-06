/*
 * Copyright 2015-2015, Fabio Melo Pfeifer
 * 
 * This file is part of Indexador e Processador de Evidencias Digitais (IPED).
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
package dpf.mg.udi.gpinf.shareazaparser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Fabio Melo Pfeifer <pfeifer.fmp@dpf.gov.br>
 */
class XMLElement extends ShareazaEntity {

	private final List<XMLAttribute> attributes = new ArrayList<>();
	private final List<XMLElement> elements = new ArrayList<>();
	private String name;
	private String value;
	
	public XMLElement() {
		super("XMLElement"); //$NON-NLS-1$
	}

	@Override
	public void read(MFCParser ar) throws IOException {
		name = ar.readString();
		value = ar.readString();
		int n = ar.readCount();
		for (int i = 0; i < n; i++) {
			XMLAttribute attr = new XMLAttribute();
			attr.read(ar);
			if (attr.getName().length() != 0) {
				attributes.add(attr);
			}
		}
		n = ar.readCount();
		for (int i = 0; i < n; i++) {
			XMLElement elem = new XMLElement();
			elem.read(ar);
			elements.add(elem);
		}
	}

	@Override
	public void write(ShareazaOutputGenerator f) {
		write(f, false);
	}

	private void write(ShareazaOutputGenerator f, boolean inXml) {
		f.incIdent();
		if (name != null && name.length() > 0) {
			if (!inXml) {
				f.out("XML Data"); //$NON-NLS-1$
			}
			StringBuilder strElem = new StringBuilder();
			strElem.append("<").append(name); //$NON-NLS-1$
			for (XMLAttribute attr : attributes) {
				strElem.append(" ").append(attr.getName()).append("=\"").append(attr.getValue()).append("\""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
			if ((value == null || value.length() == 0) && elements.isEmpty()) {
				strElem.append("/>"); //$NON-NLS-1$
				f.out(strElem.toString());
			} else {
				strElem.append(">"); //$NON-NLS-1$
				f.out(strElem.toString());
				if (value != null && value.length() > 0) {
					f.out(value);
				}
				if (!elements.isEmpty()) {
					for (XMLElement el : elements) {
						el.write(f, true);
					}
				}
				f.out("</" + name + ">");; //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		f.decIdent();
	}

	@Override
	protected void writeImpl(ShareazaOutputGenerator f) {	
	}

}
