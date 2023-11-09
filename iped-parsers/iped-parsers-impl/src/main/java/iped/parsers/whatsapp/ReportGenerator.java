package iped.parsers.whatsapp;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookup;
import org.apache.commons.text.lookup.StringLookupFactory;

import iped.data.IItemReader;
import iped.parsers.util.Messages;
import iped.parsers.vcard.VCardParser;
import iped.parsers.whatsapp.Message.MessageType;
import iped.properties.ExtraProperties;
import iped.utils.EmojiUtil;
import iped.utils.SimpleHTMLEncoder;

/**
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@pf.gov.br>
 */
public class ReportGenerator {

    private int minChatSplitSize = 6000000;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd"); //$NON-NLS-1$
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss XXX"); //$NON-NLS-1$
    private static final String template = Util.readResourceAsString("wachat-html-template.txt");
    private static final String css = Util.readResourceAsString("css/whatsapp.css");
    private static final String js = Util.readResourceAsString("js/whatsapp.js");
    private boolean firstFragment = true;
    private int currentMsg = 0;
    private static final String deletedIcon = "<img class=\"del\"/>";
    private static final String lockedIcon = "<img class=\"lock\"/>";
    private static final String locationIcon = "<img class=\"location\"/>";
    private static final String forwardedIcon = "<img class=\"fwd\"/>";
    private static final String waSuffix = "@s.whatsapp.net";

    public ReportGenerator() {
    }

    public int getNextMsgNum() {
        return currentMsg;
    }

    public void setMinChatSplitSize(int minChatSplitSize) {
        this.minChatSplitSize = minChatSplitSize;
    }

    public byte[] genarateContactHtml(WAContact contact) throws UnsupportedEncodingException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, "UTF-8")); //$NON-NLS-1$

        out.println("<!DOCTYPE html>\n" //$NON-NLS-1$
                + "<html>\n" //$NON-NLS-1$
                + "<head>\n" //$NON-NLS-1$
                + " <title>" + format(contact.getId()) + "</title>\n" //$NON-NLS-1$ //$NON-NLS-2$
                + " <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n" //$NON-NLS-1$
                + "<style>\n" //$NON-NLS-1$
                + "</style>\n" //$NON-NLS-1$
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

        return EmojiUtil.replaceByImages(bout.toByteArray());
    }

    public byte[] generateAccountHtml(WAAccount account) throws UnsupportedEncodingException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, "UTF-8")); //$NON-NLS-1$

        out.println("<!DOCTYPE html>\n" //$NON-NLS-1$
                + "<html>\n" //$NON-NLS-1$
                + "<head>\n" //$NON-NLS-1$
                + " <title>" + format(account.getId()) + "</title>\n" //$NON-NLS-1$ //$NON-NLS-2$
                + " <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n" //$NON-NLS-1$
                + "<style>\n" //$NON-NLS-1$
                + "</style>\n" //$NON-NLS-1$
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

        return EmojiUtil.replaceByImages(bout.toByteArray());
    }

    private static final String format(String s) {
        if (s == null || s.trim().isEmpty())
            return "-"; //$NON-NLS-1$
        else
            return SimpleHTMLEncoder.htmlEncode(s.trim());

    }

    public byte[] generateNextChatHtml(Chat c, WAContactsDirectory contactsDirectory, WAAccount account, int frag, StringBuilder histFrag) {

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
            String lastId = "1000000000";
            while (currentMsg < c.getMessages().size()) {
                Message m = c.getMessages().get(currentMsg);
                String thisDate = dateFormat.format(m.getTimeStamp());
                if (lastDate == null || !lastDate.equals(thisDate)) {
                    out.println("<div class=\"linha\"><div class=\"date\">" //$NON-NLS-1$
                            + thisDate + "</div></div>"); //$NON-NLS-1$
                    lastDate = thisDate;
                }
                printMessage(out, m, c.isGroupChat(), contactsDirectory, account);
                lastId = m.getUniqueId();
                currentMsg += 1;
                if (currentMsg != c.getMessages().size() && bout.size() >= minChatSplitSize || lastId.compareTo("218814")==0) {
                    out.println("<div class=\"linha\"><div class=\"date\">" //$NON-NLS-1$
                            + Messages.getString("WhatsAppReport.ChatContinues") + "</div></div>"); //$NON-NLS-1$ //$NON-NLS-2$
                    break;
                }
            }

            if (histFrag.length()==0){
                histFrag.append("<input type=\"hidden\" id=\"fragMessageChat\" value=\""+Messages.getString("WhatsAppReport.ChatFragment")+"\"/>");
                histFrag.append("<input type=\"hidden\" id=\"fragMessageId\" value=\""+Messages.getString("WhatsAppReport.ReferenceId")+"\"/>");
                histFrag.append("<input type=\"hidden\" id=\"fragMessageClose\" value=\""+Messages.getString("WhatsAppReport.Close")+"\"/>");
            }

            histFrag.append("<input type=\"hidden\" id=\"frag"+ frag +"\" value=\""+lastId+"\"/>");
            out.println(histFrag);
            out.flush();
            return new String(bout.toByteArray(), StandardCharsets.UTF_8);
        });

        printWriter.flush();
        // Just return chatBytes.toByteArray() to disable image emojis (see #1655)
        return EmojiUtil.replaceByImages(chatBytes.toByteArray());
    }

    private synchronized void printMessage(PrintWriter out, Message message, boolean group,
            WAContactsDirectory contactsDirectory,
            WAAccount account) {

        boolean isFrom = false;
        boolean isTo = false;
        boolean isFromSpecial = false;
        boolean isToSpecial = false;
        String bubbleFrom = "<div class=\"bbl\"><div class=\"aw\"><div class=\"awl\"></div></div><div class=\"incoming from\">";
        String bubbleTo = "<div class=\"bbr\"><div class=\"outgoing to\">";
        String bubbleFromSpecial = "<div class=\"bbls\"><div class=\"aw\"><div class=\"awls\"></div></div><div class=\"specialmessage from\">";
        String bubbleToSpecial = "<div class=\"bbrs\"><div class=\"specialmessage to\">";

        out.println("<div class=\"linha\" id=\"" + message.getUniqueId() + "\">"); //$NON-NLS-1$

        switch (message.getMessageType()) {
            case UNKNOWN_MESSAGE:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println("<i>" + Messages.getString("WhatsAppReport.UnknownMessage") + " [ID: " + message.getId() + "]</i>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                break;
            case ENCRYPTION_KEY_CHANGED:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.print(lockedIcon);
                out.println(format(message.getRemoteResource()) + " " //$NON-NLS-1$
                        + Messages.getString("WhatsAppReport.SecurityChanged")); //$NON-NLS-1$
                break;
            case BLOCKED_CONTACT:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("WhatsAppReport.BlockedContact")); //$NON-NLS-1$
                break;
            case UNBLOCKED_CONTACT:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("WhatsAppReport.UnblockedContact")); //$NON-NLS-1$
                break;
            case BUSINESS_CHAT:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("WhatsAppReport.ChatBusiness")); //$NON-NLS-1$
                break;
            case BUSINESS_TO_STANDARD:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("WhatsAppReport.BusinessToStandard")); //$NON-NLS-1$
                break;
            case MESSAGES_ENCRYPTED:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.print(lockedIcon);
                out.println(Messages.getString("WhatsAppReport.ChatEncrypted")); //$NON-NLS-1$
                break;
            case MESSAGES_NOW_ENCRYPTED:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.print(lockedIcon);
                if (group) {
                    out.println(Messages.getString("WhatsAppReport.GroupNowEncrypted")); //$NON-NLS-1$
                } else {
                    out.println(Messages.getString("WhatsAppReport.ChatNowEncrypted")); //$NON-NLS-1$
                }
                break;
            case MISSED_VIDEO_CALL:
                if (message.isFromMe()) {
                    isToSpecial = true;
                    out.println(bubbleToSpecial);
                } else {
                    isFromSpecial = true;
                    out.println(bubbleFromSpecial);
                }
                out.println(Messages.getString("WhatsAppReport.MissedVideoCall")); //$NON-NLS-1$
                break;
            case MISSED_VOICE_CALL:
                if (message.isFromMe()) {
                    isToSpecial = true;
                    out.println(bubbleToSpecial);
                } else {
                    isFromSpecial = true;
                    out.println(bubbleFromSpecial);
                }
                out.println(Messages.getString("WhatsAppReport.MissedVoiceCall")); //$NON-NLS-1$
                break;
            case REFUSED_VOICE_CALL:
                if (message.isFromMe()) {
                    isToSpecial = true;
                    out.println(bubbleToSpecial);
                } else {
                    isFromSpecial = true;
                    out.println(bubbleFromSpecial);
                }
                out.println(Messages.getString("WhatsAppReport.RefusedVoiceCall")); //$NON-NLS-1$
                break;
            case REFUSED_VIDEO_CALL:
                if (message.isFromMe()) {
                    isToSpecial = true;
                    out.println(bubbleToSpecial);
                } else {
                    isFromSpecial = true;
                    out.println(bubbleFromSpecial);
                }
                out.println(Messages.getString("WhatsAppReport.RefusedVideoCall")); //$NON-NLS-1$
                break;
            case UNAVAILABLE_VOICE_CALL:
                if (message.isFromMe()) {
                    isToSpecial = true;
                    out.println(bubbleToSpecial);
                } else {
                    isFromSpecial = true;
                    out.println(bubbleFromSpecial);
                }
                out.println(Messages.getString("WhatsAppReport.UnavailableVoiceCall")); //$NON-NLS-1$
                break;
            case UNAVAILABLE_VIDEO_CALL:
                if (message.isFromMe()) {
                    isToSpecial = true;
                    out.println(bubbleToSpecial);
                } else {
                    isFromSpecial = true;
                    out.println(bubbleFromSpecial);
                }
                out.println(Messages.getString("WhatsAppReport.UnavailableVideoCall")); //$NON-NLS-1$
                break;
            case UNKNOWN_VOICE_CALL:
                if (message.isFromMe()) {
                    isToSpecial = true;
                    out.println(bubbleToSpecial);
                } else {
                    isFromSpecial = true;
                    out.println(bubbleFromSpecial);
                }
                out.println(Messages.getString("WhatsAppReport.UnknownVoiceCall")); //$NON-NLS-1$
                break;
            case UNKNOWN_VIDEO_CALL:
                if (message.isFromMe()) {
                    isToSpecial = true;
                    out.println(bubbleToSpecial);
                } else {
                    isFromSpecial = true;
                    out.println(bubbleFromSpecial);
                }
                out.println(Messages.getString("WhatsAppReport.UnknownVideoCall")); //$NON-NLS-1$
                break;
            case VIDEO_CALL:
                if (message.isFromMe()) {
                    isToSpecial = true;
                    out.println(bubbleToSpecial);
                } else {
                    isFromSpecial = true;
                    out.println(bubbleFromSpecial);
                }
                out.println(Messages.getString("WhatsAppReport.VideoCall")); //$NON-NLS-1$
                if (message.getMediaDuration() > 0) {
                    out.println("<br>" + Messages.getString("WhatsAppReport.Duration") + ": " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                            + formatMMSS(message.getMediaDuration()));
                }
                break;
            case VOICE_CALL:
                if (message.isFromMe()) {
                    isToSpecial = true;
                    out.println(bubbleToSpecial);
                } else {
                    isFromSpecial = true;
                    out.println(bubbleFromSpecial);
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
                IItemReader mediaItem = null;
                byte[] thumb = null;
                String name = null, number = null;
                String query = null;
                String exportPath = null;

                if (message.isFromMe()) {
                    out.println(bubbleTo);
                    isTo = true;
                    if (account != null && !account.isUnknown()) {
                        name = account.getName();
                        number = message.getLocalResource();
                    }
                } else {
                    out.println(bubbleFrom);
                    isFrom = true;
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
                    } else if (!number.equals(name) && !number.equals(name + WAContact.waSuffix)) {
                        name += " (" + number + ")"; //$NON-NLS-1$ //$NON-NLS-2$
                    }
                }
                if (!name.isEmpty()) {
                    out.println("<span style=\"font-family: Arial; color: #b4c74b;\">" //$NON-NLS-1$
                            + format(name) + "</span><br/>"); //$NON-NLS-1$
                }

                //Messages.getString("WhatsAppReport.Forwarded")
                if (message.isForwarded()) {
                    out.println(forwardedIcon + "<span class=\"fwd\">"
                            + Messages.getString("WhatsAppReport.Forwarded") + "</span><br/>");
                }

                String quoteClass = (message.isFromMe())?"quote_to":"quote_from";                
                Message messageQuote = message.getMessageQuote();

                if (message.isQuoted() && messageQuote != null){

                    String dataQuote = messageQuote.getData();
                    String quoteClick = "onclick=\"goToAnchorId("+messageQuote.getId()+");\"";
                    String quoteIcon = "";
                    String quoteDuration = "("+formatMMSS(messageQuote.getMediaDuration())+")";
                    String quoteUser = getBestContactName(messageQuote,contactsDirectory,account);
                    byte[] thumbQuote = messageQuote.getThumbData();                               

                    switch (messageQuote.getMessageType()) {
                        case AUDIO_MESSAGE:
                            if (dataQuote == null || dataQuote.isEmpty()){
                                dataQuote = Messages.getString("WhatsAppReport.Audio");
                            }
                            quoteIcon = "\uD83C\uDFA7";
                            out.print("<div class=\""+quoteClass+"\" "+quoteClick+"><div style=\"display:table-cell;\"><span class=\"quote_user\">"+quoteUser+
                            "</span></br><span class=\"quote_msg\">"+quoteIcon +
                                " "+ format(dataQuote) + " "+  quoteDuration + "</span></div>");                                    
                            break;
                        case VIDEO_MESSAGE:     
                        case GIF_MESSAGE:                           
                            quoteIcon = "\uD83D\uDCF9";
                            if (dataQuote == null || dataQuote.isEmpty()){
                                dataQuote = Messages.getString("WhatsAppReport.Video");
                            }
                            out.print("<div class=\""+quoteClass+"\" "+quoteClick+"><div style=\"display:table-cell;vertical-align:top;border-right: 10px solid transparent;\"><span class=\"quote_user\">"+quoteUser+
                            "</span></br><span class=\"quote_msg\">"+quoteIcon +
                                " "+ format(dataQuote) + " "+  quoteDuration + "</span></div>");
                            if (thumbQuote != null) {
                                out.print("<div><img style=\"width:33px;height:33px;display:table-cell\" src=\"");
                                out.print("data:image/jpg;base64," + Util.encodeBase64(thumbQuote) + "\"></div>");
                            } else {
                                out.println("<div class=\"videoImg\" style=\"width:33px;height:33px;display:table-cell\" title=\"Video\"></div>");
                            }
                            break;                        
                        case STICKER_MESSAGE:
                        case IMAGE_MESSAGE:
                            quoteIcon = "\uD83D\uDDBC";
                            if (dataQuote == null || dataQuote.isEmpty()){
                                dataQuote = Messages.getString("WhatsAppReport.Photo");
                            }
                            out.print("<div class=\""+quoteClass+"\" "+quoteClick+"><div style=\"display:table-cell;vertical-align:top;border-right: 10px solid transparent;\"><span class=\"quote_user\">"+quoteUser+
                                "</span></br><span class=\"quote_msg\">"+quoteIcon +" "+ format(dataQuote) + " </span></div>");                                    
                            if (thumbQuote != null) {
                                out.print("<div><img style=\"width:33px;height:33px;display:table-cell\" src=\"");
                                out.print("data:image/jpg;base64," + Util.encodeBase64(thumbQuote) + "\"></div>");
                            } else {
                                out.println("<div class=\"imageImg\" style=\"width:33px;height:33px;display:table-cell\" title=\"Image\"></div>");
                            }
                            break;                        
                        case APP_MESSAGE:
                            quoteIcon = "\uD83D\uDCC4";
                            if (dataQuote == null || dataQuote.isEmpty()){
                                dataQuote = Messages.getString("WhatsAppReport.Document");
                            }
                            out.print("<div class=\""+quoteClass+"\" "+quoteClick+"><div style=\"display:table-cell;vertical-align:top;border-right: 10px solid transparent;\"><span class=\"quote_user\">"+quoteUser+
                                "</span></br><span class=\"quote_msg\">"+quoteIcon +" "+ format(dataQuote) + " </span></div>");                                    
                            if (thumbQuote != null) {
                                out.print("<div><img style=\"width:33px;height:33px;display:table-cell\" src=\"");
                                out.print("data:image/jpg;base64," + Util.encodeBase64(thumbQuote) + "\"></div>");
                            } else {
                                out.println("<div class=\"attachImg\" style=\"width:33px;height:33px;display:table-cell\" title=\"Doc\"></div>");
                            }
                            break;                            
                        default:
                            out.print("<div class=\""+quoteClass+"\" "+quoteClick+"><div style=\"display:table-cell;\"><span class=\"quote_user\">"+quoteUser+
                            "</span></br><span class=\"quote_msg\">"+ format(dataQuote) + "</span></div>");
                            break;
                    }
                    if (messageQuote.isDeleted()) {
                        out.println("<div style=\"display:table-footer-group\"><br/><span style=\"float:none\" class=\"recovered\">");
                        out.println("<div class=\"deletedIcon\"></div>");
                        out.println("<i>" + Messages.getString("WhatsAppReport.Recovered") + "</i>");
                        out.println("</span></div>");
                    }                    
                    out.print("</div>");
                    
                } else if (message.isQuoted() && messageQuote == null){ //Reference not found
                    out.print("<div class=\""+quoteClass+"\"><span class=\"quote_user\">"+
                    Messages.getString("WhatsAppReport.ReferenceNotFound")+"</span></br><span class=\"quote_msg\">"+format("") + "</span></div>");
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
                        out.println("<i>" + locationIcon + Messages.getString("WhatsAppReport.LocationMessage") + "</i><br/>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        out.println("Latitude: " + message.getLatitude() + "<br/>"); //$NON-NLS-1$ //$NON-NLS-2$
                        out.println("Longitude: " + message.getLongitude() + "<br/>"); //$NON-NLS-1$ //$NON-NLS-2$
                        if (message.getData() != null) {
                            out.print(format(message.getData()) + "<br/>"); //$NON-NLS-1$
                        }
                        break;
                    case SHARE_LOCATION_MESSAGE:
                        out.println("<i>" + locationIcon + Messages.getString("WhatsAppReport.SharedLocationMessage") + "</i><br/>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        out.println("Latitude: " + message.getLatitude() + "<br/>"); //$NON-NLS-1$ //$NON-NLS-2$
                        out.println("Longitude: " + message.getLongitude() + "<br/>"); //$NON-NLS-1$ //$NON-NLS-2$
                        if (message.getData() != null) {
                            out.print(format(message.getData()) + "<br/>"); //$NON-NLS-1$
                        }
                        break;
                    case CONTACT_MESSAGE:
                        out.println("<b>" + Messages.getString("WhatsAppReport.Contact") + "</b><br/>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        for (String c : message.getVcards()) {
                            if (c != null) {
                                VCardParser.printHtmlFromString(out, c);
                            }
                        }
                        break;
                    case DELETED_MESSAGE:
                        out.println("<i>" + deletedIcon + Messages.getString("WhatsAppReport.MessageDeleted") + "</i><br/>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        break;
                    case DELETED_BY_ADMIN:
                        out.println("<i>" + deletedIcon + Messages.getString("WhatsAppReport.MessageDeletedByAdmin") + "</i><br/>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        break;
                    case DELETED_BY_SENDER:
                        out.println("<i>" + deletedIcon + Messages.getString("WhatsAppReport.MessageDeletedBySender") //$NON-NLS-1$ //$NON-NLS-2$
                                + "</i><br/>"); //$NON-NLS-1$
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
                            exportPath = iped.parsers.util.Util.getExportPath(mediaItem);
                        }

                        if (query != null) {
                            if (mediaItem != null) {
                                out.println(
                                        "<input class=\"check\" type=\"checkbox\" onclick=\"app.check(" + query
                                                + ",this.checked)\"" + " name=\"" + mediaItem.getHash() + "\"/>");
                            }
                            out.println("<a onclick=\"app.open(" + query + ")\" "); //$NON-NLS-1$ //$NON-NLS-2$

                            if (mediaItem != null) {
                                String href = iped.parsers.util.Util.getReportHref(mediaItem);
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
                                String source = iped.parsers.util.Util.getSourceFileIfExists(mediaItem)
                                        .orElse("");
                                if (message.getMessageType() == MessageType.AUDIO_MESSAGE) {
                                    out.println(Messages.getString("WhatsAppReport.AudioMessageTitle") + "<br>"); //$NON-NLS-1$
                                    out.println("<div class=\"audioImg iped-audio\" " //$NON-NLS-1$
                                            + " title=\"Audio\" " + "data-src1=\"" + format(exportPath) + "\" "
                                            + "data-src2=\"" //$NON-NLS-1$
                                            + format(source) + "\" ></div>");
                                    out.println("</a><br>"); //$NON-NLS-1$
                                } else {
                                    out.println(Messages.getString("WhatsAppReport.VideoMessageTitle") + "<br>"); //$NON-NLS-1$
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
                    message.lookupAndAddChildPornSets(mediaItem.getHash());
                }
                break;
        }
        
        Set<String> reactions = new TreeSet<String>();
        int reactionsCount = 0;
        List<MessageAddOn> mao = message.getAddOns();
        StringBuilder aoDetails = new StringBuilder();
        if (!mao.isEmpty()) {
            Collections.sort(mao);
            for (MessageAddOn a : mao) {
                if (a.getReaction() != null) {
                    if (aoDetails.length() > 0) {
                        aoDetails.append("<br>");
                    }
                    aoDetails.append(a.getReaction());

                    String name = null;
                    String number = null;
                    if (a.isFromMe()) {
                        if (account != null && !account.isUnknown()) {
                            name = account.getName();
                        } else {
                            name = "[" + Messages.getString("WhatsAppReport.Owner") + "]";
                        }
                    } else {
                        number = a.getRemoteResource();
                        if (number != null) {
                            WAContact contact = contactsDirectory.getContact(number);
                            if (contact != null) {
                                name = contact.getName();
                            }
                            if (number.endsWith(WAContact.waSuffix)) {
                                number = number.substring(0, number.length() - WAContact.waSuffix.length());
                            }
                        }
                    }
                    name = name == null ? "" : name.trim();
                    number = number == null ? "" : number.trim();
                    if (!number.isEmpty()) {
                        if (name.isEmpty()) {
                            name = number;
                        } else if (!number.equals(name)) {
                            name += " (" + number + ")";
                        }
                    }
                    if (name != null) {
                        aoDetails.append(' ');
                        aoDetails.append(format(name));
                    }

                    if (a.getTimeStamp() != null) {
                        aoDetails.append(" [");
                        aoDetails.append(timeFormat.format(a.getTimeStamp()));
                        aoDetails.append("]");
                    }

                    reactions.add(a.getReaction());
                    reactionsCount++;
                }
            }
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
            out.println("<i>" + Messages.getString("WhatsAppReport.MessageDeletedRecovered") + "</i>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            out.println("<div class=\"deletedIcon\"></div>"); //$NON-NLS-1$
            out.println("</span>"); //$NON-NLS-1$
        }

        if (message.getRecoveredFrom() != null) {
            out.println("<br/><span class=\"recovered\">"); //$NON-NLS-1$
            out.print(Messages.getString("WhatsAppReport.RecoveredFrom") + " " + format(message.getRecoveredFrom()));
            out.println("</span>"); //$NON-NLS-1$

        }

        if (isTo)
            out.println("</div><div class=\"aw\"><div class=\"awr\"></div></div>");
        if (isFrom)
            out.println("</div>");
        if (isToSpecial)
            out.println("</div><div class=\"aw\"><div class=\"awrs\"></div></div>");
        if (isFromSpecial)
            out.println("</div>");

        out.println("</div></div>"); //$NON-NLS-1$

        if (reactionsCount > 0) {
            out.print("<div class=\"reaction ");
            out.print(message.isFromMe() ? "to" : "from");
            out.print("\">");
            for (String r : reactions) {
                out.print(r);
            }
            if (reactionsCount > reactions.size()) {
                out.print(reactionsCount);
            }
            if (aoDetails.length() > 0) {
                out.print("<span class=\"tt\">");
                out.print(aoDetails);
                out.print("</span>");
            }
            out.println("</div><br>");
        }
    }

    private String getBestContactName(Message a, WAContactsDirectory contactsDirectory,WAAccount account){
        String name = null;
        String number = null;
        if (a.isFromMe()) {
            if (account != null && !account.isUnknown()) {
                name = account.getName();
            } else {
                name = "[" + Messages.getString("WhatsAppReport.Owner") + "]";
            }
        } else {
            number = a.getRemoteResource();
            if (number != null) {
                WAContact contact = contactsDirectory.getContact(number);
                if (contact != null) {
                    name = contact.getName();
                }
                if (number.endsWith(waSuffix)) {
                    number = number.substring(0, number.length() - waSuffix.length());
                }
            }
        }
        name = name == null ? "" : name.trim();
        number = number == null ? "" : number.trim();
        if (!number.isEmpty()) {
            if (name.isEmpty()) {
                name = number;
            } else if (!number.equals(name)) {
                name += " (" + number + ")";
            }
        }
        if (name != null) {
            return format(name);
        }
        return "User Unknown";
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
