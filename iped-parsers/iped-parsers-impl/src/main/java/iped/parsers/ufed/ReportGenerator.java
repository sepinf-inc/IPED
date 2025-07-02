package iped.parsers.ufed;
import static j2html.TagCreator.attrs;
import static j2html.TagCreator.b;
import static j2html.TagCreator.br;
import static j2html.TagCreator.div;
import static j2html.TagCreator.i;
import static j2html.TagCreator.img;
import static j2html.TagCreator.span;
import static j2html.TagCreator.table;
import static j2html.TagCreator.td;
import static j2html.TagCreator.tr;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import iped.parsers.ufed.handler.ChatHandler;
import iped.parsers.ufed.handler.PartyHandler;
import iped.parsers.ufed.model.Attachment;
import iped.parsers.ufed.model.Chat;
import iped.parsers.ufed.model.Contact;
import iped.parsers.ufed.model.ContactPhoto;
import iped.parsers.ufed.model.InstantMessage;
import iped.parsers.ufed.model.Party;
import iped.parsers.ufed.reference.ReferencedAccountable;
import iped.parsers.ufed.reference.ReferencedLocation;
import iped.parsers.util.Messages;
import iped.parsers.whatsapp.Util;
import iped.utils.EmojiUtil;
import iped.utils.SimpleHTMLEncoder;
import j2html.tags.specialized.DivTag;
import j2html.tags.specialized.TableTag;

public class ReportGenerator {

    private int minChatSplitSize;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
    private boolean firstHtml = true;
    private Chat chat;
    private int currentMsg = 0;


    public ReportGenerator(Chat chat, int minChatSplitSize) {
        this.chat = chat;
        this.minChatSplitSize = minChatSplitSize;
    }

    public int getNextMsgNum() {
        return currentMsg;
    }

    private static final String format(String text) {
        String ret = SimpleHTMLEncoder.htmlEncode(text);

        // Keep line breaks present in the content, converting to an HTML <br/>
        ret = ret.replaceAll("\n", "<br/>\n");

        return ret;
    }

    private String formatLocation(InstantMessage message) {

        if (message.getPosition() == null) {
            return StringUtils.EMPTY;
        }

        String lat = message.getPosition().getLatitude();
        String lon = message.getPosition().getLongitude();
        ReferencedLocation location = message.getPosition().getReferencedLocation();

        if (lat == null && lon == null && location == null) {
            return StringUtils.EMPTY;
        }

        if ((lat == null || lon == null) && location != null) {
            String coord = location.getLocations();
            String[] coordSplit = coord.split(";");
            if (coordSplit.length == 2) {
                lat = coordSplit[0];
                lon = coordSplit[1];
            }
        }

        lat = StringUtils.replace(lat, ",", ".");
        lon = StringUtils.replace(lon, ",", ".");

        DivTag div = div(img(attrs(".location")), b(Messages.getString("UFEDChatParser.Location.Title")), br());

        if (!StringUtils.isAllBlank(lat, lon)) {
            div.with(table(attrs(".contact-table"), //
                    tr(td(Messages.getString("UFEDChatParser.Location.Latitude")), td(lat)), //
                    tr(td(Messages.getString("UFEDChatParser.Location.Longitude")), td(lon))), //
                    br());
        }

        if (location != null) {
            String name = location.getName();
            String description = location.getDescription();
            String street = location.getStreet1();
            String houseNumber = location.getHouseNumber();
            String city = location.getCity();
            String state = location.getState();
            String country = location.getCountry();
            String positionAddress = location.getPositionAddress();
            String complement = Arrays.asList(city, state, country).stream().filter(StringUtils::isNotBlank).collect(Collectors.joining(" - "));

            if (isNotBlank(name)) {
                div.with(span(name), br());
            }
            if (isNotBlank(positionAddress)) {
                div.with(span(i(positionAddress)), br());
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

    private String formatSharedContacts(InstantMessage message) {

        if (message.getSharedContacts().isEmpty()) {
            return StringUtils.EMPTY;
        }

        DivTag div = div(b(Messages.getString("UFEDChatParser.SharedContact.Title")), br());

        for (Contact msgContact : message.getSharedContacts()) {

            Optional<ReferencedAccountable> contact = msgContact.getReferencedContact();
            String name = StringUtils.firstNonBlank(msgContact.getName(),
                    contact.map(ReferencedAccountable::getName).orElse(null),
                    Messages.getString("ReportGenerator.Unknown"));

            TableTag table = table(attrs(".contact-table"),
                    tr(td(Messages.getString("UFEDChatParser.SharedContact.Name")), td(name)));

            if (contact.isPresent()) {

                String userID = contact.get().getUserID();
                String username = contact.get().getUsername();
                String phone = contact.get().getPhoneNumber();

                if (isNotBlank(userID)) {
                    table.with(tr(td(Messages.getString("UFEDChatParser.SharedContact.UserID")), td(userID)));
                }

                if (isNotBlank(username)) {
                    table.with(tr(td(Messages.getString("UFEDChatParser.SharedContact.Username")), td(username)));
                }

                if (isNotBlank(phone)) {
                    table.with(tr(td(Messages.getString("UFEDChatParser.SharedContact.PhoneNumber")), td(phone)));
                }
            }

            div.with(table);
        }
        return div.render();
    }

    public byte[] generateNextChatHtml() throws UnsupportedEncodingException {

        if ((!firstHtml && currentMsg == 0) || (currentMsg > 0 && currentMsg == chat.getMessages().size())) {
            return null;
        }

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, "UTF-8"));

        String title = new ChatHandler(chat, null).getTitle(true, false);
        String source = chat.getSource();

        byte[] firstPhotoData = chat.getPhotos().stream().findFirst().map(ContactPhoto::getImageData).orElse(null);
        printMessageFileHeader(out, title, title, firstPhotoData, source);
        if (currentMsg > 0) {
            out.println("<div class=\"linha\"><div class=\"date\">"
                    + Messages.getString("WhatsAppReport.ChatContinuation") + "</div></div>");
        }

        String lastDate = null;
        while (currentMsg < chat.getMessages().size()) {
            InstantMessage m = chat.getMessages().get(currentMsg);
            String thisDate = m.getTimeStamp() != null ? dateFormat.format(m.getTimeStamp()) : Messages.getString("ReportGenerator.UnknownDate");
            if (lastDate == null || !lastDate.equals(thisDate)) {
                out.println("<div class=\"linha\"><div class=\"date\">"
                        + thisDate + "</div></div>");
                lastDate = thisDate;
            }
            printMessage(out, m, chat.isGroup(), chat.isDeleted());

            if (currentMsg++ != chat.getMessages().size() - 1 && bout.size() >= minChatSplitSize) {
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

    private void printMessage(PrintWriter out, InstantMessage message, boolean group, boolean chatDeleted) {

        boolean isOutgoing = message.isFromPhoneOwner();

        out.println("<div id=\"" + message.getAnchorId() + "\" class=\"linha\">");
        String name = null;
        if (message.isSystemMessage()) {
            out.println("<div class=\"systemmessage\">");
        } else {
            if (isOutgoing) {
                out.println("<div class=\"bbr\"><div class=\"outgoing to\">");
            } else {
                out.println("<div class=\"bbl\"><div class=\"aw\"><div class=\"awl\"></div></div><div class=\"incoming from\">");
            }

            if (message.getFrom().isPresent()) {
                name = new PartyHandler(message.getFrom().get(), message.getSource()).getTitle();
            }
            if (StringUtils.isBlank(name)) {
                if (isOutgoing) {
                    name = Messages.getString("WhatsAppReport.Owner");
                } else {
                    name = Messages.getString("ReportGenerator.Unknown");
                }
            }
        }

        if (name != null) {
            out.println("<span class=\"name\">" + format(name) + "</span><br/>");
        }

        if (message.isReplyMessage()) {
            printQuote(out, message);
        }

        if (message.isForwardedMessage()) {
            String forwardedFrom = "";
            Party originalSender = message.findForwardedMessageOriginalSender(chat);

            if (originalSender != null) {
                forwardedFrom = Messages.getString("UFEDChatParser.Forwarded.From") + " "
                        + new PartyHandler(originalSender, message.getSource()).getTitle();
            }
            out.println("<img class=\"fwd\"><span class=\"fwd\">" + Messages.getString("UFEDChatParser.Forwarded") + " " + forwardedFrom + "</span><br/>");

            InstantMessage forwardedMessage = message.findForwardedMessage(chat);
            if (forwardedMessage != null) {
                printMessageContent(out, forwardedMessage);
            }
        }

        printMessageContent(out, message);

        out.println("<span class=\"time\">");
        if (message.isEdited()) {
            out.print(Messages.getString("UFEDChatParser.Edited") + " ");
        }
        if (message.getTimeStamp() != null) {
            out.println(timeFormat.format(message.getTimeStamp())); // $NON-NLS-1$
        }

        boolean hasStatus = false;
        if (isOutgoing && message.getStatus() != null) {
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

        if (chatDeleted || message.isDeleted()) {
            if ("Trash".equalsIgnoreCase(message.getDeletedState())) {
                out.println("<br/><span class=\"recovered\">");
                out.println("<i>" + Messages.getString("UFEDChatParser.MessageRecovered") + "</i>");
                out.println("<div class=\"trashIcon\"></div>");
                out.println("</span>");
            } else {
                out.println("<br/><span class=\"recovered\">");
                out.println("<i>" + Messages.getString("UFEDChatParser.MessageDeletedRecovered") + "</i>");
                out.println("<div class=\"deletedIcon\"></div>");
                out.println("</span>");
            }
        }
        if (!message.isSystemMessage()) {
            if (isOutgoing) {
                out.println("</div><div class=\"aw\"><div class=\"awr\"></div></div>");
            } else {
                out.println("</div>");
            }
        }

        out.println("</div></div>");
    }

    private void printMessageContent(PrintWriter out, InstantMessage message) {

        out.println(formatLocation(message));
        out.println(formatSharedContacts(message));

        String body = message.getBody();

        for (Attachment attachment : message.getAttachments()) {

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
                    if (!exportPath.isEmpty()) {
                        out.println("href=\"" + format(exportPath) + "\"");
                    }
                    out.println(">");
                    startedLink = true;
                }
            }

            String contentType = attachment.getContentType();
            if (contentType != null) {
                contentType = contentType.toLowerCase();
            }

            if (thumb != null) {
                if (contentType != null && contentType.startsWith("video")) {
                    out.println(Messages.getString("WhatsAppReport.Video") + ":<br/>");
                }
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
                } else {
                    out.println("Attachment:<br/><div class=\"attachImg\" title=\"Doc\"></div>");
                }
            }
            if (startedLink) {
                out.println("</a>");
            }

            if (attachment.getReferencedFile() != null) {
                String transcription = attachment.getReferencedFile().getTranscription();
                if (transcription == null) {
                    transcription = attachment.getTranscript();
                }
                if (StringUtils.isNotBlank(transcription)) {
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
                if (isNotBlank(title) && !StringUtils.contains(body, title)) {
                    out.println("<br/>" + format(title));
                }

                String url = attachment.getURL();
                if (isNotBlank(url) && !StringUtils.contains(body, url) //
                        && StringUtils.equalsAny(message.getSource(), "Telegram")) {
                    out.println("<p class=\"link\">" + format(attachment.getURL()) + "</p>");
                }
            }
        }

        if (isNotBlank(body)) {
            out.print(format(body));
            if (!message.isSystemMessage()) {
                out.print("<br/>");
            }
        } else if (message.isSystemMessage()) {
            out.print("System Message");
        }
    }

    private void printQuote(PrintWriter out, InstantMessage message) {
        String quoteClass = "quoteBlock " + (message.isFromPhoneOwner() ? "quoteTo" : "quoteFrom");

        InstantMessage quotedMessage = message.findReplyMessage(chat);

        if (quotedMessage == null) {
            // Reference not found
            out.println("<div class=\"" + quoteClass + "\"><span class=\"quoteUser\">" + Messages.getString("WhatsAppReport.QuoteNotFound")
                    + "</span><br/><span class=\"quoteMsg\">" + format("") + "</span></div>");
            return;
        }

        String body = quotedMessage.getBody();
        String quoteClick = "onclick=\"goToAnchorId('" + quotedMessage.getAnchorId() + "');\"";
        String quoteIcon = "";
        String quoteUser;
        if (quotedMessage.getFrom().isPresent()) {
            quoteUser = new PartyHandler(quotedMessage.getFrom().get(), message.getSource()).getTitle();
        } else {
            if (message.isFromPhoneOwner()) {
                quoteUser = Messages.getString("WhatsAppReport.Owner");
            } else {
                quoteUser = Messages.getString("ReportGenerator.Unknown");
            }
        }

        String quoteEnd = "</span></div>";
        if (quotedMessage.isDeleted()) {
            quoteEnd = "</span><br/><span style=\"float:none\" class=\"recovered\"><div class=\"deletedIcon\"></div><i>"
                    + Messages.getString("UFEDChatParser.MessageDeletedRecovered") + "</i>" + quoteEnd;
        }

        StringBuilder msgStr = new StringBuilder();
        StringBuilder attachStr = new StringBuilder();

        for (Attachment attach : quotedMessage.getAttachments()) {

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

            } else if (attach.getURL() != null) {
                msgStr.append(formatURL(attach, body));
            }
        }

        msgStr.append(formatLocation(quotedMessage));
        msgStr.append(formatSharedContacts(quotedMessage));

        if (isNotBlank(body)) {
            if (msgStr.length() > 0) {
                msgStr.append("<br/>");
            }
            msgStr.append(body);
        }

        out.print("<div class=\"" + quoteClass + "\" " + quoteClick + ">"
                + "<div class=\"quoteTop\">"
                + "<span class=\"quoteUser\">" + format(quoteUser) + "</span><br/>"
                + "<span class=\"quoteMsg\">" + msgStr + quoteEnd
                + attachStr + "</div>");
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
        if (isNotBlank(title) && !StringUtils.contains(body, title)) {
            sb.append("<br/>" + format(title));
        }

        String url = attachment.getURL();
        if (isNotBlank(url) && !StringUtils.contains(body, url)) {
            sb.append("<p class=\"link\">" + format(attachment.getURL()) + "</p>");
        }

        return sb.toString();
    }

    private static void printMessageFileHeader(PrintWriter out, String chatName, String title, byte[] avatar,
            String source) {

        String topbarClass = " class=\"other\"";
        String backImage = " style=\"background-image:url(" + Util.getImageResourceAsEmbedded("img/other-chat-back.jpg") + ")\"";
        String icon = "message";
        if (source != null) {
            if (source.equals(Chat.SOURCE_TELEGRAM)) {
                topbarClass = " class=\"telegram\"";
                backImage = " style=\"background-image:url(" + Util.getImageResourceAsEmbedded("img/telegramwallpaper.jpg") + ")\"";
                icon = "telegram";
            } else if (source.contains(Chat.SOURCE_WHATSAPP)) {
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
                + "<script type=\"text/javascript\">\n" + Util.readResourceAsString("js/whatsapp.js") + "\n</script>\n"
                + "</body>\n"
                + "</html>");
    }
}
