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
package dpf.sp.gpinf.indexer.parsers;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.linuxense.javadbf.DBFReader;

/**
 * Parser para arquivos xBase (DBF)
 */
public class XBaseParser extends AbstractParser {

	private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("x-dbf")); //$NON-NLS-1$
	public static final String DBF_MIME_TYPE = "application/x-dbf"; //$NON-NLS-1$

	private static final long serialVersionUID = 5305942120263605439L;

	@Override
	public Set<MediaType> getSupportedTypes(ParseContext arg0) {

		return SUPPORTED_TYPES;
	}

	@Override
	public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

		XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
		xhtml.startDocument();

		DBFReader reader = new DBFReader(stream);

		for (int i = 0; i < reader.getFieldCount(); i++)
			xhtml.characters(reader.getField(i).getName() + "\t"); //$NON-NLS-1$

		xhtml.characters("\r\n"); //$NON-NLS-1$

		DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM);

		Object[] rowObjects;
		while ((rowObjects = reader.nextRecord()) != null) {
			for (int i = 0; i < rowObjects.length; i++) {
				if (rowObjects[i] != null)
					if (Date.class.isInstance(rowObjects[i]))
						xhtml.characters(df.format(rowObjects[i]) + "\t"); //$NON-NLS-1$
					else
						xhtml.characters(rowObjects[i].toString().trim() + "\t"); //$NON-NLS-1$
				else
					xhtml.characters("\t"); //$NON-NLS-1$
			}
			xhtml.characters("\r\n"); //$NON-NLS-1$
		}

		xhtml.endDocument();

	}

	public static void main(String[] args) {

		try {
			String filepath = "E:/dbfs/x 238459.DBF"; //$NON-NLS-1$
			InputStream input = new FileInputStream(filepath);
			XBaseParser parser = new XBaseParser();
			ParseContext context = new ParseContext();
			BodyContentHandler handler = new BodyContentHandler(new BufferedWriter(new FileWriter("E:/dbfs/saida.txt"))); //$NON-NLS-1$
			Metadata metadata = new Metadata();
			context.set(Parser.class, parser);

			parser.parse(input, handler, metadata, context);

		} catch (Exception e1) {
			e1.printStackTrace();
		}

	}

}
