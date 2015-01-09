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
package dpf.sp.gpinf.indexer.parsers;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashSet;
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
import org.apache.james.mime4j.dom.field.ContentTypeField;
import org.apache.james.mime4j.dom.field.DateTimeField;
import org.apache.james.mime4j.dom.field.MailboxListField;
import org.apache.james.mime4j.dom.field.ParsedField;
import org.apache.james.mime4j.dom.field.UnstructuredField;
import org.apache.james.mime4j.field.LenientFieldParser;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.mime4j.util.CharsetUtil;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TaggedInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Uses apache-mime4j to parse emails. Each part is treated with the
 * corresponding parser and displayed within elements.
 * <p>
 * A {@link MimeEntityConfig} object can be passed in the parsing context to
 * better control the parsing process.
 * 
 * @author jnioche@digitalpebble.com
 */
public class RFC822Parser extends AbstractParser {

	/** Serial version UID */
	private static final long serialVersionUID = -5504243905998074168L;

	private static final Set<MediaType> SUPPORTED_TYPES = getTypes();

	private static Set<MediaType> getTypes() {
		HashSet<MediaType> supportedTypes = new HashSet<MediaType>();
		supportedTypes.add(MediaType.parse("message/rfc822"));
		supportedTypes.add(MediaType.parse("message/x-emlx"));
		return supportedTypes;
	}

	@Override
	public Set<MediaType> getSupportedTypes(ParseContext context) {
		return SUPPORTED_TYPES;
	}

	@Override
	public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {
		// Get the mime4j configuration, or use a default one
		MimeConfig config = new MimeConfig();
		config.setMaxLineLen(100000);
		config.setMaxHeaderLen(100000); // max length of any individual header
		config = context.get(MimeConfig.class, config);

		MimeStreamParser parser = new MimeStreamParser(config);
		XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);

		MailContentHandler mch = new MailContentHandler(parser, xhtml, metadata, context, config.isStrictParsing());

		parser.setContentHandler(mch);
		parser.setContentDecoding(true);

		TaggedInputStream tagged = TaggedInputStream.get(stream);
		try {
			parser.parse(tagged);

		} catch (IOException e) {
			tagged.throwIfCauseOf(e);
			throw new TikaException("Failed to parse an email message", e);

		} catch (MimeException e) {
			// Unwrap the exception in case it was not thrown by mime4j
			Throwable cause = e.getCause();
			if (cause instanceof TikaException) {
				throw (TikaException) cause;
			} else if (cause instanceof SAXException) {
				throw (SAXException) cause;
			} else {
				throw new TikaException("Failed to parse an email message", e);
			}
		}
	}

	/**
	 * Bridge between mime4j's content handler and the generic Sax content
	 * handler used by Tika. See
	 * http://james.apache.org/mime4j/apidocs/org/apache
	 * /james/mime4j/parser/ContentHandler.html
	 */
	class MailContentHandler implements org.apache.james.mime4j.parser.ContentHandler {

		private boolean strictParsing = false;

		private XHTMLContentHandler handler;
		private ParseContext context;
		private Metadata metadata, submd;
		private String attachName;
		private boolean inPart = false, textBody, htmlBody;
		private ParsingEmbeddedDocumentExtractor embeddedParser;
		private MimeStreamParser parser;

		MailContentHandler(MimeStreamParser parser, XHTMLContentHandler xhtml, Metadata metadata, ParseContext context, boolean strictParsing) {
			this.parser = parser;
			this.handler = xhtml;
			this.context = context;
			this.metadata = metadata;
			this.strictParsing = strictParsing;
			this.embeddedParser = new ParsingEmbeddedDocumentExtractor(context);
		}

		@Override
		public void body(BodyDescriptor body, InputStream is) throws MimeException, IOException {
			// retorna parser para modo recursivo
			parser.setRecurse();

			EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class, embeddedParser);

			// use a different metadata object
			// in order to specify the mime type of the
			// sub part without damaging the main metadata

			submd.set(HttpHeaders.CONTENT_TYPE, body.getMimeType());
			submd.set(HttpHeaders.CONTENT_ENCODING, body.getCharset());

			boolean isAttach = false;
			String type = body.getMimeType();
			if (type.equalsIgnoreCase("text/plain")) {
				if (textBody || htmlBody || attachName != null)
					isAttach = true;
				else
					textBody = true;

			} else if (type.equalsIgnoreCase("text/html")) {
				if (htmlBody || attachName != null)
					isAttach = true;
				else
					htmlBody = true;

			} else
				isAttach = true;

			try {
				if (isAttach) {
					if (extractor.shouldParseEmbedded(submd))
						extractor.parseEmbedded(is, handler, submd, true);
				} else
					embeddedParser.parseEmbedded(is, handler, submd, false);

			} catch (SAXException e) {
				throw new MimeException(e);
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
					metadata.add(TikaCoreProperties.TITLE, subject);
					/*
					 * String name =
					 * metadata.get(TikaMetadataKeys.RESOURCE_NAME_KEY); if(name
					 * == null || name.isEmpty())
					 * metadata.add(TikaMetadataKeys.RESOURCE_NAME_KEY,
					 * subject);
					 */
				} else if (fieldname.equalsIgnoreCase("To")) {
					processAddressList(metadata, parsedField, "To:", Message.MESSAGE_TO);
				} else if (fieldname.equalsIgnoreCase("CC")) {
					processAddressList(metadata, parsedField, "Cc:", Message.MESSAGE_CC);
				} else if (fieldname.equalsIgnoreCase("BCC")) {
					processAddressList(metadata, parsedField, "Bcc:", Message.MESSAGE_BCC);
				} else if (fieldname.equalsIgnoreCase("Date")) {
					DateTimeField dateField = (DateTimeField) parsedField;
					metadata.set(TikaCoreProperties.CREATED, dateField.getDate());
				}

				if (fieldname.equalsIgnoreCase("Content-Type")) {
					ContentTypeField ctField = (ContentTypeField) parsedField;
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
						String attachName = ctField.getFilename();
						if (attachName == null)
							attachName = getRFC2231Value("filename", ctField.getParameters());
						if (attachName == null)
							attachName = "[Sem Nome]";
						if (this.attachName == null)
							this.attachName = attachName;

					}
				}

			} catch (RuntimeException me) {
				if (strictParsing) {
					throw me;
				}
			}
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

		private void processAddressList(Metadata metadata, ParsedField field, String addressListType, String metadataField) throws MimeException {
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
			try {
				handler.startDocument();
			} catch (SAXException e) {
				throw new MimeException(e);
			}
		}

		@Override
		public void endMessage() throws MimeException {
			try {
				handler.endDocument();
			} catch (SAXException e) {
				throw new MimeException(e);
			}
		}

		@Override
		public void endMultipart() throws MimeException {
			inPart = false;
		}

		@Override
		public void epilogue(InputStream is) throws MimeException, IOException {
		}

		@Override
		public void startHeader() throws MimeException {
			submd = new Metadata();
			attachName = null;
		}

		@Override
		public void startMultipart(BodyDescriptor descr) throws MimeException {
			inPart = true;
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
