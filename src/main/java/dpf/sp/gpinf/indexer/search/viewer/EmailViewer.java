/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dpf.sp.gpinf.indexer.search.viewer;

import java.awt.Desktop;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.codec.DecoderUtil;
import org.apache.james.mime4j.dom.address.Address;
import org.apache.james.mime4j.dom.address.AddressList;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.dom.address.MailboxList;
import org.apache.james.mime4j.dom.field.AddressListField;
import org.apache.james.mime4j.dom.field.ContentDispositionField;
import org.apache.james.mime4j.dom.field.ContentIdField;
import org.apache.james.mime4j.dom.field.ContentTypeField;
import org.apache.james.mime4j.dom.field.DateTimeField;
import org.apache.james.mime4j.dom.field.MailboxListField;
import org.apache.james.mime4j.dom.field.ParsedField;
import org.apache.james.mime4j.dom.field.UnstructuredField;
import org.apache.james.mime4j.field.LenientFieldParser;
import org.apache.james.mime4j.parser.ContentHandler;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.mime4j.util.CharsetUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.parser.ParseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.javafx.application.PlatformImpl;

import dpf.sp.gpinf.indexer.util.StreamSource;
import dpf.sp.gpinf.indexer.util.FileContentSource;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.LuceneSimpleHTMLEncoder;
import dpf.sp.gpinf.indexer.util.SeekableInputStream;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.concurrent.Worker.State;
import netscape.javascript.JSObject;

public class EmailViewer extends HtmlViewer {

	private static Logger LOGGER = LoggerFactory.getLogger(EmailViewer.class); 
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	MailContentHandler mch;
	MimeStreamParser parser;

	@Override
	public boolean isSupportedType(String contentType) {
		return contentType.equals("message/rfc822") || contentType.equals("message/x-emlx");
	}

	@Override
	public String getName() {
		return "Email";
	}

	@Override
	public void loadFile(StreamSource content, Set<String> highlightTerms) {

		if (content == null) {
			super.loadFile(null, null);
			return;
		}

		if (mch != null)
			mch.deleteFiles();

		MimeConfig config = new MimeConfig();
		config.setMaxLineLen(100000);
		config.setMaxHeaderLen(100000); // max length of any individual header
		config.setStrictParsing(false);

		parser = new MimeStreamParser(config);

		mch = new MailContentHandler(1, new Metadata(), new ParseContext(), config.isStrictParsing());
		parser.setContentHandler(mch);
		parser.setContentDecoding(true);

		TikaInputStream tagged = null;
		try {
			tagged = TikaInputStream.get(content.getStream());
			parser.parse(tagged);

		} catch (Exception e) {
			e.printStackTrace();
		}finally{
			IOUtil.closeQuietly(tagged);
		}

		super.loadFile(new FileContentSource(mch.previewFile), highlightTerms);

	}

	public class JavaApplication {
		public void open(int attNum) {
			Object[] att = mch.attachs.get(attNum);
			File file = (File) att[0];

			try {
				Desktop.getDesktop().open(file.getCanonicalFile());
			} catch (Exception e) {
				try {
					// Windows Only
					Runtime.getRuntime().exec(new String[] { "rundll32", "SHELL32.DLL,ShellExec_RunDLL", "\"" + file.getCanonicalFile() + "\"" });
				} catch (Exception e2) {
					try {
						// Linux Only
						Runtime.getRuntime().exec(new String[] { "xdg-open", "\"" + file.toURI().toURL() + "\"" });
					} catch (Exception e3) {
						e3.printStackTrace();
					}
				}
			}

		}
	}

	@Override
	protected void addHighlighter() {
		PlatformImpl.runLater(new Runnable() {
			@Override
			public void run() {
				webEngine.getLoadWorker().stateProperty().addListener(new ChangeListener<Worker.State>() {
					@Override
					public void changed(ObservableValue<? extends State> ov, State t, State t1) {
						webEngine.setJavaScriptEnabled(true);
						if (t1 == Worker.State.SUCCEEDED || t1 == Worker.State.FAILED) {

							JSObject window = (JSObject) webEngine.executeScript("window");
							window.setMember("app", new JavaApplication());

							doc = webEngine.getDocument();

							// TODO destacar documeto nulo ou alterar
							// SecurityManager
							if (file != null)
								if (doc != null) {
									// System.out.println("Highlighting");
									currentHit = -1;
									totalHits = 0;
									hits = new ArrayList<Object>();
									if (highlightTerms.size() > 0)
										highlightNode(doc, false);

								} else {
									LOGGER.info("Null DOM to highlight!");
									queryTerms = highlightTerms.toArray(new String[0]);
									currTerm = queryTerms.length > 0 ? 0 : -1;

									scrollToNextHit(true);

								}
						}
					}

				});

			}
		});
	}

	class MailContentHandler implements ContentHandler {

		private boolean strictParsing = false;

		// private XHTMLContentHandler handler;
		private Metadata metadata, submd;
		private boolean inPart = false;

		File previewFile, bodyFile;
		ArrayList<File> filesList = new ArrayList<File>();
		// boolean isAttach;
		String bodyCharset = "windows-1252";

		private String attachName, contentID;
		private boolean textBody = false, htmlBody = false;
		private ArrayList<Object[]> attachs = new ArrayList<Object[]>();

		private DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

		MailContentHandler(int num, Metadata metadata, ParseContext context, boolean strictParsing) {
			this.metadata = metadata;
			this.strictParsing = strictParsing;
		}

		public void deleteFiles() {
			for (File file : filesList)
				file.delete();
			if (previewFile != null)
				previewFile.delete();
		}

		private String decodeIfUtf8(String value) {
			boolean isUtf8 = false;
			int idx = value.indexOf('Ã');
			if (idx > -1 && idx < value.length() - 1) {
				int c_ = value.codePointAt(idx + 1);
				if (c_ >= 0x0080 && c_ <= 0x00BC)
					isUtf8 = true;
			}
			if (isUtf8) {
				try {
					byte[] buf16 = value.getBytes("UTF-16LE");
					byte[] buf8 = new byte[buf16.length / 2];
					for (int i = 0; i < buf8.length; i++)
						buf8[i] = buf16[i * 2];
					value = new String(buf8, "UTF-8");
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				}

			}

			return value;
		}

		private void createHeader(OutputStream outStream) throws IOException {
			OutputStreamWriter writer = new OutputStreamWriter(outStream, bodyCharset);

			writer.write("<html>");
			writer.write("<head>");
			writer.write("<meta http-equiv=\"content-type\" content=\"text/html; charset=" + bodyCharset + "\" />");
			writer.write("</head>");
			writer.write("<body style=\"background-color:white;text-align:left;font-family:arial;color:black;font-size:14px;margin:5px;\">");

			String[][] names = { { TikaCoreProperties.TRANSITION_SUBJECT_TO_DC_TITLE.getName(), "Assunto" }, { Message.MESSAGE_FROM, "De" }, { Message.MESSAGE_TO, "Para" },
					{ Message.MESSAGE_CC, "Cc" }, { Message.MESSAGE_BCC, "Bcc" }, { TikaCoreProperties.CREATED.getName(), "Envio" } };
			// {MESSAGE_ATTACH, "Anexos"}};

			String text;
			for (String[] name : names) {

				if (metadata.getValues(name[0]).length > 0) {
					text = "<b style=\"font-weight:bold\">" + name[1] + ": </b>";

					if (name[0].equals(names[0][0]))// ||
													// name[0].equals(names[6][0]))
						text += "<b style=\"font-weight:bold\">";

					if (!name[0].equals(TikaCoreProperties.CREATED.getName())) {
						String[] values = metadata.getValues(name[0]);
						for (int i = 0; i < values.length; i++) {
							text += LuceneSimpleHTMLEncoder.htmlEncode(decodeIfUtf8(values[i]));
							if (i < values.length - 1)
								text += ", ";
						}
					} else
						text += dateFormat.format((metadata.getDate(TikaCoreProperties.CREATED)));

					if (name[0].equals(names[0][0]))// ||
													// name[0].equals(names[6][0]))
						text += "</b>";

					text += "<br>";
					writer.write(text);
				}
			}

			boolean firstAtt = true;
			int i = 0, count = 0;
			text = "";
			for (final Object[] obj : attachs) {
				String attName = (String) obj[2];
				if (attName != null) {
					if (!firstAtt)
						text += ", ";
					text += "<a href=\"\" onclick=\"app.open(" + i + ")\">" + attName + "</a>";
					// writer.write("<a href=\"" + attName + "\" >"+ attName
					// +"</a><br><hr>");
					// writer.write("<a href=\"\" onClick=\"alert('aaaaaa');\">"+values[i++]
					// + "</a>");
					firstAtt = false;
					count++;
				}
				i++;
			}
			if (count > 0) {
				text = "<b style=\"font-weight:bold\">Anexos: (" + count + ")</b> " + text;
				writer.write(text);
			}

			writer.write("</body>");
			writer.write("<hr>");
			writer.write("</html>");
			writer.flush();
		}

		@Override
		public void body(BodyDescriptor body, InputStream is) throws MimeException, IOException {

			// retorna parser para modo recursivo
			parser.setRecurse();

			String charset = body.getCharset();
			String type = body.getMimeType();

			try {
				Charset.forName(charset);
			} catch (Exception e) {
				charset = "windows-1252";
			}
			if (charset.equalsIgnoreCase("us-ascii"))
				charset = "windows-1252";

			File attach;
			OutputStream outStream;
			String fileExt = "";
			if (attachName != null && attachName.lastIndexOf(".") > -1)
				fileExt = attachName.substring(attachName.lastIndexOf("."));

			/*
			 * try{ attach = new File(System.getProperty("java.io.tmpdir"),
			 * contentID + fileExt); }catch(Exception e ){ attach =
			 * File.createTempFile("attach", fileExt); }
			 */

			attach = File.createTempFile("attach", fileExt);
			outStream = new BufferedOutputStream(new FileOutputStream(attach));
			attach.deleteOnExit();

			if (type.equalsIgnoreCase("text/plain") || type.equalsIgnoreCase("text/html")) {
				byte[] buf = new byte[10000];
				int len;
				while ((len = is.read(buf)) >= 0) {
					String text = new String(buf, 0, len, charset);
					if (type.equalsIgnoreCase("text/plain")) {
						text = LuceneSimpleHTMLEncoder.htmlEncode(text);
						text = text.replaceAll("\n", "<br>");
					} else
						text = text.replaceAll("cid:", "");

					outStream.write(text.getBytes(charset));
				}
			} else
				IOUtil.copiaArquivo(is, outStream);

			outStream.close();

			Object[] obj = { attach, body.getMimeType(), (attachName == null ? "[Sem Nome]" : attachName) };

			if (type.equalsIgnoreCase("text/plain")) {
				if (textBody || htmlBody || attachName != null)
					attachs.add(obj);
				else {
					bodyCharset = charset;
					bodyFile = attach;
					textBody = true;
				}

			} else if (type.equalsIgnoreCase("text/html")) {
				if (htmlBody || attachName != null)
					attachs.add(obj);
				else {
					bodyCharset = charset;
					bodyFile = attach;
					htmlBody = true;
				}

			} else
				attachs.add(obj);

			filesList.add(attach);

		}

		@Override
		public void endMessage() throws MimeException {

			try {
				previewFile = File.createTempFile("message", ".html");
				previewFile.deleteOnExit();
				OutputStream outStream = new BufferedOutputStream(new FileOutputStream(previewFile));
				createHeader(outStream);
				if (bodyFile != null) {
					InputStream bodyStream = new FileInputStream(bodyFile);
					IOUtil.copiaArquivo(bodyStream, outStream);
					bodyStream.close();
					bodyFile.delete();
				}

				// String[] values = metadata.getValues(MESSAGE_ATTACH);
				// if(values.length > 0){
				OutputStreamWriter writer = new OutputStreamWriter(outStream, bodyCharset);
				// writer.write("<html><body><hr>");

				for (final Object[] obj : attachs) {
					File attFile = (File) obj[0];
					String type = (String) obj[1];
					String attName = (String) obj[2];

					if (type.startsWith("image")) {
						writer.write("<hr>");
						if (attName != null)
							writer.write(attName + ":<br>");

						writer.write("<img src=\"" + attFile.getName() + "\" style=\"max-width:100%;\">");

					} else if (type.equalsIgnoreCase("text/plain") || type.equalsIgnoreCase("text/html")) {
						writer.write("<hr>");
						if (attName != null)
							writer.write(attName + ":<br>");

						writer.flush();
						InputStream stream = new FileInputStream(attFile);
						IOUtil.copiaArquivo(stream, outStream);
						stream.close();
					}
				}
				// writer.write("</body></html>");
				writer.flush();
				// }

				outStream.close();

			} catch (IOException e) {
				e.printStackTrace();
			}

		}

		/**
		 * Header for the whole message or its parts
		 * 
		 * @see http 
		 *      ://james.apache.org/mime4j/apidocs/org/apache/james/mime4j/parser
		 *      / Field.html
		 **/
		@Override
		public void field(Field field) throws MimeException {

			Metadata metadata;
			// inPart indicates whether these metadata correspond to the
			// whole message or its parts
			if (!inPart)
				metadata = this.metadata;
			else
				metadata = submd;

			try {
				String fieldname = field.getName();
				ParsedField parsedField = LenientFieldParser.getParser().parse(field, DecodeMonitor.SILENT);

				if (fieldname.equalsIgnoreCase("From")) {
					MailboxListField fromField = (MailboxListField) parsedField;
					MailboxList mailboxList = fromField.getMailboxList();
					if (fromField.isValidField() && mailboxList != null) {
						for (int i = 0; i < mailboxList.size(); i++) {
							String from = decodeIfUtf8(getDisplayString(mailboxList.get(i)));
							metadata.add(Message.MESSAGE_FROM, from);
							metadata.add(TikaCoreProperties.CREATOR, from);
						}
					} else {
						String from = stripOutFieldPrefix(field, "From:");
						if (from.startsWith("<")) {
							from = from.substring(1);
						}
						if (from.endsWith(">")) {
							from = from.substring(0, from.length() - 1);
						}
						from = decodeIfUtf8(from);
						metadata.add(Message.MESSAGE_FROM, from);
						metadata.add(TikaCoreProperties.CREATOR, from);
					}
				} else if (fieldname.equalsIgnoreCase("Subject")) {
					String subject = decodeIfUtf8(((UnstructuredField) parsedField).getValue());
					metadata.add(TikaCoreProperties.TRANSITION_SUBJECT_TO_DC_TITLE, subject);

				} else if (fieldname.equalsIgnoreCase("To")) {
					processAddressList(parsedField, "To:", Message.MESSAGE_TO);

				} else if (fieldname.equalsIgnoreCase("CC")) {
					processAddressList(parsedField, "Cc:", Message.MESSAGE_CC);

				} else if (fieldname.equalsIgnoreCase("BCC")) {
					processAddressList(parsedField, "Bcc:", Message.MESSAGE_BCC);

				} else if (fieldname.equalsIgnoreCase("Date")) {
					DateTimeField dateField = (DateTimeField) parsedField;
					metadata.set(TikaCoreProperties.CREATED, dateField.getDate());

				}

				if (fieldname.equalsIgnoreCase("Content-Type")) {
					ContentTypeField ctField = (ContentTypeField) parsedField;
					if (attachName == null)
						attachName = ctField.getParameter("name");

					if (attachName == null)
						attachName = getRFC2231Value("name", ctField.getParameters());

					if (ctField.isMimeType("message/rfc822")) {
						// configura parser para não interpretar emails anexos
						parser.setFlat();
						if (attachName == null)
							attachName = "Mensagem Anexa.eml";

					}

				} else if (fieldname.equalsIgnoreCase("Content-Disposition")) {
					ContentDispositionField ctField = (ContentDispositionField) parsedField;
					if (ctField.isAttachment() || ctField.isInline()) {
						String name = ctField.getFilename();
						if (name == null)
							name = getRFC2231Value("filename", ctField.getParameters());
						if (name == null)
							name = "[Sem Nome]";
						if (this.attachName == null)
							attachName = name;

					}
				}

				if (fieldname.equalsIgnoreCase("Content-ID")) {
					ContentIdField cidField = (ContentIdField) parsedField;
					contentID = cidField.getId();
					contentID = contentID.substring(1, contentID.length() - 1);
				}

			} catch (RuntimeException me) {
				me.printStackTrace();
				if (strictParsing) {
					throw me;
				}
			}
		}

		private String getRFC2231Value(String paramName, Map<String, String> params) {
			TreeMap<Integer, String> paramFrags = new TreeMap<Integer, String>();
			String charset = "windows-1252";
			String[] keys = params.keySet().toArray(new String[0]);
			Arrays.sort(keys);
			for (String key : keys)
				if (key.startsWith(paramName + "*")) {
					String value = params.get(key);
					if (key.indexOf("*") == key.length() - 1) {
						charset = value.substring(0, value.indexOf("'"));
						value = value.substring(value.lastIndexOf("'") + 1);
						paramFrags.put(0, decodeRFC2231Bytes(value, charset));
						break;
					} else {
						int frag = Integer.valueOf(key.split("\\*")[1]);
						if (frag == 0 && key.endsWith("*")) {
							charset = value.substring(0, value.indexOf("'"));
							value = value.substring(value.lastIndexOf("'") + 1);
						}
						if (key.endsWith("*"))
							paramFrags.put(frag, decodeRFC2231Bytes(value, charset));
						else
							paramFrags.put(frag, value);
					}

				}
			if (paramFrags.size() == 0)
				return null;

			String value = "";
			for (String frag : paramFrags.values())
				value += frag;

			return value;

		}

		private String decodeRFC2231Bytes(String value, final String charset) {
			byte[] b = new byte[value.length()];
			int i, bi;
			for (i = 0, bi = 0; i < value.length(); i++) {
				char c = value.charAt(i);
				if (c == '%') {
					String hex = value.substring(i + 1, i + 3);
					c = (char) Integer.parseInt(hex, 16);
					i += 2;
				}
				b[bi++] = (byte) c;
			}
			return new String(b, 0, bi, CharsetUtil.lookup(charset));
		}

		private void processAddressList(ParsedField field, String addressListType, String metadataField) throws MimeException {
			AddressListField toField = (AddressListField) field;
			if (toField.isValidField()) {
				AddressList addressList = toField.getAddressList();
				for (int i = 0; i < addressList.size(); ++i) {
					metadata.add(metadataField, decodeIfUtf8(getDisplayString(addressList.get(i))));
				}
			} else {
				String to = stripOutFieldPrefix(field, addressListType);
				for (String eachTo : to.split(",")) {
					metadata.add(metadataField, decodeIfUtf8(eachTo.trim()));
				}
			}
		}

		private String getDisplayString(Address address) {
			if (address instanceof Mailbox) {
				Mailbox mailbox = (Mailbox) address;
				String name = mailbox.getName();
				if (name != null && name.length() > 0) {
					name = DecoderUtil.decodeEncodedWords(name, DecodeMonitor.SILENT);
					return name + " <" + mailbox.getAddress() + ">";
				} else {
					// return mailbox.getAddress();
					return DecoderUtil.decodeEncodedWords(mailbox.getAddress(), DecodeMonitor.SILENT);
				}
			} else {
				return address.toString();
			}
		}

		@Override
		public void preamble(InputStream is) throws MimeException, IOException {

		}

		@Override
		public void raw(InputStream is) throws MimeException, IOException {

		}

		@Override
		public void startBodyPart() throws MimeException {

		}

		@Override
		public void startHeader() throws MimeException {
			submd = new Metadata();
			attachName = null;
			contentID = null;
		}

		@Override
		public void startMultipart(BodyDescriptor descr) throws MimeException {

			inPart = true;
		}

		@Override
		public void endMultipart() throws MimeException {

			inPart = false;
		}

		@Override
		public void epilogue(InputStream is) throws MimeException, IOException {

		}

		@Override
		public void endBodyPart() throws MimeException {
		}

		@Override
		public void endHeader() throws MimeException {
			if (attachName != null) {
				attachName = decodeIfUtf8(DecoderUtil.decodeEncodedWords(attachName, DecodeMonitor.SILENT));
				submd.set(TikaMetadataKeys.RESOURCE_NAME_KEY, attachName);
			}

		}

		@Override
		public void startMessage() throws MimeException {

		}

		private String stripOutFieldPrefix(Field field, String fieldname) {
			String temp = field.getRaw().toString();
			int loc = fieldname.length();
			while (temp.charAt(loc) == ' ') {
				loc++;
			}
			return temp.substring(loc);
		}

	}

}
