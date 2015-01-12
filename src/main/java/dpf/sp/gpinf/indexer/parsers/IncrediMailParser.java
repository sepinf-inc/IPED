/*
 * Copyright 2012-2014, Gabriel de Munno Francisco
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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Date;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.util.IgnoreContentHandler;
import dpf.sp.gpinf.indexer.util.IndexerContext;

public class IncrediMailParser extends AbstractParser {

	/** Serial version UID */
	private static final long serialVersionUID = -1762689436731160661L;

	private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("x-incredimail"));

	public static final String IMM_MIME_TYPE = "application/x-incredimail";
	private static int MAIL_MAX_SIZE = 50000000;

	@Override
	public Set<MediaType> getSupportedTypes(ParseContext context) {
		return SUPPORTED_TYPES;
	}

	@Override
	public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

		EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));

		XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
		xhtml.startDocument();

		IndexerContext appContext = context.get(IndexerContext.class);
		String parentPath = appContext.getPath();

		String charsetName = "windows-1252";
		InputStreamReader isr = new InputStreamReader(stream, charsetName);
		BufferedReader reader = new BufferedReader(isr, 100000);

		int count = 0;

		try {

			// System.out.println(new Date() + "\t[AVISO]\t" +"IMM Parser - " +
			// parentPath);
			if (extractor.shouldParseEmbedded(metadata)) {
				String line;
				String strBuf1 = null;
				String strBuf2 = null;

				// enquanto tivermos entrada e a execucao nao for interrompida
				while (((line = reader.readLine()) != null) && !Thread.currentThread().isInterrupted()) {
					count++;

					try {
						// identificando a mensagem e colocando em um ByteArray
						ByteArrayOutputStream message = new ByteArrayOutputStream(100000);

						if (strBuf1 != null) {
							// escrevendo o inicio da mensagem lido
							// anteriormente
							message.write(strBuf1.getBytes(charsetName));
							message.write(0x0A);
							if (strBuf2 != null) {
								message.write(strBuf2.getBytes(charsetName));
								message.write(0x0A);
							}
						}

						do {

							String line2 = null;
							if (line.startsWith("--") && line.endsWith("--")) {
								// verificando se e uma quebra de mensagem do
								// IncrediMail
								// assumindo que as quebras de mensagens sao
								// marcadas por um:
								// 0D 0A (Enter) seguido por uma linha iniciada
								// por --SEQUENCIA-- e depois por uma linha
								// contendo um dos sequintes cabecalhos:
								// "Return-Path:", "MIME-Version:",
								// "Delivered-To:", "Message-Id", "Subject:" ,
								// "From:"

								line2 = reader.readLine();
								if (line2 != null) {
									String tmpStr = line2.toLowerCase();
									if (tmpStr.startsWith("return-path:") || tmpStr.startsWith("mime-version:") || tmpStr.startsWith("delivered-to:") || tmpStr.startsWith("message-Id")
											|| tmpStr.startsWith("subject:") || tmpStr.startsWith("from:") || tmpStr.startsWith("x-store-info:")) {

										// eh uma nova mensagem
										strBuf1 = line;
										strBuf2 = line2;
										line = null; // saindo do loop da
														// mensagem
									}
								}

							} else if (line.trim().startsWith("</html>")) {
								// algumas mensagens foram identificadas apenas
								// comecando com o Return-Path
								// precedido por uma linha contendo apenas o
								// fechamento da tag html, separado
								// ou nao por uma linha em branco

								line2 = reader.readLine();
								// System.out.println(new Date() + "\t[AVISO]\t"
								// +"IMM Parser - Apos html: " + line2);
								if (line2 != null && line2.equals(""))
									line2 = reader.readLine(); // pulando uma
																// quebra
																// adicional
								if (line2 != null) {
									String tmpStr = line2.trim().toLowerCase();

									if (tmpStr.startsWith("return-path:")) {
										// eh uma nova mensagem
										message.write(line.getBytes(charsetName));
										message.write(0x0A);
										strBuf1 = line2;
										strBuf2 = null;
										line = null; // saindo do loop da
														// mensagem
									}
								}
							}

							if (line != null) {
								message.write(line.getBytes(charsetName));
								message.write(0x0A);
								if (line2 != null) {
									message.write(line2.getBytes(charsetName));
									message.write(0x0A);
								}
								line = reader.readLine();
							}

						} while (line != null && message.size() < MAIL_MAX_SIZE && !Thread.currentThread().isInterrupted());

						// chamando o parser para a mensagem lida
						// System.out.println(new Date() + "\t[AVISO]\t"
						// +"Parser da mensagem " + count + " da caixa " +
						// parentPath);
						ByteArrayInputStream messageStream = new ByteArrayInputStream(message.toByteArray());
						message = null;

						Metadata mailMetadata = getMailMetadata(messageStream, parentPath, count);
						messageStream.reset();

						if (extractor.shouldParseEmbedded(mailMetadata))
							extractor.parseEmbedded(messageStream, xhtml, mailMetadata, true);

					} catch (Throwable t) {
						if (count == 1)
							throw new TikaException("IncrediMailParser Exception", t);

						System.out.println(new Date() + "\t[AVISO]\t" + "Exceção ao extrair email " + count + " de " + parentPath + "\t" + t);
					}
				}
			}
		} finally {
			reader.close();
		}
		xhtml.endDocument();

	}

	private Metadata getMailMetadata(InputStream stream, String path, int count) throws Exception {

		Metadata mailMetadata = new Metadata();
		mailMetadata.set(Metadata.CONTENT_TYPE, "message/rfc822");
		mailMetadata.set(EmbeddedFileParser.COMPLETE_PATH, path);

		try {
			RFC822Parser parser = new RFC822Parser();
			parser.parse(stream, new IgnoreContentHandler(), mailMetadata, new ParseContext());

		} catch (Exception e) {
			if (count == 1)
				throw e;
		}

		String subject = mailMetadata.get(TikaCoreProperties.TITLE);
		if (subject == null || subject.trim().isEmpty())
			subject = "[Sem Assunto]";
		mailMetadata.set(TikaCoreProperties.TITLE, subject);

		return mailMetadata;
	}

}