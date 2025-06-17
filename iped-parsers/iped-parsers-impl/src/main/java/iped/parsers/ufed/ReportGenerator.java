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
import iped.utils.EmojiUtil;
import iped.utils.SimpleHTMLEncoder;

/**
 * @author Fabio Melo Pfeifer <pfeifer.fmp@pf.gov.br>
 */
public class ReportGenerator {

    private int minChatSplitSize = 6000000;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ");
    private boolean firstHtml = true;
    private int currentMsg = 0;

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

        if ((!firstHtml && currentMsg == 0) || (currentMsg > 0 && currentMsg == msgs.size())) {
            return null;
        }

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, "UTF-8"));

        String[] split = c.getName().split("_", 3);
        String title = split[split.length - 1];

        String source = c.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Source");
        String phoneOwner = c.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "phoneOwner");
        String idProperty = "ufedId=" + c.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "id");
        String nameProperty = c.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Name");
        String chatType = c.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "ChatType");
        String[] parties = c.getMetadata().getValues(ExtraProperties.UFED_META_PREFIX + "Participants");

        if (chatType != null) {
            if (chatType.equals(UFEDChatParser.CHATTYPE_ONEONONE)) {
                if (parties != null) {
                    title = ((parties.length > 1) && (parties[0].equals(phoneOwner)) ? parties[1] : parties[0]);
                } else {
                    title = idProperty;
                }

            } else if (chatType.equals(UFEDChatParser.CHATTYPE_GROUP)) {
                title = UFEDChatParser.CHATTYPE_GROUP_TITLE + ": " + (nameProperty != null ? nameProperty : idProperty);

            } else if (chatType.equals(UFEDChatParser.CHATTYPE_BROADCAST)) {
                if (parties != null) {
                    if ((parties.length == 1) && ((source != null) && (source.equals(UFEDChatParser.WHATSAPP)
                            || source.equals(UFEDChatParser.WHATSAPP_BUSINESS)
                            || source.equals(UFEDChatParser.TELEGRAM)))) {
                        // "Status" chat type (known from behaviour)
                        // NOTE: Apps with this behaviour should be added to this if condition
                        title = UFEDChatParser.CHATTYPE_STATUS_TITLE + ": " + parties[0];
                    } else {
                        title = UFEDChatParser.CHATTYPE_BROADCAST_TITLE + ": "
                                + (nameProperty != null ? nameProperty : idProperty);
                    }

                } else {
                    title = UFEDChatParser.CHATTYPE_BROADCAST_TITLE + ": "
                            + (nameProperty != null ? nameProperty : idProperty);
                }

            } else if (chatType.equals(UFEDChatParser.CHATTYPE_UNKNOWN)) {
                if ((source != null) && (source.equals(UFEDChatParser.WHATSAPP)
                        || source.equals(UFEDChatParser.WHATSAPP_BUSINESS) || source.equals(UFEDChatParser.TELEGRAM))) {
                    // "Unknown" chat type regarding apps for which there are specific chat types
                    // NOTE: Apps with similar behaviour should be added to this if condition
                    title = UFEDChatParser.CHATTYPE_UNKNOWN_TITLE + ": " + idProperty;

                } else {
                    // "Unknown" chat type regarding apps for which there aren't specific chat types
                    // Communication type is derived from the number of participants
                    if ((parties != null) && (parties.length > 0)) {
                        if (parties.length > 2) {
                            title = UFEDChatParser.CHATTYPE_GROUP_TITLE + ": " + idProperty;
                        } else {
                            title = parties.length > 1 && parties[0].equals(phoneOwner) ? parties[1] : parties[0];
                        }

                    } else {
                        title = UFEDChatParser.CHATTYPE_UNKNOWN_TITLE + ": " + idProperty;
                    }
                }
            } else {
                title = chatType + ": " + idProperty;
            }

        } else {
            title = idProperty;
        }

        printMessageFileHeader(out, title, c.getName(), null, source);
        if (currentMsg > 0)
            out.println("<div class=\"linha\"><div class=\"date\">"
                    + Messages.getString("WhatsAppReport.ChatContinuation") + "</div></div>");

        String lastDate = null;
        while (currentMsg < msgs.size()) {
            UfedMessage m = msgs.get(currentMsg);
            String thisDate = m.getTimeStamp() != null ? dateFormat.format(m.getTimeStamp())
                    : Messages.getString("ReportGenerator.UnknownDate");
            if (lastDate == null || !lastDate.equals(thisDate)) {
                out.println("<div class=\"linha\"><div class=\"date\">" + thisDate + "</div></div>");
                lastDate = thisDate;
            }
            boolean isGroup = c.getMetadata().getValues(ExtraProperties.UFED_META_PREFIX + "Participants").length > 2;
            printMessage(out, m, isGroup, c.isDeleted());

            if (currentMsg++ != msgs.size() - 1 && bout.size() >= minChatSplitSize) {
                out.println("<div class=\"linha\"><div class=\"date\">"
                        + Messages.getString("WhatsAppReport.ChatContinues") + "</div></div>");
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

        out.println("<div id=\"" + message.getId() + "\" class=\"linha\">");
        String name = null;
        if (message.isSystemMessage()) {
            out.println("<div class=\"systemmessage\">");
        } else {
            if (message.isFromMe()) {
                out.println("<div class=\"bbr\"><div class=\"outgoing to\">");
                isTo = true;
                name = message.getLocalResource();
            } else {
                out.println(
                        "<div class=\"bbl\"><div class=\"aw\"><div class=\"awl\"></div></div><div class=\"incoming from\">");
                isFrom = true;
                name = message.getRemoteResource();
            }
            if (name == null)
                name = Messages.getString("ReportGenerator.Unknown");
        }

        if (name != null)
            out.println("<span style=\"font-family: Arial; color: #b4c74b;\">" + format(name) + "</span><br/>");

        if (message.getData() != null && !message.getData().trim().isEmpty()) {
            if (message.getData().startsWith("BEGIN:VCARD")) {
                String[] lines = message.getData().split("\n");
                for (String line : lines) {
                    if (line.startsWith("PHOTO;BASE64:")) {
                        out.print("PHOTO:<img src=\"data:image;BASE64," + format(line.substring(13)) + "\"/><br/>");
                    } else {
                        out.print(format(line) + "<br/>");
                    }
                }
            } else {
                out.print(format(message.getData())); // $NON-NLS-1$
                if (!message.isSystemMessage())
                    out.print("<br/>");
            }
        } else if (message.isSystemMessage()) {
            out.print("System Message");
        }
        if (message.getMediaHash() != null || message.getThumbData() != null || message.getMediaName() != null) {
            if (message.getMediaHash() != null) {
                out.println("<input class=\"check\" type=\"checkbox\" onclick=app.check(\"hash:"
                        + message.getMediaHash() + "\",this.checked) name=\"" + message.getMediaHash() + "\" />");
                out.println("<a onclick=app.open(\"hash:" + message.getMediaHash() + "\") ");
                String ext = message.getMediaTrueExt();
                String exportPath = iped.parsers.util.Util.getExportPath(message.getMediaHash(), ext); // $NON-NLS-1$
                if (!exportPath.isEmpty())
                    out.println("href=\"" + format(exportPath) + "\"");
                out.println(">");
            }
            byte[] thumb = message.getThumbData();
            if (thumb != null) {
                if (getTitle(message).equals("video"))
                    out.println(Messages.getString("WhatsAppReport.Video") + ":<br>");
                out.print("<img class=\"thumb\" src=\"");
                out.print("data:image/jpg;base64," + Util.encodeBase64(thumb) + "\"");
                out.println(" title=\"" + format(getTitle(message)) + "\"/><br/>");

            } else if (message.getMediaMime() != null) {
                if (message.getMediaMime().startsWith("audio")) {
                    out.println("<div class=\"audioImg\" title=\"Audio\"></div>");
                } else if (message.getMediaMime().startsWith("video")) {
                    out.println("<div class=\"videoImg\" title=\"Video\"></div>");
                } else if (message.getMediaMime().startsWith("image")) {
                    out.println("<div class=\"imageImg\" title=\"Image\"></div>");
                } else if (message.getMediaMime().contains("contact")) {
                    out.println("<div class=\"contactImg\" title=\"Contact\"></div>");
                } else
                    out.println("Attachment:<br><div class=\"attachImg\" title=\"Doc\"></div>");
            }
            out.println("</a>");
        }
        if (message.getMediaCaption() != null)
            out.println("<br>" + format(message.getMediaCaption()));

        String transcription = message.getTranscription();
        if (transcription != null) {
            out.print("<br>");
            out.print(Messages.getString("ReportGenerator.TranscriptionTitle"));
            String confidence = message.getTranscriptConfidence();
            if (confidence != null) {
                float score = Float.valueOf(confidence) * 100;
                out.print(" [" + (int) score + "%]");
            }
            out.println(": <i>");
            out.println(format(transcription));
            out.println("</i><br/>");
        }

        if (!message.getChildPornSets().isEmpty()) {
            out.print("<p><i>" + Messages.getString("WhatsAppReport.FoundInPedoHashDB") + " "
                    + format(message.getChildPornSets().toString()) + "</i></p>");
        }
        if (message.getTimeStamp() != null) {
            out.println("<span class=\"time\">");
            out.println(timeFormat.format(message.getTimeStamp())); // $NON-NLS-1$
            out.println("</span>");
        }
        if (chatDeleted || message.isDeleted()) {
            out.println("<br/><span class=\"recovered\">");
            out.println("<i>" + Messages.getString("WhatsAppReport.MessageDeletedRecovered") + "</i>");
            out.println("<div class=\"deletedIcon\"></div>");
            out.println("</span>");
        }
        if (isTo)
            out.println("</div><div class=\"aw\"><div class=\"awr\"></div></div>");
        if (isFrom)
            out.println("</div>");

        out.println("</div></div>");
    }

    private static String getTitle(Message message) {
        if (message.getMediaMime() != null && !message.getMediaMime().isEmpty())
            return message.getMediaMime().substring(0, message.getMediaMime().indexOf('/'));
        else
            return "File";
    }

    private static void printMessageFileHeader(PrintWriter out, String chatName, String title, byte[] avatar,
            String source) {

        String topbarClass = " class=\"other\"";
        String backImage = " style=\"background-image:url(" + Util.getImageResourceAsEmbedded("img/other-chat-back.jpg") + ")\"";
        String icon = "message";
        if (source != null) {
            if (source.equals(UFEDChatParser.TELEGRAM)) {
                topbarClass = " class=\"telegram\"";
                backImage = " style=\"background-image:url(" + Util.getImageResourceAsEmbedded("img/telegramwallpaper.jpg") + ")\"";
                icon = "telegram";
            } else if (source.contains(UFEDChatParser.WHATSAPP)) {
                topbarClass = "";
                backImage = "";
                icon = "whatsapp";
            }
        }

        out.println("<!DOCTYPE html>\n" 
                + "<html>\n"
                + "<head>\n"
                + "<title>" + format(title) + "</title>\n"
                + "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n"
                + "<meta name=\"viewport\" content=\"width=device-width\" />\n"
                + "<meta charset=\"UTF-8\" />\n"
                + "<link rel=\"icon\" href=\"" + Util.getImageResourceAsEmbedded("img/" + icon + ".png") + "\">\n"
                + "<style>\n" + Util.readResourceAsString("css/whatsapp.css")
                + "\n</style>\n" + "<style>.check {vertical-align: top;}</style>" + "</head>\n"
                + "<body"+ backImage +">\n"
                + "<div id=\"topbar\"" + topbarClass + ">\n"
                + " <span class=\"left\">"
                + " &nbsp; ");

        if (avatar != null) {
            out.println("<img src=\"data:image/jpg;base64," + Util.encodeBase64(avatar) 
                    + "\" width=\"40\" height=\"40\"/>");
        }

        out.println(format(chatName) + "</span>\n"
                + "</div>\n"
                + "<div id=\"conversation\">\n"
                + "<br/><br/><br/>");
    }

    private static void printMessageFileFooter(PrintWriter out) {
        out.println("   <br /><br /><br />\n"
                + "</div>\n"
                + "<div id=\"lastmsg\">&nbsp;</div>\n"
                + "</body>\n"
                + "</html>");
    }
}
