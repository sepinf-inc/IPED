package dpf.sp.gpinf.indexer.parsers.ufed;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.List;

import dpf.mg.udi.gpinf.whatsappextractor.Message;
import dpf.mg.udi.gpinf.whatsappextractor.Util;
import dpf.sp.gpinf.indexer.parsers.util.Messages;
import iped3.io.IItemBase;
import iped3.search.IItemSearcher;
import iped3.util.ExtraProperties;

/**
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@dpf.gov.br>
 */
public class ReportGenerator {
    
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd"); //$NON-NLS-1$
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ssZ"); //$NON-NLS-1$
    private IItemSearcher searcher;
    private Object lastChat;
    private int currentMsg = 0;

    public ReportGenerator(IItemSearcher searcher) {
        this.searcher = searcher;
    }

    public int getNextMsgNum() {
        return currentMsg;
    }
    
    public byte[] generateFullChatHtml(IItemBase c, List<Message> msgs) throws UnsupportedEncodingException {
        return this.generateNextChatHtml(c, msgs, Integer.MAX_VALUE);
    }

    public byte[] generateNextChatHtml(IItemBase c, List<Message> msgs, int maxChatSize) throws UnsupportedEncodingException {
        if (lastChat != c) {
            lastChat = c;
            currentMsg = 0;
        }
        if (currentMsg == msgs.size())
            return null;

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, "UTF-8")); //$NON-NLS-1$

        String[] split = c.getName().split("_", 3); //$NON-NLS-1$
        String title = split[split.length - 1];
        printMessageFileHeader(out, title, c.getName(), null);
        if (currentMsg > 0)
            out.println("<div class=\"linha\"><div class=\"date\">" //$NON-NLS-1$
                    + Messages.getString("WhatsAppReport.ChatContinuation") + "</div></div>"); //$NON-NLS-1$ //$NON-NLS-2$

        String lastDate = null;
        while (currentMsg < msgs.size()) {
            Message m = msgs.get(currentMsg);
            String thisDate = m.getTimeStamp() != null ? dateFormat.format(m.getTimeStamp())
                    : Messages.getString("ReportGenerator.UnknownDate"); //$NON-NLS-1$
            if (lastDate == null || !lastDate.equals(thisDate)) {
                out.println("<div class=\"linha\"><div class=\"date\">" //$NON-NLS-1$
                        + thisDate + "</div></div>"); //$NON-NLS-1$
                lastDate = thisDate;
            }
            boolean isGroup = c.getMetadata().getValues(ExtraProperties.UFED_META_PREFIX + "Participants").length > 2; //$NON-NLS-1$
            printMessage(out, m, isGroup, c.isDeleted());

            if (currentMsg++ != msgs.size() - 1 && bout.size() >= maxChatSize) {
                out.println("<div class=\"linha\"><div class=\"date\">" //$NON-NLS-1$
                        + Messages.getString("WhatsAppReport.ChatContinues") + "</div></div>"); //$NON-NLS-1$ //$NON-NLS-2$
                break;
            }
        }

        printMessageFileFooter(out);
        out.flush();

        return bout.toByteArray();
    }

    private void printMessage(PrintWriter out, Message message, boolean group, boolean chatDeleted) {
        out.println("<div id=\"" + message.getId() + "\" class=\"linha\">"); //$NON-NLS-1$
        String name;
        if (message.isFromMe()) {
            out.println("<div class=\"outgoing to\">"); //$NON-NLS-1$
            name = message.getLocalResource();
        } else {
            out.println("<div class=\"incoming from\">"); //$NON-NLS-1$
            name = message.getRemoteResource();
        }
        if (name == null)
            name = Messages.getString("ReportGenerator.Unknown"); //$NON-NLS-1$

        if (chatDeleted || message.isDeleted())
            out.println("🚫 "); //$NON-NLS-1$

        out.println("<span style=\"font-family: 'Roboto-Medium'; color: #b4c74b;\">" + name + "</span><br/>"); //$NON-NLS-1$ //$NON-NLS-2$

        if (message.getData() != null && !message.getData().isEmpty()) {
            if (message.getData().startsWith("BEGIN:VCARD")) { //$NON-NLS-1$
                String[] lines = message.getData().split("\n"); //$NON-NLS-1$
                for (String line : lines) {
                    if (line.startsWith("PHOTO;BASE64:")) { //$NON-NLS-1$
                        out.print("PHOTO:<img src=\"data:image;BASE64," + line.substring(13) + "\"/><br/>"); //$NON-NLS-1$ //$NON-NLS-2$
                    } else {
                        out.print(line + "<br/>"); //$NON-NLS-1$
                    }
                }
            } else {
                out.print(message.getData() + "<br/>"); //$NON-NLS-1$
            }
        }
        if (message.getMediaHash() != null || message.getThumbData() != null || message.getMediaName() != null) {
            if (message.getMediaHash() != null) {
                out.println("<input class=\"check\" type=\"checkbox\" onclick=app.check(\"hash:" + message.getMediaHash() + "\",this.checked) />");
                out.println("<a onclick=app.open(\"hash:" + message.getMediaHash() + "\") "); //$NON-NLS-1$ //$NON-NLS-2$
                String ext = ""; //$NON-NLS-1$
                int extIdx = message.getMediaName().lastIndexOf('.');
                if (extIdx > -1)
                    ext = message.getMediaName().substring(extIdx);
                String exportPath = dpf.sp.gpinf.indexer.parsers.util.Util.getExportPath(message.getMediaHash(), ext); // $NON-NLS-1$
                if (!exportPath.isEmpty())
                    out.println("href=\"" + exportPath + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                out.println(">"); //$NON-NLS-1$
            }
            byte[] thumb = message.getThumbData();
            if (thumb != null) {
                if (getTitle(message).equals("video")) //$NON-NLS-1$
                    out.println(Messages.getString("WhatsAppReport.Video") + ":<br>"); //$NON-NLS-1$ //$NON-NLS-2$
                out.println("<img class=\"thumb\" src=\""); //$NON-NLS-1$
                out.println("data:image/jpg;base64," + Util.encodeBase64(thumb) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                out.println(" title=\"" + getTitle(message) + "\"/>"); //$NON-NLS-1$ //$NON-NLS-2$

            } else if (message.getMediaMime() != null) {
                if (message.getMediaMime().startsWith("audio")) { //$NON-NLS-1$
                    out.println("<img src=\"" //$NON-NLS-1$
                            + Util.getImageResourceAsEmbedded("img/audio.png") //$NON-NLS-1$
                            + "\" width=\"100\" height=\"102\" title=\"Audio\"/>"); //$NON-NLS-1$
                } else if (message.getMediaMime().startsWith("video")) { //$NON-NLS-1$
                    out.println("<img src=\"" //$NON-NLS-1$
                            + Util.getImageResourceAsEmbedded("img/video.png") //$NON-NLS-1$
                            + "\" width=\"100\" height=\"102\" title=\"Video\"/>"); //$NON-NLS-1$
                } else if (message.getMediaMime().startsWith("image")) { //$NON-NLS-1$
                    out.println("<img src=\"" //$NON-NLS-1$
                            + Util.getImageResourceAsEmbedded("img/image.png") //$NON-NLS-1$
                            + "\" width=\"100\" height=\"102\" title=\"Image\"/>"); //$NON-NLS-1$
                } else if (message.getMediaMime().contains("contact")) { //$NON-NLS-1$
                    out.println("<img src=\"" //$NON-NLS-1$
                            + Util.getImageResourceAsEmbedded("img/contact.png") //$NON-NLS-1$
                            + "\" width=\"100\" height=\"102\" title=\"Image\"/>"); //$NON-NLS-1$
                } else
                    out.println("Attachment:<br><img src=\"" //$NON-NLS-1$
                            + Util.getImageResourceAsEmbedded("img/attach.png") //$NON-NLS-1$
                            + "\" width=\"100\" height=\"102\" title=\"Doc\"/>"); //$NON-NLS-1$
            }
            out.println("</a>"); //$NON-NLS-1$
        }
        if (message.getMediaCaption() != null)
            out.println("<br>" + message.getMediaCaption()); //$NON-NLS-1$

        if (message.getTimeStamp() != null) {
            out.println("<span class=\"time\">"); //$NON-NLS-1$
            out.println(timeFormat.format(message.getTimeStamp()) + " &nbsp;"); //$NON-NLS-1$
            out.println("</span>"); //$NON-NLS-1$
        }
        out.println("</div></div>"); //$NON-NLS-1$
    }

    private static String getTitle(Message message) {
        if (message.getMediaMime() != null && !message.getMediaMime().isEmpty())
            return message.getMediaMime().substring(0, message.getMediaMime().indexOf('/'));
        else
            return "File"; //$NON-NLS-1$
    }

    private static void printMessageFileHeader(PrintWriter out, String chatName, String title, byte[] avatar) {
        out.println("<!DOCTYPE html>\n" //$NON-NLS-1$
                + "<html>\n" //$NON-NLS-1$
                + "<head>\n" //$NON-NLS-1$
                + "	<title>" + title + "</title>\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "	<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n" //$NON-NLS-1$
                + "	<meta name=\"viewport\" content=\"width=device-width\" />\n" //$NON-NLS-1$
                + "     <meta charset=\"UTF-8\" />\n" //$NON-NLS-1$
                + " <link rel=\"shortcut icon\" href=\"" + Util.getImageResourceAsEmbedded("img/favicon.ico")+ "\" />\n" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + "<style>\n"
                + Util.readResourceAsString("css/whatsapp.css") //$NON-NLS-1$
                + "\n</style>\n"
                + "<style>.check {vertical-align: top;}</style>"
                + "</head>\n" //$NON-NLS-1$
                + "<body>\n" //$NON-NLS-1$
                + "<div id=\"topbar\">\n" //$NON-NLS-1$
                + "	<span class=\"left\">" //$NON-NLS-1$
                + " &nbsp; "); //$NON-NLS-1$
        if (avatar != null)
            out.println("<img src=\"data:image/jpg;base64," + Util.encodeBase64(avatar) //$NON-NLS-1$
                    + "\" width=\"40\" height=\"40\"/>"); //$NON-NLS-1$
        out.println(chatName + "</span>\n" //$NON-NLS-1$
                + "</div>\n" //$NON-NLS-1$
                + "<div id=\"conversation\">\n" //$NON-NLS-1$
                + "<br/><br/><br/>"); //$NON-NLS-1$
    }

    private static void printMessageFileFooter(PrintWriter out) {
        out.println("	<br /><br /><br />\n" //$NON-NLS-1$
                + "</div>\n" //$NON-NLS-1$
                + "<div id=\"lastmsg\">&nbsp;</div>\n" //$NON-NLS-1$
                + "</body>\n" //$NON-NLS-1$
                + "</html>"); //$NON-NLS-1$
    }

}
