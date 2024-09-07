package iped.parsers.ufed;

import static iped.parsers.ufed.UfedUtils.readUfedMetadata;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import iped.data.IItemReader;
import iped.parsers.util.Messages;
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
        String ret = SimpleHTMLEncoder.htmlEncode(text);

        // Keep line breaks present in the content, converting to an HTML <br/>
        ret = ret.replaceAll("\n", "<br/>\n");

        return ret;
    }

    private String formatLocation(Attachment attach) {

        if (attach.getFile() == null) {
            return StringUtils.EMPTY;
        }
        IItemReader fileItem = attach.getFile().getItem();

        String type = readUfedMetadata(fileItem, "Type");
        String description = readUfedMetadata(fileItem, "Description");
        String name = readUfedMetadata(fileItem, "name");
        String street = readUfedMetadata(fileItem, "Street1");
        String coord = fileItem.getMetadata().get(ExtraProperties.LOCATIONS);
        String key = "Shared".equals(type) ? "SharedLocationMessage" : "LocationMessage";

        StringBuilder sb = new StringBuilder();
        sb.append("<b>").append("<img class=\"location\"/>").append(Messages.getString("WhatsAppReport." + key)).append("</b><br/>");

        String[] coordSplit = coord.split(";");
        sb.append(Messages.getString("WhatsAppReport.Latitude")).append(": ");
        sb.append(coordSplit[0]).append("<br/>");

        sb.append(Messages.getString("WhatsAppReport.Longitude")).append(": ");
        sb.append(coordSplit[1]);

        if (isNotBlank(name)) {
            sb.append("<br/>").append(format(name));
        }
        if (isNotBlank(street)) {
            sb.append("<br/>").append(format(street));
        }
        if (isNotBlank(description)) {
            sb.append("<br/>").append(format(description));
        }
        return sb.toString();
    }

    public byte[] generateNextChatHtml(IItemReader c, List<Message> msgs) throws UnsupportedEncodingException {

        if ((!firstHtml && currentMsg == 0) || (currentMsg > 0 && currentMsg == msgs.size()))
            return null;

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, "UTF-8")); //$NON-NLS-1$

        String title = UFEDChatParser.getChatName(c.getMetadata());
        printMessageFileHeader(out, title, c.getName(), null);
        if (currentMsg > 0)
            out.println("<div class=\"linha\"><div class=\"date\">" //$NON-NLS-1$
                    + Messages.getString("WhatsAppReport.ChatContinuation") + "</div></div>"); //$NON-NLS-1$ //$NON-NLS-2$

        String lastDate = null;
        while (currentMsg < msgs.size()) {
            Message m = msgs.get(currentMsg);
            String thisDate = m.getTimeStamp() != null ? dateFormat.format(m.getTimeStamp()) : Messages.getString("ReportGenerator.UnknownDate"); //$NON-NLS-1$
            if (lastDate == null || !lastDate.equals(thisDate)) {
                out.println("<div class=\"linha\"><div class=\"date\">" //$NON-NLS-1$
                        + thisDate + "</div></div>"); //$NON-NLS-1$
                lastDate = thisDate;
            }
            boolean isGroup = c.getMetadata().getValues(ExtraProperties.CONVERSATION_PARTICIPANTS).length > 2; // $NON-NLS-1$
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

    private void printMessage(PrintWriter out, Message message, boolean group, boolean chatDeleted) {

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
            } else {
                out.println("<div class=\"bbl\"><div class=\"aw\"><div class=\"awl\"></div></div><div class=\"incoming from\">"); //$NON-NLS-1$
                isFrom = true;
            }
            name = message.getFrom();
            if (name == null) {
                if (message.isFromMe()) {
                    name = Messages.getString("WhatsAppReport.Owner"); //$NON-NLS-1$
                } else {
                    name = Messages.getString("ReportGenerator.Unknown"); //$NON-NLS-1$
                }
            }
        }

        if (name != null)
            out.println("<span class=\"name\">" + format(name) + "</span><br/>"); //$NON-NLS-1$ //$NON-NLS-2$

        if (message.isForwarded()) {
            out.println("<img class=\"fwd\"><span class=\"fwd\"/>" + Messages.getString("WhatsAppReport.Forwarded") + "</span><br/>");
        }

        if (message.isQuoted()) {
            printQuote(out, message);
        }

        String body = message.getBody();

        for (Attachment attachment : message.getAttachments()) {

            byte[] thumb = null;
            if (attachment.getFile() != null) {
                thumb = attachment.getFile().getThumb();
            }
            String contentType = attachment.getContentType();
            if (contentType != null) {
                contentType = contentType.toLowerCase();
            }

            boolean startedLink = false;
            if (attachment.getFile() != null) {
                String fileHash = attachment.getFile().getHash();
                if (fileHash != null) {
                    out.println("<input class=\"check\" type=\"checkbox\" onclick=app.check(\"hash:" + fileHash + "\",this.checked) name=\""
                            + fileHash + "\" />");
                    out.println("<a onclick=app.open(\"hash:" + fileHash + "\") "); //$NON-NLS-1$ //$NON-NLS-2$
                    String ext = attachment.getFile().getTrueExt();
                    String exportPath = iped.parsers.util.Util.getExportPath(fileHash, ext); // $NON-NLS-1$
                    if (!exportPath.isEmpty())
                        out.println("href=\"" + format(exportPath) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                    out.println(">"); //$NON-NLS-1$
                    startedLink = true;
                }
            }

            if (thumb != null) {
                if (contentType != null && contentType.startsWith("video")) //$NON-NLS-1$
                    out.println(Messages.getString("WhatsAppReport.Video") + ":<br/>"); //$NON-NLS-1$ //$NON-NLS-2$
                out.print("<img class=\"thumb\" src=\""); //$NON-NLS-1$
                out.print("data:image/jpg;base64," + Util.encodeBase64(thumb) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                out.println(" title=\"" + format(getTitle(message)) + "\"/><br/>"); //$NON-NLS-1$ //$NON-NLS-2$

            } else if (contentType != null) {
                if (contentType.startsWith("audio")) { //$NON-NLS-1$
                    out.println("<div class=\"audioImg\" title=\"Audio\"></div>"); //$NON-NLS-1$
                } else if (contentType.startsWith("video")) { //$NON-NLS-1$
                    out.println("<div class=\"videoImg\" title=\"Video\"></div>"); //$NON-NLS-1$
                } else if (contentType.startsWith("image") || contentType.startsWith("photo")) { //$NON-NLS-1$ //$NON-NLS-2$
                    out.println("<div class=\"imageImg\" title=\"Image\"></div>"); //$NON-NLS-1$
                } else if (contentType.contains("contact")) { //$NON-NLS-1$
                    out.println("<div class=\"contactImg\" title=\"Contact\"></div>"); //$NON-NLS-1$
                } else
                    out.println("Attachment:<br/><div class=\"attachImg\" title=\"Doc\"></div>"); //$NON-NLS-1$
            }
            if (startedLink) {
                out.println("</a>");
            }

            if (attachment.getFile() != null) {
                String transcription = attachment.getFile().getTranscription();
                if (transcription != null) {
                    out.print("<br/>");
                    out.print(Messages.getString("ReportGenerator.TranscriptionTitle")); //$NON-NLS-1$
                    String confidence = attachment.getFile().getTranscriptConfidence();
                    if (confidence != null) {
                        float score = Float.valueOf(confidence) * 100;
                        out.print(" [" + (int) score + "%]"); //$NON-NLS-1$ //$NON-NLS-2$
                    }
                    out.println(": <i>"); //$NON-NLS-1$
                    out.println(format(transcription));
                    out.println("</i><br/>"); //$NON-NLS-1$
                }

                if (!attachment.getFile().getChildPornSets().isEmpty()) {
                    out.print("<p><i>" + Messages.getString("WhatsAppReport.FoundInPedoHashDB") + " "
                            + format(attachment.getFile().getChildPornSets().toString()) + "</i></p>");
                }
            } else {
                // TODO: substituir url no texto
                
                String title = attachment.getTitle();
                if (title != null && !StringUtils.contains(body, title))
                    out.println("<br/>" + format(title));

                String url = attachment.getUrl();
                if (url != null && !StringUtils.contains(body, url))
                    out.println("<p class=\"link\">" + format(attachment.getUrl()) + "</p>"); //$NON-NLS-1$
            }
        }

        if (isNotBlank(body)) {
            if (body.startsWith("BEGIN:VCARD")) { //$NON-NLS-1$
                String[] lines = body.split("\n"); //$NON-NLS-1$
                for (String line : lines) {
                    if (line.startsWith("PHOTO;BASE64:")) { //$NON-NLS-1$
                        out.print("PHOTO:<img src=\"data:image;BASE64," + format(line.substring(13)) + "\"/><br/>"); //$NON-NLS-1$ //$NON-NLS-2$
                    } else {
                        out.print(format(line) + "<br/>"); //$NON-NLS-1$
                    }
                }
            } else {
                out.print(format(body)); // $NON-NLS-1$
                if (!message.isSystemMessage())
                    out.print("<br/>");
            }
        } else if (message.isSystemMessage()) {
            out.print("System Message"); //$NON-NLS-1$
        }

        out.println("<span class=\"time\">"); //$NON-NLS-1$
        if (message.isEdited()) {
            out.print(Messages.getString("UfedChatReport.Edited") + " ");
        }
        out.println(timeFormat.format(message.getTimeStamp())); // $NON-NLS-1$

        boolean hasStatus = false;
        if (message.isFromMe() && message.getStatus() != null) {
            switch (message.getStatus()) {
            case Unsent:
                out.print("<div class=\"unsent\"></div>"); //$NON-NLS-1$
                hasStatus = true;
                break;
            case Sent:
                out.print("<div class=\"sent\"></div>"); //$NON-NLS-1$
                hasStatus = true;
                break;
            case Delivered:
                out.print("<div class=\"delivered\"></div>"); //$NON-NLS-1$
                hasStatus = true;
                break;
            case Read:
                out.print("<div class=\"viewed\"></div>"); //$NON-NLS-1$
                hasStatus = true;
                break;
            default:
                break;
            }
        }
        if (message.isEdited() && hasStatus) {
            out.print("<div class=\"edit\"></div>");
        }
        out.println("</span>"); //$NON-NLS-1$

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

    private void printQuote(PrintWriter out, Message message) {
        String quoteClass = "quoteBlock " + (message.isFromMe() ? "quoteTo" : "quoteFrom");
        Message messageQuote = message.getMessageQuote();

        if (messageQuote != null) {
            String body = messageQuote.getBody();
            String quoteClick = "onclick=\"goToAnchorId(" + messageQuote.getId() + ");\"";
            String quoteIcon = "";
            String quoteUser = messageQuote.getFrom();

            String quoteEnd = "</span></div>";
            if (messageQuote.isDeleted()) {
                quoteEnd = "</span><br/><span style=\"float:none\" class=\"recovered\"><div class=\"deletedIcon\"></div><i>"
                        + Messages.getString("WhatsAppReport.QuoteNotFound") + "</i>" + quoteEnd;
            }
            
            StringBuilder msgStr = new StringBuilder();
            StringBuilder attachStr = new StringBuilder();

            for (Attachment attach : messageQuote.getAttachments()) {

                boolean hasThumb = false;
                if (attach.getFile() != null) {
                    byte[] quoteThumb = attach.getFile().getThumb();
                    if (quoteThumb != null) {
                        attachStr.append("<div><img class=\"quoteImg\" src=\"");
                        attachStr.append("data:image/jpg;base64," + Util.encodeBase64(quoteThumb) + "\"/></div>");
                        hasThumb = true;
                    }
                }
                
                String quoteDuration = "";
                if (attach.getFile() != null) {
                    Float duration = attach.getFile().getDuration();
                    if (duration != null && duration > 0) {
                        quoteDuration = formatDuration(duration);
                    }
                }

                String attachContentType = attach.getContentType();
                if (attachContentType != null) {
                    attachContentType = attachContentType.toLowerCase();
                }

                if (attachContentType != null) {
                    if (attachContentType.startsWith("audio")) {
                        quoteIcon = "\uD83C\uDFA7";
                        msgStr.append(quoteIcon + " " + quoteDuration);
 
                    } else if (attachContentType.startsWith("video")) {
                        quoteIcon = "\uD83D\uDCF9";
                        msgStr.append(quoteIcon + " " + quoteDuration);
                        if (!hasThumb) {
                            attachStr.append("<div class=\"videoImg quoteImg\" title=\"Video\"></div>");
                        }

                    } else if (attachContentType.startsWith("image") || attachContentType.startsWith("photo")) {
                        quoteIcon = "\uD83D\uDDBC";
                        out.print(quoteIcon);
                        if (!hasThumb) {
                            attachStr.append("<div class=\"imageImg quoteImg\" title=\"Image\"></div>");
                        }

                    } else if (attachContentType.startsWith("application")) {
                        quoteIcon = "\uD83D\uDCC4";
                        out.print(quoteIcon);
                        if (!hasThumb) {
                            attachStr.append("<div class=\"attachImg quoteImg\" title=\"Doc\"></div>");
                        }
//                    } else if (attachContentType.equals("application/x-ufed-location")) {
//                        out.print("<div class=\"" + quoteClass + "\" " + quoteClick + "><div class=\"quoteTop\"><span class=\"quoteUser\">"
//                                + quoteUser + "</span><br/><span class=\"quoteMsg\">" + formatLocation(messageQuote) + quoteEnd);
//                        if (quoteThumb != null) {
//                            out.print("<div><img class=\"quoteImg\" src=\"");
//                            out.print("data:image/jpg;base64," + Util.encodeBase64(quoteThumb) + "\"/></div>");
//                        }
                    }
                    break;

                } else if (attach.getUrl() != null) {
                    msgStr.append(formatURL(attach, body));
                }
            }

            if (isNotBlank(body)) {
                if (msgStr.length() > 0) {
                    msgStr.append("<br/>");
                }
                msgStr.append(body);
            }

            out.print("<div class=\"" + quoteClass + "\" " + quoteClick + ">" 
                    + "<div class=\"quoteTop\">"
                    + "<span class=\"quoteUser\">" + quoteUser + "</span><br/>" 
                    + "<span class=\"quoteMsg\">" + msgStr + quoteEnd
                    + attachStr + "</div>");

        } else {
            // Reference not found
            out.println("<div class=\"" + quoteClass + "\"><span class=\"quoteUser\">" + Messages.getString("WhatsAppReport.QuoteNotFound")
                    + "</span><br/><span class=\"quoteMsg\">" + format("") + "</span></div>");
        }
    }
    
    private static String formatDuration(float duration) {
        if (duration == 0) {
            return "";
        }
        return "(" + formatMMSS(duration) + ")";
    }
    
    public static String formatMMSS(float duration) {
        return String.format("%02d:%02d", (int) duration / 60, (int) duration % 60);
    }

    private String formatURL(Attachment attachment, String body) {

        StringBuilder sb = new StringBuilder();

        String title = attachment.getTitle();
        if (isNotBlank(title) && !StringUtils.contains(body, title))
            sb.append("<br/>" + format(title));

        String url = attachment.getUrl();
        if (isNotBlank(url) && !StringUtils.contains(body, url))
            sb.append("<p class=\"link\">" + format(attachment.getUrl()) + "</p>"); //$NON-NLS-1$

        return sb.toString();
    }

    private static String getTitle(Message message) {
        for (Attachment attachment : message.getAttachments()) {
            if (attachment.getContentType() != null) {
                String title = StringUtils.substringBefore(attachment.getContentType(), "/");
                if (isNotBlank(title)) {
                    return title;
                }
            }
        }
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
