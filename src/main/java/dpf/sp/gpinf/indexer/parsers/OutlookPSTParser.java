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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Set;
import java.util.Vector;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.pff.PSTAttachment;
import com.pff.PSTContact;
import com.pff.PSTException;
import com.pff.PSTFile;
import com.pff.PSTFolder;
import com.pff.PSTMessage;
import com.pff.PSTObject;
import com.pff.PSTRecipient;

import dpf.sp.gpinf.indexer.util.IndexerContext;
import dpf.sp.gpinf.indexer.util.SimpleHTMLEncoder;

public class OutlookPSTParser extends AbstractParser {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5552796814190294332L;
	public static final String HAS_ATTACHS = "pst_email_has_attachs";
	public static final String OUTLOOK_MSG_MIME = "message/outlook-pst";

	IndexerContext appContext;
	private EmbeddedDocumentExtractor extractor;
	private XHTMLContentHandler xhtml;

	private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("vnd.ms-outlook-pst"));

	private SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

	@Override
	public Set<MediaType> getSupportedTypes(ParseContext arg0) {
		return SUPPORTED_TYPES;
	}

	@Override
	public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

		new OutlookPSTParser().safeParse(stream, handler, metadata, context);
	}

	private void safeParse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

		extractor = context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));

		appContext = context.get(IndexerContext.class);

		xhtml = new XHTMLContentHandler(handler, metadata);
		xhtml.startDocument();

		TemporaryResources tmp = new TemporaryResources();
		PSTFile pstFile = null;
		try {
			TikaInputStream tis = TikaInputStream.get(stream, tmp);
			pstFile = new PSTFile(tis.getFile());

			if (extractor.shouldParseEmbedded(metadata))
				processFolder(pstFile.getRootFolder(), appContext.getPath());

			metadata.set(Metadata.TITLE, pstFile.getMessageStore().getDisplayName());

		} catch (PSTException e) {
			throw new TikaException("OutlookPSTParser Exception", e);

		} catch (InterruptedException e) {
			throw new TikaException("OutlookPSTParser Interrupted", e);

		} finally {
			if (pstFile != null && pstFile.getFileHandle() != null)
				pstFile.getFileHandle().close();
			tmp.close();
		}

		xhtml.endDocument();

	}

	public void processFolder(PSTFolder folder, String path) throws InterruptedException {

		String folderName = folder.getDisplayName();
		if (!folderName.isEmpty())
			path += ">>" + folderName;

		// process the emails for this folder
		try {
			if (folder.getContentCount() > 0) {
				PSTObject child;
				do {
					child = folder.getNextChild();

					if (child != null)
						if (child.getClass().equals(PSTMessage.class)) {
							PSTMessage email = (PSTMessage) child;
							processEmailAndAttachs(email, path, null);

						} else
							processPSTObject(child, path);

					if (Thread.currentThread().isInterrupted()) {
						// System.out.println("PSTParser interrompido. " +
						// Thread.currentThread().getName());
						throw new InterruptedException("PSTParser interrompido.");

					}

				} while (child != null);
			}

		} catch (InterruptedException e) {
			throw e;
		} catch (Exception e) {
			System.out.println(new Date() + "\t[AVISO]\t" + "Exceção ao percorrer emails da pasta " + path + "\t" + e);
			// e.printStackTrace();
		}

		// recurse into subfolders
		try {
			if (folder.hasSubfolders()) {
				Vector<PSTFolder> childFolders = folder.getSubFolders();
				for (PSTFolder childFolder : childFolders) {
					processFolder(childFolder, path);
				}
			}
		} catch (InterruptedException e) {
			throw e;
		} catch (Exception e) {
			System.out.println(new Date() + "\t[AVISO]\t" + "Exceção ao obter subpastas de " + path + "\t" + e);
			// e.printStackTrace();
		}

	}

	private void processPSTObject(PSTObject obj, String path) {

		try {
			Metadata metadata = new Metadata();
			String objName = obj.getClass().getSimpleName();
			if (obj.getClass().equals(PSTContact.class)) {
				PSTContact contact = (PSTContact) obj;
				String suffix = contact.getGivenName();
				if (suffix == null || suffix.isEmpty())
					suffix = contact.getSMTPAddress();
				if (suffix != null && !suffix.isEmpty())
					objName += "-" + suffix;
				metadata.set(Metadata.CONTENT_TYPE, "application/outlook-contact");
			} else
				metadata.set(Metadata.CONTENT_TYPE, "message/outlook-pst");

			metadata.set(Metadata.RESOURCE_NAME_KEY, objName);
			Charset charset = Charset.forName("UTF-8");

			StringBuilder preview = new StringBuilder();
			preview.append("<html>");
			preview.append("<head>");
			preview.append("<meta http-equiv=\"content-type\" content=\"text/html; charset=" + charset + "\" />");
			preview.append("</head>");

			for (Method method : obj.getClass().getDeclaredMethods()) {
				if (method.getParameterTypes().length == 0) {
					String name = method.getName();
					if (name.startsWith("get")) {
						name = name.substring(3);
						Object value = method.invoke(obj);
						if (value != null && !value.toString().isEmpty())
							preview.append(name + ": " + value + "<br>");
					}
				}

			}
			preview.append("</html>");
			ByteArrayInputStream stream = new ByteArrayInputStream(preview.toString().getBytes(charset));

			if (extractor.shouldParseEmbedded(metadata))
				extractor.parseEmbedded(stream, xhtml, metadata, true);

		} catch (Exception e) {
			System.out.println(new Date() + "\t[AVISO]\t" + "ExceÃ§Ã£o ao processar item: " + path + ">>" + obj.getDisplayName());
			// e.printStackTrace();
		}

	}

	private void processEmailAndAttachs(PSTMessage email, String path, String extract) {

		EvidenceFile prevEvidence = appContext.getEvidence();
		Metadata metadata = processEmail(email, path, extract);
		if (email.hasAttachments()) {
			/*int previousId = appContext.getId();
			String id = metadata.get(EmbeddedFileParser.INDEXER_ID);
			if (id != null)
				appContext.setId(Integer.valueOf(id));
			*/
			
			processAttachs(email, path + ">>" + email.getSubject(), metadata.get(EmbeddedFileParser.TO_EXTRACT));

			//appContext.setId(previousId);
		}
		appContext.setEvidence(prevEvidence);
	}

	private Metadata processEmail(PSTMessage email, String path, String extract) {

		Metadata metadata = new Metadata();
		metadata.set(EmbeddedFileParser.TO_EXTRACT, extract);
		try {
			String subject = email.getSubject();
			if (subject == null || subject.trim().isEmpty())
				subject = "[Sem Assunto]";
			metadata.set(TikaCoreProperties.TITLE, subject);
			metadata.set(EmbeddedFileParser.COMPLETE_PATH, path);
			metadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, OUTLOOK_MSG_MIME);
			if(email.hasAttachments())
				metadata.set(HAS_ATTACHS, "true");

			// metadata.set(Metadata.TITLE, email.getSubject());
			// metadata.set(Metadata.MESSAGE_TO, email.getDisplayTo());
			// metadata.set(Metadata.MESSAGE_FROM, email.getSenderName() + " " +
			// email.getSenderEmailAddress());
			metadata.set(TikaCoreProperties.CREATED, email.getClientSubmitTime());
			// metadata.set(TikaCoreProperties.MODIFIED,
			// email.getLastModificationTime());

			Charset charset = Charset.forName("UTF-8");

			StringBuilder preview = new StringBuilder();
			preview.append("<html>");
			preview.append("<!--PST Email Message Indexer Preview-->");
			preview.append("<head>");
			preview.append("<meta http-equiv=\"content-type\" content=\"text/html; charset=" + charset + "\" />");
			preview.append("</head>");
			preview.append("<body style=\"background-color:white;text-align:left;font-family:arial;color:black;font-size:14px;margin:5px;\">");

			preview.append("<b>Assunto: " + SimpleHTMLEncoder.htmlEncode(subject) + "</b><br>");

			String value = email.getSenderName() + " " + email.getSenderEmailAddress();
			if (!value.isEmpty())
				preview.append("<b>De:</b> " + SimpleHTMLEncoder.htmlEncode(value) + "<br>");

			/*
			 * value = email.getRecipientsString(); if(!value.isEmpty())
			 * preview.append("<b>Para:</b> " +
			 * SimpleHTMLEncoder.htmlEncode(value) + "<br>") ; /*value =
			 * email.getDisplayCC(); if(!value.isEmpty())
			 * preview.append("<b>CC:</b> " +
			 * SimpleHTMLEncoder.htmlEncode(value) + "<br>") ; value =
			 * email.getDisplayBCC(); if(!value.isEmpty())
			 * preview.append("<b>BCC:</b> " +
			 * SimpleHTMLEncoder.htmlEncode(value) + "<br>") ;
			 */
			Object[][] recipTypes = { { PSTRecipient.MAPI_TO, "Para:" }, { PSTRecipient.MAPI_CC, "CC:" }, { PSTRecipient.MAPI_BCC, "BCC:" } };
			for (int k = 0; k < recipTypes.length; k++) {
				StringBuilder recipients = new StringBuilder();
				for (int i = 0; i < email.getNumberOfRecipients(); i++) {
					PSTRecipient recip = email.getRecipient(i);
					if (recipTypes[k][0].equals(recip.getRecipientType())) {
						String recipName = recip.getDisplayName();
						String recipAddr = recip.getEmailAddress();
						if (!recipAddr.equals(recipName))
							recipName += " " + recipAddr;

						recipients.append(recipName + "; ");
					}

				}
				if (recipients.length() > 0)
					preview.append("<b>" + recipTypes[k][1] + "</b> " + SimpleHTMLEncoder.htmlEncode(recipients.toString()) + "<br>");
			}

			Date date = email.getClientSubmitTime();
			if (date != null)
				preview.append("<b>Envio:</b> " + df.format(date));

			preview.append("<hr>");

			String bodyHtml = email.getBodyHTML();
			preview.append(bodyHtml);
			if (bodyHtml == null || bodyHtml.isEmpty()) {
				preview.append("<pre>");
				String text = email.getBody();
				// text = SimpleHTMLEncoder.htmlEncode(text);
				// text = text.replaceAll("\n", "<br>");
				preview.append(text);
				preview.append("</pre>");
			}

			preview.append("</body>");
			preview.append("</html>");

			ByteArrayInputStream stream = new ByteArrayInputStream(preview.toString().getBytes(charset));
			preview = null;

			if (extractor.shouldParseEmbedded(metadata))
				extractor.parseEmbedded(stream, xhtml, metadata, true);

			stream.close();

			return metadata;

		} catch (Exception e) {
			System.out.println(new Date() + "\t[AVISO]\t" + "Exceção ao extrair email: " + path + ">>" + email.getSubject() + "\t" + e);
			// e.printStackTrace();
		}

		return metadata;
	}

	private void processAttachs(PSTMessage email, String path, String extract) {
		int numberOfAttachments = email.getNumberOfAttachments();
		for (int x = 0; x < numberOfAttachments; x++) {
			String filename = "";
			InputStream attachStream = null;
			try {
				PSTAttachment attach = email.getAttachment(x);

				PSTMessage attachedEmail = attach.getEmbeddedPSTMessage();
				if (attachedEmail != null) {
					processEmailAndAttachs(attachedEmail, path, extract);

				} else {
					attachStream = attach.getFileInputStream();

					filename = attach.getLongFilename();
					if (filename.isEmpty())
						filename = attach.getFilename();

					Metadata metadata = new Metadata();
					metadata.set(TikaMetadataKeys.RESOURCE_NAME_KEY, filename);
					metadata.set(TikaCoreProperties.CREATED, attach.getCreationTime());
					// metadata.set(TikaCoreProperties.MODIFIED,
					// attach.getLastModificationTime());
					metadata.set(EmbeddedFileParser.COMPLETE_PATH, path);
					metadata.set(Metadata.CONTENT_TYPE, attach.getMimeTag());
					metadata.set(EmbeddedFileParser.TO_EXTRACT, extract);

					if (extractor.shouldParseEmbedded(metadata))
						extractor.parseEmbedded(attachStream, xhtml, metadata, true);
				}

			} catch (Exception e) {
				System.out.println(new Date() + "\t[AVISO]\t" + "Exceção ao extrair anexo " + x + ": " + path + ">>" + filename + "\t" + e);
				// e.printStackTrace();

			} finally {
				if (attachStream != null)
					try {
						attachStream.close();
					} catch (IOException e) {
					}
			}

		}
	}

	private PSTObject getObject(PSTFile pstFile, int objectId) throws IOException, PSTException {
		return PSTObject.detectAndLoadPSTObject(pstFile, objectId);
	}

}
