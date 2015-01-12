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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Date;
import java.util.Set;

import net.sf.oereader.OEData;
import net.sf.oereader.OEDeletedMessage;
import net.sf.oereader.OEMessage;
import net.sf.oereader.OEMessageInfo;
import net.sf.oereader.OEReader;
import net.sf.oereader.OETree;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.process.task.ExpandContainerTask;
import dpf.sp.gpinf.indexer.util.IndexerContext;
import dpf.sp.gpinf.indexer.util.NtfsTimeConverter;

public class OutlookDBXParser extends AbstractParser {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("outlook-dbx"));

	@Override
	public Set<MediaType> getSupportedTypes(ParseContext arg0) {
		return SUPPORTED_TYPES;
	}

	@Override
	public void parse(InputStream input, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

		EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));

		XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
		xhtml.startDocument();

		IndexerContext appContext = context.get(IndexerContext.class);
		String parentPath = appContext.getPath();

		TemporaryResources tmp = new TemporaryResources();
		OEReader oeReader = new OEReader();
		try {
			TikaInputStream tis = TikaInputStream.get(input, tmp);
			oeReader.open(tis.getFile());

			if (extractor.shouldParseEmbedded(metadata))
				if (oeReader.open && oeReader.header.rootnodeEntries > 0) {

					OETree tree = new OETree(oeReader.data, oeReader.header.rootnode);
					recurseTree(oeReader.data, tree, extractor, xhtml, parentPath);
				}

			// processDeletedEmails(oeReader, extractor, xhtml, parentPath);

		} catch (InterruptedException e) {
			throw new TikaException("OutlookPSTParser Interrupted", e);

		} finally {
			oeReader.close();
			tmp.close();
		}

		xhtml.endDocument();

	}

	private void processDeletedEmails(OEReader oeReader, EmbeddedDocumentExtractor extractor, ContentHandler handler, String path) {
		int next = oeReader.header.rootp_deletedm;
		while (next != 0) {
			OEDeletedMessage delMsg = new OEDeletedMessage(oeReader.data, next);

			Metadata mailMetadata = new Metadata();
			mailMetadata.set(HttpHeaders.CONTENT_TYPE, "message/rfc822");
			mailMetadata.set(TikaMetadataKeys.RESOURCE_NAME_KEY, "[deletado]");
			mailMetadata.set(ExpandContainerTask.COMPLETE_PATH, path);

			ByteArrayInputStream stream = new ByteArrayInputStream(delMsg.bytes.toByteArray());

			if (extractor.shouldParseEmbedded(mailMetadata))
				try {
					extractor.parseEmbedded(stream, handler, mailMetadata, true);
				} catch (Exception e) {
					e.printStackTrace();
				}

			next = delMsg.next;
		}
	}

	private void recurseTree(OEData data, OETree t, EmbeddedDocumentExtractor extractor, ContentHandler handler, String path) throws InterruptedException {
		if (t == null)
			return;

		if (Thread.currentThread().isInterrupted())
			throw new InterruptedException("Parsing interrompido.");

		try {

			for (int i = 0; i < t.bodyentries; i++) {
				OEMessageInfo messageInfo = null;
				// try-catch evita que exceção ao processar email interrompa
				// processamento dos emails filhos do nó atual
				try {
					messageInfo = new OEMessageInfo(data, t.value[i]);
					processEmail(messageInfo, extractor, handler, path);

				} catch (Throwable e) {
					if (messageInfo != null)
						System.out.println(new Date() + "\t[AVISO]\t" + "Exceção ao extrair email: " + path + ">>" + messageInfo.subject + "\t" + e);
					// e.printStackTrace();
				}

			}

			recurseTree(data, t.dChild, extractor, handler, path);

			for (int i = 0; i < t.bodyentries; i++)
				recurseTree(data, t.bChildren[i], extractor, handler, path);

		} catch (InterruptedException e) {
			throw e;
		} catch (Exception e) {
			System.out.println(new Date() + "\t[AVISO]\t" + "Exceção ao percorrer nó interno de " + path + "\t" + e);
			// e.printStackTrace();
		}

	}

	private void processEmail(OEMessageInfo messageInfo, EmbeddedDocumentExtractor extractor, ContentHandler handler, String path) throws SAXException, IOException {

		Metadata mailMetadata = new Metadata();
		mailMetadata.set(Metadata.CONTENT_TYPE, "message/rfc822");
		String subject = messageInfo.subject;
		if (subject == null || subject.trim().isEmpty())
			subject = "[Sem Assunto]";
		mailMetadata.set(TikaCoreProperties.TITLE, subject);
		mailMetadata.set(ExpandContainerTask.COMPLETE_PATH, path);
		if (messageInfo.createtime != null)
			mailMetadata.set(TikaCoreProperties.CREATED, NtfsTimeConverter.ntfsTimeToDate(messageInfo.createtime));
		// mailMetadata.set(TikaCoreProperties.MODIFIED,
		// NtfsTimeConverter.ntfsTimeToDate(messageInfo.receivetime));

		OEMessage message = messageInfo.message;
		ByteArrayInputStream stream = new ByteArrayInputStream(message.bytes.toByteArray());

		if (extractor.shouldParseEmbedded(mailMetadata))
			extractor.parseEmbedded(stream, handler, mailMetadata, true);

	}

}
