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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

import org.apache.tika.config.Field;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Message;
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

import com.pff.PSTAttachment;
import com.pff.PSTContact;
import com.pff.PSTException;
import com.pff.PSTFile;
import com.pff.PSTFolder;
import com.pff.PSTMessage;
import com.pff.PSTObject;
import com.pff.PSTRecipient;

import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import dpf.sp.gpinf.indexer.parsers.util.Messages;
import dpf.sp.gpinf.indexer.parsers.util.Util;
import dpf.sp.gpinf.indexer.util.SimpleHTMLEncoder;
import iped3.util.ExtraProperties;

/**
 * Parser para arquivos PST. Extrai emails, anexos, contatos, tarefas, etc.
 * O Outlook fragmenta o email em diversos objetos, então é gerado e extraído
 * um preview HTML do email.
 * 
 * @author Nassif
 *
 */
public class OutlookPSTParser extends AbstractParser {

	private static Logger LOGGER = LoggerFactory.getLogger(OutlookPSTParser.class);
	private static final long serialVersionUID = 5552796814190294332L;
	public static final String HAS_ATTACHS = "pst_email_has_attachs"; //$NON-NLS-1$
	public static final String PST_ATTACH = "pst_attachment"; //$NON-NLS-1$
	public static final String OUTLOOK_MSG_MIME = "message/outlook-pst"; //$NON-NLS-1$
	
	public static Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("vnd.ms-outlook-pst")); //$NON-NLS-1$

	private ParseContext context;
	private EmbeddedDocumentExtractor extractor;
	private XHTMLContentHandler xhtml;

	private SimpleDateFormat df = new SimpleDateFormat(Messages.getString("OutlookPSTParser.DateFormat")); //$NON-NLS-1$
	private LibpffPSTParser libpffParser = new LibpffPSTParser();
	
	private boolean recoverDeleted = true;
	private boolean useLibpffParser = true;
	
	private int numEmails = 0;

	@Override
	public Set<MediaType> getSupportedTypes(ParseContext arg0) {
		return SUPPORTED_TYPES;
	}
	
	@Field
	public void setRecoverDeleted(boolean value) {
	    this.recoverDeleted = value;
	}
	
	@Field
    public void setUseLibpffParser(boolean value) {
        this.useLibpffParser = value;
    }

	@Override
	public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

	    OutlookPSTParser pstParser = new OutlookPSTParser();
	    pstParser.setRecoverDeleted(recoverDeleted);
	    pstParser.setUseLibpffParser(useLibpffParser);
	    pstParser.safeParse(stream, handler, metadata, context);
	}

	private void safeParse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

		this.context = context;
		extractor = context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));
		
		String fileName =  metadata.get(Metadata.RESOURCE_NAME_KEY);
		ItemInfo itemInfo = context.get(ItemInfo.class);
		if(itemInfo != null)
			fileName =  itemInfo.getPath();

		xhtml = new XHTMLContentHandler(handler, metadata);
		xhtml.startDocument();

		TemporaryResources tmp = new TemporaryResources();
		TikaInputStream tis = null;
		File tmpFile = null;
		PSTFile pstFile = null;
		try {
			tis = TikaInputStream.get(stream, tmp);
			tmpFile = tis.getFile();
			pstFile = new PSTFile(tmpFile);

			if (extractor.shouldParseEmbedded(metadata))
				walkFolder(pstFile.getRootFolder(), "", -1); //$NON-NLS-1$

			metadata.set(TikaCoreProperties.TITLE, pstFile.getMessageStore().getDisplayName());
			metadata.set("NumEmails", numEmails + "");
			
			if(recoverDeleted) {
			    libpffParser.setExtractOnlyDeleted(true);
	            libpffParser.parse(tis, handler, metadata, context);
			}

		} catch (InterruptedException e) {
			LOGGER.error("Extraction of emails was interrupted on " + fileName + " " + e.toString()); //$NON-NLS-1$ //$NON-NLS-2$
			throw new TikaException(this.getClass().getSimpleName() + " interrupted", e); //$NON-NLS-1$
		
		} catch (Exception e) {
			if(e instanceof IOException && tmpFile == null){
				LOGGER.error("Tempfile creation and processing failed on " + fileName + " " + e.toString()); //$NON-NLS-1$ //$NON-NLS-2$
			    throw (IOException) e;
			}else if(e instanceof TikaException && e.getCause() instanceof InterruptedException)
				throw (TikaException)e;
			else{
			    if(useLibpffParser) {
			        LOGGER.warn("java-libpst failed, using libpff on " + fileName, e); //$NON-NLS-1$
	                libpffParser.setExtractOnlyDeleted(false);
	                if(!recoverDeleted)
	                    libpffParser.setExtractOnlyActive(true);
	                libpffParser.parse(tis, handler, metadata, context);
			    }else
			        LOGGER.error("java-libpst failed on " + fileName, e); //$NON-NLS-1$
				
				if(e.toString().contains("Only unencrypted and compressable PST files are supported at this time")) //$NON-NLS-1$
	                throw new EncryptedDocumentException(e);
			}

		} finally {
			if (pstFile != null && pstFile.getFileHandle() != null)
				pstFile.getFileHandle().close();
			tmp.close();
		}

		xhtml.endDocument();

	}

	public void walkFolder(PSTFolder folder, String path, long parent) throws InterruptedException {

		try {
			String folderName = folder.getDisplayName();
			if (folderName != null && !folderName.isEmpty()){
				path += ">>" + folderName; //$NON-NLS-1$
				parent = processFolder(folder, parent);
			}

			// process the emails for this folder
			if (folder.getContentCount() > 0) {
				PSTObject child;
				do {
					child = folder.getNextChild();

					if (child != null)
						if (child.getClass().equals(PSTMessage.class)) {
							PSTMessage email = (PSTMessage) child;
							processEmailAndAttachs(email, path, parent+"");

						} else
							processPSTObject(child, path, parent);

					if (Thread.currentThread().isInterrupted()) {
						// System.out.println("PSTParser interrompido. " +
						// Thread.currentThread().getName());
						throw new InterruptedException("PSTParser interrupted."); //$NON-NLS-1$

					}

				} while (child != null);
			}

		} catch (InterruptedException e) {
			throw e;
		} catch (Exception e) {
			LOGGER.warn("Exception walking email folder {}\t{}", path, e.toString()); //$NON-NLS-1$
			// e.printStackTrace();
		}

		// recurse into subfolders
		try {
			if (folder.hasSubfolders()) {
				Vector<PSTFolder> childFolders = folder.getSubFolders();
				for (PSTFolder childFolder : childFolders) {
					walkFolder(childFolder, path, parent);
				}
			}
		} catch (InterruptedException e) {
			throw e;
		} catch (Exception e) {
			LOGGER.warn("Exception recursing into subfolders of {}\t{}", path, e.toString()); //$NON-NLS-1$
			//e.printStackTrace();
		}

	}
	
	private long processFolder(PSTFolder folder, long parent) throws SAXException, IOException{
		Metadata entrydata = new Metadata();
		entrydata.set(TikaCoreProperties.TITLE, folder.getDisplayName());
		entrydata.set(TikaCoreProperties.CREATED, folder.getCreationTime());
		entrydata.set(TikaCoreProperties.MODIFIED, folder.getLastModificationTime());
		entrydata.set(Metadata.COMMENT, folder.getComment());
		entrydata.set(ExtraProperties.EMBEDDED_FOLDER, "true"); //$NON-NLS-1$
		entrydata.set(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(folder.getDescriptorNodeId()));
		entrydata.set(ExtraProperties.PARENT_VIRTUAL_ID, String.valueOf(parent));
		extractor.parseEmbedded(new ByteArrayInputStream(new byte[0]), xhtml, entrydata, true);
        return folder.getDescriptorNodeId();
    }

	private void processEmailAndAttachs(PSTMessage email, String path, String parent) {

		parent = processEmail(email, path, parent);
		if (email.hasAttachments()) {
			processAttachs(email, path + ">>" + email.getSubject(), parent); //$NON-NLS-1$
		}
	}

	private void processPSTObject(PSTObject obj, String path, long parent) {

		try {
			Metadata metadata = new Metadata();
			String objName = obj.getClass().getSimpleName();
			if (obj.getClass().equals(PSTContact.class)) {
				PSTContact contact = (PSTContact) obj;
				String suffix = contact.getGivenName();
				if (suffix == null || suffix.isEmpty())
					suffix = contact.getSMTPAddress();
				if (suffix != null && !suffix.isEmpty())
					objName += "-" + suffix; //$NON-NLS-1$
				metadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, "application/outlook-contact"); //$NON-NLS-1$
			} else
				metadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, OUTLOOK_MSG_MIME);

			metadata.set(TikaCoreProperties.TITLE, objName);
			Charset charset = Charset.forName("UTF-8"); //$NON-NLS-1$
			
			metadata.set(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(obj.getDescriptorNodeId()));
            metadata.set(ExtraProperties.PARENT_VIRTUAL_ID, String.valueOf(parent));
            
			StringBuilder preview = new StringBuilder();
			preview.append("<html>"); //$NON-NLS-1$
			preview.append("<head>"); //$NON-NLS-1$
			preview.append("<meta http-equiv=\"content-type\" content=\"text/html; charset=" + charset + "\" />"); //$NON-NLS-1$ //$NON-NLS-2$
			preview.append("</head>"); //$NON-NLS-1$
			
			Set<Method> methodList = new TreeSet<Method>(new MethodComarator());
			Method subject = null, body = null, bodyHtml = null;
			if(obj instanceof PSTMessage){
			    subject = PSTMessage.class.getMethod("getSubject"); //$NON-NLS-1$
			    body = PSTMessage.class.getMethod("getBody"); //$NON-NLS-1$
			    bodyHtml = PSTMessage.class.getMethod("getBodyHTML"); //$NON-NLS-1$
			    Method[] methods = PSTMessage.class.getDeclaredMethods();
	            methodList.addAll(Arrays.asList(methods));
	            methodList.removeAll(Arrays.asList(subject, body, bodyHtml));
			}
			Method[] methods = obj.getClass().getDeclaredMethods();
			methodList.addAll(Arrays.asList(methods));
			
			if(subject != null) appendValue(obj, subject, preview);
			if(body != null) appendValue(obj, body, preview);
			for (Method method : methodList)
			    appendValue(obj, method, preview);
			if(bodyHtml != null) appendValue(obj, bodyHtml, preview);
			
			preview.append("</html>"); //$NON-NLS-1$
			ByteArrayInputStream stream = new ByteArrayInputStream(preview.toString().getBytes(charset));

			if (extractor.shouldParseEmbedded(metadata))
				extractor.parseEmbedded(stream, xhtml, metadata, true);

		} catch (Exception e) {
			LOGGER.warn("Exception extracting object {}>>{}", path, obj.getDisplayName()); //$NON-NLS-1$
			// e.printStackTrace();
		}

	}
	
	private void appendValue(PSTObject obj, Method method, StringBuilder preview) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException{
	    if (method.getParameterTypes().length == 0) {
            String name = method.getName();
            if (name.startsWith("get") && !name.equals("getRTFBody")) { //$NON-NLS-1$ //$NON-NLS-2$
                name = name.substring(3);
                Object value = method.invoke(obj);
                if (value != null && !value.toString().trim().isEmpty())
                    preview.append(name + ": " + value + "<br>"); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }
	}
	
	private class MethodComarator implements Comparator<Method>{
        @Override
        public int compare(Method o1, Method o2) {
            return o1.getName().compareTo(o2.getName());
        }
	}

	private String processEmail(PSTMessage email, String path, String parent) {
	    String virtualId = "email-" + numEmails++; //$NON-NLS-1$
		Metadata metadata = new Metadata();
		try {
			String subject = email.getSubject();
			if (subject == null || subject.trim().isEmpty())
				subject = Messages.getString("OutlookPSTParser.NoSubject"); //$NON-NLS-1$
			metadata.set(ExtraProperties.MESSAGE_SUBJECT, subject);
			metadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, OUTLOOK_MSG_MIME);
			
			metadata.set(ExtraProperties.ITEM_VIRTUAL_ID, virtualId);
			metadata.set(ExtraProperties.PARENT_VIRTUAL_ID, parent);
			
			if(email.hasAttachments())
				metadata.set(HAS_ATTACHS, "true"); //$NON-NLS-1$
			
			Charset charset = Charset.forName("UTF-8"); //$NON-NLS-1$
			StringBuilder preview = new StringBuilder();
			preview.append("<html>"); //$NON-NLS-1$
			preview.append("<!--PST Email Message Indexer Preview-->"); //$NON-NLS-1$
			preview.append("<head>"); //$NON-NLS-1$
			preview.append("<meta http-equiv=\"content-type\" content=\"text/html; charset=" + charset + "\" />"); //$NON-NLS-1$ //$NON-NLS-2$
			preview.append("</head>"); //$NON-NLS-1$
			preview.append("<body style=\"background-color:white;text-align:left;font-family:arial;color:black;font-size:14px;margin:5px;\">"); //$NON-NLS-1$

			preview.append("<b>" + Messages.getString("OutlookPSTParser.Subject") + ": " + SimpleHTMLEncoder.htmlEncode(subject) + "</b><br>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

			String value = email.getSenderName() + " " + email.getSenderEmailAddress(); //$NON-NLS-1$
			if (!value.isEmpty()){
				metadata.set(Message.MESSAGE_FROM, value);
				preview.append("<b>" + Messages.getString("OutlookPSTParser.From") + ":</b> " + SimpleHTMLEncoder.htmlEncode(value) + "<br>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			}

			Object[][] recipTypes = { { PSTRecipient.MAPI_TO, Messages.getString("OutlookPSTParser.To") }, { PSTRecipient.MAPI_CC, "CC:" }, { PSTRecipient.MAPI_BCC, "BCC:" } }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			String[] metaRecips = {Message.MESSAGE_TO, Message.MESSAGE_CC, Message.MESSAGE_BCC};
			
			for (int k = 0; k < recipTypes.length; k++) {
				StringBuilder recipients = new StringBuilder();
				for (int i = 0; i < email.getNumberOfRecipients(); i++) {
					PSTRecipient recip = email.getRecipient(i);
					if (recipTypes[k][0].equals(recip.getRecipientType())) {
						String recipName = recip.getDisplayName();
						String recipAddr = recip.getEmailAddress();
						if (!recipAddr.equals(recipName))
							recipName += " " + recipAddr; //$NON-NLS-1$

						recipients.append(recipName + "; "); //$NON-NLS-1$
					}
				}
				if (recipients.length() > 0){
					metadata.set(metaRecips[k], recipients.toString());
					preview.append("<b>" + recipTypes[k][1] + "</b> " + SimpleHTMLEncoder.htmlEncode(recipients.toString()) + "<br>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				}
			}

			Date date = email.getClientSubmitTime();
			if (date != null){
				metadata.set(ExtraProperties.MESSAGE_DATE, date);
				preview.append("<b>" + Messages.getString("OutlookPSTParser.Sent") + ":</b> " + df.format(date) + "<br>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			}
			
			List<String> attachNames = getAttachNames(email);
			if(!attachNames.isEmpty()) {
			    preview.append("<b>" + Messages.getString("OutlookPSTParser.Attachments") + " (" + attachNames.size() + "):</b><br>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                for(String attach : attachNames) {
                    preview.append(SimpleHTMLEncoder.htmlEncode(attach) + "<br>"); //$NON-NLS-1$
                }
			}

			preview.append("<hr>"); //$NON-NLS-1$

			String bodyHtml = email.getBodyHTML();
			if (bodyHtml != null && !bodyHtml.isEmpty()) {
				preview.append(bodyHtml);
				metadata.set(ExtraProperties.MESSAGE_BODY, Util.getContentPreview(bodyHtml, true));
			}else{
				String text = email.getBody();
				if(text != null && !text.trim().isEmpty()){
					metadata.set(ExtraProperties.MESSAGE_BODY, Util.getContentPreview(text, false));
					text = SimpleHTMLEncoder.htmlEncode(text);
					preview.append("<pre>"); //$NON-NLS-1$
					preview.append(text);
					preview.append("</pre>"); //$NON-NLS-1$
				}
			}
			
			writeInternetHeaders(email.getTransportMessageHeaders(), preview);

			preview.append("</body>"); //$NON-NLS-1$
			preview.append("</html>"); //$NON-NLS-1$

			ByteArrayInputStream stream = new ByteArrayInputStream(preview.toString().getBytes(charset));
			preview = null;

			if (extractor.shouldParseEmbedded(metadata))
				extractor.parseEmbedded(stream, xhtml, metadata, true);

			stream.close();

		} catch (Exception e) {
			LOGGER.warn("Exception extracting email: {}>>{}\t{}", path, email.getSubject(), e.toString()); //$NON-NLS-1$
			// e.printStackTrace();
		}
		
		return virtualId;
	}
	
	private void writeInternetHeaders(String headers, StringBuilder preview){
		if(!headers.isEmpty()){
			preview.append("<hr>"); //$NON-NLS-1$
			preview.append("<div style=\"background-color:white;text-align:left;font-family:arial;color:black;font-size:12px;margin:5px;\">"); //$NON-NLS-1$
			preview.append("Internet Headers:<br>"); //$NON-NLS-1$
			String[] lines = headers.split("\n"); //$NON-NLS-1$
			for(String line : lines){
				if(!line.trim().isEmpty())
					preview.append(SimpleHTMLEncoder.htmlEncode(line.trim()) + "<br>"); //$NON-NLS-1$
			}
			preview.append("</div>"); //$NON-NLS-1$
		}
	}
	
	private List<String> getAttachNames(PSTMessage email){
	    ArrayList<String> attachs = new ArrayList<>();
	    for (int i = 0; i < email.getNumberOfAttachments(); i++) {
	        try {
                PSTAttachment attach = email.getAttachment(i);
                String filename = attach.getLongFilename();
                if (filename.isEmpty())
                    filename = attach.getFilename();
                PSTMessage attachedEmail = attach.getEmbeddedPSTMessage();
                if (attachedEmail != null)
                    filename = attachedEmail.getSubject();
                if (filename.isEmpty())
                    filename = Messages.getString("OutlookPSTParser.Attachment") + i; //$NON-NLS-1$
                attachs.add(filename);
                
            } catch (PSTException | IOException e) {
            }
	    }
	    return attachs;
	}

	private void processAttachs(PSTMessage email, String path, String parent) {
		int numberOfAttachments = email.getNumberOfAttachments();
		for (int x = 0; x < numberOfAttachments; x++) {
			String filename = ""; //$NON-NLS-1$
			InputStream attachStream = null;
			try {
				PSTAttachment attach = email.getAttachment(x);

				PSTMessage attachedEmail = attach.getEmbeddedPSTMessage();
				if (attachedEmail != null) {
					processEmailAndAttachs(attachedEmail, path, parent);

				} else {
					attachStream = attach.getFileInputStream();

					filename = attach.getLongFilename();
					if (filename.isEmpty())
						filename = attach.getFilename();

					Metadata metadata = new Metadata();
					metadata.set(Metadata.RESOURCE_NAME_KEY, filename);
					metadata.set(TikaCoreProperties.CREATED, attach.getCreationTime());
					// metadata.set(TikaCoreProperties.MODIFIED,
					// attach.getLastModificationTime());
					//metadata.set(ExtraProperties.EMBEDDED_PATH, path);
					metadata.set(Metadata.CONTENT_TYPE, attach.getMimeTag());
					metadata.set(PST_ATTACH, "true"); //$NON-NLS-1$
					
					metadata.set(ExtraProperties.ITEM_VIRTUAL_ID, parent + "_attach" + x);
		            metadata.set(ExtraProperties.PARENT_VIRTUAL_ID, parent);
                    
					if (extractor.shouldParseEmbedded(metadata))
						extractor.parseEmbedded(attachStream, xhtml, metadata, true);
				}

			} catch (Exception e) {
				LOGGER.warn("Exception extracting attachment {}:{}>>{}\t{}", x, path, filename, e.toString()); //$NON-NLS-1$
				//e.printStackTrace();

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
