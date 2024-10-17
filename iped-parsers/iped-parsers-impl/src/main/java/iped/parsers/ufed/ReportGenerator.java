package iped.parsers.ufed;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.List;

import iped.data.IItemReader;
import iped.parsers.util.Messages;
import iped.parsers.whatsapp.Message;
import iped.parsers.whatsapp.Util;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;
import iped.utils.EmojiUtil;
import iped.utils.SimpleHTMLEncoder;

/**
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@pf.gov.br>
 */
public class ReportGenerator {

    private int minChatSplitSize = 6000000;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd"); //$NON-NLS-1$
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ"); //$NON-NLS-1$
    private IItemSearcher searcher;
    private boolean firstHtml = true;
    private int currentMsg = 0;

    public ReportGenerator(IItemSearcher searcher) {
        this.searcher = searcher;
    }

    public int getNextMsgNum() {
        return currentMsg;
    }

    public void setMinChatSplitSize(int minChatSplitSize) {
        this.minChatSplitSize = minChatSplitSize;
    }

    private static final String format(String text) {
        return SimpleHTMLEncoder.htmlEncode(text);
    }

    public byte[] generateNextChatHtml(IItemReader c, List<UfedMessage> msgs) throws UnsupportedEncodingException {

        if ((!firstHtml && currentMsg == 0) || (currentMsg > 0 && currentMsg == msgs.size()))
            return null;

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, "UTF-8")); //$NON-NLS-1$

        String[] split = c.getName().split("_", 3); //$NON-NLS-1$
        String title = split[split.length - 1];

        String source = c.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Source"); //$NON-NLS-1$
        String phoneOwner = c.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "phoneOwner"); //$NON-NLS-1$
        String idProperty = "ufedId=" + c.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "id"); //$NON-NLS-1$
        String nameProperty = c.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Name"); //$NON-NLS-1$
        String chatType = c.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "ChatType");
        String[] parties = c.getMetadata().getValues(ExtraProperties.UFED_META_PREFIX + "Participants"); //$NON-NLS-1$

        if (chatType != null) {
            if (chatType.equals(UFEDChatParser.CHATTYPE_ONEONONE)) {
                title = UFEDChatParser.CHATTYPE_ONEONONE_TITLE;
                if (parties != null)
                    title += ": " + ((parties.length > 1) && (parties[0].equals(phoneOwner)) ? parties[1] : parties[0]);
                else
                    title += ": " + idProperty;
            }
            else if (chatType.equals(UFEDChatParser.CHATTYPE_GROUP))
                title = UFEDChatParser.CHATTYPE_GROUP_TITLE + ": " + (nameProperty != null ? nameProperty : idProperty);
            else if (chatType.equals(UFEDChatParser.CHATTYPE_BROADCAST)) {
                if (parties != null) {
                    if ((parties.length == 1) && ((source != null) && (source.equals(UFEDChatParser.WHATSAPP) || 
                                                                       source.equals(UFEDChatParser.WHATSAPP_BUSINESS) || 
                                                                       source.equals(UFEDChatParser.TELEGRAM))))
                        // "Status" chat type (known from behaviour)
                        // NOTE: Apps with this behaviour should be added to this if condition
                        title = UFEDChatParser.CHATTYPE_BROADCAST_STATUS_TITLE + ": " + parties[0];
                    else
                        title = UFEDChatParser.CHATTYPE_BROADCAST_TITLE + ": " + (nameProperty != null ? nameProperty : idProperty);
                }
                else
                    title = UFEDChatParser.CHATTYPE_BROADCAST_TITLE + ": " + (nameProperty != null ? nameProperty : idProperty);
            }
            else if (chatType.equals(UFEDChatParser.CHATTYPE_UNKNOWN)) {
                if ((source != null) && (source.equals(UFEDChatParser.WHATSAPP) || 
                                         source.equals(UFEDChatParser.WHATSAPP_BUSINESS) || 
                                         source.equals(UFEDChatParser.TELEGRAM)))
                    // "Unknown" chat type regarding apps for which there are specific chat types
                    // NOTE: Apps with similar behaviour should be added to this if condition
                    title = UFEDChatParser.CHATTYPE_UNKNOWN_TITLE + ": " + idProperty;
                else {
                    // "Unknown" chat type regarding apps for which there aren't specific chat types
                    // Communication type is derived from the number of participants
                    if ((parties != null) && (parties.length > 0)) {
                        if (parties.length > 2)
                            title = UFEDChatParser.CHATTYPE_GROUP_TITLE + ": " + idProperty;
                        else
                            title = UFEDChatParser.CHATTYPE_ONEONONE_TITLE + ": " + ((parties.length > 1) && (parties[0].equals(phoneOwner)) ? parties[1] : parties[0]);
                    }
                    else
                        title = UFEDChatParser.CHATTYPE_UNKNOWN_TITLE + ": " + idProperty;
                }
            }
            else
                title = chatType + ": " + idProperty;
        }
        else {
            title = idProperty;
        }
    
        printMessageFileHeader(out, title, c.getName(), null);
        if (currentMsg > 0)
            out.println("<div class=\"linha\"><div class=\"date\">" //$NON-NLS-1$
                    + Messages.getString("WhatsAppReport.ChatContinuation") + "</div></div>"); //$NON-NLS-1$ //$NON-NLS-2$

        String lastDate = null;
        while (currentMsg < msgs.size()) {
            UfedMessage m = msgs.get(currentMsg);
            String thisDate = m.getTimeStamp() != null ? dateFormat.format(m.getTimeStamp())
                    : Messages.getString("ReportGenerator.UnknownDate"); //$NON-NLS-1$
            if (lastDate == null || !lastDate.equals(thisDate)) {
                out.println("<div class=\"linha\"><div class=\"date\">" //$NON-NLS-1$
                        + thisDate + "</div></div>"); //$NON-NLS-1$
                lastDate = thisDate;
            }
            boolean isGroup = c.getMetadata().getValues(ExtraProperties.UFED_META_PREFIX + "Participants").length > 2; //$NON-NLS-1$
            printMessage(out, m, isGroup, c.isDeleted());

            if (currentMsg++ != msgs.size() - 1 && bout.size() >= minChatSplitSize) {
                out.println("<div class=\"linha\"><div class=\"date\">" //$NON-NLS-1$
                        + Messages.getString("WhatsAppReport.ChatContinues") + "</div></div>"); //$NON-NLS-1$ //$NON-NLS-2$
                break;
            }
        }

        printMessageFileFooter(out);
        out.flush();

        firstHtml = false;

        return EmojiUtil.replaceByImages(bout.toByteArray());
    }

    private void printMessage(PrintWriter out, UfedMessage message, boolean group, boolean chatDeleted) {

        boolean isFrom = false;
        boolean isTo = false;

        out.println("<div id=\"" + message.getId() + "\" class=\"linha\">"); //$NON-NLS-1$
        String name = null;
        if (message.isSystemMessage()) {
            out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
        } else {
            if (message.isFromMe()) {
                out.println("<div class=\"bbr\"><div class=\"outgoing to\">"); //$NON-NLS-1$
                isTo = true;
                name = message.getLocalResource();
            } else {
                out.println(
                        "<div class=\"bbl\"><div class=\"aw\"><div class=\"awl\"></div></div><div class=\"incoming from\">"); //$NON-NLS-1$
                isFrom = true;
                name = message.getRemoteResource();
            }
            if (name == null)
                name = Messages.getString("ReportGenerator.Unknown"); //$NON-NLS-1$
        }


        if (name != null)
            out.println(
                    "<span style=\"font-family: Arial; color: #b4c74b;\">" + format(name) + "</span><br/>"); //$NON-NLS-1$ //$NON-NLS-2$

        if (message.getData() != null && !message.getData().trim().isEmpty()) {
            if (message.getData().startsWith("BEGIN:VCARD")) { //$NON-NLS-1$
                String[] lines = message.getData().split("\n"); //$NON-NLS-1$
                for (String line : lines) {
                    if (line.startsWith("PHOTO;BASE64:")) { //$NON-NLS-1$
                        out.print("PHOTO:<img src=\"data:image;BASE64," + format(line.substring(13)) + "\"/><br/>"); //$NON-NLS-1$ //$NON-NLS-2$
                    } else {
                        out.print(format(line) + "<br/>"); //$NON-NLS-1$
                    }
                }
            } else {
                out.print(format(message.getData())); // $NON-NLS-1$
                if (!message.isSystemMessage())
                    out.print("<br/>");
            }
        } else if (message.isSystemMessage()) {
            out.print("System Message"); //$NON-NLS-1$
        }
        if (message.getMediaHash() != null || message.getThumbData() != null || message.getMediaName() != null) {
            if (message.getMediaHash() != null) {
                out.println("<input class=\"check\" type=\"checkbox\" onclick=app.check(\"hash:"
                        + message.getMediaHash() + "\",this.checked) name=\"" + message.getMediaHash() + "\" />");
                out.println("<a onclick=app.open(\"hash:" + message.getMediaHash() + "\") "); //$NON-NLS-1$ //$NON-NLS-2$
                String ext = message.getMediaTrueExt();
                String exportPath = iped.parsers.util.Util.getExportPath(message.getMediaHash(), ext); // $NON-NLS-1$
                if (!exportPath.isEmpty())
                    out.println("href=\"" + format(exportPath) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                out.println(">"); //$NON-NLS-1$
            }
            byte[] thumb = message.getThumbData();
            if (thumb != null) {
                if (getTitle(message).equals("video")) //$NON-NLS-1$
                    out.println(Messages.getString("WhatsAppReport.Video") + ":<br>"); //$NON-NLS-1$ //$NON-NLS-2$
                out.print("<img class=\"thumb\" src=\""); //$NON-NLS-1$
                out.print("data:image/jpg;base64," + Util.encodeBase64(thumb) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                out.println(" title=\"" + format(getTitle(message)) + "\"/><br/>"); //$NON-NLS-1$ //$NON-NLS-2$

            } else if (message.getMediaMime() != null) {
                if (message.getMediaMime().startsWith("audio")) { //$NON-NLS-1$
                    out.println("<div class=\"audioImg\" title=\"Audio\"></div>"); //$NON-NLS-1$
                } else if (message.getMediaMime().startsWith("video")) { //$NON-NLS-1$
                    out.println("<div class=\"videoImg\" title=\"Video\"></div>"); //$NON-NLS-1$
                } else if (message.getMediaMime().startsWith("image")) { //$NON-NLS-1$
                    out.println("<div class=\"imageImg\" title=\"Image\"></div>"); //$NON-NLS-1$
                } else if (message.getMediaMime().contains("contact")) { //$NON-NLS-1$
                    out.println("<div class=\"contactImg\" title=\"Contact\"></div>"); //$NON-NLS-1$
                } else
                    out.println("Attachment:<br><div class=\"attachImg\" title=\"Doc\"></div>"); //$NON-NLS-1$
            }
            out.println("</a>"); //$NON-NLS-1$
        }
        if (message.getMediaCaption() != null)
            out.println("<br>" + format(message.getMediaCaption())); //$NON-NLS-1$

        String transcription = message.getTranscription();
        if (transcription != null) {
            out.print("<br>");
            out.print(Messages.getString("ReportGenerator.TranscriptionTitle")); //$NON-NLS-1$
            String confidence = message.getTranscriptConfidence();
            if (confidence != null) {
                float score = Float.valueOf(confidence) * 100;
                out.print(" [" + (int) score + "%]"); //$NON-NLS-1$ //$NON-NLS-2$
            }
            out.println(": <i>"); //$NON-NLS-1$
            out.println(format(transcription));
            out.println("</i><br/>"); //$NON-NLS-1$
        }

        if (!message.getChildPornSets().isEmpty()) {
            out.print("<p><i>" + Messages.getString("WhatsAppReport.FoundInPedoHashDB") + " "
                    + format(message.getChildPornSets().toString()) + "</i></p>");
        }
        if (message.getTimeStamp() != null) {
            out.println("<span class=\"time\">"); //$NON-NLS-1$
            out.println(timeFormat.format(message.getTimeStamp())); // $NON-NLS-1$
            out.println("</span>"); //$NON-NLS-1$
        }
        if (chatDeleted || message.isDeleted()) {
            out.println("<br/><span class=\"recovered\">"); //$NON-NLS-1$
            out.println("<i>" + Messages.getString("WhatsAppReport.MessageDeletedRecovered") + "</i>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            out.println("<div class=\"deletedIcon\"></div>"); //$NON-NLS-1$
            out.println("</span>"); //$NON-NLS-1$
        }
        if (isTo)
            out.println("</div><div class=\"aw\"><div class=\"awr\"></div></div>"); 
        if (isFrom)
            out.println("</div>");



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
                + "	<title>" + format(title) + "</title>\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "	<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n" //$NON-NLS-1$
                + "	<meta name=\"viewport\" content=\"width=device-width\" />\n" //$NON-NLS-1$
                + "     <meta charset=\"UTF-8\" />\n" //$NON-NLS-1$
                + " <link rel=\"shortcut icon\" href=\"" + Util.getImageResourceAsEmbedded("img/favicon.ico") //$NON-NLS-1$ //$NON-NLS-2$
                + "\" />\n" //$NON-NLS-1$
                + "<style>\n" + Util.readResourceAsString("css/whatsapp.css") //$NON-NLS-2$
                + "\n</style>\n" + "<style>.check {vertical-align: top;}</style>" + "</head>\n" //$NON-NLS-3$
                + "<body>\n" //$NON-NLS-1$
                + "<div id=\"topbar\">\n" //$NON-NLS-1$
                + "	<span class=\"left\">" //$NON-NLS-1$
                + " &nbsp; "); //$NON-NLS-1$
        if (avatar != null)
            out.println("<img src=\"data:image/jpg;base64," + Util.encodeBase64(avatar) //$NON-NLS-1$
                    + "\" width=\"40\" height=\"40\"/>"); //$NON-NLS-1$
        out.println(format(chatName) + "</span>\n" //$NON-NLS-1$
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
