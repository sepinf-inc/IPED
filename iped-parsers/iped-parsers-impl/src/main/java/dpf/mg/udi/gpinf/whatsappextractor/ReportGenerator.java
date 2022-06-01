package dpf.mg.udi.gpinf.whatsappextractor;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.function.Supplier;

import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookup;
import org.apache.commons.text.lookup.StringLookupFactory;

import dpf.mg.udi.gpinf.vcardparser.VCardParser;
import dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType;
import dpf.sp.gpinf.indexer.parsers.util.ChildPornHashLookup;
import dpf.sp.gpinf.indexer.parsers.util.Messages;
import dpf.sp.gpinf.indexer.util.SimpleHTMLEncoder;
import iped3.IItemBase;
import iped3.util.ExtraProperties;

/**
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@pf.gov.br>
 */
public class ReportGenerator {

    private static final int MIN_SIZE_TO_SPLIT_CHAT = 5000000;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd"); //$NON-NLS-1$
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss XXX"); //$NON-NLS-1$
    private static final String template = Util.readResourceAsString("wachat-html-template.txt");
    private static final String css = Util.readResourceAsString("css/whatsapp.css");
    private static final String js = Util.readResourceAsString("js/whatsapp.js");
    private boolean firstFragment = true;
    private int currentMsg = 0;

    public ReportGenerator() {
    }

    public int getNextMsgNum() {
        return currentMsg;
    }

    public byte[] genarateContactHtml(WAContact contact) throws UnsupportedEncodingException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, "UTF-8")); //$NON-NLS-1$

        out.println("<!DOCTYPE html>\n" //$NON-NLS-1$
                + "<html>\n" //$NON-NLS-1$
                + "<head>\n" //$NON-NLS-1$
                + " <title>" + format(contact.getId()) + "</title>\n" //$NON-NLS-1$ //$NON-NLS-2$
                + " <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n" //$NON-NLS-1$
                + "</head>\n" //$NON-NLS-1$
                + "<body>\n"); //$NON-NLS-1$

        if (contact.getAvatar() != null)
            out.println("<img src=\"data:image/jpg;base64," + Util.encodeBase64(contact.getAvatar()) //$NON-NLS-1$
                    + "\" width=\"112\"/><br>"); //$NON-NLS-1$
        out.println(Messages.getString("WhatsAppReport.ContactID") + format(contact.getId())); //$NON-NLS-1$
        out.println("<br>" + Messages.getString("WhatsAppReport.DisplayName") + format(contact.getDisplayName())); //$NON-NLS-1$ //$NON-NLS-2$
        out.println("<br>" + Messages.getString("WhatsAppReport.WAName") + format(contact.getWaName())); //$NON-NLS-1$ //$NON-NLS-2$
        out.println("<br>" + Messages.getString("WhatsAppReport.GivenName") + format(contact.getGivenName())); //$NON-NLS-1$ //$NON-NLS-2$
        out.println("<br>" + Messages.getString("WhatsAppReport.NickName") + format(contact.getNickName())); //$NON-NLS-1$ //$NON-NLS-2$
        out.println("<br>" + Messages.getString("WhatsAppReport.SortName") + format(contact.getSortName())); //$NON-NLS-1$ //$NON-NLS-2$
        out.println("<br>" + Messages.getString("WhatsAppReport.Status") + format(contact.getStatus())); //$NON-NLS-1$ //$NON-NLS-2$
        if (contact.isDeleted()) {
            out.println("<br><i>" + Messages.getString("WhatsAppReport.ContactDeleted") + "</i>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }

        out.println("</body>\n</html>"); //$NON-NLS-1$

        out.flush();
        out.close();

        return bout.toByteArray();
    }

    public byte[] generateAccountHtml(WAAccount account) throws UnsupportedEncodingException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, "UTF-8")); //$NON-NLS-1$

        out.println("<!DOCTYPE html>\n" //$NON-NLS-1$
                + "<html>\n" //$NON-NLS-1$
                + "<head>\n" //$NON-NLS-1$
                + " <title>" + format(account.getId()) + "</title>\n" //$NON-NLS-1$ //$NON-NLS-2$
                + " <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n" //$NON-NLS-1$
                + "</head>\n" //$NON-NLS-1$
                + "<body>\n"); //$NON-NLS-1$

        if (account.getAvatar() != null)
            out.println("<img src=\"data:image/jpg;base64," + Util.encodeBase64(account.getAvatar()) //$NON-NLS-1$
                    + "\" width=\"112\"/><br>"); //$NON-NLS-1$
        out.println(Messages.getString("WhatsAppReport.AccountID") + format(account.getId())); //$NON-NLS-1$
        out.println("<br>" + Messages.getString("WhatsAppReport.WAName") + format(account.getWaName())); //$NON-NLS-1$ //$NON-NLS-2$
        out.println("<br>" + Messages.getString("WhatsAppReport.Status") + format(account.getStatus())); //$NON-NLS-1$ //$NON-NLS-2$

        out.println("</body>\n</html>"); //$NON-NLS-1$

        out.flush();
        out.close();

        return bout.toByteArray();
    }

    private static final String format(String s) {
        if (s == null || s.trim().isEmpty())
            return "-"; //$NON-NLS-1$
        else
            return SimpleHTMLEncoder.htmlEncode(s.trim());

    }

    public byte[] generateNextChatHtml(Chat c, WAContactsDirectory contactsDirectory, WAAccount account) {

        if ((!firstFragment && currentMsg == 0) || (currentMsg > 0 && currentMsg == c.getMessages().size()))
            return null;

        firstFragment = false;

        ByteArrayOutputStream chatBytes = new ByteArrayOutputStream();
        PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(chatBytes, StandardCharsets.UTF_8)); // $NON-NLS-1$

        printMessageFile(printWriter, c.getTitle(), c.getPrintId(), c.getRemote().getAvatar(), c.isDeleted(),
                () -> {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, StandardCharsets.UTF_8)); // $NON-NLS-1$
            if (c.getRecoveredFrom() != null) {
                out.println("<div class=\"linha\"><div class=\"date\">" //$NON-NLS-1$
                        + Messages.getString("WhatsAppReport.RecoveredFrom") + " " + format(c.getRecoveredFrom())
                        + "</div></div>");
            }
            if (currentMsg > 0)
                out.println("<div class=\"linha\"><div class=\"date\">" //$NON-NLS-1$
                        + Messages.getString("WhatsAppReport.ChatContinuation") + "</div></div>"); //$NON-NLS-1$ //$NON-NLS-2$

            String lastDate = null;
            while (currentMsg < c.getMessages().size()) {
                Message m = c.getMessages().get(currentMsg);
                String thisDate = dateFormat.format(m.getTimeStamp());
                if (lastDate == null || !lastDate.equals(thisDate)) {
                    out.println("<div class=\"linha\"><div class=\"date\">" //$NON-NLS-1$
                            + thisDate + "</div></div>"); //$NON-NLS-1$
                    lastDate = thisDate;
                }
                printMessage(out, m, c.isGroupChat(), contactsDirectory, account);
                currentMsg += 1;
                if (currentMsg != c.getMessages().size() && bout.size() >= MIN_SIZE_TO_SPLIT_CHAT) {
                    out.println("<div class=\"linha\"><div class=\"date\">" //$NON-NLS-1$
                            + Messages.getString("WhatsAppReport.ChatContinues") + "</div></div>"); //$NON-NLS-1$ //$NON-NLS-2$
                    break;
                }
            }
            out.flush();
            return new String(bout.toByteArray(), StandardCharsets.UTF_8);
        });

        printWriter.flush();
        return chatBytes.toByteArray();
    }

    private synchronized void printMessage(PrintWriter out, Message message, boolean group,
            WAContactsDirectory contactsDirectory,
            WAAccount account) {

        out.println("<div class=\"linha\" id=\"" + message.getUniqueId() + "\">"); //$NON-NLS-1$

        switch (message.getMessageType()) {
            case UNKNOWN_MESSAGE:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println("<i>" + Messages.getString("WhatsAppReport.UnknwonMessage") + "</i>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                break;
            case ENCRIPTION_KEY_CHANGED:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(format(message.getRemoteResource()) + " " //$NON-NLS-1$
                        + Messages.getString("WhatsAppReport.SecurityChanged")); //$NON-NLS-1$
                break;
            case MESSAGES_NOW_ENCRYPTED:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                if (group) {
                    out.println(Messages.getString("WhatsAppReport.GroupNowEncrypted")); //$NON-NLS-1$
                } else {
                    out.println(Messages.getString("WhatsAppReport.ChatNowEncrypted")); //$NON-NLS-1$
                }
                break;
            case MISSED_VIDEO_CALL:
                if (message.isFromMe()) {
                    out.println("<div class=\"specialmessage to\">"); //$NON-NLS-1$
                } else {
                    out.println("<div class=\"specialmessage from\">"); //$NON-NLS-1$
                }
                out.println(Messages.getString("WhatsAppReport.MissedVideoCall")); //$NON-NLS-1$
                break;
            case MISSED_VOICE_CALL:
                if (message.isFromMe()) {
                    out.println("<div class=\"specialmessage to\">"); //$NON-NLS-1$
                } else {
                    out.println("<div class=\"specialmessage from\">"); //$NON-NLS-1$
                }
                out.println(Messages.getString("WhatsAppReport.MissedVoiceCall")); //$NON-NLS-1$
                break;
            case VIDEO_CALL:
                if (message.isFromMe()) {
                    out.println("<div class=\"specialmessage to\">"); //$NON-NLS-1$
                } else {
                    out.println("<div class=\"specialmessage from\">"); //$NON-NLS-1$
                }
                out.println(Messages.getString("WhatsAppReport.VideoCall") + "<br>"); //$NON-NLS-1$ //$NON-NLS-2$
                out.println(
                        Messages.getString("WhatsAppReport.Duration") + ": " + formatMMSS(message.getMediaDuration())); //$NON-NLS-1$ //$NON-NLS-2$
                break;
            case VOICE_CALL:
                if (message.isFromMe()) {
                    out.println("<div class=\"specialmessage to\">"); //$NON-NLS-1$
                } else {
                    out.println("<div class=\"specialmessage from\">"); //$NON-NLS-1$
                }
                out.println(Messages.getString("WhatsAppReport.VoiceCall") + "<br>"); //$NON-NLS-1$ //$NON-NLS-2$
                out.println(
                        Messages.getString("WhatsAppReport.Duration") + ": " + formatMMSS(message.getMediaDuration())); //$NON-NLS-1$ //$NON-NLS-2$
                break;
            case GROUP_CREATED:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(
                        Messages.getString("WhatsAppReport.GroupCreated") + " " + format(message.getRemoteResource()) //$NON-NLS-1$ //$NON-NLS-2$
                        + "</br>"); //$NON-NLS-1$
                if (message.getData() != null && !message.getData().isEmpty()) {
                    out.print(format(message.getData()) + "<br/>"); //$NON-NLS-1$
                }
                break;
            case USER_JOINED_GROUP:
            case USERS_JOINED_GROUP:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(
                        Messages.getString("WhatsAppReport.UserJoinedGroup") + format(message.getRemoteResource()) //$NON-NLS-1$
                                + "</br>"); //$NON-NLS-1$
                if (message.getData() != null) {
                    out.print(format(message.getData()) + "<br/>"); //$NON-NLS-1$
                }
                break;
            case USER_JOINED_GROUP_FROM_LINK:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("WhatsAppReport.UserJoinedGroupLink")); //$NON-NLS-1$
                if (message.getData() != null) {
                    out.print(format(message.getData()) + "<br/>"); //$NON-NLS-1$
                }
                break;
            case USER_LEFT_GROUP:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("WhatsAppReport.UserLeftGroup") + format(message.getRemoteResource()) //$NON-NLS-1$
                        + "</br>"); //$NON-NLS-1$
                break;
            case USER_REMOVED_FROM_GROUP:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(
                        Messages.getString("WhatsAppReport.UserRemovedGroup") + format(message.getRemoteResource()) //$NON-NLS-1$
                                + "</br>"); //$NON-NLS-1$
                break;
            case GROUP_ICON_CHANGED:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("WhatsAppReport.GroupIconChanged")); //$NON-NLS-1$
                break;
            case GROUP_ICON_DELETED:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("WhatsAppReport.GroupIconDeleted")); //$NON-NLS-1$
                break;
            case GROUP_DESCRIPTION_CHANGED:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("WhatsAppReport.GroupDescriptionChanged")); //$NON-NLS-1$
                break;
            case SUBJECT_CHANGED:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("WhatsAppReport.SubjectChanged")); //$NON-NLS-1$
                break;
            case YOU_ADMIN:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("WhatsAppReport.YouAdmin")); //$NON-NLS-1$
                break;

            default:
                IItemBase mediaItem = null;
                byte[] thumb = null;
                String name = null, number = null;
                String query = null;
                String exportPath = null;

                if (message.isFromMe()) {
                    out.println("<div class=\"outgoing to\">"); //$NON-NLS-1$
                    if (account != null && !account.isUnknown()) {
                        name = account.getName();
                        number = message.getLocalResource();
                    }
                } else {
                    out.println("<div class=\"incoming from\">"); //$NON-NLS-1$
                    number = message.getRemoteResource();
                    if (number != null) {
                        WAContact contact = contactsDirectory.getContact(number);
                        name = contact == null ? null : contact.getName();
                    }
                }
                name = name == null ? "" : name.trim();
                number = number == null ? "" : number.trim();
                if (!number.isEmpty()) {
                    if (name.isEmpty()) {
                        name = number;
                    } else if (!number.equals(name) && !number.equals(name + "@s.whatsapp.net")) {
                        name += " (" + number + ")"; //$NON-NLS-1$ //$NON-NLS-2$
                    }
                }
                if (!name.isEmpty()) {
                    out.println("<span style=\"font-family: 'Roboto-Medium'; color: #b4c74b;\">" //$NON-NLS-1$
                            + format(name) + "</span><br/>"); //$NON-NLS-1$
                }

                switch (message.getMessageType()) {
                    case TEXT_MESSAGE:
                        if (message.getData() != null) {
                            out.print(format(message.getData()) + "<br/>"); //$NON-NLS-1$
                        }
                        break;
                    case UNKNOWN_MEDIA_MESSAGE:
                        if (message.getMediaCaption() != null) {
                            out.println("<i>" + Messages.getString("WhatsAppReport.UnknownMediaMessage") + "</i><br/>");
                            out.print(format(message.getMediaCaption()) + "<br/>"); //$NON-NLS-1$
                        }
                        break;
                    case URL_MESSAGE:
                        out.println("<a href=\"" + format(message.getUrl()) + "\">" + format(message.getUrl()) //$NON-NLS-1$ //$NON-NLS-2$
                                + "</a><br/>"); //$NON-NLS-1$
                        if (message.getData() != null) {
                            out.print(format(message.getData()) + "<br/>"); //$NON-NLS-1$
                        }
                        break;
                    case LOCATION_MESSAGE:
                        out.println("<i>" + Messages.getString("WhatsAppReport.LocationMessage") + "</i><br/>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        out.println("Latitude: " + message.getLatitude() + "<br/>"); //$NON-NLS-1$ //$NON-NLS-2$
                        out.println("Longitude: " + message.getLongitude() + "<br/>"); //$NON-NLS-1$ //$NON-NLS-2$
                        if (message.getData() != null) {
                            out.print(format(message.getData()) + "<br/>"); //$NON-NLS-1$
                        }
                        break;
                    case SHARE_LOCATION_MESSAGE:
                        out.println("<i>" + Messages.getString("WhatsAppReport.SharedLocationMessage") + "</i><br/>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        out.println("Latitude: " + message.getLatitude() + "<br/>"); //$NON-NLS-1$ //$NON-NLS-2$
                        out.println("Longitude: " + message.getLongitude() + "<br/>"); //$NON-NLS-1$ //$NON-NLS-2$
                        if (message.getData() != null) {
                            out.print(format(message.getData()) + "<br/>"); //$NON-NLS-1$
                        }
                        break;
                    case CONTACT_MESSAGE:
                        out.println("<b>" + Messages.getString("WhatsAppReport.Contact") + "</b><br/>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        for (String c : message.getVcards()) {
                            VCardParser.printHtmlFromString(out, c);
                        }
                        break;
                    case DELETED_MESSAGE:
                        out.println("<i>" + Messages.getString("WhatsAppReport.MessageDeleted") + "</i><br/>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        break;
                    case DELETED_FROM_SENDER:
                        out.println(
                                "<i>" + Messages.getString("WhatsAppReport.MessageDeletedFromSender") + "</i><br/>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        break;
                    case WAITING_MESSAGE:
                        out.println("<i>" + Messages.getString("WhatsAppReport.WaitingMessage") + "</i><br/>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        break;
                    case AUDIO_MESSAGE:
                    case VIDEO_MESSAGE:
                    case GIF_MESSAGE:
                    case IMAGE_MESSAGE:
                    case APP_MESSAGE:
                    case STICKER_MESSAGE:
                        mediaItem = message.getMediaItem();
                        // query is already html escaped
                        query = message.getMediaQuery();

                        if (mediaItem != null) {
                            exportPath = dpf.sp.gpinf.indexer.parsers.util.Util.getExportPath(mediaItem);
                        }

                        if (query != null) {
                            if (mediaItem != null) {
                                out.println(
                                        "<input class=\"check\" type=\"checkbox\" onclick=\"app.check(" + query
                                                + ",this.checked)\"" + " name=\"" + mediaItem.getHash() + "\"/>");
                            }
                            out.println("<a onclick=\"app.open(" + query + ")\" "); //$NON-NLS-1$ //$NON-NLS-2$

                            if (mediaItem != null) {
                                String href = dpf.sp.gpinf.indexer.parsers.util.Util.getReportHref(mediaItem);
                                out.println("href=\"" + format(href) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                            }
                            out.println(">"); //$NON-NLS-1$
                        }

                        thumb = message.getThumbData();

                        if (mediaItem != null) { // media file found
                            byte[] generatedThumb = mediaItem.getThumb();
                            if (generatedThumb != null)
                                thumb = generatedThumb;

                            if (message.getMessageType() == MessageType.AUDIO_MESSAGE
                                    || message.getMessageType() == MessageType.VIDEO_MESSAGE
                                    || message.getMessageType() == MessageType.GIF_MESSAGE) {
                                String source = dpf.sp.gpinf.indexer.parsers.util.Util.getSourceFileIfExists(mediaItem)
                                        .orElse("");
                                if (message.getMessageType() == MessageType.AUDIO_MESSAGE) {
                                    out.println(Messages.getString("WhatsAppReport.AudioMessageTitle")); //$NON-NLS-1$
                                    out.println("<div class=\"audioImg iped-audio\" " //$NON-NLS-1$
                                            + " title=\"Audio\" " + "data-src1=\"" + format(exportPath) + "\" "
                                            + "data-src2=\"" //$NON-NLS-1$
                                            + format(source) + "\" ></div>");
                                    out.println("</a><br>"); //$NON-NLS-1$
                                } else {
                                    out.println(Messages.getString("WhatsAppReport.VideoMessageTitle")); //$NON-NLS-1$
                                    if (thumb != null) {
                                        out.print("<img class=\"thumb iped-video\" src=\""); //$NON-NLS-1$
                                        out.print("data:image/jpg;base64," + Util.encodeBase64(thumb) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                                        out.print(" data-src1=\"" + format(exportPath) + "\"");
                                        out.print(" data-src2=\"" + format(source) + "\"");
                                        out.println(" title=\"" + getTitle(message) + "\"/>"); //$NON-NLS-1$ //$NON-NLS-2$

                                    } else {
                                        out.println("<div class=\"videoImg iped-video\" title=\"Video\""); //$NON-NLS-1$
                                        out.println(" data-src1=\"" + format(exportPath) + "\"");
                                        out.println(" data-src2=\"" + format(source) + "\" ></div>");
                                    }
                                    out.println("</a><br>"); //$NON-NLS-1$
                                }
                                if (mediaItem.getMetadata().get(ExtraProperties.DOWNLOADED_DATA) != null) {
                                    out.println("<b>" + Messages.getString("ReportGenerator.DownloadedFile") + "</b><br/>");
                                }
                                String transcription = mediaItem.getMetadata().get(ExtraProperties.TRANSCRIPT_ATTR);
                                if (transcription != null) {
                                    out.print(Messages.getString("ReportGenerator.TranscriptionTitle")); //$NON-NLS-1$
                                    String confidence = mediaItem.getMetadata().get(ExtraProperties.CONFIDENCE_ATTR);
                                    if (confidence != null) {
                                        float score = Float.valueOf(confidence) * 100;
                                        out.print(" [" + (int) score + "%]"); //$NON-NLS-1$ //$NON-NLS-2$
                                    }
                                    out.println(": <i>"); //$NON-NLS-1$
                                    out.println(format(transcription));
                                    out.println("</i><br/>"); //$NON-NLS-1$
                                }
                            } else {
                                if (thumb != null) {
                                    if (getTitle(message).equals("video")) //$NON-NLS-1$
                                        out.println(Messages.getString("WhatsAppReport.Video") + ":<br>"); //$NON-NLS-1$ //$NON-NLS-2$
                                    out.print("<img class=\"thumb\" src=\""); //$NON-NLS-1$
                                    out.print("data:image/jpg;base64," + Util.encodeBase64(thumb) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                                    out.println(" title=\"" + getTitle(message) + "\"/>"); //$NON-NLS-1$ //$NON-NLS-2$

                                } else {
                                    if (message.getMessageType() == MessageType.IMAGE_MESSAGE) { // $NON-NLS-1$
                                        out.println("<div class=\"imageImg\" title=\"Image\"></div>"); //$NON-NLS-1$
                                    } else
                                        out.println("Attachment:<br><div class=\"attachImg\" title=\"Doc\"></div>"); //$NON-NLS-1$
                                }
                                out.println("</a><br>"); //$NON-NLS-1$
                                if (mediaItem.getMetadata().get(ExtraProperties.DOWNLOADED_DATA) != null) {
                                    out.println("<b>" + Messages.getString("ReportGenerator.DownloadedFile") + "</b><br/>");
                                }
                            }
                        } else { // mediaItem is null (media file not found)
                            switch (message.getMessageType()) {
                                case AUDIO_MESSAGE:
                                    out.println("<div class=\"audioImg\" title=\"Audio\"></div>"); //$NON-NLS-1$
                                    break;
                                case VIDEO_MESSAGE:
                                case GIF_MESSAGE:
                                    if (thumb != null) {
                                        out.print("<img class=\"thumb iped-video\" src=\""); //$NON-NLS-1$
                                        out.print("data:image/jpg;base64," + Util.encodeBase64(thumb) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                                        out.println(" title=\"" + getTitle(message) + "\"/>"); //$NON-NLS-1$ //$NON-NLS-2$
                                    } else {
                                        out.println("<div class=\"videoImg\" title=\"Video\"></div>"); //$NON-NLS-1$
                                    }
                                    break;
                                case STICKER_MESSAGE:
                                case IMAGE_MESSAGE:
                                case APP_MESSAGE:
                                    if (thumb != null) {
                                        if (getTitle(message).equals("video")) //$NON-NLS-1$
                                            out.println(Messages.getString("WhatsAppReport.Video") + ":<br>"); //$NON-NLS-1$ //$NON-NLS-2$
                                        out.print("<img class=\"thumb\" src=\""); //$NON-NLS-1$
                                        out.print("data:image/jpg;base64," + Util.encodeBase64(thumb) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                                        out.println(" title=\"" + getTitle(message) + "\"/>"); //$NON-NLS-1$ //$NON-NLS-2$

                                    } else if (message.getMessageType() == MessageType.APP_MESSAGE) {
                                        out.println("Attachment:<br><div class=\"attachImg\" title=\"Doc\"></div>"); //$NON-NLS-1$
                                    } else {
                                        out.println("<div class=\"imageImg\" title=\"Image\"></div>"); //$NON-NLS-1$
                                    }
                                    break;
                                default:
                            }

                            if (query != null) {
                                out.println("</a><br>"); //$NON-NLS-1$
                            }
                        }

                        if (message.getMediaCaption() != null)
                            out.println("<br>" + format(message.getMediaCaption()) + "<br>"); //$NON-NLS-1$ //$NON-NLS-2$
                        break;

                    default:
                        break;
                }
                if (mediaItem != null) {
                    message.addChildPornSets(ChildPornHashLookup.lookupHash(mediaItem.getHash()));
                }
                break;
        }
        if (!message.getChildPornSets().isEmpty()) {
            out.print("<p><i>" + Messages.getString("WhatsAppReport.FoundInPedoHashDB") + " "
                    + format(message.getChildPornSets().toString()) + "</i></p>");
        }

        out.println("<span class=\"time\">"); //$NON-NLS-1$

        out.println(timeFormat.format(message.getTimeStamp()) + " &nbsp;"); //$NON-NLS-1$
        if (message.isFromMe() && message.getMessageStatus() != null) {
            switch (message.getMessageStatus()) {
                case MESSAGE_UNSENT:
                    out.println("<div class=\"unsent\"></div>"); //$NON-NLS-1$
                    break;
                case MESSAGE_SENT:
                    out.println("<div class=\"sent\"></div>"); //$NON-NLS-1$
                    break;
                case MESSAGE_DELIVERED:
                    out.println("<div class=\"delivered\"></div>"); //$NON-NLS-1$
                    break;
                case MESSAGE_VIEWED:
                    out.println("<div class=\"viewed\"></div>"); //$NON-NLS-1$
                    break;
            }
        }
        out.println("</span>"); //$NON-NLS-1$
        if (message.isDeleted()) {
            out.println("<br/><span class=\"recovered\">"); //$NON-NLS-1$
            out.println("<i>" + Messages.getString("WhatsAppReport.MessageDeleted") + "</i>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            out.println("<div class=\"deletedIcon\"></div>"); //$NON-NLS-1$
            out.println("</span>"); //$NON-NLS-1$
        }

        if (message.getRecoveredFrom() != null) {
            out.println("<br/><span class=\"recovered\">"); //$NON-NLS-1$
            out.print(Messages.getString("WhatsAppReport.RecoveredFrom") + " " + format(message.getRecoveredFrom()));
            out.println("</span>"); //$NON-NLS-1$

        }

        out.println("</div></div>"); //$NON-NLS-1$
    }

    public static String formatMMSS(int duration) {
        return String.format("%02d:%02d", duration / 60, duration % 60); //$NON-NLS-1$
    }

    private static String getTitle(Message message) {
        if (message.getMediaMime() != null && !message.getMediaMime().isEmpty())
            return format(message.getMediaMime().substring(0, message.getMediaMime().indexOf('/')));
        else
            return "File"; //$NON-NLS-1$
    }

    private void printMessageFile(PrintWriter out, String title, String id, byte[] avatar, boolean isDeleted, Supplier<String> messages) {
        String strAvatar;
        if (avatar == null || avatar.length == 0) {
            strAvatar = Util.getImageResourceAsEmbedded("img/avatar.png");
        } else {
            strAvatar = "data:image/jpg;base64," + Util.encodeBase64(avatar);
        }
        String deletedDiv;
        if (isDeleted) {
            deletedDiv = "<div class=\"linha\"><div class=\"recoveredChat\">"
                    + Messages.getString("WhatsAppReport.RecoveredChat")
                    + "</div></div>";
        } else {
            deletedDiv = "";
        }
        StringSubstitutor interpolator = new StringSubstitutor(new StringLookup() {

            @Override
            public String lookup(String key) {
                switch (key) {
                    case "title":
                        return title;
                    case "id":
                        return id;
                    case "avatar":
                        return strAvatar;
                    case "messages":
                        return messages.get();
                    case "javascript":
                        return js;
                    case "css":
                        return css;
                    case "deleted":
                        return deletedDiv;
                }
                return StringLookupFactory.INSTANCE.interpolatorStringLookup().lookup(key);
            }
        });
        out.println(interpolator.replace(template));
    }
}
