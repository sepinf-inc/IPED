package iped.parsers.ufed;
import static iped.parsers.ufed.UfedUtils.readUfedMetadata;
import static iped.parsers.ufed.UfedUtils.readUfedMetadataArray;
import static j2html.TagCreator.*;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import iped.parsers.util.Messages;
import iped.parsers.whatsapp.Util;
import iped.utils.EmojiUtil;
import iped.utils.SimpleHTMLEncoder;
import j2html.tags.specialized.DivTag;
import j2html.tags.specialized.TableTag;

/**
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@pf.gov.br>
 */
public class ReportGenerator {

    private int minChatSplitSize;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ");
    private boolean firstHtml = true;
    private int currentMsg = 0;

    public ReportGenerator(int minChatSplitSize) {
        this.minChatSplitSize = minChatSplitSize;
    }

    public int getNextMsgNum() {
        return currentMsg;
    }

    private static final String formatParty(String party) {
        party = StringUtils.remove(party, "@s.whatsapp.net");
        return format(party);
    }

    private static final String format(String text) {
        String ret = SimpleHTMLEncoder.htmlEncode(text);

        // Keep line breaks present in the content, converting to an HTML <br/>
        ret = ret.replaceAll("\n", "<br/>\n");

        return ret;
    }

    private String formatLocation(Message message) {

        String lat = message.getLatitude();
        String lon = message.getLongitude();
        ReferencedLocalization localization = message.getReferencedLocalization();

        if (lat == null && lon == null && localization == null) {
            return StringUtils.EMPTY;
        }

        if ((lat == null || lon == null) && localization != null) {
            String coord = localization.getLocations();
            String[] coordSplit = coord.split(";");
            lat = StringUtils.firstNonBlank(lat, coordSplit[0]);
            lon = StringUtils.firstNonBlank(lat, coordSplit[1]);
        }
        
        lat = StringUtils.defaultString(lat).replace(",", ".");
        lon = StringUtils.defaultString(lon).replace(",", ".");

        DivTag div = div(img(attrs(".location")), b(Messages.getString("UfedChatReport.Location.Title")), br(),
                table(attrs(".contact-table"), //
                        tr(td(Messages.getString("UfedChatReport.Location.Latitude")), td(lat)),
                        tr(td(Messages.getString("UfedChatReport.Location.Longitude")), td(lon))),
                br());

        if (localization != null) {
            String name = localization.getName();
            String description = localization.getDescription();
            String street = localization.getStreet1();
            String houseNumber = localization.getHouseNumber();
            String city = localization.getCity();
            String state = localization.getState();
            String country = localization.getCountry();
            String complement = Arrays.asList(city, state, country).stream().filter(StringUtils::isNotBlank).collect(Collectors.joining(" - "));

            if (isNotBlank(name)) {
                div.with(span(name), br());
            }
            if (isNotBlank(street)) {
                String fullStreet = street;
                if (isNotBlank(houseNumber) && !"0".equals(houseNumber)) {
                    fullStreet += ", " + houseNumber;
                }
                div.with(span(fullStreet), br());
            }
            if (isNotBlank(complement)) {
                div.with(span(complement), br());
            }
            if (isNotBlank(description)) {
                div.with(span(description), br());
            }
        }
        return div.toString();
    }

    private String formatSharedContacts(Message message) {

        if (message.getSharedContacts().isEmpty()) {
            return StringUtils.EMPTY;
        }

        DivTag div = div(b(Messages.getString("UfedChatReport.SharedContact.Title")), br());

        for (MessageContact msgContact : message.getSharedContacts()) {

            ReferencedContact contact = msgContact.getReferencedContact();
            String name = StringUtils.firstNonBlank(msgContact.getName(), contact != null ? contact.getName() : null);

            TableTag table = table(attrs(".contact-table"),
                    tr(td(Messages.getString("UfedChatReport.SharedContact.Name")), td(name)));

            if (contact != null) {

                String userID = contact.getUserID();
                String username = contact.getUsername();
                String phone = contact.getPhoneNumber();

                if (isNotBlank(userID)) {
                    table.with(tr(td(Messages.getString("UfedChatReport.SharedContact.UserID")), td(userID)));
                }

                if (isNotBlank(username)) {
                    table.with(tr(td(Messages.getString("UfedChatReport.SharedContact.Username")), td(username)));
                }

                if (isNotBlank(phone)) {
                    table.with(tr(td(Messages.getString("UfedChatReport.SharedContact.PhoneNumber")), td(phone)));
                }
            }

            div.with(table);
        }
        return div.render();
    }

    public byte[] generateNextChatHtml(Chat chat, List<Message> msgs) throws UnsupportedEncodingException {

        if ((!firstHtml && currentMsg == 0) || (currentMsg > 0 && currentMsg == msgs.size()))
            return null;

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, "UTF-8"));

        String title = getChatTitle(chat);
        String source = readUfedMetadata(chat.getItem(), "Source");

        printMessageFileHeader(out, title, title, chat.getContactPhotoThumb(), source);
        if (currentMsg > 0)
            out.println("<div class=\"linha\"><div class=\"date\">"
                    + Messages.getString("WhatsAppReport.ChatContinuation") + "</div></div>");

        String lastDate = null;
        while (currentMsg < msgs.size()) {
            Message m = msgs.get(currentMsg);
            String thisDate = m.getTimeStamp() != null ? dateFormat.format(m.getTimeStamp()) : Messages.getString("ReportGenerator.UnknownDate");
            if (lastDate == null || !lastDate.equals(thisDate)) {
                out.println("<div class=\"linha\"><div class=\"date\">"
                        + thisDate + "</div></div>");
                lastDate = thisDate;
            }
            printMessage(out, m, chat.isGroup(), chat.getItem().isDeleted());

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

    private String getChatTitle(Chat chat) {
        
        String[] split = chat.getItem().getName().split("_", 3);
        String title = split[split.length - 1];

        String source = readUfedMetadata(chat.getItem(), "Source");
        String phoneOwner =readUfedMetadata(chat.getItem(), "phoneOwner");
        String idProperty =  "ufedId=" + readUfedMetadata(chat.getItem(), "id");
        String nameProperty = readUfedMetadata(chat.getItem(), "Name");
        String chatType = readUfedMetadata(chat.getItem(), "ChatType");
        List<String> parties = readUfedMetadataArray(chat.getItem(), "Participants");

        if (chatType != null) {
            if (chatType.equals(UFEDChatParser.CHATTYPE_ONEONONE)) {
                if (parties != null) {
                    title = ((parties.size() > 1) && (parties.get(0).equals(phoneOwner)) ? parties.get(0) : parties.get(1));
                } else {
                    title = idProperty;
                }

            } else if (chatType.equals(UFEDChatParser.CHATTYPE_GROUP)) {
                title = UFEDChatParser.CHATTYPE_GROUP_TITLE + ": " + (nameProperty != null ? nameProperty : idProperty);

            } else if (chatType.equals(UFEDChatParser.CHATTYPE_BROADCAST)) {
                if (parties != null) {
                    if ((parties.size() == 1) && ((source != null) && (source.equals(UFEDChatParser.WHATSAPP)
                            || source.equals(UFEDChatParser.WHATSAPP_BUSINESS)
                            || source.equals(UFEDChatParser.TELEGRAM)))) {
                        // "Status" chat type (known from behaviour)
                        // NOTE: Apps with this behaviour should be added to this if condition
                        title = UFEDChatParser.CHATTYPE_STATUS_TITLE + ": " + parties.get(0);
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
                    if ((parties != null) && (parties.size() > 0)) {
                        if (parties.size() > 2) {
                            title = UFEDChatParser.CHATTYPE_GROUP_TITLE + ": " + idProperty;
                        } else {
                            title = parties.size() > 1 && parties.get(0).equals(phoneOwner) ? parties.get(1) : parties.get(0);
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

        return title;
    }

    private void printMessage(PrintWriter out, Message message, boolean group, boolean chatDeleted) {

        boolean isFrom = false;
        boolean isTo = false;

        out.println("<div id=\"" + message.getSourceIndex() + "\" class=\"linha\">");
        String name = null;
        if (message.isSystemMessage()) {
            out.println("<div class=\"systemmessage\">");
        } else {
            if (message.isFromMe()) {
                out.println("<div class=\"bbr\"><div class=\"outgoing to\">");
                isTo = true;
            } else {
                out.println("<div class=\"bbl\"><div class=\"aw\"><div class=\"awl\"></div></div><div class=\"incoming from\">");
                isFrom = true;
            }
            name = message.getFrom();
            if (name == null) {
                if (message.isFromMe()) {
                    name = Messages.getString("WhatsAppReport.Owner");
                } else {
                    name = Messages.getString("ReportGenerator.Unknown");
                }
            }
        }

        if (name != null)
            out.println("<span class=\"name\">" + formatParty(name) + "</span><br/>");

        if (message.isForwarded()) {
            String forwardedBy = "";
            String originalSender = message.getOriginalSender();
            if (isNotBlank(originalSender)) {
                forwardedBy = Messages.getString("UfedChatReport.Forwarded.By") + " " + originalSender;
            }
            out.println("<img class=\"fwd\"><span class=\"fwd\"/>" + Messages.getString("UfedChatReport.Forwarded") + " " + forwardedBy + "</span><br/>");
        }

        if (message.isQuoted()) {
            printQuote(out, message);
        }

        out.println(formatLocation(message));
        out.println(formatSharedContacts(message));

        String body = message.getBody();

        for (MessageAttachment attachment : message.getAttachments()) {

            byte[] thumb = null;
            boolean startedLink = false;

            if (attachment.getReferencedFile() != null) {
                thumb = attachment.getReferencedFile().getThumb();
 
                String fileHash = attachment.getReferencedFile().getHash();
                if (fileHash != null) {
                    out.println("<input class=\"check\" type=\"checkbox\" onclick=app.check(\"hash:" + fileHash + "\",this.checked) name=\""
                            + fileHash + "\" />");
                    out.println("<a onclick=app.open(\"hash:" + fileHash + "\") ");
                    String ext = attachment.getReferencedFile().getTrueExt();
                    String exportPath = iped.parsers.util.Util.getExportPath(fileHash, ext); // $NON-NLS-1$
                    if (!exportPath.isEmpty())
                        out.println("href=\"" + format(exportPath) + "\"");
                    out.println(">");
                    startedLink = true;
                }
            }
            
            String contentType = attachment.getContentType();
            if (contentType != null) {
                contentType = contentType.toLowerCase();
            }

            if (thumb != null) {
                if (contentType != null && contentType.startsWith("video"))
                    out.println(Messages.getString("WhatsAppReport.Video") + ":<br/>");
                out.print("<img class=\"thumb\" src=\"");
                out.print("data:image/jpg;base64," + Util.encodeBase64(thumb) + "\"");
                out.println(" title=\"" + format(attachment.getTitle()) + "\"/><br/>");

            } else if (contentType != null) {
                if (contentType.startsWith("audio")) {
                    out.println("<div class=\"audioImg\" title=\"Audio\"></div>");
                } else if (contentType.startsWith("video")) {
                    out.println("<div class=\"videoImg\" title=\"Video\"></div>");
                } else if (contentType.startsWith("image") || contentType.startsWith("photo")) {
                    out.println("<div class=\"imageImg\" title=\"Image\"></div>");
                } else if (contentType.contains("contact")) {
                    out.println("<div class=\"contactImg\" title=\"Contact\"></div>");
                } else
                    out.println("Attachment:<br/><div class=\"attachImg\" title=\"Doc\"></div>");
            }
            if (startedLink) {
                out.println("</a>");
            }

            if (attachment.getReferencedFile() != null) {
                String transcription = attachment.getReferencedFile().getTranscription();
                if (transcription != null) {
                    out.print("<br/>");
                    out.print(Messages.getString("ReportGenerator.TranscriptionTitle"));
                    String confidence = attachment.getReferencedFile().getTranscriptConfidence();
                    if (confidence != null) {
                        float score = Float.valueOf(confidence) * 100;
                        out.print(" [" + (int) score + "%]");
                    }
                    out.println(": <i>");
                    out.println(format(transcription));
                    out.println("</i><br/>");
                }

                if (!attachment.getReferencedFile().getChildPornSets().isEmpty()) {
                    out.print("<p><i>" + Messages.getString("WhatsAppReport.FoundInPedoHashDB") + " "
                            + format(attachment.getReferencedFile().getChildPornSets().toString()) + "</i></p>");
                }
            } else {

                String title = attachment.getTitle();
                if (isNotBlank(title) && !StringUtils.contains(body, title))
                    out.println("<br/>" + format(title));

                String url = attachment.getUrl();
                if (isNotBlank(url) && !StringUtils.contains(body, url) // 
                        && StringUtils.equalsAny(message.getSource(), "Telegram"))
                    out.println("<p class=\"link\">" + format(attachment.getUrl()) + "</p>");
            }
        }

        if (isNotBlank(body)) {
            out.print(format(body));
            if (!message.isSystemMessage())
                out.print("<br/>");
        } else if (message.isSystemMessage()) {
            out.print("System Message");
        }

        out.println("<span class=\"time\">");
        if (message.isEdited()) {
            out.print(Messages.getString("UfedChatReport.Edited") + " ");
        }
        if (message.getTimeStamp() != null) {
            out.println(timeFormat.format(message.getTimeStamp())); // $NON-NLS-1$
        }

        boolean hasStatus = false;
        if (message.isFromMe() && message.getStatus() != null) {
            switch (message.getStatus()) {
            case Unsent:
                out.print("<div class=\"unsent\"></div>");
                hasStatus = true;
                break;
            case Sent:
                out.print("<div class=\"sent\"></div>");
                hasStatus = true;
                break;
            case Delivered:
                out.print("<div class=\"delivered\"></div>");
                hasStatus = true;
                break;
            case Read:
                out.print("<div class=\"viewed\"></div>");
                hasStatus = true;
                break;
            default:
                break;
            }
        }
        if (message.isEdited() && hasStatus) {
            out.print("<div class=\"edit\"></div>");
        }
        out.println("</span>");

        if (chatDeleted || message.getItem().isDeleted()) {
            out.println("<br/><span class=\"recovered\">");
            out.println("<i>" + Messages.getString("UfedChatReport.MessageDeletedRecovered") + "</i>");
            out.println("<div class=\"deletedIcon\"></div>");
            out.println("</span>");
        } else if (message.isTrash()) {
            out.println("<br/><span class=\"recovered\">");
            out.println("<i>" + Messages.getString("UfedChatReport.MessageRecovered") + "</i>");
            out.println("<div class=\"trashIcon\"></div>");
            out.println("</span>");
        }
        if (isTo)
            out.println("</div><div class=\"aw\"><div class=\"awr\"></div></div>");
        if (isFrom)
            out.println("</div>");

        out.println("</div></div>");
    }

    private void printQuote(PrintWriter out, Message message) {
        String quoteClass = "quoteBlock " + (message.isFromMe() ? "quoteTo" : "quoteFrom");
        Message messageQuote = message.getMessageQuote();

        if (messageQuote != null) {
            String body = messageQuote.getBody();
            String quoteClick = "onclick=\"goToAnchorId(" + messageQuote.getSourceIndex() + ");\"";
            String quoteIcon = "";
            String quoteUser = messageQuote.getFrom();
            if (quoteUser == null) {
                if (message.isFromMe()) {
                    quoteUser = Messages.getString("WhatsAppReport.Owner");
                } else {
                    quoteUser = Messages.getString("ReportGenerator.Unknown");
                }
            }

            String quoteEnd = "</span></div>";
            if (messageQuote.getItem().isDeleted()) {
                quoteEnd = "</span><br/><span style=\"float:none\" class=\"recovered\"><div class=\"deletedIcon\"></div><i>"
                        + Messages.getString("UfedChatReport.MessageDeletedRecovered") + "</i>" + quoteEnd;
            }

            StringBuilder msgStr = new StringBuilder();
            StringBuilder attachStr = new StringBuilder();

            for (MessageAttachment attach : messageQuote.getAttachments()) {

                boolean hasThumb = false;
                String quoteDuration = "";
                if (attach.getReferencedFile() != null) {
                    byte[] quoteThumb = attach.getReferencedFile().getThumb();
                    if (quoteThumb != null) {
                        attachStr.append("<div><img class=\"quoteImg\" src=\"");
                        attachStr.append("data:image/jpg;base64," + Util.encodeBase64(quoteThumb) + "\"/></div>");
                        hasThumb = true;
                    }
                    Float duration = attach.getReferencedFile().getDuration();
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

                    } else {
                        quoteIcon = "\uD83D\uDCC4";
                        out.print(quoteIcon);
                        if (!hasThumb) {
                            attachStr.append("<div class=\"attachImg quoteImg\" title=\"Doc\"></div>");
                        }
                    }

                    String title = attach.getTitle();
                    if (isNotBlank(title)) {
                        msgStr.append(format(title));
                    }

                    break;

                } else if (attach.getUrl() != null) {
                    msgStr.append(formatURL(attach, body));
                }
            }

            msgStr.append(formatLocation(messageQuote));
            msgStr.append(formatSharedContacts(messageQuote));

            if (isNotBlank(body)) {
                if (msgStr.length() > 0) {
                    msgStr.append("<br/>");
                }
                msgStr.append(body);
            }

            out.print("<div class=\"" + quoteClass + "\" " + quoteClick + ">" 
                    + "<div class=\"quoteTop\">"
                    + "<span class=\"quoteUser\">" + formatParty(quoteUser) + "</span><br/>"
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

    private String formatURL(MessageAttachment attachment, String body) {

        StringBuilder sb = new StringBuilder();

        String title = attachment.getTitle();
        if (isNotBlank(title) && !StringUtils.contains(body, title))
            sb.append("<br/>" + format(title));

        String url = attachment.getUrl();
        if (isNotBlank(url) && !StringUtils.contains(body, url))
            sb.append("<p class=\"link\">" + format(attachment.getUrl()) + "</p>");

        return sb.toString();
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
                    + "\" width=\"72\" height=\"72\"/>");
        }

        out.println(format(chatName) + "</span>\n"
                + "</div>\n"
                + "<div id=\"conversation\">\n"
                + "<br/><br/><br/>");
    }
    private static void printMessageFileFooter(PrintWriter out) {
        out.println("	<br /><br /><br />\n"
                + "</div>\n"
                + "<div id=\"lastmsg\">&nbsp;</div>\n"
                + "</body>\n"
                + "</html>");
    }

}
