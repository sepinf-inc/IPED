package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.poi.hsmf.datatypes.AttachmentChunks;
import org.apache.poi.hsmf.datatypes.ByteChunk;
import org.apache.poi.hsmf.datatypes.StringChunk;
import org.apache.poi.hsmf.exceptions.ChunkNotFoundException;
import org.bbottema.rtftohtml.RTF2HTMLConverter;
import org.bbottema.rtftohtml.impl.RTF2HTMLConverterRFCCompliant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private ArrayList<Object[]> attachs;
    private final DateFormat dateFormat = new SimpleDateFormat(Messages.getString("EmailViewer.DateFormat"));

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

        if (content == null) {
            super.loadFile(null, null);
            return;
        }

        File tmpFile = null;

        try {

            tmpFile = File.createTempFile("emailHtmlFile", ".html");
            tmpFile.deleteOnExit();

            createEmailHtml(content.getFile(), tmpFile);

        } catch (Exception e) {
            e.printStackTrace();
        }

        super.loadFile(new FileContentSource(tmpFile), highlightTerms);

    }

    private synchronized void createEmailHtml(File msgFile, File emailHtmlFile) throws IOException {

        attachs = new ArrayList<Object[]>();

        InputStream in = new FileInputStream(msgFile);

        StringBuilder preview = new StringBuilder();

        Charset charset = Charset.forName("windows-1252");

        preview.append("<html>");
        preview.append("<head>");
        preview.append("<meta http-equiv=\"content-type\" content=\"text/html; charset=" + charset + "\" />");
        preview.append("</head>");
        preview.append(
                "<body style=\"background-color:white;text-align:left;font-family:arial;color:black;font-size:14px;margin:5px;\">");

        PrintWriter out = null;
        org.apache.poi.hsmf.MAPIMessage msg = null;

        Index index = new Index();

        try {

            out = new PrintWriter(new BufferedWriter(new FileWriter(emailHtmlFile)), true);
            msg = new org.apache.poi.hsmf.MAPIMessage(in);
            parseMsg(msg, preview, attachs, index);

        } catch (Exception e) {
            LOGGER.warn("Failed to parse msg e-mail file. Error:{}", e.toString());
            e.printStackTrace();
        }

        preview.append("</body>");
        preview.append("</html>");

        out.write(preview.toString());
        out.close();
        out = null;

    }

    public void parseMsg(org.apache.poi.hsmf.MAPIMessage msg, StringBuilder preview, ArrayList<Object[]> attachs,
            Index index) throws Exception {

        String attachmentList;

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

        value = msg.getSubject();
        value = (value == null || value.isEmpty()) ? Messages.getString("EmailViewer.UnNamed") : value;
        preview.append("<b>" + Messages.getString("EmailViewer.Subject") + ": "
                + SimpleHTMLEncoder.htmlEncode(value.trim()) + "</b><br>");

        value = "";
        for (String header : msg.getHeaders()) {
            if (header.startsWith("From: ")) {
                value = header.replace("From: ", "");
            }
        }

        from[0] = (msg.getDisplayFrom() != null) ? msg.getDisplayFrom() : "";
        from[1] = parserEmail(value, "<?([a-z0-9.-]+\\@[a-z0-9.-]+)>?", 1);

        if (!from[1].isEmpty() && from[1].compareTo(from[0]) != 0) {
            preview.append(
                    "<b>" + Messages.getString("EmailViewer.From") + ":</b> " + SimpleHTMLEncoder.htmlEncode(from[0])
                            + SimpleHTMLEncoder.htmlEncode(" <" + from[1] + ">") + "<br>");
        } else {
            preview.append("<b>" + Messages.getString("EmailViewer.From") + ":</b> "
                    + SimpleHTMLEncoder.htmlEncode(from[0]) + "<br>");
        }

        i = 0;
        value = msg.getDisplayTo();
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
        value = msg.getDisplayCC();
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
        value = msg.getDisplayBCC();
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

        String values[] = msg.getRecipientEmailAddressList();
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

        if (msg.getMessageDate() != null)
            date = msg.getMessageDate().getTime();
        if (date != null)
            preview.append(
                    "<b>" + Messages.getString("EmailViewer.Date") + ":</b> " + dateFormat.format(date) + "<br>");

        attachmentList = "";

        AttachmentChunks[] atts = msg.getAttachmentFiles();
        Map<String, String> cids = new HashMap<String, String>();

        int count = 0;
        for (AttachmentChunks att : atts) {

            if (!att.isEmbeddedMessage()) {

                StringChunk attachChunk = att.getAttachLongFileName();
                String attachName = ((attachChunk == null || attachChunk.getValue().isEmpty())
                        ? Messages.getString("EmailViewer.UnNamed")
                        : attachChunk.getValue());
                if (attachmentList.isEmpty())
                    attachmentList = "<a href=\"\" onclick=\"app.open(" + index.index + ")\">" + attachName + "</a>";
                else
                    attachmentList += separator + "<a href=\"\" onclick=\"app.open(" + index.index + ")\">" + attachName
                            + "</a>";

                File attach;
                String fileExt = "";
                if (attachName != null && attachName.lastIndexOf(".") > -1)
                    fileExt = attachName.substring(attachName.lastIndexOf("."));

                ByteChunk byteChunk = att.getAttachData();
                if (byteChunk != null) {
                    attach = File.createTempFile("attach", fileExt);
                    FileUtils.writeByteArrayToFile(attach, byteChunk.getValue());
                    attach.deleteOnExit();

                    Object[] obj = { attach, attachName };

                    attachs.add(obj);

                    index.index++;

                    if (att.getAttachMimeTag() != null && att.getAttachMimeTag().getValue().startsWith("image")) {
                        cids.put(attachName, attach.getName());
                    }
                }
            } else {

                org.apache.poi.hsmf.MAPIMessage msgEmbedded = att.getEmbeddedMessage();

                if (msgEmbedded != null && msgEmbedded.getSubject() != null) {
                    if (attachmentList.isEmpty())
                        attachmentList = "<a href=\"#" + msgEmbedded.getSubject() + "\">" + msgEmbedded.getSubject()
                                + ".msg" + "</a>";
                    else
                        attachmentList += separator + "<a href=\"#" + msgEmbedded.getSubject() + "\">"
                                + msgEmbedded.getSubject() + ".msg" + "</a>";
                }

            }

            count++;

        }

        if (count > 0)
            preview.append(
                    "<b>" + Messages.getString("EmailViewer.Attachments") + "(" + (count) + "):</b> " + attachmentList);

        preview.append("<hr>");

        preview.append("<pre>");

        boolean noHtml = false;
        String corpo = "";
        try {
            corpo = msg.getHtmlBody();
            corpo = corpo.trim();
            corpo = corpo.replaceAll("(src=\"[^@]+)@([^\"]+)", "$1");
            corpo = corpo.replaceAll("(background=\"[^@]+)@([^\"]+)", "$1");
            for (String cid : cids.keySet()) {
                corpo = corpo.replace("cid:" + cid, "file:" + cids.get(cid));
                corpo += "<hr>" + cid + ":<br><img src=\"" + cids.get(cid) + "\">";
            }
            preview.append(corpo);
            cids.clear();
            cids = null;
        } catch (ChunkNotFoundException cnfe) {
            noHtml = true;
        }

        boolean noRtf = false;
        if (noHtml) {
            try {
                RTF2HTMLConverter converter = RTF2HTMLConverterRFCCompliant.INSTANCE;
                corpo = msg.getRtfBody();
                corpo = converter.rtf2html(corpo);
                corpo = corpo.replaceAll("(src=\"[^@]+)@([^\"]+)", "$1");
                corpo = corpo.replaceAll("(background=\"[^@]+)@([^\"]+)", "$1");
                for (String cid : cids.keySet()) {
                    corpo = corpo.replace("cid:" + cid, "file:" + cids.get(cid));
                    corpo += "<hr>" + cid + ":<br><img src=\"" + cids.get(cid) + "\">";
                }
                preview.append(corpo);
                cids.clear();
                cids = null;
            } catch (ChunkNotFoundException cnfe) {
                noRtf = true;
            }
        }

        if (noRtf) {
            try {
                corpo = msg.getTextBody();
                preview.append(corpo);
                cids.clear();
                cids = null;
            } catch (ChunkNotFoundException cnfe) {
                ;
            }
        }

        preview.append("</pre>");

        for (AttachmentChunks att : atts) {

            if (att.isEmbeddedMessage()) {

                org.apache.poi.hsmf.MAPIMessage msgEmbedded = att.getEmbeddedMessage();
                if (msgEmbedded != null && msgEmbedded.getSubject() != null) {

                    String titulo = msgEmbedded.getSubject() + ".msg";

                    preview.append("<B><a id=\"" + msgEmbedded.getSubject() + "\"></a>--- Embedded MSG Attachment: '"
                            + titulo + "' ---</B></BR>");

                    parseMsg(msgEmbedded, preview, attachs, index);

                }
            }
        }

    }

    public String parserEmail(String texto, String regexp, int grupo) {

        Pattern modelPattern = Pattern.compile(regexp);
        Matcher matcher = modelPattern.matcher(texto);
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
            Object[] att = attachs.get(attNum);
            File file = (File) att[0];
            String attachName = (String) att[1];
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
