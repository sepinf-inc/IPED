package iped.parsers.threema;

import iped.data.IItemReader;
import iped.parsers.util.ChildPornHashLookup;
import iped.parsers.util.Messages;
import iped.properties.ExtraProperties;
import iped.utils.EmojiUtil;
import iped.utils.SimpleHTMLEncoder;
import iped.parsers.threema.Message.MessageType;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookupFactory;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.function.Supplier;

/**
 *
 * @author André Rodrigues Costa <andre.arc@pf.gov.br>
 */
public class ReportGenerator {

    private int minChatSplitSize = 6000000;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd"); //$NON-NLS-1$
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss XXX"); //$NON-NLS-1$
    private static final String template = Util.readResourceAsString("threema-html-template.txt");
    private static final String css = Util.readResourceAsString("css/threema.css");
    private static final String js = Util.readResourceAsString("js/threema.js");
    private boolean firstFragment = true;
    private int currentMsg = 0;
    private static final String locationIcon = "<img class=\"location\"/>";

    public ReportGenerator() {
    }

    public int getNextMsgNum() {
        return currentMsg;
    }

    public void setMinChatSplitSize(int minChatSplitSize) {
        this.minChatSplitSize = minChatSplitSize;
    }

    private static String format(String s) {
        if (s == null || s.trim().isEmpty())
            return "-"; //$NON-NLS-1$
        else
            return SimpleHTMLEncoder.htmlEncode(s.trim());

    }

    public byte[] generateNextChatHtml(Chat c, ThreemaAccount account) {

        if ((!firstFragment && currentMsg == 0) || (currentMsg > 0 && currentMsg == c.getMessages().size()))
            return null;

        firstFragment = false;

        ByteArrayOutputStream chatBytes = new ByteArrayOutputStream();
        PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(chatBytes, StandardCharsets.UTF_8)); // $NON-NLS-1$

        printMessageFile(printWriter, c.getTitle(), c.getSubject(), c.getImage(),
                () -> {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, StandardCharsets.UTF_8)); // $NON-NLS-1$

            if (currentMsg > 0)
                out.println("<div class=\"linha\"><div class=\"date\">" //$NON-NLS-1$
                        + Messages.getString("ThreemaReport.ChatContinuation") + "</div></div>"); //$NON-NLS-1$ //$NON-NLS-2$

            String lastDate = null;
            while (currentMsg < c.getMessages().size()) {
                Message m = c.getMessages().get(currentMsg);
                String thisDate = dateFormat.format(m.getTimeStamp());
                if (lastDate == null || !lastDate.equals(thisDate)) {
                    out.println("<div class=\"linha\"><div class=\"date\">" //$NON-NLS-1$
                            + thisDate + "</div></div>"); //$NON-NLS-1$
                    lastDate = thisDate;
                }
                printMessage(out, m, account);
                currentMsg += 1;
                if (currentMsg != c.getMessages().size() && bout.size() >= minChatSplitSize) {
                    out.println("<div class=\"linha\"><div class=\"date\">" //$NON-NLS-1$
                            + Messages.getString("ThreemaReport.ChatContinues") + "</div></div>"); //$NON-NLS-1$ //$NON-NLS-2$
                    break;
                }
            }
            out.flush();
            return bout.toString(StandardCharsets.UTF_8);
        });

        printWriter.flush();
        // Just return chatBytes.toByteArray() to disable image emojis (see #1655)
        return EmojiUtil.replaceByImages(chatBytes.toByteArray());
    }

    private synchronized void printMessage(PrintWriter out, Message message, ThreemaAccount account) {

        boolean isFrom = false;
        boolean isTo = false;
        boolean isFromSpecial = false;
        boolean isToSpecial = false;
        String bubbleFrom = "<div class=\"bbl\"><div class=\"aw\"><div class=\"awl\"></div></div><div class=\"incoming from\">";
        String bubbleTo = "<div class=\"bbr\"><div class=\"outgoing to\">";
        String bubbleToSpecial = "<div class=\"bbrs\"><div class=\"specialmessage to\">";
        String bubbleFromSpecial = "<div class=\"bbls\"><div class=\"aw\"><div class=\"awls\"></div></div><div class=\"specialmessage from\">";

        out.println("<div class=\"linha\" id=\"" + message.getId() + "\">"); //$NON-NLS-1$

        switch (message.getMessageType()) {
            case UNKNOWN_MESSAGE:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println("<i>" + Messages.getString("ThreemaReport.UnknownMessage") + " [ID: " + message.getId() + "]</i>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                break;
            case GROUP_RENAMED:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("ThreemaReport.GroupRenamed")); //$NON-NLS-1$
                break;
            case USER_LEFT_GROUP:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("ThreemaReport.UserLeftGroup")); //$NON-NLS-1$
                break;
            case USER_JOINED_GROUP:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("ThreemaReport.UserJoinedGroup")); //$NON-NLS-1$
                break;
            case USER_REMOVED_FROM_GROUP:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("ThreemaReport.UserRemovedFromGroup")); //$NON-NLS-1$
                break;
            case SELF_ADDED_TO_GROUP:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("ThreemaReport.SelfAddedToGroup")); //$NON-NLS-1$
                break;
            case SELF_REMOVED_FROM_GROUP:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("ThreemaReport.SelfRemovedToGroup")); //$NON-NLS-1$
                break;
            case WORK_CONSUMER_INFO:
                out.println("<div class=\"systemmessage\">");
                out.println(Messages.getString("ThreemaReport.WorkConsumerInfo")); //$NON-NLS-1$
                break;
            case SELF_LEFT_GROUP:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("ThreemaReport.SelfLeftGroup")); //$NON-NLS-1$
                break;
            case GROUP_NOTE_STARTED:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("ThreemaReport.GroupNoteStart")); //$NON-NLS-1$
                break;
            case GROUP_NOTE_ENDED:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("ThreemaReport.GroupNoteEnd")); //$NON-NLS-1$
                break;
            case GROUP_CREATOR_LEFT:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("ThreemaReport.GroupCreatorLeft")); //$NON-NLS-1$
                break;
            case GROUP_ICON_CHANGED:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("ThreemaReport.GroupIconChanged")); //$NON-NLS-1$
                break;
            case GROUP_CALL_STARTED:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("ThreemaReport.GroupCallStarted")); //$NON-NLS-1$
                break;
            case GROUP_CALL_ENDED:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("ThreemaReport.GroupCallEnded")); //$NON-NLS-1$
                break;
            case MISSED_CALL:
                if (message.isFromMe()) {
                    isToSpecial = true;
                    out.println(bubbleToSpecial);
                } else {
                    isFromSpecial = true;
                    out.println(bubbleFromSpecial);
                }
                out.println(Messages.getString("ThreemaReport.MissedCall")); //$NON-NLS-1$
                break;
            case REJECTED_CALL:
                if (message.isFromMe()) {
                    isToSpecial = true;
                    out.println(bubbleToSpecial);
                } else {
                    isFromSpecial = true;
                    out.println(bubbleFromSpecial);
                }
                out.println(Messages.getString("ThreemaReport.RejectedCall")); //$NON-NLS-1$
                break;
            case REJECTED_CALL_BUSY:
                if (message.isFromMe()) {
                    isToSpecial = true;
                    out.println(bubbleToSpecial);
                } else {
                    isFromSpecial = true;
                    out.println(bubbleFromSpecial);
                }
                out.println(Messages.getString("ThreemaReport.RejectedCallBusy")); //$NON-NLS-1$
                break;
            case REJECTED_CALL_TIMEOUT:
                if (message.isFromMe()) {
                    isToSpecial = true;
                    out.println(bubbleToSpecial);
                } else {
                    isFromSpecial = true;
                    out.println(bubbleFromSpecial);
                }
                out.println(Messages.getString("ThreemaReport.RejectedCallTimeout")); //$NON-NLS-1$
                break;
            case REJECTED_CALL_DISABLED:
                if (message.isFromMe()) {
                    isToSpecial = true;
                    out.println(bubbleToSpecial);
                } else {
                    isFromSpecial = true;
                    out.println(bubbleFromSpecial);
                }
                out.println(Messages.getString("ThreemaReport.RejectedCallDisabled")); //$NON-NLS-1$
                break;
            case UNKNOWN_CALL_RESPONSE:
                if (message.isFromMe()) {
                    isToSpecial = true;
                    out.println(bubbleToSpecial);
                } else {
                    isFromSpecial = true;
                    out.println(bubbleFromSpecial);
                }
                out.println(Messages.getString("ThreemaReport.RejectedCallUnknown")); //$NON-NLS-1$
                break;
            case REJECTED_CALL_OFF_HOURS:
                if (message.isFromMe()) {
                    isToSpecial = true;
                    out.println(bubbleToSpecial);
                } else {
                    isFromSpecial = true;
                    out.println(bubbleFromSpecial);
                }
                out.println(Messages.getString("ThreemaReport.RejectedCallOffHours"));
                break;
            case THREEMA_CALL:
                if (message.isFromMe()) {
                    isToSpecial = true;
                    out.println(bubbleToSpecial);
                } else {
                    isFromSpecial = true;
                    out.println(bubbleFromSpecial);
                }
                out.println(Messages.getString("ThreemaReport.ThreemaCall") + "<br>"); //$NON-NLS-1$ //$NON-NLS-2$
                out.println( Messages.getString("ThreemaReport.Duration") + ": " + message.getMediaDuration()); //$NON-NLS-1$ //$NON-NLS-2$
                break;
            default:
                IItemReader mediaItem = null;
                byte[] thumb;
                String sender = null;
                String query;
                String exportPath = null;

                if (message.isFromMe()) {
                    out.println(bubbleTo);
                    isTo = true;
                    if (account != null && !account.isUnknown()) {
                        sender = account.getFullId();
                    }
                } else {
                    out.println(bubbleFrom);
                    isFrom = true;
                    sender = message.getRemoteResource();
                }
                if (sender != null && !sender.isEmpty()) {
                    out.println("<span style=\"font-family: Arial; color: #b4c74b;\">" //$NON-NLS-1$
                            + format(sender) + "</span><br/>"); //$NON-NLS-1$
                }

                switch (message.getMessageType()) {
                    case TEXT_MESSAGE:
                        if (message.getText() != null) {
                            out.print(format(message.getText()) + "<br/>"); //$NON-NLS-1$
                        }
                        break;
                    case UNKNOWN_MEDIA_MESSAGE:
                        if (message.getMediaDescription() != null) {
                            out.println("<i>" + Messages.getString("ThreemaReport.UnknownMediaMessage") + "</i><br/>");
                            out.print(format(message.getMediaDescription()) + "<br/>"); //$NON-NLS-1$
                        }
                        break;
                    case URL_MESSAGE:
                        out.println("<a href=\"" + format(message.getUrl()) + "\">" + format(message.getUrl()) //$NON-NLS-1$ //$NON-NLS-2$
                                + "</a><br/>"); //$NON-NLS-1$
                        if (message.getText() != null) {
                            out.print(format(message.getText()) + "<br/>"); //$NON-NLS-1$
                        }
                        break;
                    case LOCATION_MESSAGE:
                        out.println("<i>" + locationIcon + Messages.getString("ThreemaReport.LocationMessage") + "</i><br/>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        out.println("Latitude: " + message.getLatitude() + "<br/>"); //$NON-NLS-1$ //$NON-NLS-2$
                        out.println("Longitude: " + message.getLongitude() + "<br/>"); //$NON-NLS-1$ //$NON-NLS-2$
                        if (message.getText() != null) {
                            out.print(format(message.getText()) + "<br/>"); //$NON-NLS-1$
                        }
                        break;
                    case AUDIO_MESSAGE:
                    case VIDEO_MESSAGE:
                    case GIF_MESSAGE:
                    case IMAGE_MESSAGE:
                    case APP_MESSAGE:
                        mediaItem = message.getMediaItem();
                        // query is already html escaped
                        query = message.getMediaQuery();

                        if (mediaItem != null) {
                            exportPath = iped.parsers.util.Util.getExportPath(mediaItem);
                        }

                        if (query != null) {
                            if (mediaItem != null) {
                                out.println("<input class=\"check\" type=\"checkbox\" onclick=\"app.check(" + query + ",this.checked)\"" + " name=\"" + mediaItem.getHash() + "\"/>");
                                out.println("<a onclick=\"app.open('hash:" + mediaItem.getHash() + "')\" "); //$NON-NLS-1$ //$NON-NLS-2$
                            }

                            String href = iped.parsers.util.Util.getReportHref(mediaItem);
                            out.println("href=\"" + format(href) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                            out.println(">"); //$NON-NLS-1$
                        }

                        thumb = message.getThumbnail();

                        if (mediaItem != null) { // media file found
                            byte[] generatedThumb = mediaItem.getThumb();
                            if (generatedThumb != null)
                                thumb = generatedThumb;

                            if (message.getMessageType() == MessageType.AUDIO_MESSAGE
                                    || message.getMessageType() == MessageType.VIDEO_MESSAGE
                                    || message.getMessageType() == MessageType.GIF_MESSAGE) {
                                String source = iped.parsers.util.Util.getSourceFileIfExists(mediaItem).orElse("");
                                if (message.getMessageType() == MessageType.AUDIO_MESSAGE) {
                                    out.println(Messages.getString("ThreemaReport.AudioMessageTitle") + "<br>"); //$NON-NLS-1$
                                    out.println("<div class=\"audioImg iped-audio\" " //$NON-NLS-1$
                                            + " title=\"Audio\" " + "data-src1=\"" + format(exportPath) + "\" "
                                            + "data-src2=\"" //$NON-NLS-1$
                                            + format(source) + "\" ></div>");
                                    out.println("</a><br>"); //$NON-NLS-1$
                                } else {
                                    out.println(Messages.getString("ThreemaReport.VideoMessageTitle") + "<br>"); //$NON-NLS-1$
                                    if (thumb != null) {
                                        out.print("<img class=\"thumb iped-video\" src=\""); //$NON-NLS-1$
                                        out.print("data:image/jpg; base64," + Util.encodeBase64(thumb) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
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

                                String transcription = mediaItem.getMetadata().get(ExtraProperties.TRANSCRIPT_ATTR);
                                if (transcription != null) {
                                    out.print(Messages.getString("ReportGenerator.TranscriptionTitle")); //$NON-NLS-1$
                                    String confidence = mediaItem.getMetadata().get(ExtraProperties.CONFIDENCE_ATTR);
                                    if (confidence != null) {
                                        float score = Float.parseFloat(confidence) * 100;
                                        out.print(" [" + (int) score + "%]"); //$NON-NLS-1$ //$NON-NLS-2$
                                    }
                                    out.println(": <i>"); //$NON-NLS-1$
                                    out.println(format(transcription));
                                    out.println("</i><br/>"); //$NON-NLS-1$
                                }
                            } else {
                                if (thumb != null) {
                                    if (getTitle(message).equals("video")) //$NON-NLS-1$
                                        out.println(Messages.getString("ThreemaReport.Video") + ":<br>"); //$NON-NLS-1$ //$NON-NLS-2$
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
                                case IMAGE_MESSAGE:
                                case APP_MESSAGE:
                                    if (thumb != null) {
                                        if (getTitle(message).equals("video")) //$NON-NLS-1$
                                            out.println(Messages.getString("ThreemaReport.Video") + ":<br>"); //$NON-NLS-1$ //$NON-NLS-2$
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

                            out.println("</a><br>"); //$NON-NLS-1$
                        }

                        if (message.getMediaDescription() != null)
                            out.println("<br><i>\"" + format(message.getMediaDescription()) + "\"</i><br>"); //$NON-NLS-1$ //$NON-NLS-2$
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
            out.print("<p><i>" + Messages.getString("ThreemaReport.FoundInPedoHashDB") + " "
                    + format(message.getChildPornSets().toString()) + "</i></p>");
        }

        out.println("<span class=\"time\">"); //$NON-NLS-1$

        out.println(timeFormat.format(message.getTimeStamp()) + " &nbsp;"); //$NON-NLS-1$
        if (message.isFromMe() && message.getMessageStatus() != null) {
            switch (message.getMessageStatus()) {
                case MESSAGE_VIEWED:
                    out.println("<div class=\"viewed\"></div>"); //$NON-NLS-1$
                    break;
                case MESSAGE_UNSENT:
                    out.println("<div class=\"unsent\"></div>"); //$NON-NLS-1$
                    break;
                case MESSAGE_SENT:
                    out.println("<div class=\"sent\"></div>"); //$NON-NLS-1$
                    break;
                case MESSAGE_DELIVERED:
                    out.println("<div class=\"delivered\"></div>"); //$NON-NLS-1$
                    break;
            }
        }
        out.println("</span>"); //$NON-NLS-1$

        if (isTo)
            out.println("</div><div class=\"aw\"><div class=\"awr\"></div></div>");
        if (isFrom)
            out.println("</div>");
        if (isToSpecial)
            out.println("</div><div class=\"aw\"><div class=\"awrs\"></div></div>");
        if (isFromSpecial)
            out.println("</div>");

        out.println("</div></div>"); //$NON-NLS-1$

    }

    private static String getTitle(Message message) {
        if (message.getMediaDescription() != null)
            return message.getMediaDescription();
        else
            return "Attachment"; //$NON-NLS-1$
    }

    private void printMessageFile(PrintWriter out, String title, String id, byte[] avatar, Supplier<String> messages) {
        String strAvatar;
        if (avatar == null || avatar.length == 0) {
            strAvatar = Util.getImageResourceAsEmbedded("img/avatar.png");
        } else {
            strAvatar = "data:image/jpg;base64," + Util.encodeBase64(Arrays.copyOfRange(avatar, 1, avatar.length));
        }

        StringSubstitutor interpolator = new StringSubstitutor(key -> {
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
        });
        out.println(interpolator.replace(template));
    }
}
