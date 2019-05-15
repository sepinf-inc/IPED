package dpf.sp.gpinf.indexer.parsers;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import org.apache.tika.config.Field;
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

import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import dpf.sp.gpinf.indexer.parsers.util.Messages;
import dpf.sp.gpinf.indexer.parsers.util.Util;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.SimpleHTMLEncoder;
import iped3.util.ExtraProperties;

public class LibpffPSTParser extends AbstractParser {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    /**
     * 
     */
    private static Logger LOGGER = LoggerFactory.getLogger(LibpffPSTParser.class);
    private static Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("vnd.ms-outlook-pst")); //$NON-NLS-1$

    private static String TOOL_NAME = "pffexport"; //$NON-NLS-1$

    public static final String TOOL_PATH_PROP = TOOL_NAME + ".path"; //$NON-NLS-1$
    private String TOOL_PATH = System.getProperty(TOOL_PATH_PROP, ""); //$NON-NLS-1$

    private static String[] OUT_SUFFIX = { ".export", ".orphans", ".recovered" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    private static String[] ROOT_NAME = { "[root]", "[orphans]", "[recovered]" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    private static String[] types = { "Message", "Attachment", "Contact", "Meeting", "Task", "Appointment", "Activity", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
            "Note", "Feed", "Document" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    private static boolean tested = false;

    private ParseContext context;
    private EmbeddedDocumentExtractor extractor;
    private ContentHandler handler;
    private boolean extractOnlyDeleted = false;
    private boolean extractOnlyActive = false;
    private int virtualId = -1;

    private SimpleDateFormat df = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.US); //$NON-NLS-1$
    private boolean deleted = false;

    @Field
    public void setExtractOnlyDeleted(boolean extractOnlyDeleted) {
        this.extractOnlyDeleted = extractOnlyDeleted;
    }

    @Field
    public void setExtractOnlyActive(boolean extractOnlyActive) {
        this.extractOnlyActive = extractOnlyActive;
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {

        synchronized (this.getClass()) {
            if (!tested)
                try {
                    String[] cmd = { TOOL_PATH + TOOL_NAME, "-V" }; //$NON-NLS-1$
                    Process p = Runtime.getRuntime().exec(cmd);
                    p.waitFor();
                    if (p.exitValue() != 0)
                        throw new Exception();

                    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String version = reader.readLine().replace(TOOL_NAME + " ", ""); //$NON-NLS-1$ //$NON-NLS-2$
                    if (version.compareToIgnoreCase("20130722") < 0) //$NON-NLS-1$
                        LOGGER.error(" Old version " + version //$NON-NLS-1$
                                + " of pffexport (libpff) detected: OST 2013 files will NOT be parsed!"); //$NON-NLS-1$

                } catch (Exception e) {
                    LOGGER.error("Error testing pffexport (libpff): Outlook OST 2013 files will NOT be parsed!"); //$NON-NLS-1$
                    SUPPORTED_TYPES = Collections.EMPTY_SET;
                }
            tested = true;
        }

        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        if (SUPPORTED_TYPES.isEmpty())
            return;
        LibpffPSTParser pffParser = new LibpffPSTParser();
        pffParser.setExtractOnlyDeleted(extractOnlyDeleted);
        pffParser.setExtractOnlyActive(extractOnlyActive);
        pffParser.safeparse(stream, handler, metadata, context);
    }

    public void safeparse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        this.context = context;
        this.handler = handler;
        extractor = context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));

        String fileName = metadata.get(Metadata.RESOURCE_NAME_KEY);
        ItemInfo itemInfo = context.get(ItemInfo.class);
        if (itemInfo != null)
            fileName = itemInfo.getPath();

        df.setTimeZone(TimeZone.getTimeZone("UTC")); //$NON-NLS-1$
        df.setLenient(true);

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        TemporaryResources tmp = new TemporaryResources();
        File tmpFile = File.createTempFile("iped", ".tmp"); //$NON-NLS-1$ //$NON-NLS-2$
        Process p = null;
        try {
            TikaInputStream tis = TikaInputStream.get(stream, tmp);
            File file = tis.getFile();

            String extractionMode = "all"; //$NON-NLS-1$
            if (extractOnlyDeleted)
                extractionMode = "recovered"; //$NON-NLS-1$
            else if (extractOnlyActive)
                extractionMode = "items"; //$NON-NLS-1$

            String[] cmd = { TOOL_PATH + TOOL_NAME, "-m", extractionMode, "-f", "html", "-t", tmpFile.getAbsolutePath(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    file.getAbsolutePath() };
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            p = pb.start();

            // p.waitFor();
            Util.waitFor(p, xhtml);

            for (int i = 0; i < OUT_SUFFIX.length; i++) {
                File outDir = new File(tmpFile.getAbsolutePath() + OUT_SUFFIX[i]);
                if (outDir.exists()) {
                    deleted = (i == 0) ? false : true;
                    int folder = processFolder(ROOT_NAME[i], -1);
                    walkFolder(outDir, folder);
                }
            }

        } catch (InterruptedException e) {
            if (extractOnlyDeleted)
                LOGGER.error("Recovery of deleted emails was interrupted on " + fileName + " " + e.toString()); //$NON-NLS-1$ //$NON-NLS-2$
            else
                LOGGER.error("Extraction of emails was interrupted on " + fileName + " " + e.toString()); //$NON-NLS-1$ //$NON-NLS-2$

            throw new TikaException(this.getClass().getSimpleName() + " interrupted", e); //$NON-NLS-1$

        } catch (Exception e) {
            LOGGER.error("Error parsing " + fileName, e); //$NON-NLS-1$

        } finally {
            if (p != null)
                p.destroyForcibly();
            tmpFile.delete();
            tmp.close();
        }

        xhtml.endDocument();

    }

    private void walkFolder(File dir, int parent) throws SAXException, IOException, InterruptedException {

        for (File file : dir.listFiles()) {
            if (Thread.currentThread().isInterrupted())
                throw new InterruptedException("Parsing interrupted"); //$NON-NLS-1$

            try {
                String fileName = file.getName();

                boolean isObject = false;
                for (int i = 0; i < types.length; i++) {
                    if (file.isDirectory() && fileName.startsWith(types[i])
                            && fileName.length() == types[i].length() + 5) {
                        try {
                            Integer.parseInt(fileName.substring(types[i].length()));

                            isObject = true;
                            if (i != 1) {
                                handleObject(file, parent);
                                try {
                                    IOUtil.deletarDiretorio(file);
                                } catch (Exception e) {
                                }

                            } else
                                walkFolder(file, parent);

                        } catch (NumberFormatException ne) {
                        }

                    }
                }

                if (!isObject) {
                    if (file.isDirectory()) {
                        int folder = processFolder(file.getName(), parent);
                        walkFolder(file, folder);
                    } else {
                        handleAttachment(file, parent);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        try {
            IOUtil.deletarDiretorio(dir);
        } catch (Exception e) {
        }

    }

    private int processFolder(String folderName, int parent) throws SAXException, IOException {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.TITLE, folderName);
        metadata.set(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(++virtualId));
        metadata.set(ExtraProperties.PARENT_VIRTUAL_ID, String.valueOf(parent));
        // metadata.set(TikaCoreProperties.CREATED, folder.getCreationTime());
        // metadata.set(TikaCoreProperties.MODIFIED, folder.getLastModificationTime());
        // metadata.set(Metadata.COMMENT, folder.getComment());
        metadata.set(ExtraProperties.EMBEDDED_FOLDER, "true"); //$NON-NLS-1$
        if (deleted)
            metadata.set(ExtraProperties.DELETED, "true"); //$NON-NLS-1$

        extractor.parseEmbedded(new ByteArrayInputStream(new byte[0]), handler, metadata, true);

        return virtualId;
    }

    private void handleObject(File file, int parent) throws IOException, SAXException, InterruptedException {

        File bodyRTF = new File(file, "Message.rtf"); //$NON-NLS-1$
        File attachments = new File(file, "Attachments"); //$NON-NLS-1$

        parent = handleMessage(file, parent);

        if (bodyRTF.exists())
            handleAttachment(bodyRTF, parent);

        if (attachments.exists() && attachments.isDirectory()) {
            walkFolder(attachments, parent);
        }

    }

    private int handleMessage(File file, int parent) throws IOException, SAXException {

        Metadata metadata = new Metadata();
        metadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, OutlookPSTParser.OUTLOOK_MSG_MIME);
        metadata.set(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(++virtualId));
        metadata.set(ExtraProperties.PARENT_VIRTUAL_ID, String.valueOf(parent));
        if (deleted)
            metadata.set(ExtraProperties.DELETED, "true"); //$NON-NLS-1$

        Charset charset = Charset.forName("UTF-8"); //$NON-NLS-1$
        StringBuilder preview = new StringBuilder();

        preview.append("<html>"); //$NON-NLS-1$
        preview.append("<!--PST Email Message Indexer Preview-->"); //$NON-NLS-1$
        preview.append("<head>"); //$NON-NLS-1$
        preview.append("<meta http-equiv=\"content-type\" content=\"text/html; charset=" + charset + "\" />"); //$NON-NLS-1$ //$NON-NLS-2$
        preview.append("</head>"); //$NON-NLS-1$
        preview.append(
                "<body style=\"background-color:white;text-align:left;font-family:arial;color:black;font-size:14px;margin:5px;\">"); //$NON-NLS-1$

        writeHeader(file, metadata, preview);
        writeRecipients(file, metadata, preview);
        writeOtherProps(file, metadata, preview);

        List<String> attachNames = getAttachNames(file);
        if (!attachNames.isEmpty()) {
            preview.append("<b>" + Messages.getString("LibpffPSTParser.Attachments") + " (" + attachNames.size() //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    + "):</b><br>"); //$NON-NLS-1$
            for (String attach : attachNames) {
                preview.append(SimpleHTMLEncoder.htmlEncode(attach) + "<br>"); //$NON-NLS-1$
            }
            metadata.set(ExtraProperties.PST_EMAIL_HAS_ATTACHS, "true"); //$NON-NLS-1$
        }

        preview.append("<hr>"); //$NON-NLS-1$

        writeBody(file, metadata, preview);

        writeInternetHeaders(file, preview);

        preview.append("</body>"); //$NON-NLS-1$
        preview.append("</html>"); //$NON-NLS-1$

        ByteArrayInputStream stream = new ByteArrayInputStream(preview.toString().getBytes(charset));
        preview = null;

        if (extractor.shouldParseEmbedded(metadata))
            extractor.parseEmbedded(stream, handler, metadata, true);

        stream.close();

        return virtualId;
    }

    private void writeBody(File file, Metadata metadata, StringBuilder preview) throws IOException {
        File body = new File(file, "Message.html"); //$NON-NLS-1$
        if (body.exists()) {
            String text = Util.decodeMixedCharset(Files.readAllBytes(body.toPath()));
            /*
             * byte[] content = Files.readAllBytes(body.toPath()); Charset charset; try {
             * AutoDetectReader adr = new AutoDetectReader(new
             * ByteArrayInputStream(content)); charset = adr.getCharset(); adr.close(); }
             * catch (TikaException e) { charset = Charset.forName("UTF-8"); } String text =
             * new String(content, charset);
             */
            if (text != null && !text.isEmpty()) {
                preview.append(text);
                metadata.set(ExtraProperties.MESSAGE_BODY, Util.getContentPreview(text, true));
            }
        } else {
            body = new File(file, "Message.txt"); //$NON-NLS-1$
            if (body.exists()) {
                String text = Util.decodeMixedCharset(Files.readAllBytes(body.toPath()));
                if (text != null && !text.isEmpty()) {
                    metadata.set(ExtraProperties.MESSAGE_BODY, Util.getContentPreview(text, false));
                    text = SimpleHTMLEncoder.htmlEncode(text);
                    preview.append("<pre>"); //$NON-NLS-1$
                    preview.append(text);
                    preview.append("</pre>"); //$NON-NLS-1$
                }
            }
        }
    }

    private void writeOtherProps(File file, Metadata metadata, StringBuilder preview) {
        for (int i = 1; i < types.length; i++) {
            File item = new File(file, types[i] + ".txt"); //$NON-NLS-1$
            if (item.exists()) {
                metadata.set(TikaCoreProperties.TITLE, types[i]);
                if (item.getName().equals("Contact.txt")) //$NON-NLS-1$
                    metadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, "application/outlook-contact"); //$NON-NLS-1$

                List<String> lines = readAllLines(item);
                for (String line : lines) {
                    String[] l = line.split(":", 2); //$NON-NLS-1$
                    if (l.length > 1) {
                        String value = l[1].trim();
                        if (!value.isEmpty())
                            preview.append("<b>" + l[0] + ":</b> " + SimpleHTMLEncoder.htmlEncode(value) + "<br>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    }
                }
            }
        }
    }

    private void writeRecipients(File file, Metadata metadata, StringBuilder preview) {
        File recipients = new File(file, "Recipients.txt"); //$NON-NLS-1$
        if (recipients.exists()) {
            List<String> lines = readAllLines(recipients);
            StringBuilder to = new StringBuilder();
            StringBuilder cc = new StringBuilder();
            StringBuilder bcc = new StringBuilder();
            String name = ""; //$NON-NLS-1$
            for (int i = 0; i < lines.size(); i++) {
                String[] l = lines.get(i).split(":", 2); //$NON-NLS-1$
                if (l.length > 1 && !l[1].trim().isEmpty()) {
                    if (l[0].trim().equals("Display name")) //$NON-NLS-1$
                        name = l[1].trim();
                    if (l[0].trim().equals("Email address") && !name.contains(l[1].trim())) //$NON-NLS-1$
                        name += " <" + l[1].trim() + ">"; //$NON-NLS-1$ //$NON-NLS-2$
                    if (l[0].trim().equals("Recipient type")) { //$NON-NLS-1$
                        String type = l[1].trim();
                        if (type.equals("To")) //$NON-NLS-1$
                            to.append(name + "; "); //$NON-NLS-1$
                        else if (type.equals("CC")) //$NON-NLS-1$
                            cc.append(name + "; "); //$NON-NLS-1$
                        else if (type.equals("CCO")) //$NON-NLS-1$
                            bcc.append(name + "; "); //$NON-NLS-1$
                    }
                }
            }
            if (to.length() > 0) {
                metadata.set(Message.MESSAGE_TO, to.toString());
                preview.append("<b>TO:</b> " + SimpleHTMLEncoder.htmlEncode(to.toString()) + "<br>"); //$NON-NLS-1$ //$NON-NLS-2$
            }
            if (cc.length() > 0) {
                metadata.set(Message.MESSAGE_CC, cc.toString());
                preview.append("<b>CC:</b> " + SimpleHTMLEncoder.htmlEncode(cc.toString()) + "<br>"); //$NON-NLS-1$ //$NON-NLS-2$
            }
            if (bcc.length() > 0) {
                metadata.set(Message.MESSAGE_BCC, bcc.toString());
                preview.append("<b>bcc:</b> " + SimpleHTMLEncoder.htmlEncode(bcc.toString()) + "<br>"); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }
    }

    private void writeHeader(File file, Metadata metadata, StringBuilder preview) {
        File outlookHeader = new File(file, "OutlookHeaders.txt"); //$NON-NLS-1$
        if (outlookHeader.exists()) {
            List<String> lines = readAllLines(outlookHeader);
            String from = "", subject = Messages.getString("LibpffPSTParser.NoSubject"); //$NON-NLS-1$ //$NON-NLS-2$
            for (String line : lines) {
                String[] l = line.split(":", 2); //$NON-NLS-1$
                if (l.length > 1) {
                    String value = l[1].trim();
                    if (!value.isEmpty()) {
                        if (l[0].trim().equals("Subject")) //$NON-NLS-1$
                            subject = value;

                        if (l[0].trim().equals("Client submit time")) //$NON-NLS-1$
                            try {
                                metadata.set(ExtraProperties.MESSAGE_DATE,
                                        df.parse(value.substring(0, value.length() - 14)));
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }

                        if (l[0].trim().equals("Sender name")) //$NON-NLS-1$
                            from = value;
                        else if (l[0].trim().equals("Sender email address")) { //$NON-NLS-1$
                            if (!from.contains(value))
                                from += " <" + value + ">"; //$NON-NLS-1$ //$NON-NLS-2$
                        } else if (l[0].trim().equals("Sent representing name")) { //$NON-NLS-1$
                            if (!from.contains(value))
                                from += " " + value; //$NON-NLS-1$
                        } else if (l[0].trim().equals("Sent representing email address")) { //$NON-NLS-1$
                            if (!from.contains(value))
                                from += " <" + value + ">"; //$NON-NLS-1$ //$NON-NLS-2$
                        } else
                            preview.append("<b>" + l[0] + ":</b> " + SimpleHTMLEncoder.htmlEncode(value) + "<br>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    }
                }
            }
            preview.append("<b>From:</b> " + SimpleHTMLEncoder.htmlEncode(from) + "<br>\n"); //$NON-NLS-1$ //$NON-NLS-2$
            metadata.set(Message.MESSAGE_FROM, from);
            metadata.set(ExtraProperties.MESSAGE_SUBJECT, subject);
        }
    }

    private void writeInternetHeaders(File file, StringBuilder preview) {
        File internetHeaders = new File(file, "InternetHeaders.txt"); //$NON-NLS-1$
        if (internetHeaders.exists()) {
            preview.append("<hr>"); //$NON-NLS-1$
            preview.append(
                    "<div style=\"background-color:white;text-align:left;font-family:arial;color:black;font-size:12px;margin:5px;\">"); //$NON-NLS-1$
            preview.append("Internet Headers:<br>"); //$NON-NLS-1$
            List<String> lines = readAllLines(internetHeaders);
            for (String line : lines) {
                if (!line.trim().isEmpty())
                    preview.append(SimpleHTMLEncoder.htmlEncode(line.trim()) + "<br>"); //$NON-NLS-1$
            }
            preview.append("</div>"); //$NON-NLS-1$
        }
    }

    private void handleAttachment(File file, int parent) throws IOException, SAXException {

        // ignora arquivos/pastas com nomes proibidos no FS gerados pelo pffexport
        if (!file.isFile())
            return;

        try (InputStream is = Files.newInputStream(file.toPath())) {

            String name = getAttachName(file);
            Metadata metadata = new Metadata();
            metadata.set(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(++virtualId));
            metadata.set(ExtraProperties.PARENT_VIRTUAL_ID, String.valueOf(parent));
            metadata.set(Metadata.RESOURCE_NAME_KEY, name);
            metadata.set(ExtraProperties.PST_ATTACH, "true"); //$NON-NLS-1$
            if (deleted)
                metadata.set(ExtraProperties.DELETED, "true"); //$NON-NLS-1$

            // metadata.set(TikaCoreProperties.CREATED, file.);
            // metadata.set(TikaCoreProperties.MODIFIED, attach.getLastModificationTime());

            if (extractor.shouldParseEmbedded(metadata))
                extractor.parseEmbedded(is, handler, metadata, true);
        }

        file.delete();

    }

    private List<String> getAttachNames(File file) {
        ArrayList<String> names = new ArrayList<>();
        File bodyRTF = new File(file, "Message.rtf"); //$NON-NLS-1$
        File attachments = new File(file, "Attachments"); //$NON-NLS-1$
        if (bodyRTF.exists())
            names.add(getAttachName(bodyRTF));
        if (attachments.isDirectory())
            for (File attach : attachments.listFiles())
                names.add(getAttachName(attach));
        return names;
    }

    private String getAttachName(File file) {
        String name = file.getName();
        int i = name.indexOf('_');
        if (i > 0)
            try {
                Integer.parseInt(name.substring(0, i));
                name = name.substring(i + 1);

            } catch (NumberFormatException e) {
            }

        if (name.startsWith("Attachment0") && file.isDirectory()) //$NON-NLS-1$
            for (File f : file.listFiles())
                if (f.getName().startsWith("Message")) { //$NON-NLS-1$
                    name = Messages.getString("LibpffPSTParser.AttachedMessage"); //$NON-NLS-1$
                    break;
                }

        return name;
    }

    private List<String> readAllLines(File file) {
        try {
            String content = Util.decodeUnknowCharset(Files.readAllBytes(file.toPath()));
            String[] newLines = { "\r\n", "\n", "\r" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            for (String newLine : newLines) {
                String[] lines = content.split(newLine);
                if (lines.length > 1)
                    return Arrays.asList(lines);
            }
        } catch (IOException e) {
        }
        return Collections.EMPTY_LIST;
    }

}
