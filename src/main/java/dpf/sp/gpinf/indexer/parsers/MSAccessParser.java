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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
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
import org.xml.sax.helpers.AttributesImpl;

import com.healthmarketscience.jackcess.Column;
import com.healthmarketscience.jackcess.DataType;
import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.DatabaseBuilder;
import com.healthmarketscience.jackcess.PropertyMap.Property;
import com.healthmarketscience.jackcess.Row;
import com.healthmarketscience.jackcess.Table;
import com.healthmarketscience.jackcess.impl.OleUtil;
import com.healthmarketscience.jackcess.util.OleBlob;
import com.healthmarketscience.jackcess.util.OleBlob.Content;
import com.healthmarketscience.jackcess.util.OleBlob.EmbeddedContent;
import com.healthmarketscience.jackcess.util.OleBlob.PackageContent;

import dpf.sp.gpinf.indexer.util.IOUtil;

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
			
			xhtml.startElement("head");
			xhtml.startElement("style");
			xhtml.characters("table {border-collapse: collapse;} table, td, th {border: 1px solid black;}");
			xhtml.endElement("style");
			xhtml.endElement("head");
			
			// consome input para TIKA nao reclamar ????
			int skip = 0;
			//while ((skip += stream.skip(file.length() - skip)) != file.length());

			DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
			EmbeddedDocumentExtractor embeddedParser = context.get(EmbeddedDocumentExtractor.class, 
					new ParsingEmbeddedDocumentExtractor(context));  

			Set<String> tableNames = database.getTableNames();
			for (String tableName : tableNames) {
				
				Table table = database.getTable(tableName);
				xhtml.startElement("div");
				xhtml.characters("Tabela: " + tableName + "\r\n");
				AttributesImpl attr = new AttributesImpl();
				//attr.addAttribute("", "border", "CDATA", "CDATA", "1");
				//attr.addAttribute("", "border-collapse", "border-collapse", "CDATA", "collapse");
				xhtml.startElement("table", attr);
				
				xhtml.startElement("tr");
				for (Column col : table.getColumns()) {
					xhtml.startElement("td");
					xhtml.characters(col.getName());
					xhtml.endElement("td");
				}
				xhtml.endElement("tr");

				for (Row row : table) {
					xhtml.startElement("tr");
					for(Column column : table.getColumns()) {
						String columnName = column.getName();
					    Object value = row.get(columnName);
					    
					    xhtml.startElement("td");
						if (value != null)
							if (value instanceof Date)
								xhtml.characters(df.format(value));
						
							else if(column.getType().equals(DataType.OLE)){
								OleBlob oleBlob = OleUtil.parseBlob((byte[])value);
								Content content = oleBlob.getContent();
								InputStream blobStream = null;
								Metadata meta = new Metadata();
								
								try{
									if(content instanceof OleBlob.EmbeddedContent) {
										blobStream = ((EmbeddedContent)content).getStream();
									}else
										blobStream = oleBlob.getBinaryStream();
									
									if(content instanceof OleBlob.PackageContent){
										metadata.set(Metadata.RESOURCE_NAME_KEY, ((PackageContent)content).getPrettyName());
									}
									
									embeddedParser.parseEmbedded(blobStream, xhtml, meta, true);
									
								} catch (SQLException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
									
								}finally{
									if(blobStream != null)
										IOUtil.closeQuietly(blobStream);
								}
							
							}else if(column.getType().equals(DataType.BINARY)){
								embeddedParser.parseEmbedded(new ByteArrayInputStream((byte[])value), xhtml, new Metadata(), true);

							}else
								xhtml.characters(value.toString().trim());	
						
						xhtml.endElement("td");
					}
					xhtml.endElement("tr");
				}

				xhtml.endElement("table");
				xhtml.endElement("div");
				xhtml.startElement("br");
				xhtml.endElement("br");
			}
			xhtml.endDocument();

		} finally {
			if (database != null)
				database.close();
			tmp.dispose();
		}
	}
}
