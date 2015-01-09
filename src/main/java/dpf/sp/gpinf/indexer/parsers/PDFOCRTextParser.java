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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Date;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.util.CharCountContentHandler;

public class PDFOCRTextParser extends AbstractParser {

	private static final long serialVersionUID = 1L;
	private static MediaType PDF_TYPE = MediaType.application("pdf");
	private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(PDF_TYPE);
	public static int MAXCHARS2OCR = 100;
	private PDFParser SortedPDFParser, PDFParser;
	private OCRParser ocrParser = new OCRParser();

	public PDFOCRTextParser() {
		if (PDFParser == null) {
			PDFParser = new PDFParser();
			SortedPDFParser = new PDFParser();
			SortedPDFParser.setSortByPosition(true);
		}
	}

	@Override
	public Set<MediaType> getSupportedTypes(ParseContext arg0) {
		return SUPPORTED_TYPES;
	}

	@Override
	public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

		metadata.set(HttpHeaders.CONTENT_TYPE, "application/pdf");

		handler.startDocument();
		handler = new CharCountContentHandler(handler);

		TemporaryResources tmp = new TemporaryResources();
		try {
			TikaInputStream tis = TikaInputStream.get(stream, tmp);

			File file = null;
			if (OCRParser.ENABLED) 
				file = tis.getFile();

			if (Configuration.sortPDFChars) {
				file = tis.getFile();
				try {
					SortedPDFParser.parse(tis, handler, new Metadata(), context);

				} catch (Exception e) {
					if (e.toString().contains("Comparison method violates its general contract"))
						System.out.println(new Date() + "\t[ALERTA]\t" + "SortedPDFParser error on '" + file.getPath() + "' (" + file.length() + " bytes)\t"
								+ "Execute o java com -Djava.util.Arrays.useLegacyMergeSort=true");
				}
				tis = TikaInputStream.get(file);
			}

			try {
				PDFParser.parse(tis, handler, metadata, context);

			} finally {
				if (Configuration.sortPDFChars)
					tis.close();
			}

			int charCount = ((CharCountContentHandler) handler).getCharCount();
			if (OCRParser.ENABLED && charCount <= MAXCHARS2OCR) {
				tis = TikaInputStream.get(file);
				try {
					ocrParser.parse(tis, handler, metadata, context);

				} catch (Exception e) {
					System.out.println(new Date() + "\t[AVISO]\t" + "OCRParser error on '" + file.getPath() + "' (" + file.length() + " bytes)\t" + e.toString());

				} finally {
					tis.close();
				}
				metadata.set("OCRCharCount", "OCRCharCount" + (((CharCountContentHandler)handler).getCharCount() - charCount));

			}

		} finally {
			tmp.close();
		}

		handler.endDocument();

	}

	/*
	 * private void icePDFParse(){
	 * 
	 * Document iceDoc = null; try{ iceDoc = new Document();
	 * iceDoc.setFile(tis.getFile().getAbsolutePath());
	 * 
	 * int numPages = iceDoc.getNumberOfPages(); for(int i = 0; i < numPages;
	 * i++){ try{ PageText pageText = iceDoc.getPageText(i); if (pageText !=
	 * null){ char[] chars = ("\nPage " + i + "\n").toCharArray();
	 * handler.characters(chars, 0, chars.length); chars =
	 * pageText.toString().toCharArray(); handler.characters(chars, 0,
	 * chars.length); } }catch(Exception e){ } }
	 * 
	 * try{ PInfo info = iceDoc.getInfo(); String value = info.getSubject();
	 * if(value != null) metadata.add(Metadata.SUBJECT, value); value =
	 * info.getTitle(); if(value != null) metadata.add(Metadata.TITLE, value);
	 * value = info.getAuthor(); if(value != null) metadata.add(Metadata.AUTHOR,
	 * value); value = info.getCreator(); if(value != null)
	 * metadata.add(Metadata.CREATOR, value); value = info.getProducer();
	 * if(value != null) metadata.add(Metadata.APPLICATION_NAME, value); value =
	 * info.getKeywords(); if(value != null) metadata.add(Metadata.KEYWORDS,
	 * value); PDate date = info.getCreationDate(); if(date != null)
	 * metadata.add(Metadata.CREATION_DATE, date.toString()); date =
	 * info.getModDate(); if(date != null) metadata.add(Metadata.MODIFIED,
	 * date.toString());
	 * 
	 * }catch(Exception e){ }
	 * 
	 * }catch(Exception e){ e.printStackTrace(); throw new
	 * TikaException("IcePDF Exception", e); }finally{ iceDoc.dispose(); }
	 * 
	 * }
	 */

}
