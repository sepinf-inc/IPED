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

import gpinf.dev.data.EvidenceFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.ContentHandlerDecorator;
import org.apache.tika.sax.SecureContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.IndexFiles;
import dpf.sp.gpinf.indexer.index.FileCarver;
import dpf.sp.gpinf.indexer.util.IndexerContext;

/**
 * Parser padrão do Indexador. Como o AutoDetectParser, detecta o tipo do
 * arquivo e delega o parsing para o parser apropriado. Porém aproveita o
 * CONTENT_TYPE caso informado via metadados, evitando nova detecção. Além disso
 * utiliza o RawStringParser como fallback ou no caso de alguma Exceção durante
 * o parsing padrão. Finalmente, escreve os metadados ao final (inclusive de
 * subitens).
 */
public class IndexerDefaultParser extends CompositeParser {

	private static final long serialVersionUID = 1L;
	private Parser errorParser = new RawStringParser();
	private Detector detector;
	public static int parsingErrors = 0;
	public static String INDEXER_CONTENT_TYPE = "Indexer-Content-Type";
	public static String INDEXER_TIMEOUT = "Indexer-Timeout-Occurred";

	public IndexerDefaultParser() {
		super(TikaConfig.getDefaultConfig().getMediaTypeRegistry(), TikaConfig.getDefaultConfig().getParser());
		detector = TikaConfig.getDefaultConfig().getDetector();
		this.setFallback(new RawStringParser());
	}

	public void setErrorParser(Parser parser) {
		this.errorParser = parser;
	}

	private static synchronized void incParsingErrors() {
		parsingErrors++;
	}

	@Override
	public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

		// Utilizado para não terminar documento antes de escrever metadados
		NoEndBodyContentHandler noEndHandler = new NoEndBodyContentHandler(handler);

		TemporaryResources tmp = new TemporaryResources();
		TikaInputStream tis = TikaInputStream.get(stream, tmp);

		IndexerContext appContext = context.get(IndexerContext.class);
		EvidenceFile evidence = context.get(EvidenceFile.class);
		File file = null;
		if (evidence == null)
			file = tis.getFile();
		else
			context.set(EvidenceFile.class, null);

		String filePath = appContext.getPath();
		long length = -1;

		String lengthStr = metadata.get(Metadata.CONTENT_LENGTH);
		if (lengthStr != null)
			length = Long.parseLong(lengthStr);
		else if(file != null)
			lengthStr = Long.toString(file.length());

		String contentType = null;
		try {
			// calcula content_type caso não seja conhecido
			contentType = metadata.get(INDEXER_CONTENT_TYPE);
			if (contentType == null) {
				try {
					contentType = detector.detect(tis, metadata).toString();

				} catch (IOException e) {
					System.out.println(new Date() + "\t[ALERTA]\t" + Thread.currentThread().getName() + " Detecção do tipo abortada: " + filePath + "' (" + length + " bytes)\t\t" + e.toString());
				}
			}
			metadata.set(Metadata.CONTENT_TYPE, contentType);
			metadata.set(INDEXER_CONTENT_TYPE, contentType);

			// TIKA-216: Zip bomb prevention
			SecureContentHandler sch = new SecureContentHandler(noEndHandler, tis);
			// sch.setMaximumPackageEntryDepth(100);
			try {
				if (length != 0)
					if (metadata.get(INDEXER_TIMEOUT) == null)
						super.parse(tis, sch, metadata, context);
					else
						errorParser.parse(tis, sch, metadata, context);

			} catch (SAXException e) {
				// Convert zip bomb exceptions to TikaExceptions
				sch.throwIfCauseOf(e);
				throw e;
			}

			
		// Parsing com rawparser no caso de exceções de parsing
		} catch (TikaException e) {
			if (!Thread.currentThread().isInterrupted() && !(e.getCause() instanceof InterruptedException)
			/*
			 * && !contentType.equals("image/gif") &&
			 * !contentType.equals("image/jpeg") &&
			 * !contentType.equals("image/x-ms-bmp") &&
			 * !contentType.equals("image/png") && !contentType.startsWith(
			 * "application/vnd.openxmlformats-officedocument") &&
			 * !contentType.startsWith("application/vnd.oasis.opendocument") &&
			 * !contentType.equals("application/zip") &&
			 * !contentType.equals("application/pdf") &&
			 * !contentType.equals("application/gzip")
			 */
			) {
				if(evidence != null && evidence.isCarved() && FileCarver.ignoreCorrupted)
					throw e;
				
				String value;
				if( (e instanceof EncryptedDocumentException ||
					(value = metadata.get("pdf:encrypted")) != null && Boolean.valueOf(value)) || 
					((value = metadata.get("Security")) != null && Integer.valueOf(value) == 1) ||
					(contentType.contains("vnd.oasis.opendocument") && e.getCause() != null && e.getCause().toString().contains("only DEFLATED entries can have EXT descriptor"))){
					
					metadata.set("EncryptedDocument", "true");
					if(evidence != null)
						evidence.setEncrypted(true);
					
				}else{
					
					incParsingErrors();
					metadata.set("ParserException", "true");
					
					String errorMsg = "";
					if (errorParser instanceof RawStringParser)
						errorMsg = "extraindo raw strings de ";

					System.out.println(new Date() + "\t[AVISO]\t" + Thread.currentThread().getName() + " Exceção de parsing: " + errorMsg + filePath + " (" + lengthStr + " bytes)\t\t" + e.toString());

					if (IndexFiles.getInstance() != null && IndexFiles.getInstance().verbose)
						e.printStackTrace();

					try {
						if (evidence != null)
							tis = evidence.getTikaStream();
						else
							tis = TikaInputStream.get(file);

						errorParser.parse(tis, noEndHandler, metadata, context);	

					} finally {
						tis.close();
					}
				}
				
			}

		} finally {
			tmp.dispose();
		}

		// Escreve metadados ao final do texto
		String text;
		text = "\nMETADADOS:";
		noEndHandler.characters(text.toCharArray(), 0, text.length());
		String[] names = metadata.names();
		Arrays.sort(names);
		for (String name : names) {
			if (name != null && !name.equals(TikaMetadataKeys.RESOURCE_NAME_KEY) && !name.equals("PLTE PLTEEntry") && !name.equals("Chroma Palette PaletteEntry")
					&& !name.equals(Metadata.CONTENT_TYPE) && !name.equals("X-Parsed-By")) {
				text = "\n" + name + ": ";
				for (String value : metadata.getValues(name))
					text += value + " ";
				noEndHandler.characters(text.toCharArray(), 0, text.length());
			}
		}
		text = "\n-----------------------------------\n";
		noEndHandler.characters(text.toCharArray(), 0, text.length());

		if (noEndHandler.getEndDocumentWasCalled())
			noEndHandler.reallyEndDocument();

	}

	private class NoEndBodyContentHandler extends ContentHandlerDecorator {

		public static final String XHTML = "http://www.w3.org/1999/xhtml";
		private boolean endDocument, endFrameset, endBody, endHtml;

		public NoEndBodyContentHandler(ContentHandler handler) {
			super(handler);
		}

		@Override
		public void endElement(String uri, String local, String name) throws SAXException {
			if (name.equals("frameset"))
				endFrameset = true;
			else if (name.equals("body"))
				endBody = true;
			else if (name.equals("html"))
				endHtml = true;
			else
				super.endElement(uri, local, name);
		}

		@Override
		public void endDocument() throws SAXException {
			endDocument = true;
		}

		public void reallyEndDocument() throws SAXException {
			if (endFrameset)
				super.endElement(XHTML, "frameset", "frameset");
			if (endBody)
				super.endElement(XHTML, "body", "body");
			if (endHtml)
				super.endElement(XHTML, "html", "html");

			super.endDocument();
		}

		public boolean getEndDocumentWasCalled() {
			return endDocument;
		}
	}

}
