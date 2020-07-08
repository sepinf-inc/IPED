package dpf.mg.udi.gpinf.whatsappextractor;

import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.AUDIO_MESSAGE;
import static dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType.IMAGE_MESSAGE;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookup;
import org.apache.commons.text.lookup.StringLookupFactory;

import dpf.mg.udi.gpinf.vcardparser.VCardParser;
import dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType;
import dpf.sp.gpinf.indexer.parsers.util.Messages;
import dpf.sp.gpinf.indexer.util.SimpleHTMLEncoder;
import iped3.io.IItemBase;
import iped3.search.IItemSearcher;
import iped3.util.BasicProps;
import iped3.util.ExtraProperties;

/**
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@pf.gov.br>
 */
public class ReportGenerator {

    private static final int MAX_CHAT_SIZE = 5000000;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd"); //$NON-NLS-1$
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss XXX"); //$NON-NLS-1$
    private static final String template = Util.readResourceAsString("wachat-html-template.txt");
    private static final String css = Util.readResourceAsString("css/whatsapp.css");
    private static final String js = Util.readResourceAsString("js/whatsapp.js");
    private IItemSearcher searcher;
    private Chat lastChat;
    private int currentMsg = 0;

    public ReportGenerator(IItemSearcher searcher) {
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

    public byte[] generateAccountHtml(WAAccount account) throws UnsupportedEncodingException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, "UTF-8")); //$NON-NLS-1$

        out.println("<!DOCTYPE html>\n" //$NON-NLS-1$
                + "<html>\n" //$NON-NLS-1$
                + "<head>\n" //$NON-NLS-1$
                + " <title>" + account.getId() + "</title>\n" //$NON-NLS-1$ //$NON-NLS-2$
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

    private String format(String s) {
        if (s == null || s.trim().isEmpty())
            return "-"; //$NON-NLS-1$
        else
            return s.trim();

    }

    public byte[] generateNextChatHtml(Chat c, WAContactsDirectory contactsDirectory) {
        if (lastChat != c) {
            lastChat = c;
            currentMsg = 0;
        }
        if (currentMsg == c.getMessages().size())
            return null;

        ByteArrayOutputStream chatBytes = new ByteArrayOutputStream();
        PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(chatBytes, StandardCharsets.UTF_8)); // $NON-NLS-1$

        printMessageFile(printWriter, c.getTitle(), c.getPrintId(), c.getRemote().getAvatar(), () -> {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, StandardCharsets.UTF_8)); // $NON-NLS-1$
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
            out.flush();
            return new String(bout.toByteArray(), StandardCharsets.UTF_8);
        });

        printWriter.flush();
        return chatBytes.toByteArray();
    }

    private void printMessage(PrintWriter out, Message message, boolean group, WAContactsDirectory contactsDirectory) {
        out.println("<div class=\"linha\" id=\"" + message.getId() + "\">"); //$NON-NLS-1$

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
                    out.print(message.getData() + "<br/>"); //$NON-NLS-1$
                }
                break;
            case USER_JOINED_GROUP:
            case USERS_JOINED_GROUP:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(
                        Messages.getString("WhatsAppReport.UserJoinedGroup") + message.getRemoteResource() + "</br>"); //$NON-NLS-1$ //$NON-NLS-2$
                if (message.getData() != null) {
                    out.print(message.getData() + "<br/>"); //$NON-NLS-1$
                }
                break;
            case USER_JOINED_GROUP_FROM_LINK:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("WhatsAppReport.UserJoinedGroupLink")); //$NON-NLS-1$
                if (message.getData() != null) {
                    out.print(message.getData() + "<br/>"); //$NON-NLS-1$
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
                List<IItemBase> result = null;
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
                            out.print(message.getData() + "<br/>"); //$NON-NLS-1$
                        }
                        break;
                    case URL_MESSAGE:
                        out.println("<a href=\"" + message.getUrl() + "\">" + message.getUrl() + "</a><br/>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        if (message.getData() != null) {
                            out.print(message.getData() + "<br/>"); //$NON-NLS-1$
                        }
                        break;
                    case LOCATION_MESSAGE:
                        out.println("<i>" + Messages.getString("WhatsAppReport.LocationMessage") + "</i><br/>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        out.println("Latitude: " + message.getLatitude() + "<br/>"); //$NON-NLS-1$ //$NON-NLS-2$
                        out.println("Longitude: " + message.getLongitude() + "<br/>"); //$NON-NLS-1$ //$NON-NLS-2$
                        if (message.getData() != null) {
                            out.print(message.getData() + "<br/>"); //$NON-NLS-1$
                        }
                        break;
                    case SHARE_LOCATION_MESSAGE:
                        out.println("<i>" + Messages.getString("WhatsAppReport.SharedLocationMessage") + "</i><br/>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        out.println("Latitude: " + message.getLatitude() + "<br/>"); //$NON-NLS-1$ //$NON-NLS-2$
                        out.println("Longitude: " + message.getLongitude() + "<br/>"); //$NON-NLS-1$ //$NON-NLS-2$
                        if (message.getData() != null) {
                            out.print(message.getData() + "<br/>"); //$NON-NLS-1$
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
                                if (onclick != null) {
                                    String onCheck = onclick.replaceFirst(".open", ".check").replace("\")",
                                            "\",this.checked)");
                                    out.println("<input class=\"check\" type=\"checkbox\" onclick=" + onCheck + "/>");
                                }
                                out.println("<a "); //$NON-NLS-1$
                                if (onclick != null)
                                    out.println("onclick=" + onclick); //$NON-NLS-1$
                                out.println(" href=\"" + exportPath + "\">"); //$NON-NLS-1$ //$NON-NLS-2$
                                String source;
                                if (message.getMessageType() == AUDIO_MESSAGE) {
                                    out.println(Messages.getString("WhatsAppReport.AudioMessageTitle")); //$NON-NLS-1$
                                    source = getSourceFileIfExists(result.get(0)).orElse("");
                                    out.println("<div class=\"audioImg iped-audio\" " //$NON-NLS-1$
                                            + " title=\"Audio\" " + "data-src1=\"" + exportPath + "\" " + "data-src2=\"" //$NON-NLS-4$
                                            + source + "\" ></div>");
                                    out.println("</a><br>"); //$NON-NLS-1$
                                } else {
                                    out.println(Messages.getString("WhatsAppReport.VideoMessageTitle")); //$NON-NLS-1$
                                    thumb = message.getThumbData();
                                    if (thumb == null && result != null && !result.isEmpty())
                                        thumb = result.get(0).getThumb();
                                    source = getSourceFileIfExists(result.get(0)).orElse("");
                                    if (thumb != null) {
                                        out.println("<img class=\"thumb iped-video\" src=\""); //$NON-NLS-1$
                                        out.println("data:image/jpg;base64," + Util.encodeBase64(thumb) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                                        out.println(" data-src1=\"" + exportPath + "\"");
                                        out.println(" data-src2=\"" + source + "\"");
                                        out.println(" title=\"" + getTitle(message) + "\"/>"); //$NON-NLS-1$ //$NON-NLS-2$

                                    } else {
                                        out.println("<div class=\"videoImg iped-video\" title=\"Video\""); //$NON-NLS-1$
                                        out.println(" data-src1=\"" + exportPath + "\"");
                                        out.println(" data-src2=\"" + source + "\" ></div>");
                                    }
                                    out.println("</a><br>"); //$NON-NLS-1$
                                }
                                String transcription = result.get(0).getMetadata().get(ExtraProperties.TRANSCRIPT_ATTR);
                                if (transcription != null) {
                                    out.print(Messages.getString("ReportGenerator.TranscriptionTitle")); //$NON-NLS-1$
                                    String confidence = result.get(0).getMetadata()
                                            .get(ExtraProperties.CONFIDENCE_ATTR);
                                    if (confidence != null) {
                                        float score = Float.valueOf(confidence) * 100;
                                        out.print(" [" + (int) score + "%]"); //$NON-NLS-1$ //$NON-NLS-2$
                                    }
                                    out.println(": <i>"); //$NON-NLS-1$
                                    out.println(transcription);
                                    out.println("</i><br/>"); //$NON-NLS-1$
                                }
                            }

                        } else {
                            out.println("<a onclick=" + onclick + ">"); //$NON-NLS-1$
                            if (message.getMessageType() == AUDIO_MESSAGE) {
                                out.println("<div class=\"audioImg\" title=\"Audio\"></div>"); //$NON-NLS-1$
                            } else {
                                out.println("<div class=\"videoImg\" title=\"Video\"></div>"); //$NON-NLS-1$
                            }
                            out.println("</a>"); //$NON-NLS-1$
                        }
                        if (message.getMediaCaption() != null)
                            out.println("<br>" + message.getMediaCaption() + "<br>"); //$NON-NLS-1$ //$NON-NLS-2$
                        break;
                    case IMAGE_MESSAGE:
                    case APP_MESSAGE:
                        String linkParam = null;
                        String exportPath = null;
                        String quote = "";
                        if (message.getMediaHash() != null) {
                            linkParam = "\"sha-256:" + message.getMediaHash() + "\"";
                            result = dpf.sp.gpinf.indexer.parsers.util.Util
                                    .getItems("sha-256:" + message.getMediaHash(), searcher); //$NON-NLS-1$
                            if (result != null && !result.isEmpty()) {
                                exportPath = getReportExportPath(result.get(0), message.getMessageType());
                            }
                        } else if (message.getMediaName() != null && !message.getMediaName().isEmpty()) {
                            String mediaName = message.getMediaName();
                            if (mediaName.contains("/")) { //$NON-NLS-1$
                                mediaName = mediaName.substring(mediaName.lastIndexOf('/') + 1); // $NON-NLS-1$
                            }
                            if (searcher != null)
                                mediaName = searcher.escapeQuery(mediaName);
                            String query = BasicProps.NAME + ":\"" + mediaName + "\" AND " + BasicProps.LENGTH + ":" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                    + message.getMediaSize();
                            linkParam = SimpleHTMLEncoder.htmlEncode("\"" + query.replace("\"", "\\\"") + "\""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                            quote = "\"";
                            if (searcher != null)
                                result = searcher.search(query);
                            if (result != null && !result.isEmpty()) {
                                exportPath = getReportExportPath(result.get(0), message.getMessageType());
                            }
                        }
                        if (linkParam != null) {
                            if (result != null && !result.isEmpty()) {
                                out.println("<input class=\"check\" type=\"checkbox\" onclick=" + quote + "app.check("
                                        + linkParam + ",this.checked)" + quote + " />");
                            }
                            out.println("<a onclick=" + quote + "app.open(" + linkParam + ")" + quote + " "); //$NON-NLS-1$ //$NON-NLS-2$
                        }
                        if (exportPath != null && !exportPath.isEmpty()) {
                            out.println("href=\"" + exportPath + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                        }
                        if (linkParam != null) {
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
                                out.println("<div class=\"imageImg\" title=\"Image\"></div>"); //$NON-NLS-1$
                            } else
                                out.println("Attachment:<br><div class=\"attachImg\" title=\"Doc\"></div>"); //$NON-NLS-1$
                        }
                        if (linkParam != null) {
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

    private Optional<String> getSourceFileIfExists(IItemBase item) {
        if (item.hasFile()) {
            File origFile = item.getFile();
            String path = getRelativePath(origFile);
            if (path != null) {
                path = ajustPath(path);
            }
            return Optional.of(path);
        }
        return Optional.empty();
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

    private static String getReportExportPath(IItemBase item, MessageType type) {
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

    public static String formatMMSS(int duration) {
        return String.format("%02d:%02d", duration / 60, duration % 60); //$NON-NLS-1$
    }

    private static String getTitle(Message message) {
        if (message.getMediaMime() != null && !message.getMediaMime().isEmpty())
            return message.getMediaMime().substring(0, message.getMediaMime().indexOf('/'));
        else
            return "File"; //$NON-NLS-1$
    }

    private void printMessageFile(PrintWriter out, String title, String id, byte[] avatar, Supplier<String> messages) {
        String strAvatar;
        if (avatar == null || avatar.length == 0) {
            strAvatar = Util.getImageResourceAsEmbedded("img/avatar.png");
        } else {
            strAvatar = "data:image/jpg;base64," + Util.encodeBase64(avatar);
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
                }
                return StringLookupFactory.INSTANCE.interpolatorStringLookup().lookup(key);
            }
        });
        out.println(interpolator.replace(template));
    }

}
