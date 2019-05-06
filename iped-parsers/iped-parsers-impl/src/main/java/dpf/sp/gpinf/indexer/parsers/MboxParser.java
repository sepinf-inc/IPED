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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.util.IgnoreContentHandler;
import dpf.sp.gpinf.indexer.parsers.util.Messages;
import iped3.util.ExtraProperties;

/**
 * Parser para arquivos MBOX.
 * 
 * @author Nassif
 *
 */
public class MboxParser extends AbstractParser {

	private static Logger LOGGER = LoggerFactory.getLogger(MboxParser.class);
	/** Serial version UID */
	private static final long serialVersionUID = -1762689436731160661L;

	private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("mbox")); //$NON-NLS-1$

	public static final String MBOX_MIME_TYPE = "application/mbox"; //$NON-NLS-1$
	public static final String MBOX_RECORD_DIVIDER = "From "; //$NON-NLS-1$
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

		String charsetName = "windows-1252"; //$NON-NLS-1$
		InputStreamReader isr = new InputStreamReader(stream, charsetName);
		BufferedReader reader = new BufferedReader(isr, 100000);
		String name = metadata.get(Metadata.RESOURCE_NAME_KEY);

		String line;
		int count = 0;
		if (extractor.shouldParseEmbedded(metadata))
			do {
				line = reader.readLine();

				if (!line.startsWith(MBOX_RECORD_DIVIDER)) {
					try {
						count++;
						ByteArrayOutputStream message = new ByteArrayOutputStream(100000);
						do {

							message.write(line.getBytes(charsetName));
							message.write(0x0A);
							line = reader.readLine();

						} while (line != null && !line.startsWith(MBOX_RECORD_DIVIDER) && message.size() < MAIL_MAX_SIZE);

						ByteArrayInputStream messageStream = new ByteArrayInputStream(message.toByteArray());
						message = null;

						Metadata mailMetadata = getMailMetadata(messageStream, count);
						messageStream.reset();

						if (extractor.shouldParseEmbedded(mailMetadata))
							extractor.parseEmbedded(messageStream, xhtml, mailMetadata, true);

					} catch (Throwable t) {
						if (count == 1)
							throw new TikaException("MboxParser Exception", t); //$NON-NLS-1$

						LOGGER.warn("Error extracting email {} from {}\t{}", count, name, t.toString()); //$NON-NLS-1$
					}

				}

			} while (line != null && !Thread.currentThread().isInterrupted());

		reader.close();
		xhtml.endDocument();

	}

	private Metadata getMailMetadata(InputStream stream, int count) throws Exception {

		Metadata mailMetadata = new Metadata();
		mailMetadata.set(Metadata.CONTENT_TYPE, "message/rfc822"); //$NON-NLS-1$

		try {
			RFC822Parser parser = new RFC822Parser();
			parser.parse(stream, new IgnoreContentHandler(), mailMetadata, new ParseContext());

		} catch (Exception e) {
			if (count == 1)
				throw e;
		}

		String subject = mailMetadata.get(ExtraProperties.MESSAGE_SUBJECT);
		if (subject == null || subject.trim().isEmpty())
			subject = Messages.getString("MboxParser.NoSubject"); //$NON-NLS-1$
		
		Metadata finalMetadata = new Metadata(); 
		finalMetadata.set(Metadata.CONTENT_TYPE, "message/rfc822"); //$NON-NLS-1$
		finalMetadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, "message/rfc822"); //$NON-NLS-1$
		finalMetadata.set(ExtraProperties.MESSAGE_SUBJECT, subject);
		finalMetadata.set(TikaCoreProperties.CREATED, mailMetadata.getDate(TikaCoreProperties.CREATED));

		return finalMetadata;
	}

}
