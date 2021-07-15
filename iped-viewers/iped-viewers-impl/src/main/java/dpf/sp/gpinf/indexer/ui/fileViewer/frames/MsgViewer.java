package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.poi.hmef.attribute.MAPIRtfAttribute;
import org.apache.poi.hsmf.MAPIMessage;
import org.apache.poi.hsmf.datatypes.AttachmentChunks;
import org.apache.poi.hsmf.datatypes.ByteChunk;
import org.apache.poi.hsmf.datatypes.MAPIProperty;
import org.apache.poi.hsmf.datatypes.StringChunk;
import org.apache.poi.hsmf.datatypes.Types;
import org.apache.poi.hsmf.exceptions.ChunkNotFoundException;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.html.HtmlEncodingDetector;
import org.apache.tika.parser.rtf.RTFParser2;
import org.bbottema.rtftohtml.RTF2HTMLConverter;
import org.bbottema.rtftohtml.impl.RTF2HTMLConverterRFCCompliant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.parsers.util.ToXMLContentHandler;
import dpf.sp.gpinf.indexer.ui.fileViewer.Messages;
import dpf.sp.gpinf.indexer.util.FileContentSource;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.SimpleHTMLEncoder;
import iped3.io.IStreamSource;

/**
 *
 * @author guilherme.dutra
 */

public class MsgViewer extends HtmlViewer {

    private static Logger LOGGER = LoggerFactory.getLogger(MsgViewer.class);
    /**
     *
     */
    private static final long serialVersionUID = 12L;

    private HtmlEncodingDetector detector = new HtmlEncodingDetector();
    private Charset win1252 = Charset.forName("windows-1252");
    private Pattern emaillPattern = Pattern.compile("<?([0-9a-zA-Z\\+\\.\\_\\%\\-\\#\\!]+\\@[a-zA-Z0-9.-]+)>?");
    private TreeSet<String> imageExts = new TreeSet<>(Arrays.asList(".jpg", ".jpeg", ".png", ".bmp", ".gif"));

    private ArrayList<Pair<File, String>> attachs = new ArrayList<>();
    private final DateFormat dateFormat = new SimpleDateFormat(Messages.getString("EmailViewer.DateFormat"));
    private File tmpFile = null;

    public MsgViewer() {
        this.fileHandler = new AttachmentOpen();
        this.enableJavascript = true;
    }

    @Override
    public boolean isSupportedType(String contentType) {
        return contentType.equals("application/vnd.ms-outlook");
    }

    @Override
    public String getName() {
        return "Msg";
    }

    @Override
    public void loadFile(IStreamSource content, Set<String> highlightTerms) {

        deletePreviousTempFiles();

        if (content == null) {
            super.loadFile(null, null);
            return;
        }

        try {
            tmpFile = File.createTempFile("emailHtmlFile", ".html");
            tmpFile.deleteOnExit();

            createEmailHtml(content.getFile(), tmpFile);

        } catch (Exception e) {
            e.printStackTrace();
        }

        super.loadFile(new FileContentSource(tmpFile), highlightTerms);

    }

    private void deletePreviousTempFiles() {
        for (Pair<File, String> pair : attachs) {
            pair.getLeft().delete();
        }
        attachs.clear();
        if (tmpFile != null) {
            tmpFile.delete();
            tmpFile = null;
        }
    }

    private synchronized void createEmailHtml(File msgFile, File emailHtmlFile) throws IOException {

        attachs.clear();

        StringBuilder preview = new StringBuilder();

        Charset charset = StandardCharsets.UTF_8;

        preview.append("<html>");
        preview.append("<head>");
        preview.append("<meta http-equiv=\"content-type\" content=\"text/html; charset=" + charset + "\" />");
        preview.append("</head>");
        preview.append(
                "<body style=\"background-color:white;text-align:left;font-family:arial;color:black;font-size:14px;margin:5px;\">");

        Index index = new Index();
        try {
            MAPIMessage msg = new MAPIMessage(msgFile);
            parseMsg(msg, preview, attachs, index);

        } catch (Exception e) {
            LOGGER.warn("Failed to parse msg e-mail file. Error:{}", e.toString());
            e.printStackTrace();
        }

        preview.append("</body>");
        preview.append("</html>");

        Files.write(emailHtmlFile.toPath(), preview.toString().getBytes(charset));

    }

    public void parseMsg(org.apache.poi.hsmf.MAPIMessage msg, StringBuilder preview,
            ArrayList<Pair<File, String>> attachs, Index index) throws IOException {

        Date date = null;
        String separator = ", ";
        String value = "";

        int i = 0;
        String split[] = null;
        String from[] = new String[2];
        String to[] = null;
        String cc[] = null;
        String bcc[] = null;

        ArrayList<Object[]> RecipientList = new ArrayList<Object[]>();

        try {
            value = msg.getSubject();
        } catch (ChunkNotFoundException e) {
            value = null;
        }
        value = (value == null || value.isEmpty()) ? Messages.getString("EmailViewer.UnNamed") : value;
        preview.append("<b>" + Messages.getString("EmailViewer.Subject") + ": "
                + SimpleHTMLEncoder.htmlEncode(value.trim()) + "</b><br>");

        value = "";
        try {
            for (String header : msg.getHeaders()) {
                if (header.startsWith("From: ")) {
                    value = header.replace("From: ", "");
                }
            }
        } catch (ChunkNotFoundException e) {
            // ignore
        }


        try {
            from[0] = (msg.getDisplayFrom() != null) ? msg.getDisplayFrom() : "";
        } catch (ChunkNotFoundException e) {
            from[0] = "";
        }
        from[1] = parserEmail(value, 1);

        if (!from[1].isEmpty() && from[1].compareTo(from[0]) != 0) {
            preview.append(
                    "<b>" + Messages.getString("EmailViewer.From") + ":</b> " + SimpleHTMLEncoder.htmlEncode(from[0])
                            + SimpleHTMLEncoder.htmlEncode(" <" + from[1] + ">") + "<br>");

        } else if (!from[0].isEmpty() || !from[1].isEmpty()) {
            preview.append("<b>" + Messages.getString("EmailViewer.From") + ":</b> "
                    + SimpleHTMLEncoder.htmlEncode(!from[0].isEmpty() ? from[0] : from[1]) + "<br>");
        }

        i = 0;
        try {
            value = msg.getDisplayTo();
        } catch (ChunkNotFoundException e) {
            value = null;
        }
        if (value != null && !value.isEmpty()) {
            split = value.split(";");
            if (split != null && split.length > 0) {
                to = new String[split.length];
                for (String chunck : split) {
                    to[i] = chunck;
                    i++;
                }
            }
        }

        i = 0;
        try {
            value = msg.getDisplayCC();
        } catch (ChunkNotFoundException e) {
            value = null;
        }
        if (value != null && !value.isEmpty()) {
            split = value.split(";");
            if (split != null && split.length > 0) {
                cc = new String[split.length];
                for (String chunck : split) {
                    cc[i] = chunck;
                    i++;
                }
            }
        }

        i = 0;
        try {
            value = msg.getDisplayBCC();
        } catch (ChunkNotFoundException e) {
            value = null;
        }
        if (value != null && !value.isEmpty()) {
            split = value.split(";");
            if (split != null && split.length > 0) {
                bcc = new String[split.length];
                for (String chunck : split) {
                    bcc[i] = chunck;
                    i++;
                }
            }
        }

        String values[];
        try {
            values = msg.getRecipientEmailAddressList();
        } catch (ChunkNotFoundException e) {
            values = null;
        }
        if (values != null && values.length > 0) {
            i = 0;
            for (int j = 0; to != null && j < to.length; j++) {
                Object[] obj = { to[j], values[i], 0 };
                RecipientList.add(obj);
                i++;
            }
            for (int j = 0; cc != null && j < cc.length; j++) {
                Object[] obj = { cc[j], values[i], 1 };
                RecipientList.add(obj);
                i++;
            }
            for (int j = 0; bcc != null && j < bcc.length; j++) {
                Object[] obj = { bcc[j], values[i], 2 };
                RecipientList.add(obj);
                i++;
            }
        }

        String toValue = "";
        String ccValue = "";
        String bccValue = "";

        for (int k = 0; k < RecipientList.size(); k++) {
            Object[] rec = RecipientList.get(k);
            String display = (String) rec[0];
            String address = (String) rec[1];
            int type = (int) rec[2];
            switch (type) {
                case 0:
                    if (toValue.isEmpty())
                        toValue += display + " <" + address + ">";
                    else
                        toValue += separator + display + " <" + address + ">";
                    break;
                case 1:
                    if (ccValue.isEmpty())
                        ccValue += display + " <" + address + ">";
                    else
                        ccValue += separator + display + " <" + address + ">";
                    break;
                case 2:
                    if (bccValue.isEmpty())
                        bccValue += display + " <" + address + ">";
                    else
                        bccValue += separator + display + " <" + address + ">";
                    break;

            }

        }

        if (!toValue.isEmpty())
            preview.append("<b>" + Messages.getString("EmailViewer.To") + ":</b> "
                    + SimpleHTMLEncoder.htmlEncode(toValue) + "<br>");

        if (!ccValue.isEmpty())
            preview.append("<b>" + Messages.getString("EmailViewer.Cc") + ":</b> "
                    + SimpleHTMLEncoder.htmlEncode(ccValue) + "<br>");

        if (!bccValue.isEmpty())
            preview.append("<b>" + Messages.getString("EmailViewer.Bcc") + ":</b> "
                    + SimpleHTMLEncoder.htmlEncode(bccValue) + "<br>");

        date = null;
        try {
            if (msg.getMessageDate() != null)
                date = msg.getMessageDate().getTime();
        } catch (ChunkNotFoundException e) {
            // ignore
        }
        if (date != null)
            preview.append(
                    "<b>" + Messages.getString("EmailViewer.Date") + ":</b> " + dateFormat.format(date) + "<br>");

        StringBuilder attachmentList = new StringBuilder();

        AttachmentChunks[] atts = msg.getAttachmentFiles();
        Map<String, Pair<File, String>> cids = new HashMap<>();

        int count = 0;
        for (AttachmentChunks att : atts) {

            if (!att.isEmbeddedMessage()) {

                String attachName = getAttachName(att);

                if (attachmentList.length() > 0) {
                    attachmentList.append(separator);
                }
                attachmentList.append("<a href=\"\" onclick=\"app.open(" + index.index + ")\">"
                        + SimpleHTMLEncoder.htmlEncode(attachName) + "</a>");

                ByteChunk byteChunk = att.getAttachData();
                if (byteChunk != null) {
                    String fileExt = "";
                    if (attachName != null && attachName.lastIndexOf(".") > -1)
                        fileExt = attachName.substring(attachName.lastIndexOf("."));

                    File file = File.createTempFile("attach", fileExt);
                    FileUtils.writeByteArrayToFile(file, byteChunk.getValue());
                    file.deleteOnExit();

                    Pair<File, String> pair = Pair.of(file, attachName);
                    attachs.add(pair);

                    index.index++;

                    if (isImageMimeType(att)) {
                        StringChunk cidChunk = att.getAttachContentId();
                        String cid = cidChunk != null && !cidChunk.getValue().isEmpty() ? cidChunk.getValue()
                                : UUID.randomUUID().toString();
                        cids.put(cid, pair);
                    }
                }
            } else {
                org.apache.poi.hsmf.MAPIMessage msgEmbedded = att.getEmbeddedMessage();
                if (msgEmbedded != null) {
                    String subject;
                    try {
                        subject = SimpleHTMLEncoder.htmlEncode(msgEmbedded.getSubject());
                    } catch (ChunkNotFoundException e) {
                        subject = Messages.getString("EmailViewer.EmbeddedMessage");
                    }
                    if (attachmentList.length() > 0) {
                        attachmentList.append(separator);
                    }
                    attachmentList.append("<a href=\"#embedded_msg_" + count + "\">" + subject + ".msg" + "</a>");
                }
            }
            count++;
        }

        if (count > 0) {
            preview.append("<b>" + Messages.getString("EmailViewer.Attachments") + "(" + (count) + "):</b> ");
            preview.append(attachmentList);
        }

        preview.append("<hr>");

        Set<String> inlined = new HashSet<>();
        boolean noHtml = false;
        String corpo = "";
        try {
            corpo = msg.getHtmlBody().trim();
            corpo = fixUTF8AsWin1252(corpo);
            corpo = inlineImages(corpo, cids, inlined);
            preview.append(corpo);
        } catch (ChunkNotFoundException cnfe) {
            noHtml = true;
        }

        boolean noRtf = false;
        if (noHtml) {
            try {
                corpo = getTikaRTFToHTML(msg).trim();
                if (corpo.isEmpty() || hasOnlyBasicChars(corpo)) {
                    RTF2HTMLConverter converter = RTF2HTMLConverterRFCCompliant.INSTANCE;
                    String rtfString = msg.getRtfBody().trim();
                    corpo = converter.rtf2html(rtfString);
                }
                if (!corpo.matches(".*<(br|BR)/?>.*")) {
                    corpo = "<pre>" + corpo + "</pre>";
                }
                corpo = inlineImages(corpo, cids, inlined);
                preview.append(corpo);
            } catch (ChunkNotFoundException cnfe) {
                noRtf = true;
            }
        }

        if (noRtf) {
            try {
                corpo = msg.getTextBody().trim();
                corpo = SimpleHTMLEncoder.htmlEncode(corpo);
                preview.append("<pre>");
                preview.append(corpo);
                preview.append("</pre>");
            } catch (ChunkNotFoundException cnfe) {
                // ignore
            }
        }

        // append image attachments not inlined at the end
        inlined.stream().forEach(image -> cids.remove(image));
        for (String cid : cids.keySet()) {
            Pair<File, String> pair = cids.get(cid);
            preview.append("<hr>" + pair.getRight() + ":<br><img src=\"" + pair.getLeft().toURI().toString() + "\">");
        }
        cids.clear();

        count = 0;
        for (AttachmentChunks att : atts) {
            if (att.isEmbeddedMessage()) {
                org.apache.poi.hsmf.MAPIMessage msgEmbedded = att.getEmbeddedMessage();
                if (msgEmbedded != null) {
                    String subject;
                    try {
                        subject = SimpleHTMLEncoder.htmlEncode(msgEmbedded.getSubject());
                    } catch (ChunkNotFoundException e) {
                        subject = Messages.getString("EmailViewer.EmbeddedMessage");
                    }
                    preview.append("<hr><B><a id=\"embedded_msg_" + count + "\"></a>--- "
                            + Messages.getString("EmailViewer.EmbeddedMessage") + ": '" + subject + ".msg"
                            + "' ---</B></BR>");

                    parseMsg(msgEmbedded, preview, attachs, index);
                }
            }
            count++;
        }

    }

    private String getAttachName(AttachmentChunks att) {
        StringChunk attachChunk = att.getAttachLongFileName();
        if (attachChunk == null || attachChunk.getValue().isEmpty()) {
            attachChunk = att.getAttachFileName();
        }
        return ((attachChunk == null || attachChunk.getValue().isEmpty()) ? Messages.getString("EmailViewer.UnNamed")
                : attachChunk.getValue());
    }

    private boolean isImageMimeType(AttachmentChunks att) {
        if (att.getAttachMimeTag() != null && att.getAttachMimeTag().getValue().startsWith("image")) {
            return true;
        }
        String name = getAttachName(att);
        for (String imgExt : imageExts) {
            if (name.toLowerCase().endsWith(imgExt)) {
                return true;
            }
        }
        if (att.getAttachData() != null) {
            Tika tika = new Tika();
            return tika.detect(att.getAttachData().getValue()).startsWith("image");
        }
        return false;
    }

    private String inlineImages(String corpo, Map<String, Pair<File, String>> cids, Set<String> inlined) {
        for (String cid : cids.keySet()) {
            int prevLen = corpo.length();
            corpo = corpo.replace("cid:" + cid, cids.get(cid).getLeft().toURI().toString());
            if (corpo.length() != prevLen) {
                inlined.add(cid);
            }
        }
        return corpo;
    }

    private String fixUTF8AsWin1252(String body) throws IOException {
        byte[] win1252Bytes = body.getBytes(win1252);
        Charset charset = detector.detect(new ByteArrayInputStream(win1252Bytes), new Metadata());
        if (StandardCharsets.UTF_8.equals(charset)) {
            return new String(win1252Bytes, StandardCharsets.UTF_8);
        }
        return body;
    }

    private byte[] getRTFData(MAPIMessage msg) throws IOException, ChunkNotFoundException {
        ByteChunk chunk = msg.getMainChunks().getRtfBodyChunk();
        if (chunk == null) {
            throw new ChunkNotFoundException();
        }
        MAPIRtfAttribute rtf = new MAPIRtfAttribute(MAPIProperty.RTF_COMPRESSED, Types.BINARY.getId(),
                chunk.getValue());
        return rtf.getData();
    }

    private boolean hasOnlyBasicChars(String text) {
        for (char c : text.toCharArray()) {
            if (c > 255 && (c < 0x2000 || c > 0x202F)) {
                return false;
            }
        }
        return true;
    }

    private String getTikaRTFToHTML(MAPIMessage msg) {
        try {
            RTFParser2 parser = new RTFParser2();
            ToXMLContentHandler handler = new ToXMLContentHandler();
            parser.parse(new ByteArrayInputStream(getRTFData(msg)), handler, new Metadata(),
                    new ParseContext());
            return handler.toString();

        } catch (Exception e) {
            // ignore
        }
        return "";
    }

    public String parserEmail(String texto, int grupo) {

        Matcher matcher = emaillPattern.matcher(texto);
        String found = "";

        if (matcher.find() && grupo >= 0) {
            if (grupo <= matcher.groupCount())
                found = matcher.group(grupo);
        }
        if (grupo == -1)
            found = "";

        return found;

    }

    public class AttachmentOpen extends FileHandler {

        public void open(int attNum) {
            Pair<File, String> pair = attachs.get(attNum);
            File file = pair.getLeft();
            String attachName = pair.getRight();
            if (IOUtil.isToOpenExternally(attachName, IOUtil.getExtension(file))) {
                this.openFile(file);
            }
        }

    }

    class Index {

        public int index = 0;

        public Index() {
            index = 0;
        }

    }

}
