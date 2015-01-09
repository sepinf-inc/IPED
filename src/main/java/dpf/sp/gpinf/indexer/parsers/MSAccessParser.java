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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.healthmarketscience.jackcess.Column;
import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.DatabaseBuilder;
import com.healthmarketscience.jackcess.PropertyMap.Property;
import com.healthmarketscience.jackcess.Row;
import com.healthmarketscience.jackcess.Table;

/**
 * Parser para arquivos MS Access
 */
public class MSAccessParser extends AbstractParser {

	private static final long serialVersionUID = 3632017735942270181L;
	private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("x-msaccess"));
	public static final String ACCESS_MIME_TYPE = "application/x-msaccess";

	public static void main(String[] args) {
		// teste
		try {
			String filepath = "E:/mdbs/A7472C6B74B24F740ACA8777BC93F8FE.MDB";
			InputStream input = new FileInputStream(filepath);
			MSAccessParser parser = new MSAccessParser();
			ParseContext context = new ParseContext();
			BodyContentHandler handler = new BodyContentHandler(new BufferedWriter(new FileWriter("E:/mdbs/saida.txt")));
			Metadata metadata = new Metadata();
			metadata.add(TikaMetadataKeys.RESOURCE_NAME_KEY, filepath);
			context.set(Parser.class, parser);

			parser.parse(input, handler, metadata, context);

			String[] names = metadata.names();
			Arrays.sort(names);
			for (String name : names)
				System.out.print(name + ": " + metadata.get(name) + "\n");

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	@Override
	public Set<MediaType> getSupportedTypes(ParseContext arg0) {
		return SUPPORTED_TYPES;
	}

	@Override
	public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

		TemporaryResources tmp = new TemporaryResources();
		Database database = null;
		DatabaseBuilder builder = new DatabaseBuilder();
		builder.setReadOnly(true);

		try {
			TikaInputStream tis = TikaInputStream.get(stream, tmp);
			File file = tis.getFile();
			builder.setFile(file);
			database = builder.open();
			
			metadata.set(HttpHeaders.CONTENT_TYPE, ACCESS_MIME_TYPE);
			metadata.remove(TikaMetadataKeys.RESOURCE_NAME_KEY);
			for (Property prop : database.getSummaryProperties())
				metadata.set(prop.getName(), prop.getValue().toString());

			XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
			xhtml.startDocument();

			// consome input para TIKA nao reclamar ????
			int skip = 0;
			while ((skip += stream.skip(file.length() - skip)) != file.length())
				;

			DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

			Set<String> tableNames = database.getTableNames();
			for (String tableName : tableNames) {
				Table table = database.getTable(tableName);
				xhtml.characters("Tabela: " + tableName);
				xhtml.characters("\r\n");

				for (Column col : table.getColumns()) {
					xhtml.characters(col.getName() + "\t");
				}
				xhtml.characters("\r\n");

				for (Row row : table) {
					for (Object value : row.values()) {
						if (value != null)
							if (Date.class.isInstance(value))
								xhtml.characters(df.format(value) + "\t");
							else
								xhtml.characters(value.toString().trim() + "\t");// replaceAll("\n",
																					// " "));
						else
							xhtml.characters("\t");
					}
					xhtml.characters("\r\n");
				}

				// xhtml.characters(table.display());
				xhtml.characters("\r\n");
			}
			xhtml.endDocument();

		} finally {
			if (database != null)
				database.close();
			tmp.dispose();
		}
	}
}
