package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
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

import dpf.sp.gpinf.indexer.ui.fileViewer.Messages;
import dpf.sp.gpinf.indexer.util.FileContentSource;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.LuceneSimpleHTMLEncoder;
import iped3.io.IStreamSource;

public class EmailViewer extends HtmlViewer {

    private static Logger LOGGER = LoggerFactory.getLogger(EmailViewer.class);
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    MailContentHandler mch;
    MimeStreamParser parser;

    public EmailViewer() {
        this.fileHandler = new AttachmentOpen();
        this.enableJavascript = true;
    }

    @Override
    public boolean isSupportedType(String contentType) {
        return contentType.equals("message/rfc822") || contentType.equals("message/x-emlx"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Override
    public String getName() {
        return "Email"; //$NON-NLS-1$
    }

    @Override
    public void loadFile(IStreamSource content, Set<String> highlightTerms) {

        if (content == null) {
            super.loadFile(null, null);
            return;
        }

        if (mch != null) {
            mch.deleteFiles();
        }

        MimeConfig config = new MimeConfig.Builder().setMaxLineLen(100000).setMaxHeaderLen(100000)
                .setStrictParsing(false).build();

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
        } finally {
            IOUtil.closeQuietly(tagged);
        }

        super.loadFile(new FileContentSource(mch.previewFile), highlightTerms);

    }

    public class AttachmentOpen extends FileHandler {

        public void open(int attNum) {
            Object[] att = mch.attachs.get(attNum);
            File file = (File) att[0];
            String attachName = (String) att[2];
            if (IOUtil.isToOpenExternally(attachName, IOUtil.getExtension(file))) {
                this.openFile(file);
            }
        }
    }

    class MailContentHandler implements ContentHandler {

        private boolean strictParsing = false;

        // private XHTMLContentHandler handler;
        private Metadata metadata, submd;
        private boolean inPart = false;

        File previewFile, bodyFile;
        ArrayList<File> filesList = new ArrayList<File>();
        // boolean isAttach;
        String bodyCharset = "windows-1252"; //$NON-NLS-1$

        private String attachName, contentID;
        private boolean textBody = false, htmlBody = false;
        private ArrayList<Object[]> attachs = new ArrayList<Object[]>();

        private DateFormat dateFormat = new SimpleDateFormat(Messages.getString("EmailViewer.DateFormat")); //$NON-NLS-1$

        MailContentHandler(int num, Metadata metadata, ParseContext context, boolean strictParsing) {
            this.metadata = metadata;
            this.strictParsing = strictParsing;
        }

        public void deleteFiles() {
            for (File file : filesList) {
                file.delete();
            }
            if (previewFile != null) {
                previewFile.delete();
            }
        }

        private String decodeIfUtf8(String value) {
            boolean isUtf8 = false;
            int idx = value.indexOf('Ã');
            if (idx > -1 && idx < value.length() - 1) {
                int c_ = value.codePointAt(idx + 1);
                if (c_ >= 0x0080 && c_ <= 0x00BC) {
                    isUtf8 = true;
                }
            }
            if (isUtf8) {
                try {
                    byte[] buf16 = value.getBytes("UTF-16LE"); //$NON-NLS-1$
                    byte[] buf8 = new byte[buf16.length / 2];
                    for (int i = 0; i < buf8.length; i++) {
                        buf8[i] = buf16[i * 2];
                    }
                    value = new String(buf8, "UTF-8"); //$NON-NLS-1$
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }

            }

            return value;
        }

        private void createHeader(OutputStream outStream) throws IOException {
            OutputStreamWriter writer = new OutputStreamWriter(outStream, bodyCharset);

            writer.write("<html>"); //$NON-NLS-1$
            writer.write("<head>"); //$NON-NLS-1$
            writer.write("<meta http-equiv=\"content-type\" content=\"text/html; charset=" + bodyCharset + "\" />"); //$NON-NLS-1$ //$NON-NLS-2$
            writer.write("</head>"); //$NON-NLS-1$
            writer.write(
                    "<body style=\"background-color:white;text-align:left;font-family:arial;color:black;font-size:14px;margin:5px;\">"); //$NON-NLS-1$

            String[][] names = {
                    { TikaCoreProperties.TRANSITION_SUBJECT_TO_DC_TITLE.getName(),
                            Messages.getString("EmailViewer.Subject") }, //$NON-NLS-1$
                    { Message.MESSAGE_FROM, Messages.getString("EmailViewer.From") }, //$NON-NLS-1$
                    { Message.MESSAGE_TO, Messages.getString("EmailViewer.To") }, //$NON-NLS-1$
                    { Message.MESSAGE_CC, Messages.getString("EmailViewer.Cc") }, //$NON-NLS-1$
                    { Message.MESSAGE_BCC, Messages.getString("EmailViewer.Bcc") }, //$NON-NLS-1$
                    { TikaCoreProperties.CREATED.getName(), Messages.getString("EmailViewer.Date") } }; //$NON-NLS-1$
            // {MESSAGE_ATTACH, "Anexos"}};

            String text;
            for (String[] name : names) {

                if (metadata.getValues(name[0]).length > 0) {
                    text = "<b style=\"font-weight:bold\">" + name[1] + ": </b>"; //$NON-NLS-1$ //$NON-NLS-2$

                    if (name[0].equals(names[0][0]))// ||
                    // name[0].equals(names[6][0]))
                    {
                        text += "<b style=\"font-weight:bold\">"; //$NON-NLS-1$
                    }

                    if (!name[0].equals(TikaCoreProperties.CREATED.getName())) {
                        String[] values = metadata.getValues(name[0]);
                        for (int i = 0; i < values.length; i++) {
                            text += LuceneSimpleHTMLEncoder.htmlEncode(decodeIfUtf8(values[i]));
                            if (i < values.length - 1) {
                                text += ", "; //$NON-NLS-1$
                            }
                        }
                    } else {
                        text += dateFormat.format((metadata.getDate(TikaCoreProperties.CREATED)));
                    }

                    if (name[0].equals(names[0][0]))// ||
                    // name[0].equals(names[6][0]))
                    {
                        text += "</b>"; //$NON-NLS-1$
                    }

                    text += "<br>"; //$NON-NLS-1$
                    writer.write(text);
                }
            }

            boolean firstAtt = true;
            int i = 0, count = 0;
            text = ""; //$NON-NLS-1$
            for (final Object[] obj : attachs) {
                String attName = (String) obj[2];
                if (attName != null) {
                    if (!firstAtt) {
                        text += ", "; //$NON-NLS-1$
                    }
                    text += "<a href=\"\" onclick=\"app.open(" + i + ")\">" + attName + "</a>"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
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
                text = "<b style=\"font-weight:bold\">" + Messages.getString("EmailViewer.Attachments") + ": (" + count //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        + ")</b> " + text; //$NON-NLS-1$
                writer.write(text);
            }

            writer.write("</body>"); //$NON-NLS-1$
            writer.write("<hr>"); //$NON-NLS-1$
            writer.write("</html>"); //$NON-NLS-1$
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
                charset = "windows-1252"; //$NON-NLS-1$
            }
            if (charset.equalsIgnoreCase("us-ascii")) { //$NON-NLS-1$
                charset = "windows-1252"; //$NON-NLS-1$
            }

            File attach;
            OutputStream outStream;
            String fileExt = ""; //$NON-NLS-1$
            if (attachName != null && attachName.lastIndexOf(".") > -1) { //$NON-NLS-1$
                fileExt = attachName.substring(attachName.lastIndexOf(".")); //$NON-NLS-1$
            }

            /*
             * try{ attach = new File(System.getProperty("java.io.tmpdir"), contentID +
             * fileExt); }catch(Exception e ){ attach = File.createTempFile("attach",
             * fileExt); }
             */
            attach = File.createTempFile("attach", fileExt); //$NON-NLS-1$
            outStream = new BufferedOutputStream(new FileOutputStream(attach));
            attach.deleteOnExit();

            if (type.equalsIgnoreCase("text/plain") || type.equalsIgnoreCase("text/html")) { //$NON-NLS-1$ //$NON-NLS-2$
                byte[] buf = new byte[10000];
                int len;
                while ((len = is.read(buf)) >= 0) {
                    String text = new String(buf, 0, len, charset);
                    if (type.equalsIgnoreCase("text/plain")) { //$NON-NLS-1$
                        text = LuceneSimpleHTMLEncoder.htmlEncode(text);
                        text = text.replaceAll("\n", "<br>"); //$NON-NLS-1$ //$NON-NLS-2$
                    } else {
                        text = text.replaceAll("cid:", ""); //$NON-NLS-1$ //$NON-NLS-2$
                    }

                    outStream.write(text.getBytes(charset));
                }
            } else {
                IOUtil.copiaArquivo(is, outStream);
            }

            outStream.close();

            Object[] obj = { attach, body.getMimeType(),
                    (attachName == null ? Messages.getString("EmailViewer.UnNamed") : attachName) }; //$NON-NLS-1$

            if (type.equalsIgnoreCase("text/plain")) { //$NON-NLS-1$
                if (textBody || htmlBody || attachName != null) {
                    attachs.add(obj);
                } else {
                    bodyCharset = charset;
                    bodyFile = attach;
                    textBody = true;
                }

            } else if (type.equalsIgnoreCase("text/html")) { //$NON-NLS-1$
                if (htmlBody || attachName != null) {
                    attachs.add(obj);
                } else {
                    bodyCharset = charset;
                    bodyFile = attach;
                    htmlBody = true;
                }

            } else {
                attachs.add(obj);
            }

            filesList.add(attach);

        }

        @Override
        public void endMessage() throws MimeException {

            try {
                previewFile = File.createTempFile("message", ".html"); //$NON-NLS-1$ //$NON-NLS-2$
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

                    if (type.startsWith("image")) { //$NON-NLS-1$
                        writer.write("<hr>"); //$NON-NLS-1$
                        if (attName != null) {
                            writer.write(attName + ":<br>"); //$NON-NLS-1$
                        }

                        writer.write("<img src=\"" + attFile.getName() + "\" style=\"max-width:100%;\">"); //$NON-NLS-1$ //$NON-NLS-2$

                    } else if (type.equalsIgnoreCase("text/plain") || type.equalsIgnoreCase("text/html")) { //$NON-NLS-1$ //$NON-NLS-2$
                        writer.write("<hr>"); //$NON-NLS-1$
                        if (attName != null) {
                            writer.write(attName + ":<br>"); //$NON-NLS-1$
                        }

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
         * @see http ://james.apache.org/mime4j/apidocs/org/apache/james/mime4j/parser /
         *      Field.html
         *
         */
        @Override
        public void field(Field field) throws MimeException {

            Metadata metadata;
            // inPart indicates whether these metadata correspond to the
            // whole message or its parts
            if (!inPart) {
                metadata = this.metadata;
            } else {
                metadata = submd;
            }

            try {
                String fieldname = field.getName();
                ParsedField parsedField = LenientFieldParser.getParser().parse(field, DecodeMonitor.SILENT);

                if (fieldname.equalsIgnoreCase("From")) { //$NON-NLS-1$
                    MailboxListField fromField = (MailboxListField) parsedField;
                    MailboxList mailboxList = fromField.getMailboxList();
                    if (fromField.isValidField() && mailboxList != null) {
                        for (int i = 0; i < mailboxList.size(); i++) {
                            String from = decodeIfUtf8(getDisplayString(mailboxList.get(i)));
                            metadata.add(Message.MESSAGE_FROM, from);
                            metadata.add(TikaCoreProperties.CREATOR, from);
                        }
                    } else {
                        String from = stripOutFieldPrefix(field, "From:"); //$NON-NLS-1$
                        if (from.startsWith("<")) { //$NON-NLS-1$
                            from = from.substring(1);
                        }
                        if (from.endsWith(">")) { //$NON-NLS-1$
                            from = from.substring(0, from.length() - 1);
                        }
                        from = decodeIfUtf8(from);
                        metadata.add(Message.MESSAGE_FROM, from);
                        metadata.add(TikaCoreProperties.CREATOR, from);
                    }
                } else if (fieldname.equalsIgnoreCase("Subject")) { //$NON-NLS-1$
                    String subject = decodeIfUtf8(((UnstructuredField) parsedField).getValue());
                    metadata.add(TikaCoreProperties.TRANSITION_SUBJECT_TO_DC_TITLE, subject);

                } else if (fieldname.equalsIgnoreCase("To")) { //$NON-NLS-1$
                    processAddressList(parsedField, "To:", Message.MESSAGE_TO); //$NON-NLS-1$

                } else if (fieldname.equalsIgnoreCase("CC")) { //$NON-NLS-1$
                    processAddressList(parsedField, "Cc:", Message.MESSAGE_CC); //$NON-NLS-1$

                } else if (fieldname.equalsIgnoreCase("BCC")) { //$NON-NLS-1$
                    processAddressList(parsedField, "Bcc:", Message.MESSAGE_BCC); //$NON-NLS-1$

                } else if (fieldname.equalsIgnoreCase("Date")) { //$NON-NLS-1$
                    DateTimeField dateField = (DateTimeField) parsedField;
                    if (metadata.get(TikaCoreProperties.CREATED) == null)
                        metadata.set(TikaCoreProperties.CREATED, dateField.getDate());

                }

                if (fieldname.equalsIgnoreCase("Content-Type")) { //$NON-NLS-1$
                    ContentTypeField ctField = (ContentTypeField) parsedField;
                    if (attachName == null) {
                        attachName = ctField.getParameter("name"); //$NON-NLS-1$
                    }

                    if (attachName == null) {
                        attachName = getRFC2231Value("name", ctField.getParameters()); //$NON-NLS-1$
                    }

                    if (ctField.isMimeType("message/rfc822")) { //$NON-NLS-1$
                        // configura parser para não interpretar emails anexos
                        parser.setFlat();
                        if (attachName == null) {
                            attachName = Messages.getString("EmailViewer.AttachedEmail"); //$NON-NLS-1$
                        }

                    }

                } else if (fieldname.equalsIgnoreCase("Content-Disposition")) { //$NON-NLS-1$
                    ContentDispositionField ctField = (ContentDispositionField) parsedField;
                    if (ctField.isAttachment() || ctField.isInline()) {
                        String name = ctField.getFilename();
                        if (name == null) {
                            name = getRFC2231Value("filename", ctField.getParameters()); //$NON-NLS-1$
                        }
                        if (name == null) {
                            name = Messages.getString("EmailViewer.UnNamed"); //$NON-NLS-1$
                        }
                        if (this.attachName == null) {
                            attachName = name;
                        }

                    }
                }

                if (fieldname.equalsIgnoreCase("Content-ID")) { //$NON-NLS-1$
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
            String charset = "windows-1252"; //$NON-NLS-1$
            String[] keys = params.keySet().toArray(new String[0]);
            Arrays.sort(keys);
            for (String key : keys) {
                if (key.startsWith(paramName + "*")) { //$NON-NLS-1$
                    String value = params.get(key);
                    if (key.indexOf("*") == key.length() - 1) { //$NON-NLS-1$
                        charset = value.substring(0, value.indexOf("'")); //$NON-NLS-1$
                        value = value.substring(value.lastIndexOf("'") + 1); //$NON-NLS-1$
                        paramFrags.put(0, decodeRFC2231Bytes(value, charset));
                        break;
                    } else {
                        int frag = Integer.valueOf(key.split("\\*")[1]); //$NON-NLS-1$
                        if (frag == 0 && key.endsWith("*")) { //$NON-NLS-1$
                            charset = value.substring(0, value.indexOf("'")); //$NON-NLS-1$
                            value = value.substring(value.lastIndexOf("'") + 1); //$NON-NLS-1$
                        }
                        if (key.endsWith("*")) { //$NON-NLS-1$
                            paramFrags.put(frag, decodeRFC2231Bytes(value, charset));
                        } else {
                            paramFrags.put(frag, value);
                        }
                    }

                }
            }
            if (paramFrags.size() == 0) {
                return null;
            }

            String value = ""; //$NON-NLS-1$
            for (String frag : paramFrags.values()) {
                value += frag;
            }

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

        private void processAddressList(ParsedField field, String addressListType, String metadataField)
                throws MimeException {
            AddressListField toField = (AddressListField) field;
            if (toField.isValidField()) {
                AddressList addressList = toField.getAddressList();
                for (int i = 0; i < addressList.size(); ++i) {
                    metadata.add(metadataField, decodeIfUtf8(getDisplayString(addressList.get(i))));
                }
            } else {
                String to = stripOutFieldPrefix(field, addressListType);
                for (String eachTo : to.split(",")) { //$NON-NLS-1$
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
                    return name + " <" + mailbox.getAddress() + ">"; //$NON-NLS-1$ //$NON-NLS-2$
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
