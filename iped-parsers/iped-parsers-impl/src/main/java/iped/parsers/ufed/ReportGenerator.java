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
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import ezvcard.util.org.apache.commons.codec.binary.Base64;
import iped.parsers.ufed.handler.ChatHandler;
import iped.parsers.ufed.handler.PartyHandler;
import iped.parsers.ufed.model.Attachment;
import iped.parsers.ufed.model.Chat;
import iped.parsers.ufed.model.Contact;
import iped.parsers.ufed.model.ContactPhoto;
import iped.parsers.ufed.model.InstantMessage;
import iped.parsers.ufed.model.Party;
import iped.parsers.ufed.reference.ReferencedAccountable;
import iped.parsers.ufed.reference.ReferencedFile;
import iped.parsers.ufed.reference.ReferencedLocation;
import iped.parsers.util.Messages;
import iped.parsers.whatsapp.Util;
import iped.properties.BasicProps;
import iped.utils.EmojiUtil;
import iped.utils.SimpleHTMLEncoder;
import j2html.tags.specialized.DivTag;
import j2html.tags.specialized.TableTag;

public class ReportGenerator {

    private static final int MIN_MESSAGES_TO_SHOW_MODAL = 500;

    private final int minChatSplitSize;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
    private boolean firstHtml = true;
    private final Chat chat;
    private final boolean isWhatsApp;
    private int currentMsg = 0;


    public ReportGenerator(Chat chat, int minChatSplitSize) {
        this.chat = chat;
        this.isWhatsApp = StringUtils.containsIgnoreCase(chat.getSource(), Chat.SOURCE_WHATSAPP);
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

        DivTag div = div(img(attrs(".location")), b(Messages.getString("UfedChatParser.Location.Title")), br());

        if (!StringUtils.isAllBlank(lat, lon)) {
            div.with(table(attrs(".contact-table"), //
                    tr(td(Messages.getString("UfedChatParser.Location.Latitude")), td(lat)), //
                    tr(td(Messages.getString("UfedChatParser.Location.Longitude")), td(lon))), //
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

        DivTag div = div(b(Messages.getString("UfedChatParser.SharedContact.Title")), br());

        for (Contact msgContact : message.getSharedContacts()) {

            Optional<ReferencedAccountable> contact = msgContact.getReferencedContact();
            String name = StringUtils.firstNonBlank(msgContact.getName(),
                    contact.map(ReferencedAccountable::getName).orElse(null),
                    Messages.getString("ReportGenerator.Unknown"));

            TableTag table = table(attrs(".contact-table"),
                    tr(td(Messages.getString("UfedChatParser.SharedContact.Name")), td(name)));

            if (contact.isPresent()) {

                String userID = contact.get().getUserID();
                String username = contact.get().getUsername();
                String phone = contact.get().getPhoneNumber();

                if (isNotBlank(userID)) {
                    table.with(tr(td(Messages.getString("UfedChatParser.SharedContact.UserID")), td(userID)));
                }

                if (isNotBlank(username)) {
                    table.with(tr(td(Messages.getString("UfedChatParser.SharedContact.Username")), td(username)));
                }

                if (isNotBlank(phone)) {
                    table.with(tr(td(Messages.getString("UfedChatParser.SharedContact.PhoneNumber")), td(phone)));
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

        byte[] firstPhotoData = chat.getPhotos().stream()
                .map(ContactPhoto::getReferencedFile)                          // Optional<ReferencedFile>
                .flatMap(opt -> opt.map(Stream::of).orElseGet(Stream::empty))  // unwrap Optional<ReferencedFile>
                .map(ReferencedFile::getThumb)                                 // extract data
                .filter(Objects::nonNull)                                      // skip nulls
                .findFirst()                                                   // get the first
                .orElse(null);
        boolean printModal = (chat.getMessages().size() - currentMsg) > MIN_MESSAGES_TO_SHOW_MODAL;
        printMessageFileHeader(out, title, title, firstPhotoData, source, printModal);
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
            out.println("<div class=\"systemmessage\"><div>");
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
                forwardedFrom = Messages.getString("UfedChatParser.Forwarded.From") + " "
                        + new PartyHandler(originalSender, message.getSource()).getTitle();
            }
            out.println("<img class=\"fwd\"><span class=\"fwd\">" + Messages.getString("UfedChatParser.Forwarded") + " " + forwardedFrom + "</span><br/>");

            InstantMessage forwardedMessage = message.findForwardedMessage(chat);
            if (forwardedMessage != null) {
                printMessageContent(out, forwardedMessage);
            }
        }

        printMessageContent(out, message);

        if (message.isSystemMessage()) {
            out.print("</div>");
        }
        out.println("<span class=\"time\">");
        if (message.isEdited()) {
            out.print(Messages.getString("UfedChatParser.Edited") + " ");
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
                out.println("<i>" + Messages.getString("UfedChatParser.MessageRecovered") + "</i>");
                out.println("<div class=\"trashIcon\"></div>");
                out.println("</span>");
            } else {
                out.println("<br/><span class=\"recovered\">");
                out.println("<i>" + Messages.getString("UfedChatParser.MessageDeletedRecovered") + "</i>");
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

            boolean startedLink = false;
            byte[] thumb = null;
            String exportPath = null;
            String source = null;
            Float duration = null;

            if (attachment.getReferencedFile() != null) {
                thumb = attachment.getReferencedFile().getThumb();
                exportPath = iped.parsers.util.Util.getExportPath(attachment.getReferencedFile().getItem());
                source = iped.parsers.util.Util.getSourceFileIfExists(attachment.getReferencedFile().getItem()).orElse("");
                duration = attachment.getReferencedFile().getDuration();

                String fileHash = attachment.getReferencedFile().getHash();
                if (fileHash != null) {
                    String query = BasicProps.HASH + ":" + fileHash;
                    String href = iped.parsers.util.Util.getReportHref(attachment.getReferencedFile().getItem());
                    out.println("<input class=\"check\" type=\"checkbox\" onclick=\"app.check('" + query + "',this.checked)\" name=\"" + fileHash + "\" />");
                    out.println("<a onclick=\"app.open('" + query + "')\" href=\"" + format(href) + "\" >");
                    startedLink = true;
                }
            }

            String contentType = attachment.getContentType();
            if (contentType != null) {
                contentType = contentType.toLowerCase();
            }

            if (contentType != null) {
                if (contentType.startsWith("audio")) {
                    out.println(Messages.getString("UfedChatParser.AudioMessageTitle") + "<br>");
                    out.println("<div class=\"audioImg iped-audio\" title=\"Audio\" "
                                + "data-src1=\"" + format(exportPath) + "\" "
                                + "data-src2=\"" + format(source) + "\" >");
                    if (duration != null && duration > 0) {
                        out.print("<span class=\"duration\"> " + formatDuration(duration) + "</span>");
                    }
                    out.print("</div>");

                } else if (contentType.startsWith("video")) {
                    out.println(Messages.getString("UfedChatParser.VideoMessageTitle") + "<br>"); //$NON-NLS-1$
                    if (thumb != null) {
                        out.print("<img class=\"thumb iped-video\" src=\"data:image/jpg;base64,");
                        out.print(Base64.encodeBase64String(thumb));
                        out.println("\"");
                        out.print(" data-src1=\"" + format(exportPath) + "\"");
                        out.print(" data-src2=\"" + format(source) + "\"");
                        out.print(" /><br/>");
                    } else {
                        out.println("<div class=\"videoImg iped-video\" title=\"Video\"");
                        out.print(" data-src1=\"" + format(exportPath) + "\"");
                        out.print(" data-src2=\"" + format(source) + "\"");
                        out.print("></div>");
                    }
                } else if (thumb != null) {
                    out.print("<img class=\"thumb\" src=\"data:image/jpg;base64,");
                    out.print(Base64.encodeBase64String(thumb));
                    out.println("\" /><br/>");
                } else {
                    if (contentType.startsWith("image") || contentType.startsWith("photo")) {
                        out.println("<div class=\"imageImg\" title=\"Image\"></div>");
                    } else if (contentType.contains("contact")) {
                        out.println("<div class=\"contactImg\" title=\"Contact\"></div>");
                    } else if (contentType.equalsIgnoreCase("URL")) {
                        // nothing
                    } else {
                        out.println("Attachment:<br/><div class=\"attachImg\" title=\"Doc\"></div>");
                    }
                }
            } else if (thumb != null) {
                out.print("<img class=\"thumb\" src=\"data:image/jpg;base64,");
                out.print(Base64.encodeBase64String(thumb));
                out.println("\" /><br/>");
            }

            if (startedLink) {
                out.println("</a>");
            }

            if (attachment.getReferencedFile() != null) {

                // render audio transcription
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

                // render child porn sets
                if (!attachment.getReferencedFile().getChildPornSets().isEmpty()) {
                    out.print("<p><i>" + Messages.getString("WhatsAppReport.FoundInPedoHashDB") + " "
                            + format(attachment.getReferencedFile().getChildPornSets().toString()) + "</i></p>");
                }
            }

            out.println(formatTitleAndUrl(attachment, body));
        }

        boolean hasSubject = isNotBlank(message.getSubject());
        
        if (isNotBlank(body) || hasSubject) {
            if (hasSubject)
                out.print(format(message.getSubject()));
            if (isNotBlank(body)) {
                if (hasSubject)
                    out.print("<br/>");

                String formattedBody = format(body);
                if (isWhatsApp) {
                    formattedBody = iped.parsers.whatsapp.ReportGenerator.convertWhatsAppTagsToHTML(formattedBody);
                }
                out.print(formattedBody);
            }
            if (!message.isSystemMessage()) {
                out.print("<br/>");
            }
        } else if (message.isSystemMessage()) {
            out.print(Messages.getString("UfedChatParser.SystemMessage"));
        }
    }

    private void printQuote(PrintWriter out, InstantMessage message) {
        String quoteClass = "quoteBlock " + (message.isFromPhoneOwner() ? "quoteTo" : "quoteFrom");

        InstantMessage quotedMessage = message.findReplyMessage(chat);

        if (quotedMessage == null) {
            // Reference not found
            out.println("<div class=\"" + quoteClass + "\"><span class=\"quoteUser\"><i>" + Messages.getString("WhatsAppReport.QuoteNotFound")
                    + "</i></span></div>");
            return;
        }

        String body = quotedMessage.getBody();
        String quoteClick = "onclick=\"goToAnchorId('" + quotedMessage.getAnchorId() + "');\"";
        String quoteUser = null;
        if (quotedMessage.getFrom().isPresent()) {
            quoteUser = new PartyHandler(quotedMessage.getFrom().get(), message.getSource()).getTitle();
        }
        if (isBlank(quoteUser)) {
            if (quotedMessage.isFromPhoneOwner()) {
                quoteUser = Messages.getString("WhatsAppReport.Owner");
            } else {
                quoteUser = Messages.getString("ReportGenerator.Unknown");
            }
        }

        StringBuilder msgStr = new StringBuilder();
        StringBuilder attachStr = new StringBuilder();

        for (Attachment attach : quotedMessage.getAttachments()) {

            byte[] quoteThumb = null;
            String quoteDuration = "";
            if (attach.getReferencedFile() != null) {
                quoteThumb = attach.getReferencedFile().getThumb();
                Float duration = attach.getReferencedFile().getDuration();
                if (duration != null && duration > 0) {
                    quoteDuration = formatDuration(duration);
                }
            }

            String attachContentType = attach.getContentType();
            if (attachContentType != null) {
                attachContentType = attachContentType.toLowerCase();

                if (attachContentType.startsWith("audio")) {
                    String quoteIcon = "\uD83C\uDFA7";
                    msgStr.append(quoteIcon + " " + quoteDuration);

                } else if (attachContentType.startsWith("video")) {
                    String quoteIcon = "\uD83D\uDCF9";
                    msgStr.append(quoteIcon + " " + quoteDuration);
                    if (quoteThumb != null) {
                        attachStr.append("<div><img class=\"quoteImg\" src=\"data:image/jpg;base64,")
                                .append(Base64.encodeBase64String(quoteThumb))
                                .append("\"/></div>");
                    } else {
                        attachStr.append("<div class=\"videoImg quoteImg\" title=\"Video\"></div>");
                    }

                } else if (quoteThumb != null) {
                    attachStr.append("<div><img class=\"quoteImg\" src=\"data:image/jpg;base64,")
                            .append(Base64.encodeBase64String(quoteThumb))
                            .append("\"/></div>");
                } else {
                    if (attachContentType.startsWith("image") || attachContentType.startsWith("photo")) {
                        attachStr.append("<div class=\"imageImg quoteImg\" title=\"Image\"></div>");
                    } else if (attachContentType.contains("contact")) {
                        attachStr.append("<div class=\"contactImg quoteImg\" title=\"Contact\"></div>");
                    } else if (attachContentType.equalsIgnoreCase("URL")) {
                        // nothing
                    } else {
                        attachStr.append("<div class=\"attachImg quoteImg\" title=\"Doc\"></div>");
                    }
                }
            } else if (quoteThumb != null) {
                attachStr.append("<div><img class=\"quoteImg\" src=\"data:image/jpg;base64,")
                        .append(Base64.encodeBase64String(quoteThumb))
                        .append("\"/></div>");
            }

            msgStr.append(formatTitleAndUrl(attach, body));
        }

        msgStr.append(formatLocation(quotedMessage));
        msgStr.append(formatSharedContacts(quotedMessage));

        if (isNotBlank(body)) {
            if (msgStr.length() > 0) {
                msgStr.append("<br/>");
            }
            String formattedBody = format(body);
            if (isWhatsApp) {
                formattedBody = iped.parsers.whatsapp.ReportGenerator.convertWhatsAppTagsToHTML(formattedBody);
            }
            msgStr.append(formattedBody);
        }

        String quoteEnd = "";
        if (quotedMessage.isDeleted()) {
            quoteEnd = "<br/><span style=\"display:table-cell\" class=\"recovered\"><div class=\"deletedIcon\"></div>"
                    + "<i>" + Messages.getString("UfedChatParser.MessageDeletedRecovered") + "</i></span>";
        }

        out.println("<div class=\"" + quoteClass + "\" " + quoteClick + ">"
                    + "<div class=\"quoteTop\">"
                        + "<span class=\"quoteUser\">" + format(quoteUser) + "</span>");

        if (quotedMessage.isForwardedMessage()) {
            out.println(" <img class=\"fwd\">");
        }

        out.println("<br/>");
        out.println(attachStr);
        out.println("<span class=\"quoteMsg\">" + msgStr + "</span>");
        out.println(quoteEnd);
        out.println("</div></div>");
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

    private String formatTitleAndUrl(Attachment attachment, String body) {

        StringBuilder sb = new StringBuilder();

        String title = attachment.getTitle();
        if (isNotBlank(title) && !StringUtils.contains(body, title)) {
            sb.append("<p>").append(format(title)).append("</p>");
        }

        // when contentType is "URL", fileName may contain relevant information
        String contentType = attachment.getContentType();
        String fileName = attachment.getFilename();
        if ("URL".equalsIgnoreCase(contentType) && isNotBlank(fileName) 
                && !StringUtils.contains(body, fileName) && !StringUtils.contains(title, fileName)) {
            sb.append("<p>").append(format(fileName)).append("</p>");
        }

        String url = attachment.getURL();
        if (isNotBlank(url) && !StringUtils.contains(body, url) && !StringUtils.contains(title, url)  && !StringUtils.contains(fileName, url)
                && (contentType == null || contentType.equalsIgnoreCase("URL"))) {
            sb.append("<p class=\"link\">").append(format(url)).append("</p>");
        }

        return sb.toString();
    }

    private static void printMessageFileHeader(PrintWriter out, String chatName, String title, byte[] avatar,
            String source, boolean printModal) {

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
                + "<body"+ backImage +">");
       if (printModal) {
           out.println("<div id=\"loading-modal\">\n"
                        + "  <div class=\"loader\">" + Messages.getString("UfedChatParser.Loading") + "</div>\n"
                        + "</div>");
       }
       out.println("<div id=\"topbar\"" + topbarClass + ">\n"
                + " <span class=\"left\">"
                + " &nbsp; ");

        if (avatar != null) {
            out.println("<img src=\"data:image/jpg;base64," + Util.encodeBase64(avatar)
                    + "\" width=\"72\" height=\"72\"/>");
        }

        out.println(format(chatName) + "</span>\n"
                + "</div>\n"
                + "<div id=\"conversation\">");
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
