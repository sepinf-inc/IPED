package dpf.mg.udi.gpinf.whatsappextractor;

import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.List;

import dpf.mg.udi.gpinf.vcardparser.VCardParser;
import dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType;
import dpf.sp.gpinf.indexer.parsers.util.Messages;
import dpf.sp.gpinf.indexer.util.SimpleHTMLEncoder;
import iped3.io.ItemBase;
import iped3.search.ItemSearcher;
import iped3.util.BasicProps;

/**
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@dpf.gov.br>
 */
public class ReportGenerator {

    private static final int MAX_CHAT_SIZE = 5000000;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd"); //$NON-NLS-1$
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss XXX"); //$NON-NLS-1$
    private ItemSearcher searcher;
    private Chat lastChat;
    private int currentMsg = 0;

    static final String RSRC_PATH = "../../../../indexador/htm/whatsapp/"; //$NON-NLS-1$

    public ReportGenerator(ItemSearcher searcher) {
        this.searcher = searcher;
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
                + "	<title>" + contact.getId() + "</title>\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "	<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n" //$NON-NLS-1$
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

        out.println("</body>\n</html>"); //$NON-NLS-1$

        out.flush();
        out.close();

        return bout.toByteArray();
    }

    private String format(String s) {
        if (s == null || s.trim().isEmpty())
            return "-"; //$NON-NLS-1$
        else
            return s.trim();

    }

    public byte[] generateNextChatHtml(Chat c, WAContactsDirectory contactsDirectory)
            throws UnsupportedEncodingException {
        if (lastChat != c) {
            lastChat = c;
            currentMsg = 0;
        }
        if (currentMsg == c.getMessages().size())
            return null;

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, "UTF-8")); //$NON-NLS-1$

        printMessageFileHeader(out, c.getTitle(), c.getPrintId(), c.getRemote().getAvatar());
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
            printMessage(out, m, c.isGroupChat(), contactsDirectory);

            if (currentMsg++ != c.getMessages().size() - 1 && bout.size() >= MAX_CHAT_SIZE) {
                out.println("<div class=\"linha\"><div class=\"date\">" //$NON-NLS-1$
                        + Messages.getString("WhatsAppReport.ChatContinues") + "</div></div>"); //$NON-NLS-1$ //$NON-NLS-2$
                break;
            }
        }

        printMessageFileFooter(out);
        out.flush();

        return bout.toByteArray();
    }

    private void printMessage(PrintWriter out, Message message, boolean group, WAContactsDirectory contactsDirectory) {
        out.println("<div class=\"linha\">"); //$NON-NLS-1$

        switch (message.getMessageType()) {
            case UNKNOWN_MESSAGE:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println("<i>" + Messages.getString("WhatsAppReport.UnknwonMessage") + "</i>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                break;
            case ENCRIPTION_KEY_CHANGED:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(message.getRemoteResource() + " " + Messages.getString("WhatsAppReport.SecurityChanged")); //$NON-NLS-1$ //$NON-NLS-2$
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
                out.println(Messages.getString("WhatsAppReport.GroupCreated") + " " + message.getRemoteResource() //$NON-NLS-1$ //$NON-NLS-2$
                        + "</br>"); //$NON-NLS-1$
                if (message.getData() != null && !message.getData().isEmpty()) {
                    out.print(Util.convertEmojis(message.getData()) + "<br/>"); //$NON-NLS-1$
                }
                break;
            case USER_JOINED_GROUP:
            case USERS_JOINED_GROUP:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(
                        Messages.getString("WhatsAppReport.UserJoinedGroup") + message.getRemoteResource() + "</br>"); //$NON-NLS-1$ //$NON-NLS-2$
                if (message.getData() != null) {
                    out.print(Util.convertEmojis(message.getData()) + "<br/>"); //$NON-NLS-1$
                }
                break;
            case USER_JOINED_GROUP_FROM_LINK:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("WhatsAppReport.UserJoinedGroupLink")); //$NON-NLS-1$
                if (message.getData() != null) {
                    out.print(Util.convertEmojis(message.getData()) + "<br/>"); //$NON-NLS-1$
                }
                break;
            case USER_LEFT_GROUP:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("WhatsAppReport.UserLeftGroup") + message.getRemoteResource() + "</br>"); //$NON-NLS-1$ //$NON-NLS-2$
                break;
            case USER_REMOVED_FROM_GROUP:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(
                        Messages.getString("WhatsAppReport.UserRemovedGroup") + message.getRemoteResource() + "</br>"); //$NON-NLS-1$ //$NON-NLS-2$
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
                List<ItemBase> result = null;
                String onclick = null;
                byte[] thumb = null;
                if (message.isFromMe()) {
                    out.println("<div class=\"outgoing to\">"); //$NON-NLS-1$
                } else {
                    out.println("<div class=\"incoming from\">"); //$NON-NLS-1$
                    if (group) {
                        String remote = message.getRemoteResource();
                        if (remote != null) {
                            String number = remote;
                            WAContact contact = contactsDirectory.getContact(number);
                            String name = contact == null ? null : contact.getName();
                            if (name == null)
                                name = number;
                            else
                                name += " (" + number + ")"; //$NON-NLS-1$ //$NON-NLS-2$
                            out.println("<span style=\"font-family: 'Roboto-Medium'; color: #b4c74b;\">" //$NON-NLS-1$
                                    + name + "</span><br/>"); //$NON-NLS-1$
                        }
                    }
                }
                switch (message.getMessageType()) {
                    case TEXT_MESSAGE:
                        if (message.getData() != null) {
                            out.print(Util.convertEmojis(message.getData()) + "<br/>"); //$NON-NLS-1$
                        }
                        break;
                    case URL_MESSAGE:
                        out.println("<a href=\"" + message.getUrl() + "\">" + message.getUrl() + "</a><br/>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        if (message.getData() != null) {
                            out.print(Util.convertEmojis(message.getData()) + "<br/>"); //$NON-NLS-1$
                        }
                        break;
                    case LOCATION_MESSAGE:
                        out.println("<i>" + Messages.getString("WhatsAppReport.LocationMessage") + "</i><br/>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        out.println("Latitude: " + message.getLatitude() + "<br/>"); //$NON-NLS-1$ //$NON-NLS-2$
                        out.println("Longitude: " + message.getLongitude() + "<br/>"); //$NON-NLS-1$ //$NON-NLS-2$
                        if (message.getData() != null) {
                            out.print(Util.convertEmojis(message.getData()) + "<br/>"); //$NON-NLS-1$
                        }
                        break;
                    case SHARED_LOCATION_MESSAGE:
                        out.println("<i>" + Messages.getString("WhatsAppReport.SharedLocationMessage") + "</i><br/>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        out.println("Latitude: " + message.getLatitude() + "<br/>"); //$NON-NLS-1$ //$NON-NLS-2$
                        out.println("Longitude: " + message.getLongitude() + "<br/>"); //$NON-NLS-1$ //$NON-NLS-2$
                        if (message.getData() != null) {
                            out.print(Util.convertEmojis(message.getData()) + "<br/>"); //$NON-NLS-1$
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
                        if (message.getMediaHash() != null) {
                            onclick = "app.open(\"sha-256:" + message.getMediaHash() + "\") "; //$NON-NLS-1$ //$NON-NLS-2$
                            result = dpf.sp.gpinf.indexer.parsers.util.Util
                                    .getItems("sha-256:" + message.getMediaHash(), searcher); //$NON-NLS-1$
                        } else if (message.getMediaName() != null && !message.getMediaName().isEmpty()) {
                            String mediaName = message.getMediaName();
                            if (mediaName.contains("/")) { //$NON-NLS-1$
                                mediaName = mediaName.substring(mediaName.lastIndexOf('/') + 1); // $NON-NLS-1$
                            }
                            if (searcher != null)
                                mediaName = searcher.escapeQuery(mediaName);
                            String query = BasicProps.NAME + ":\"" + mediaName + "\" AND " + BasicProps.LENGTH + ":" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                    + message.getMediaSize();
                            onclick = "\"app.open(" //$NON-NLS-1$
                                    + SimpleHTMLEncoder.htmlEncode("\"" + query.replace("\"", "\\\"") + "\"") + ")\" "; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                            if (searcher != null)
                                result = searcher.search(query);
                        }

                        if (result != null && !result.isEmpty()) {
                            String exportPath = dpf.sp.gpinf.indexer.parsers.util.Util.getExportPath(result.get(0));
                            if (!exportPath.isEmpty()) {
                                out.println("<a "); //$NON-NLS-1$
                                if (onclick != null)
                                    out.println("onclick=" + onclick); //$NON-NLS-1$
                                out.println(" href=\"" + exportPath + "\">"); //$NON-NLS-1$ //$NON-NLS-2$
                                if (message.getMessageType() == AUDIO_MESSAGE) {
                                    out.println(Messages.getString("WhatsAppReport.AudioMessageTitle")); //$NON-NLS-1$
                                    out.println("<img class=\"iped-show\" src=\"" + RSRC_PATH //$NON-NLS-1$
                                            + "img/audio.png\" width=\"100\" height=\"102\" title=\"Audio\"/>"); //$NON-NLS-1$
                                    out.println("</a><br/>"); //$NON-NLS-1$
                                    out.println("<audio class=\"iped-hide\" controls>"); //$NON-NLS-1$
                                    out.println("<source src=\"" + exportPath + "\"/>"); //$NON-NLS-1$ //$NON-NLS-2$
                                    printSourceFileIfExists(result.get(0), out);
                                    out.println("</audio>"); //$NON-NLS-1$
                                } else {
                                    out.println(Messages.getString("WhatsAppReport.VideoMessageTitle")); //$NON-NLS-1$
                                    thumb = message.getThumbData();
                                    if (thumb == null && result != null && !result.isEmpty())
                                        thumb = result.get(0).getThumb();
                                    if (thumb != null) {
                                        out.println("<img class=\"thumb iped-show\" src=\""); //$NON-NLS-1$
                                        out.println("data:image/jpg;base64," + Util.encodeBase64(thumb) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                                        out.println(" title=\"" + getTitle(message) + "\"/>"); //$NON-NLS-1$ //$NON-NLS-2$

                                    } else {
                                        out.println("<img class=\"iped-show\" src=\"" + RSRC_PATH //$NON-NLS-1$
                                                + "img/video.png\" width=\"100\" height=\"102\" title=\"Video\"/>"); //$NON-NLS-1$
                                    }
                                    out.println("</a><br/>"); //$NON-NLS-1$
                                    out.println("<video class=\"iped-hide\" controls>"); //$NON-NLS-1$
                                    out.println("<source src=\"" + exportPath + "\"/>"); //$NON-NLS-1$ //$NON-NLS-2$
                                    printSourceFileIfExists(result.get(0), out);
                                    out.println("</video>"); //$NON-NLS-1$
                                }
                            }
                        } else {
                            if (message.getMessageType() == AUDIO_MESSAGE) {
                                out.println("<img src=\"" + RSRC_PATH //$NON-NLS-1$
                                        + "img/audio.png\" width=\"100\" height=\"102\" title=\"Audio\"/>"); //$NON-NLS-1$
                            } else {
                                out.println("<img src=\"" + RSRC_PATH //$NON-NLS-1$
                                        + "img/video.png\" width=\"100\" height=\"102\" title=\"Video\"/>"); //$NON-NLS-1$
                            }
                        }
                        if (message.getMediaCaption() != null)
                            out.println("<br>" + message.getMediaCaption() + "<br>"); //$NON-NLS-1$ //$NON-NLS-2$
                        break;
                    case IMAGE_MESSAGE:
                    case APP_MESSAGE:
                        boolean inLink = false;
                        if (message.getMediaHash() != null) {
                            out.println("<a onclick=app.open(\"sha-256:" + message.getMediaHash() + "\") "); //$NON-NLS-1$ //$NON-NLS-2$
                            inLink = true;
                            result = dpf.sp.gpinf.indexer.parsers.util.Util
                                    .getItems("sha-256:" + message.getMediaHash(), searcher); //$NON-NLS-1$
                            if (result != null && !result.isEmpty()) {
                                String exportPath = getReportExportPath(result.get(0), message.getMessageType());
                                if (exportPath != null && !exportPath.isEmpty())
                                    out.println("href=\"" + exportPath + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                            }
                            out.println(">"); //$NON-NLS-1$
                        } else if (message.getMediaName() != null && !message.getMediaName().isEmpty()) {
                            String mediaName = message.getMediaName();
                            if (mediaName.contains("/")) { //$NON-NLS-1$
                                mediaName = mediaName.substring(mediaName.lastIndexOf('/') + 1); // $NON-NLS-1$
                            }
                            if (searcher != null)
                                mediaName = searcher.escapeQuery(mediaName);
                            String query = BasicProps.NAME + ":\"" + mediaName + "\" AND " + BasicProps.LENGTH + ":" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                    + message.getMediaSize();
                            out.println("<a onclick=\"app.open(" //$NON-NLS-1$
                                    + SimpleHTMLEncoder.htmlEncode("\"" + query.replace("\"", "\\\"") + "\"") + ")\" "); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                            inLink = true;
                            if (searcher != null)
                                result = searcher.search(query);
                            if (result != null && !result.isEmpty()) {
                                String exportPath = getReportExportPath(result.get(0), message.getMessageType());
                                if (exportPath != null && !exportPath.isEmpty())
                                    out.println("href=\"" + exportPath + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                            }
                            out.println(">"); //$NON-NLS-1$
                        }
                        thumb = message.getThumbData();
                        if (thumb == null && result != null && !result.isEmpty())
                            thumb = result.get(0).getThumb();
                        if (thumb != null) {
                            if (getTitle(message).equals("video")) //$NON-NLS-1$
                                out.println(Messages.getString("WhatsAppReport.Video") + ":<br>"); //$NON-NLS-1$ //$NON-NLS-2$
                            out.println("<img class=\"thumb\" src=\""); //$NON-NLS-1$
                            out.println("data:image/jpg;base64," + Util.encodeBase64(thumb) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                            out.println(" title=\"" + getTitle(message) + "\"/>"); //$NON-NLS-1$ //$NON-NLS-2$

                        } else {
                            if (message.getMessageType() == IMAGE_MESSAGE) { // $NON-NLS-1$
                                out.println("<img src=\"" + RSRC_PATH //$NON-NLS-1$
                                        + "img/image.png\" width=\"100\" height=\"102\" title=\"Image\"/>"); //$NON-NLS-1$
                            } else
                                out.println("Attachment:<br><img src=\"" + RSRC_PATH //$NON-NLS-1$
                                        + "img/attach.png\" width=\"100\" height=\"102\" title=\"Doc\"/>"); //$NON-NLS-1$
                        }
                        if (inLink) {
                            out.println("</a>"); //$NON-NLS-1$
                        }
                        if (message.getMediaCaption() != null)
                            out.println("<br>" + message.getMediaCaption() + "<br>"); //$NON-NLS-1$ //$NON-NLS-2$
                        break;
                    default:
                        break;
                }
                break;
        }

        out.println("<span class=\"time\">"); //$NON-NLS-1$
        out.println(timeFormat.format(message.getTimeStamp()) + " &nbsp;"); //$NON-NLS-1$
        if (message.isFromMe() && message.getMessageStatus() != null) {
            out.println(
                    "<div style=\"display: inline-block;\"><svg id=\"Layer_1\" xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 16 15\" width=\"16\" height=\"15\">"); //$NON-NLS-1$
            switch (message.getMessageStatus()) {
                case MESSAGE_UNSENT:
                    out.println(
                            "<path fill=\"#859479\" d=\"M9.75 7.713H8.244V5.359a.5.5 0 0 0-.5-.5H7.65a.5.5 0 0 0-.5.5v2.947a.5.5 0 0 0 .5.5h.094l.003-.001.003.002h2a.5.5 0 0 0 .5-.5v-.094a.5.5 0 0 0-.5-.5zm0-5.263h-3.5c-1.82 0-3.3 1.48-3.3 3.3v3.5c0 1.82 1.48 3.3 3.3 3.3h3.5c1.82 0 3.3-1.48 3.3-3.3v-3.5c0-1.82-1.48-3.3-3.3-3.3zm2 6.8a2 2 0 0 1-2 2h-3.5a2 2 0 0 1-2-2v-3.5a2 2 0 0 1 2-2h3.5a2 2 0 0 1 2 2v3.5z\"></path>"); //$NON-NLS-1$
                    break;
                case MESSAGE_SENT:
                    out.println(
                            "<path fill=\"#92A58C\" d=\"M10.91 3.316l-.478-.372a.365.365 0 0 0-.51.063L4.566 9.879a.32.32 0 0 1-.484.033L1.891 7.769a.366.366 0 0 0-.515.006l-.423.433a.364.364 0 0 0 .006.514l3.258 3.185c.143.14.361.125.484-.033l6.272-8.048a.365.365 0 0 0-.063-.51z\"></path>"); //$NON-NLS-1$
                    break;
                case MESSAGE_DELIVERED:
                    out.println(
                            "<path fill=\"#92A58C\" d=\"M15.01 3.316l-.478-.372a.365.365 0 0 0-.51.063L8.666 9.879a.32.32 0 0 1-.484.033l-.358-.325a.319.319 0 0 0-.484.032l-.378.483a.418.418 0 0 0 .036.541l1.32 1.266c.143.14.361.125.484-.033l6.272-8.048a.366.366 0 0 0-.064-.512zm-4.1 0l-.478-.372a.365.365 0 0 0-.51.063L4.566 9.879a.32.32 0 0 1-.484.033L1.891 7.769a.366.366 0 0 0-.515.006l-.423.433a.364.364 0 0 0 .006.514l3.258 3.185c.143.14.361.125.484-.033l6.272-8.048a.365.365 0 0 0-.063-.51z\"></path>"); //$NON-NLS-1$
                    break;
                case MESSAGE_VIEWED:
                    out.println(
                            "<path fill=\"#4FC3F7\" d=\"M15.01 3.316l-.478-.372a.365.365 0 0 0-.51.063L8.666 9.879a.32.32 0 0 1-.484.033l-.358-.325a.319.319 0 0 0-.484.032l-.378.483a.418.418 0 0 0 .036.541l1.32 1.266c.143.14.361.125.484-.033l6.272-8.048a.366.366 0 0 0-.064-.512zm-4.1 0l-.478-.372a.365.365 0 0 0-.51.063L4.566 9.879a.32.32 0 0 1-.484.033L1.891 7.769a.366.366 0 0 0-.515.006l-.423.433a.364.364 0 0 0 .006.514l3.258 3.185c.143.14.361.125.484-.033l6.272-8.048a.365.365 0 0 0-.063-.51z\"></path>"); //$NON-NLS-1$
                    break;
            }
            out.println("</svg></div>"); //$NON-NLS-1$
        }
        out.println("</span>"); //$NON-NLS-1$
        out.println("</div></div>"); //$NON-NLS-1$
    }

    private static Path ipedHtmTemplateDir = null;

    static {
        String strOutDir = System.getProperty("IPED_OUTPUT_DIR");
        if (strOutDir != null) {
            ipedHtmTemplateDir = Paths.get(strOutDir, "htm", "whatsapp", "css").toAbsolutePath().normalize();
        }
    }

    private static void printSourceFileIfExists(ItemBase item, PrintWriter out) {
        if (item.hasFile()) {
            File origFile = item.getFile();
            String path = getRelativePath(origFile);
            if (path != null) {
                path = ajustPath(path);
                out.println("<source src=\"" + path + "\"/>"); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }
    }

    private static String ajustPath(String path) {
        path = path.replaceAll("\\\\", "/");
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.length() > 2 && path.charAt(1) == ':') {
            path = "file:///" + path;
        }
        return path;
    }

    private static String getReportExportPath(ItemBase item, MessageType type) {
        String exportPath = dpf.sp.gpinf.indexer.parsers.util.Util.getExportPath(item);
        if (item.hasFile() && type == IMAGE_MESSAGE) {
            File origFile = item.getFile();
            String path = getRelativePath(origFile);
            if (path != null) {
                path = ajustPath(path);
                return "javascript:openIfExists('" + exportPath + "','" + path + "')";
            }
        }
        return exportPath;
    }

    private static String getRelativePath(File file) {
        if (ipedHtmTemplateDir == null) {
            return null;
        }
        if (file == null || !file.exists()) {
            return null;
        }
        try {
            Path filePath = file.toPath().toAbsolutePath().normalize();
            return ipedHtmTemplateDir.relativize(filePath).toString();

        } catch (Exception e) {
            return null;
        }
    }

    private static String formatMMSS(int duration) {
        return String.format("%02d:%02d", duration / 60, duration % 60); //$NON-NLS-1$
    }

    private static String getTitle(Message message) {
        if (message.getMediaMime() != null && !message.getMediaMime().isEmpty())
            return message.getMediaMime().substring(0, message.getMediaMime().indexOf('/'));
        else
            return "File"; //$NON-NLS-1$
    }

    private static void printMessageFileHeader(PrintWriter out, String title, String id, byte[] avatar) {
        out.println("<!DOCTYPE html>\n" //$NON-NLS-1$
                + "<html>\n" //$NON-NLS-1$
                + "<head>\n" //$NON-NLS-1$
                + "	<title>" + id + "</title>\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "	<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n" //$NON-NLS-1$
                + "	<meta name=\"viewport\" content=\"width=device-width\" />\n" //$NON-NLS-1$
                + "     <meta charset=\"UTF-8\" />\n" //$NON-NLS-1$
                + "	<link rel=\"shortcut icon\" href=\"" + RSRC_PATH + "img/favicon.ico\" />\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "	<link rel=\"stylesheet\" type=\"text/css\" href=\"" + RSRC_PATH + "css/whatsapp.css\" />\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "<script>\n" //$NON-NLS-1$
                + "var css = document.createElement(\"style\");\n" //$NON-NLS-1$
                + "css.type = \"text/css\";\n" //$NON-NLS-1$
                + "var inHtml = \"\";\n" //$NON-NLS-1$
                + "if (navigator.userAgent.search(\"JavaFX\") >= 0) {\n" //$NON-NLS-1$
                + "  inHtml = \".iped-hide { display: none; }\";\n" //$NON-NLS-1$
                + "  inHtml += \".iped-show { display: block; }\";\n" //$NON-NLS-1$
                + "} else {\n" //$NON-NLS-1$
                + "  inHtml = \".iped-hide { display: block; }\";\n" //$NON-NLS-1$
                + "  inHtml += \".iped-show { display: none; }\";\n" //$NON-NLS-1$
                + "}\n" //$NON-NLS-1$
                + "css.innerHTML = inHtml;\n" //$NON-NLS-1$
                + "document.head.appendChild(css);\n" //$NON-NLS-1$
                + "function openIfExists(url1, url2){\r\n" + "    var img1 = new Image();\r\n"
                + "    img1.onload = () => window.location = url1;\r\n"
                + "    img1.onerror = () => window.location = url2;\r\n" + "    img1.src = url1;\r\n" + "}\r\n"
                + "</script>\n" //$NON-NLS-1$
                + VCardParser.HTML_STYLE + "</head>\n" //$NON-NLS-1$
                + "<body>\n" //$NON-NLS-1$
                + "<div id=\"topbar\">\n" //$NON-NLS-1$
                + "	<span class=\"left\">" //$NON-NLS-1$
                + " &nbsp; "); //$NON-NLS-1$
        if (avatar != null)
            out.println("<img src=\"data:image/jpg;base64," + Util.encodeBase64(avatar) //$NON-NLS-1$
                    + "\" width=\"40\" height=\"40\"/>"); //$NON-NLS-1$
        out.println(Util.convertEmojis(title) + "</span>\n" //$NON-NLS-1$
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
