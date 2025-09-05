package iped.parsers.whatsapp;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
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
import iped.utils.LocalizedFormat;
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
    private static final String deletedIcon = "<img class=\"del\">";
    private static final String lockedIcon = "<img class=\"lock\">";
    private static final String locationIcon = "<img class=\"location\">";
    private static final String forwardedIcon = "<img class=\"fwd\">";
    private static final String viewOnceIcon = "<img class=\"vo\">";
    private static final String waSuffix = "@s.whatsapp.net";

    public ReportGenerator() {
    }

    public int getNextMsgNum() {
        return currentMsg;
    }

    public void setMinChatSplitSize(int minChatSplitSize) {
        this.minChatSplitSize = minChatSplitSize;
    }

    public byte[] generateContactHtml(WAContact contact) throws UnsupportedEncodingException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, "UTF-8")); //$NON-NLS-1$

        out.println("<!DOCTYPE html>\n" //$NON-NLS-1$
                + "<html>\n" //$NON-NLS-1$
                + "<head>\n" //$NON-NLS-1$
                + " <title>" + format(contact.getId()) + "</title>\n" //$NON-NLS-1$ //$NON-NLS-2$
                + " <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n" //$NON-NLS-1$
                + "<style>\n" //$NON-NLS-1$
                + "</style>\n" //$NON-NLS-1$
                + "</head>\n" //$NON-NLS-1$
                + "<body>\n"); //$NON-NLS-1$

        if (contact.getAvatar() != null)
            out.println("<img src=\"data:image/jpg;base64," + Util.encodeBase64(contact.getAvatar()) //$NON-NLS-1$
                    + "\" width=\"112\"><br>"); //$NON-NLS-1$
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
                + " <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n" //$NON-NLS-1$
                + "<style>\n" //$NON-NLS-1$
                + "</style>\n" //$NON-NLS-1$
                + "</head>\n" //$NON-NLS-1$
                + "<body>\n"); //$NON-NLS-1$

        if (account.getAvatar() != null)
            out.println("<img src=\"data:image/jpg;base64," + Util.encodeBase64(account.getAvatar()) //$NON-NLS-1$
                    + "\" width=\"112\"><br>"); //$NON-NLS-1$
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

        String ret = SimpleHTMLEncoder.htmlEncode(s.trim());

        // Keep line breaks present in the content, converting to an HTML <br>
        ret = ret.replaceAll("\n", "<br>\n");

        // Apply Bold, Italic, Strikethrough and Monospaced styles
        ret = convertWhatsAppTagsToHTML(ret);

        return ret;
    }

    private static final String[] tagsWA = { "```", "*", "_", "~" };
    private static final String[] tagsHTML = { "tt", "b", "i", "s" };

    public static final String convertWhatsAppTagsToHTML(String s) {
        int start = 0;
        while (start < s.length()) {
            int min = s.length();
            int idx = -1;
            for (int i = 0; i < tagsWA.length; i++) {
                int p0 = s.indexOf(tagsWA[i], start);
                if (p0 >= 0 && p0 < min) {
                    min = p0;
                    idx = i;
                }
            }
            if (idx == -1) {
                break;
            }
            start = min;
            String ta = tagsWA[idx];
            int p1 = s.indexOf(ta, start + ta.length());
            if (p1 >= 0) {
                String sub = s.substring(start + ta.length(), p1);
                if (!sub.isBlank()) {
                    String th = tagsHTML[idx];
                    StringBuilder sb = new StringBuilder();
                    sb.append(s, 0, start);
                    sb.append('<');
                    sb.append(th);
                    sb.append('>');
                    if (idx == 0) {
                        // Monospaced -> no internal formatting
                        sb.append(sub);
                    } else {
                        sb.append(convertWhatsAppTagsToHTML(sub));
                    }
                    sb.append("</");
                    sb.append(th);
                    sb.append('>');
                    sb.append(convertWhatsAppTagsToHTML(s.substring(p1 + ta.length())));
                    return sb.toString();
                }
            }
            start += ta.length();
        }
        return s;
    }

    public byte[] generateNextChatHtml(Chat c, WAContactsDirectory contactsDirectory, WAAccount account, int frag,
            StringBuilder histFrag) {

        if ((!firstFragment && currentMsg == 0) || (currentMsg > 0 && currentMsg == c.getMessages().size()))
            return null;

        firstFragment = false;

        ByteArrayOutputStream chatBytes = new ByteArrayOutputStream();
        PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(chatBytes, StandardCharsets.UTF_8)); // $NON-NLS-1$

        printMessageFile(printWriter, c.getTitle(), c.getPrintId(), c.getRemote().getAvatar(), c.isDeleted(),
                c.isBroadcast(), () -> {
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
                Message m = c.getMessages().get(currentMsg++);
                if (m.getMessageType() == MessageType.CALL_MESSAGE) {
                    // These messages are currently redundant with calls information already
                    // extracted from other tables (these come from messages table). So, at least
                    // for now, nothing should be included in the report.
                    continue;
                }
                if (m.getMessageType() == MessageType.MESSAGE_ASSOCIATION) {
                    // These messages are not visible on the app and don't contain any data
                    continue;
                }
                String thisDate = dateFormat.format(m.getTimeStamp());
                if (lastDate == null || !lastDate.equals(thisDate)) {
                    out.println("<div class=\"linha\"><div class=\"date\">" //$NON-NLS-1$
                            + thisDate + "</div></div>"); //$NON-NLS-1$
                    lastDate = thisDate;
                }
                printMessage(out, m, c.isGroupOrChannelChat(), contactsDirectory, account);
                lastId = m.getUniqueId();
                if (currentMsg != c.getMessages().size() && bout.size() >= minChatSplitSize) {
                    out.println("<div class=\"linha\"><div class=\"date\">" //$NON-NLS-1$
                            + Messages.getString("WhatsAppReport.ChatContinues") + "</div></div>"); //$NON-NLS-1$ //$NON-NLS-2$
                    break;
                }
            }

            if (histFrag.length() == 0) {
                histFrag.append("<input type=\"hidden\" id=\"fragMessageChat\" value=\""
                        + Messages.getString("WhatsAppReport.ChatFragment") + "\">");
                histFrag.append("<input type=\"hidden\" id=\"fragMessageId\" value=\""
                        + Messages.getString("WhatsAppReport.ReferenceId") + "\">");
                histFrag.append("<input type=\"hidden\" id=\"fragMessageClose\" value=\""
                        + Messages.getString("WhatsAppReport.Close") + "\">");
            }

            histFrag.append("<input type=\"hidden\" id=\"frag" + frag + "\" value=\"" + lastId + "\">");
            out.println(histFrag);
            out.flush();
            return new String(bout.toByteArray(), StandardCharsets.UTF_8);
        });

        printWriter.flush();
        // Just return chatBytes.toByteArray() to disable image emojis (see #1655)
        return EmojiUtil.replaceByImages(chatBytes.toByteArray());
    }

    private synchronized void printMessage(PrintWriter out, Message message, boolean isGroupOrChannel,
            WAContactsDirectory contactsDirectory, WAAccount account) {

        boolean isFrom = false;
        boolean isTo = false;
        boolean isFromSpecial = false;
        boolean isToSpecial = false;
        String bubbleFrom = "<div class=\"bbl\"><div class=\"aw\"><div class=\"awl\"></div></div><div class=\"incoming from\">";
        String bubbleTo = "<div class=\"bbr\"><div class=\"outgoing to\">";
        String bubbleFromSpecial = "<div class=\"bbls\"><div class=\"aw\"><div class=\"awls\"></div></div><div class=\"specialmessage from\">";
        String bubbleToSpecial = "<div class=\"bbrs\"><div class=\"specialmessage to\">";

        out.println("<div class=\"linha\" id=\"" + message.getUniqueId() + "\">"); //$NON-NLS-1$
        String name = getBestContactName(message, contactsDirectory, account);

        switch (message.getMessageType()) {
            case UNKNOWN_MESSAGE:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println("<i>" + Messages.getString("WhatsAppReport.UnknownMessage") + " [ID: " + message.getId() //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        + "]</i>");
                break;
            case AI_THIRD_PARTY:
                out.println("<div class=\"systemmessage\">");
                out.println(Messages.getString("WhatsAppReport.AIThirdParty"));
                break;
            case CHAT_STARTED_FROM_AD:
                out.println("<div class=\"systemmessage\">");
                out.println(Messages.getString("WhatsAppReport.ChatStartedFromAd"));
                break;
            case ENCRYPTION_KEY_CHANGED:
                out.println("<div class=\"systemmessage\">");
                out.print(lockedIcon);
                out.println(Messages.getString("WhatsAppReport.SecurityChanged"));
                break;
            case SENDER_IN_CONTACTS:
                out.println("<div class=\"systemmessage\">");
                out.println(name + " " + Messages.getString("WhatsAppReport.SenderInContacts"));
                break;
            case SENDER_ADDED_TO_CONTACTS:
                out.println("<div class=\"systemmessage\">");
                out.println(name + " " + Messages.getString("WhatsAppReport.SenderAddedToContacts"));
                break;
            case EPHEMERAL_CHANGED:
            case EPHEMERAL_DEFAULT:
            case EPHEMERAL_DURATION_CHANGED:
                out.println("<div class=\"systemmessage\">");
                out.print(name + " ");
                int seconds = message.getDuration();
                if (seconds == 0) {
                    out.print(Messages.getString("WhatsAppReport.EphemeralOff"));
                } else {
                    int days = seconds / 86400;
                    String duration = days > 1 ? days + " " + Messages.getString("WhatsAppReport.Days")
                            : seconds / 3600 + " " + Messages.getString("WhatsAppReport.Hours");
                    if (message.getMessageType() == MessageType.EPHEMERAL_CHANGED) {
                        out.print(Messages.getString("WhatsAppReport.EphemeralOn"));
                    } else if (message.getMessageType() == MessageType.EPHEMERAL_DEFAULT) {
                        out.print(Messages.getString("WhatsAppReport.EphemeralDefault"));
                    } else {
                        out.print(Messages.getString("WhatsAppReport.EphemeralDurationChanged"));
                    }
                    out.println(" " + duration + ".");
                }
                break;
            case EPHEMERAL_SAVE:
                out.println("<div class=\"systemmessage\">");
                out.println(Messages.getString("WhatsAppReport.EphemeralSave") + "<br>");
                break;
            case BLOCKED_CONTACT:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("WhatsAppReport.BlockedContact") + "<br>"); //$NON-NLS-1$
                break;
            case UNBLOCKED_CONTACT:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("WhatsAppReport.UnblockedContact") + "<br>"); //$NON-NLS-1$
                break;
            case BUSINESS_CHAT:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("WhatsAppReport.ChatBusiness") + "<br>"); //$NON-NLS-1$
                break;
            case BUSINESS_OFFICIAL:
                out.println("<div class=\"systemmessage\">");
                out.println(Messages.getString("WhatsAppReport.ChatBusinessOfficial") + "<br>");
                break;
            case BUSINESS_CHANGED_NAME:
                out.println("<div class=\"systemmessage\">");
                out.println(Messages.getString("WhatsAppReport.BusinessChangedName") + "<br>");
                break;
            case STANDARD_CHAT:
                out.println("<div class=\"systemmessage\">");
                out.println(Messages.getString("WhatsAppReport.ChatStandard") + "<br>");
                break;
            case BUSINESS_META_SECURE_SERVICE:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("WhatsAppReport.BusinessSecureService") + "<br>"); //$NON-NLS-1$
                break;
            case BUSINESS_TO_STANDARD:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("WhatsAppReport.BusinessToStandard") + "<br>"); //$NON-NLS-1$
                break;
            case MESSAGES_ENCRYPTED:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.print(lockedIcon);
                out.println(Messages.getString("WhatsAppReport.ChatEncrypted") + "<br>"); //$NON-NLS-1$
                break;
            case MESSAGES_NOW_ENCRYPTED:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.print(lockedIcon);
                if (isGroupOrChannel) {
                    out.println(Messages.getString("WhatsAppReport.GroupNowEncrypted") + "<br>"); //$NON-NLS-1$
                } else {
                    out.println(Messages.getString("WhatsAppReport.ChatNowEncrypted") + "<br>"); //$NON-NLS-1$
                }
                break;
            case MISSED_VIDEO_CALL:
                if (message.isFromMe()) {
                    isToSpecial = true;
                    out.println(bubbleToSpecial);
                } else {
                    isFromSpecial = true;
                    out.println(bubbleFromSpecial);
                    if (!name.isEmpty() && isGroupOrChannel) {
                        out.println("<span class=\"name_call\">" + name + "</span><br>");
                    }
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
                    if (!name.isEmpty() && isGroupOrChannel) {
                        out.println("<span class=\"name_call\">" + name + "</span><br>");
                    }
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
                    if (!name.isEmpty() && isGroupOrChannel) {
                        out.println("<span class=\"name_call\">" + name + "</span><br>");
                    }
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
                    if (!name.isEmpty() && isGroupOrChannel) {
                        out.println("<span class=\"name_call\">" + name + "</span><br>");
                    }
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
                    if (!name.isEmpty() && isGroupOrChannel) {
                        out.println("<span class=\"name_call\">" + name + "</span><br>");
                    }
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
                    if (!name.isEmpty() && isGroupOrChannel) {
                        out.println("<span class=\"name_call\">" + name + "</span><br>");
                    }
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
                    if (!name.isEmpty() && isGroupOrChannel) {
                        out.println("<span class=\"name_call\">" + name + "</span><br>");
                    }
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
                    if (!name.isEmpty() && isGroupOrChannel) {
                        out.println("<span class=\"name_call\">" + name + "</span><br>");
                    }
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
                    if (!name.isEmpty() && isGroupOrChannel) {
                        out.println("<span class=\"name_call\">" + name + "</span><br>");
                    }
                }
                out.println(Messages.getString("WhatsAppReport.VideoCall")); //$NON-NLS-1$
                if (message.getDuration() > 0) {
                    out.println("<br>" + Messages.getString("WhatsAppReport.Duration") + ": " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                            + formatMMSS(message.getDuration()));
                }
                break;
            case VOICE_CALL:
                if (message.isFromMe()) {
                    isToSpecial = true;
                    out.println(bubbleToSpecial);
                } else {
                    isFromSpecial = true;
                    out.println(bubbleFromSpecial);
                    if (!name.isEmpty() && isGroupOrChannel) {
                        out.println("<span class=\"name_call\">" + name + "</span><br>");
                    }
                }
                out.println(Messages.getString("WhatsAppReport.VoiceCall") + "<br>"); //$NON-NLS-1$ //$NON-NLS-2$
                out.println(Messages.getString("WhatsAppReport.Duration") + ": " + formatMMSS(message.getDuration())); //$NON-NLS-1$ //$NON-NLS-2$
                break;
            case GROUP_CREATED:
                out.println("<div class=\"systemmessage\">");
                out.println(Messages.getString("WhatsAppReport.GroupCreated") + " " + name + "<br>");
                if (notNullNorBlank(message.getData())) {
                    out.print("\"" + format(message.getData()) + "\"<br>");
                }
                break;
            case GROUP_ADDED_TO_COMMUNITY:
            case GROUP_REMOVED_FROM_COMMUNITY:
                out.println("<div class=\"systemmessage\">");
                if (message.getMessageType() == MessageType.GROUP_ADDED_TO_COMMUNITY) {
                    out.print(Messages.getString("WhatsAppReport.GroupAddedToCommunity"));
                } else {
                    out.print(Messages.getString("WhatsAppReport.GroupRemovedFromCommunity"));
                }
                if (notNullNorBlank(message.getData())) {
                    out.print(":<br>" + format(message.getData()));
                } else {
                    out.print(".");
                }
                out.println("<br>");
                break;
            case COMMUNITY_NOT_AVAILABLE:
                out.println("<div class=\"systemmessage\">");
                out.println(name + " " + Messages.getString("WhatsAppReport.CommunityNotAvailable") + ".<br>");
                break;
            case GROUP_NOT_PART_OF_COMMUNITY:
                out.println("<div class=\"systemmessage\">");
                out.println(name + " " + Messages.getString("WhatsAppReport.GroupNotPartOfCommunity") + ".<br>");
                break;
            case COMMUNITY_RENAMED:
                out.println("<div class=\"systemmessage\">");
                out.println(name + " " + Messages.getString("WhatsAppReport.CommunityRenamed") + ".<br>");
                break;
            case ANY_COMMUNITY_MEMBER_CAN_JOIN_GROUP:
                out.println("<div class=\"systemmessage\">");
                out.println(Messages.getString("WhatsAppReport.AnyCommunityMemberCanJoinThisGroup") + "<br>");
                break;
            case COMMUNITY_MANAGEMENT_ACTION:
                out.println("<div class=\"systemmessage\">");
                out.println(Messages.getString("WhatsAppReport.CommunityManagementAction") + "<br>");
                break;
            case COMMUNITY_WELCOME:
                out.println("<div class=\"systemmessage\">");
                out.println(Messages.getString("WhatsAppReport.CommunityWelcome") + "<br>");
                break;
            case COMMUNITY_CHANGED_ONLY_ADMINS_CAN_ADD:
                out.println("<div class=\"systemmessage\">");
                out.println(Messages.getString("WhatsAppReport.CommunityChangedOnlyAdminsCanAdd") + "<br>");
                break;
            case COMMUNITY_CHANGED_ALL_MEMBERS_CAN_ADD:
                out.println("<div class=\"systemmessage\">");
                out.println(Messages.getString("WhatsAppReport.CommunityChangedAllMembersCanAdd") + "<br>");
                break;
            case NEW_PARTICIPANTS_NEED_ADMIN_APPROVAL:
                out.println("<div class=\"systemmessage\">");
                out.println(Messages.getString("WhatsAppReport.NewParticipantsNeedAdminApproval") + "<br>");
                break;
            case USER_ADDED_TO_GROUP:
            case USER_ADDED_TO_COMMUNITY:
            case USER_COMMUNITY_ADMIN:
            case USER_JOINED_GROUP_FROM_COMMUNITY:
            case USER_JOINED_GROUP_FROM_INVITATION:
            case USER_JOINED_GROUP_FROM_LINK:
            case USER_REQUEST_TO_ADD_TO_GROUP:
            case USER_REMOVED_FROM_GROUP:
                List<String> users = message.getUsersAction();
                out.println("<div class=\"systemmessage\">");
                out.print(name + " ");
                boolean selfAction = false;
                if (users.size() == 1) {
                    String user = users.get(0);
                    user = getBestContactName(user == null || user.isBlank(), user, contactsDirectory, account);
                    if (user != null && user.equals(name)) {
                        if (message.getMessageType() == MessageType.USER_REMOVED_FROM_GROUP) {
                            out.print(Messages.getString("WhatsAppReport.RemovedGroup"));
                            selfAction = true;
                        } else if (message.getMessageType() == MessageType.USER_ADDED_TO_GROUP) {
                            out.print(Messages.getString("WhatsAppReport.AddedToGroup"));
                            selfAction = true;
                        } else if (message.getMessageType() == MessageType.USER_ADDED_TO_COMMUNITY) {
                            out.print(Messages.getString("WhatsAppReport.AddedToCommunity"));
                            selfAction = true;
                        } else if (message.getMessageType() == MessageType.USER_COMMUNITY_ADMIN) {
                            out.print(Messages.getString("WhatsAppReport.CommunityAdmin"));
                            selfAction = true;
                        }
                    }
                }
                if (!selfAction) {
                    if (message.getMessageType() == MessageType.USER_REMOVED_FROM_GROUP) {
                        out.print(Messages.getString("WhatsAppReport.UserRemovedGroup"));
                    } else if (message.getMessageType() == MessageType.USER_JOINED_GROUP_FROM_COMMUNITY) {
                        out.print(Messages.getString("WhatsAppReport.UserJoinedGroupCommunity"));
                    } else if (message.getMessageType() == MessageType.USER_JOINED_GROUP_FROM_INVITATION) {
                        out.print(Messages.getString("WhatsAppReport.UserJoinedGroupInvitation"));
                    } else if (message.getMessageType() == MessageType.USER_JOINED_GROUP_FROM_LINK) {
                        out.print(Messages.getString("WhatsAppReport.UserJoinedGroupLink"));
                    } else if (message.getMessageType() == MessageType.USER_ADDED_TO_COMMUNITY) {
                        out.print(Messages.getString("WhatsAppReport.UserAddedToCommunity"));
                    } else if (message.getMessageType() == MessageType.USER_ADDED_TO_GROUP) {
                        out.print(Messages.getString("WhatsAppReport.UserAddedToGroup"));
                    } else if (message.getMessageType() == MessageType.USER_COMMUNITY_ADMIN) {
                        out.print(Messages.getString("WhatsAppReport.UserCommunityAdmin"));
                    } else if (message.getMessageType() == MessageType.USER_REQUEST_TO_ADD_TO_GROUP) {
                        out.print(Messages.getString("WhatsAppReport.UserRequestToAddToGroup"));
                    }
                    boolean first = true;
                    for (int i = 0; i < users.size(); i++) {
                        String user = users.get(i);
                        String contactName = getBestContactName(user == null || user.isBlank(), user, contactsDirectory,
                                account);
                        if (!name.equals(contactName)) {
                            out.print(first ? ": " : ", ");
                            out.print(contactName);
                            first = false;
                        }
                    }
                }
                out.print(".<br>");
                if (notNullNorBlank(message.getData())) {
                    out.print(format(message.getData()) + "<br>");
                }
                out.println();
                break;
            case USER_LEFT_GROUP:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("WhatsAppReport.UserLeftGroup") + ": " + name + "<br>");
                break;
            case GROUP_ICON_CHANGED:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("WhatsAppReport.GroupIconChanged") + "<br>"); //$NON-NLS-1$
                break;
            case GROUP_ICON_DELETED:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("WhatsAppReport.GroupIconDeleted") + "<br>"); //$NON-NLS-1$
                break;
            case COMMUNITY_DESCRIPTION_CHANGED:
                out.println("<div class=\"systemmessage\">");
                out.println(Messages.getString("WhatsAppReport.CommunityDescriptionChanged") + "<br>");
                break;
            case GROUP_DESCRIPTION_CHANGED:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("WhatsAppReport.GroupDescriptionChanged") + "<br>"); //$NON-NLS-1$
                break;
            case GROUP_DESCRIPTION_DELETED:
                out.println("<div class=\"systemmessage\">");
                out.print(name + " ");
                out.println(Messages.getString("WhatsAppReport.GroupDescriptionDeleted") + "<br>");
                break;
            case GROUP_NAME_CHANGED:
                out.println("<div class=\"systemmessage\">");
                out.print(name + " ");
                out.print(Messages.getString("WhatsAppReport.GroupNameChanged"));
                out.println(" \"" + format(message.getData()) + "\"<br>");
                break;
            case SUBJECT_CHANGED:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("WhatsAppReport.SubjectChanged") + "<br>"); //$NON-NLS-1$
                break;
            case YOU_ADMIN:
                out.println("<div class=\"systemmessage\">"); //$NON-NLS-1$
                out.println(Messages.getString("WhatsAppReport.YouAdmin") + "<br>"); //$NON-NLS-1$
                break;
            case YOU_NOT_ADMIN:
                out.println("<div class=\"systemmessage\">");
                out.println(Messages.getString("WhatsAppReport.YouNotAdmin") + "<br>");
                break;
            case OVER_256_MEMBERS_ONLY_ADMINS_CAN_EDIT:
                out.println("<div class=\"systemmessage\">");
                out.println(Messages.getString("WhatsAppReport.Over256MembersOnlyAdminsCanEdit") + "<br>");
                break;
            case SECURITY_NOTIFICATIONS_NO_LONGER_AVAILABLE:
                out.println("<div class=\"systemmessage\">");
                out.println(Messages.getString("WhatsAppReport.SecurityNotificationsNoLongerAvailable") + "<br>");
                break;
            case CONTACTED_FIND_BUSINESSES:
                out.println("<div class=\"systemmessage\">");
                out.println(Messages.getString("WhatsAppReport.ContactedFindBusinesses", name) + "<br>");
                break;
            case GROUP_CHANGED_ADMIN_APPROVAL_OFF:
                out.println("<div class=\"systemmessage\">");
                out.print(name + " ");
                out.println(Messages.getString("WhatsAppReport.GroupChangedAdminApprovalOff") + "<br>");
                break;
            case USER_ADMIN:
                out.println("<div class=\"systemmessage\">");
                out.print(name + " ");
                out.println(Messages.getString("WhatsAppReport.UserAdmin") + "<br>");
                break;
            case GROUP_CHANGED_ALL_MEMBERS_CAN_ADD:
                out.println("<div class=\"systemmessage\">");
                out.println(name + " " + Messages.getString("WhatsAppReport.GroupChangedAllMembersCanAdd") + "<br>");
                break;
            case GROUP_CHANGED_ONLY_ADMINS_CAN_ADD:
                out.println("<div class=\"systemmessage\">");
                out.println(name + " " + Messages.getString("WhatsAppReport.GroupChangedOnlyAdminsCanAdd") + "<br>");
                break;
            case GROUP_CHANGED_ALL_MEMBERS_CAN_SEND:
                out.println("<div class=\"systemmessage\">");
                out.println(name + " " + Messages.getString("WhatsAppReport.GroupChangedAllMembersCanSend") + "<br>");
                break;
            case GROUP_CHANGED_ONLY_ADMINS_CAN_SEND:
                out.println("<div class=\"systemmessage\">");
                out.println(name + " " + Messages.getString("WhatsAppReport.GroupChangedOnlyAdminsCanSend") + "<br>");
                break;
            case GROUP_CHANGED_ALL_MEMBERS_CAN_EDIT:
                out.println("<div class=\"systemmessage\">");
                out.println(name + " " + Messages.getString("WhatsAppReport.GroupChangedAllMembersCanEdit") + "<br>");
                break;
            case GROUP_CHANGED_ONLY_ADMINS_CAN_EDIT:
                out.println("<div class=\"systemmessage\">");
                out.println(name + " " + Messages.getString("WhatsAppReport.GroupChangedOnlyAdminsCanEdit") + "<br>");
                break;
            case GROUP_ONLY_ADMINS_CAN_SEND:
                out.println("<div class=\"systemmessage\">");
                out.println(Messages.getString("WhatsAppReport.GroupOnlyAdminsCanSend") + "<br>");
                break;
            case RESET_GROUP_LINK:
                out.println("<div class=\"systemmessage\">");
                out.print(name + " ");
                out.println(Messages.getString("WhatsAppReport.ResetGroupLink") + ".<br>");
                break;
            case CHANGED_DEVICE:
                out.println("<div class=\"systemmessage\">");
                out.println(name + " " + Messages.getString("WhatsAppReport.ChangedDevice") + "<br>");
                break;
            case CHANGED_NUMBER_TO:
                out.println("<div class=\"systemmessage\">");
                users = message.getUsersAction();
                if (users.size() >= 2) {
                    out.println(getBestContactName(false, users.get(0), contactsDirectory, account) + " "
                            + Messages.getString("WhatsAppReport.ChangedNumberTo") + " "
                            + getBestContactName(false, users.get(1), contactsDirectory, account) + ".<br>");
                }
                break;
            case CHANGED_NUMBER_CHATTING_WITH_NEW:
                out.println("<div class=\"systemmessage\">");
                out.println(name + " " + Messages.getString("WhatsAppReport.ChangedNumberChattingWithNew") + "<br>");
                break;
            case CHANGED_NUMBER_CHATTING_WITH_OLD:
                out.println("<div class=\"systemmessage\">");
                out.println(name + " " + Messages.getString("WhatsAppReport.ChangedNumberChattingWithOld") + "<br>");
                break;
            case CHAT_ADDED_PRIVACY:
                out.println("<div class=\"systemmessage\">");
                out.println(Messages.getString("WhatsAppReport.ChatAddedPrivacy") + "<br>");
                break;
            case CHANNEL_ADDED_PRIVACY:
                out.println("<div class=\"systemmessage\">");
                out.println(Messages.getString("WhatsAppReport.ChannelAddedPrivacy") + "<br>");
                break;
            case CHANNEL_CREATED:
                out.println("<div class=\"systemmessage\">");
                out.println(Messages.getString("WhatsAppReport.ChannelCreated") + "<br>");
                break;
            case USER_JOINED_WHATSAPP:
                out.println("<div class=\"systemmessage\">");
                out.println(name + " " + Messages.getString("WhatsAppReport.UserJoinedWhatsApp") + ".<br>");
                break;
            case PINNED_MESSAGE:
                out.println("<div class=\"systemmessage\">");
                out.println(name + " " + Messages.getString("WhatsAppReport.PinnedMessage") + ".<br>");
                break;

            default:
                IItemReader mediaItem = null;
                byte[] thumb = null;
                String query = null;
                String exportPath = null;

                if (message.isFromMe()) {
                    out.println(bubbleTo);
                    isTo = true;
                } else {
                    out.println(bubbleFrom);
                    isFrom = true;
                }

                if (!name.isEmpty()) {
                    out.println("<span class=\"name\">" + name + "</span><br>");
                }

                if (message.isForwarded()) {
                    out.println(forwardedIcon + "<span class=\"fwd\">" + Messages.getString("WhatsAppReport.Forwarded")
                            + "</span><br>");
                }

                if (message.isQuoted()) {
                    printQuote(out, message, contactsDirectory, account);
                }

                switch (message.getMessageType()) {
                    case TEXT_MESSAGE:
                    case TEMPLATE_QUOTE:
                    case UI_ELEMENTS_QUOTE:
                    case UI_ELEMENTS:
                        // Some textual messages may have thumbs, URL and UIElements
                        printThumb(out, message);
                        if (notNullNorBlank(message.getUrl())) {
                            out.print(format(message.getUrl()) + "<br>");
                        }
                        if (notNullNorBlank(message.getData())) {
                            out.print(format(message.getData()) + "<br>");
                        }
                        if (notNullNorBlank(message.getUiElements())) {
                            out.print(formatUiElements(message.getUiElements()));
                        }
                        break;
                    case PRODUCT_MESSAGE:
                    case ORDER_MESSAGE:
                        printThumb(out, message);
                        if (notNullNorBlank(message.getData())) {
                            out.print(format(message.getData()) + "<br>");
                        }
                        MessageProduct product = message.getProduct();
                        if (product != null) {
                            String seller = getBestContactName(false, product.getSeller(), contactsDirectory, account);
                            out.println(formatProduct(product, seller));
                        }
                        break;
                    case UNKNOWN_MEDIA_MESSAGE:
                        if (message.getMediaCaption() != null) {
                            out.print("<i>" + Messages.getString("WhatsAppReport.UnknownMediaMessage") + "</i><br>");
                            out.println(format(message.getMediaCaption()) + "<br>"); //$NON-NLS-1$
                        }
                        break;
                    case URL_MESSAGE:
                        printThumb(out, message);
                        out.println(formatURL(message) + "<br>");
                        break;
                    case LOCATION_MESSAGE:
                    case SHARE_LOCATION_MESSAGE:
                        printThumb(out, message);
                        out.println(formatLocation(message) + "<br>");
                        break;
                    case CONTACT_MESSAGE:
                        out.println("<b>" + Messages.getString("WhatsAppReport.Contact") + "</b><br>");
                        for (String c : message.getVcards()) {
                            if (notNullNorBlank(c)) {
                                VCardParser.printHtmlFromString(out, c);
                            }
                        }
                        break;
                    case GROUP_INVITE:
                        printThumb(out, message);
                        out.println("<b>" + Messages.getString("WhatsAppReport.GroupInvite") + "</b><br>");
                        if (message.getGroupInviteName() != null) {
                            out.println("<b>" + format(message.getGroupInviteName()) + "</b><br>");
                        }
                        if (notNullNorBlank(message.getData())) {
                            out.println(format(message.getData()) + "<br>");
                        }
                        break;
                    case TEMPLATE_MESSAGE:
                        printThumb(out, message);
                        out.println(formatTemplate(message) + "<br>");
                        break;
                    case POLL_MESSAGE:
                        printThumb(out, message);
                        out.println("<b>" + Messages.getString("WhatsAppReport.Poll") + "</b><br>");
                        if (notNullNorBlank(message.getData())) {
                            out.println(format(message.getData()) + "<br>");
                        }
                        out.println("<ul>");
                        List<PollOption> pollOptions = message.getPollOptions();
                        for (PollOption opt : pollOptions) {
                            if (opt.getName() != null) {
                                out.print("<li>" + format(opt.getName()));
                                int total = opt.getTotal();
                                if (total == 1) {
                                    out.print(" [1 " + Messages.getString("WhatsAppReport.Vote") + "]");
                                } else if (total > 1) {
                                    out.print(" [" + total + " " + Messages.getString("WhatsAppReport.Votes") + "]");
                                }
                                out.print("</li>");
                            }
                        }
                        out.println("</ul>");
                        break;
                    case DELETED_MESSAGE:
                        out.println(
                                "<i>" + deletedIcon + Messages.getString("WhatsAppReport.MessageDeleted") + "</i><br>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        break;
                    case DELETED_BY_ADMIN:
                        out.print("<i>" + deletedIcon + Messages.getString("WhatsAppReport.MessageDeletedByAdmin"));
                        if (!message.getUsersAction().isEmpty()) {
                            out.print(" (" + getBestContactName(false, message.getUsersAction().get(0),
                                    contactsDirectory, account) + ")");
                        }
                        out.println("</i><br>");
                        break;
                    case DELETED_BY_SENDER:
                        out.println("<i>" + deletedIcon + Messages.getString("WhatsAppReport.MessageDeletedBySender") //$NON-NLS-1$ //$NON-NLS-2$
                                + "</i><br>"); //$NON-NLS-1$
                        break;
                    case WAITING_MESSAGE:
                        out.println("<i>" + Messages.getString("WhatsAppReport.WaitingMessage") + "</i><br>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        break;
                    case AUDIO_MESSAGE:
                    case VIEW_ONCE_AUDIO_MESSAGE:
                    case VIDEO_MESSAGE:
                    case VIEW_ONCE_VIDEO_MESSAGE:
                    case GIF_MESSAGE:
                    case IMAGE_MESSAGE:
                    case VIEW_ONCE_IMAGE_MESSAGE:
                    case DOC_MESSAGE:
                    case STICKER_MESSAGE:
                        mediaItem = message.getMediaItem();
                        // query is already html escaped
                        query = message.getMediaQuery();

                        if (mediaItem != null) {
                            exportPath = iped.parsers.util.Util.getExportPath(mediaItem);
                        }

                        if (query != null) {
                            if (mediaItem != null) {
                                out.println("<input class=\"check\" type=\"checkbox\" onclick=\"app.check(" + query
                                        + ",this.checked)\"" + " name=\"" + mediaItem.getHash() + "\">");
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
                                    || message.getMessageType() == MessageType.VIEW_ONCE_AUDIO_MESSAGE
                                    || message.getMessageType() == MessageType.VIDEO_MESSAGE
                                    || message.getMessageType() == MessageType.VIEW_ONCE_VIDEO_MESSAGE
                                    || message.getMessageType() == MessageType.GIF_MESSAGE) {
                                String source = iped.parsers.util.Util.getSourceFileIfExists(mediaItem).orElse("");
                                if (message.getMessageType() == MessageType.AUDIO_MESSAGE
                                        || message.getMessageType() == MessageType.VIEW_ONCE_AUDIO_MESSAGE) {
                                    out.println(Messages.getString("WhatsAppReport.AudioMessageTitle") + "<br>"); //$NON-NLS-1$
                                    out.println("<div class=\"audioImg iped-audio\" "
                                            + " title=\"Audio\" " + "data-src1=\"" + format(exportPath) + "\" "
                                            + "data-src2=\""
                                            + format(source) + "\" >");
                                    out.print("<span class=\"duration\"> " + formatDuration(message.getDuration())
                                            + "</span>");
                                    out.print("</div>");
                                    out.println("</a><br>");
                                } else {
                                    out.println(Messages.getString("WhatsAppReport.VideoMessageTitle") + "<br>"); //$NON-NLS-1$
                                    if (thumb != null) {
                                        out.print("<img class=\"thumb iped-video\" src=\""); //$NON-NLS-1$
                                        out.print("data:image/jpg;base64," + Util.encodeBase64(thumb) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                                        out.print(" data-src1=\"" + format(exportPath) + "\"");
                                        out.print(" data-src2=\"" + format(source) + "\"");
                                        out.println(" title=\"" + getTitle(message) + "\">"); //$NON-NLS-1$ //$NON-NLS-2$

                                    } else {
                                        out.println("<div class=\"videoImg iped-video\" title=\"Video\""); //$NON-NLS-1$
                                        out.println(" data-src1=\"" + format(exportPath) + "\"");
                                        out.println(" data-src2=\"" + format(source) + "\" ></div>");
                                    }
                                    out.println("</a><br>"); //$NON-NLS-1$
                                }
                                if (mediaItem.getMetadata().get(ExtraProperties.DOWNLOADED_DATA) != null) {
                                    out.println(
                                            "<b>" + Messages.getString("ReportGenerator.DownloadedFile") + "</b><br>");
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
                                    out.println("</i><br>"); //$NON-NLS-1$
                                }
                            } else {
                                if (thumb != null) {
                                    if (getTitle(message).equals("video")) //$NON-NLS-1$
                                        out.println(Messages.getString("WhatsAppReport.Video") + ":<br>"); //$NON-NLS-1$ //$NON-NLS-2$
                                    out.print("<img class=\"thumb\" src=\""); //$NON-NLS-1$
                                    out.print("data:image/jpg;base64," + Util.encodeBase64(thumb) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                                    out.println(" title=\"" + getTitle(message) + "\">"); //$NON-NLS-1$ //$NON-NLS-2$

                                } else {
                                    if (message.getMessageType() == MessageType.IMAGE_MESSAGE
                                            || message.getMessageType() == MessageType.VIEW_ONCE_IMAGE_MESSAGE) {
                                        out.println("<div class=\"imageImg\" title=\"Image\"></div>"); //$NON-NLS-1$
                                    } else {
                                        out.print(Messages.getString("WhatsAppReport.Attachment"));
                                        out.println("<br><div class=\"attachImg\" title=\"Doc\"></div>"); //$NON-NLS-1$
                                    }
                                }
                                out.println("</a><br>"); //$NON-NLS-1$
                                if (mediaItem.getMetadata().get(ExtraProperties.DOWNLOADED_DATA) != null) {
                                    out.println(
                                            "<b>" + Messages.getString("ReportGenerator.DownloadedFile") + "</b><br>");
                                }
                            }
                        } else { // mediaItem is null (media file not found)
                            switch (message.getMessageType()) {
                                case AUDIO_MESSAGE:
                                case VIEW_ONCE_AUDIO_MESSAGE:
                                    out.println("<div class=\"audioImg\" title=\"Audio\">");
                                    out.print("<span class=\"duration\"> " + formatDuration(message.getDuration())
                                            + "</span>");
                                    out.println("</div>");
                                    break;
                                case VIDEO_MESSAGE:
                                case VIEW_ONCE_VIDEO_MESSAGE:
                                case GIF_MESSAGE:
                                    if (thumb != null) {
                                        out.println(Messages.getString("WhatsAppReport.Video") + ":<br>");
                                        out.print("<img class=\"thumb\" src=\""); //$NON-NLS-1$
                                        out.print("data:image/jpg;base64," + Util.encodeBase64(thumb) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                                        out.print(" title=\"" + getTitle(message) + "\">"); //$NON-NLS-1$ //$NON-NLS-2$
                                    } else {
                                        out.println("<div class=\"videoImg\" title=\"Video\">");
                                        out.print("<span class=\"duration\"> " + formatDuration(message.getDuration())
                                                + "</span>");
                                        out.println("</div>");
                                    }
                                    break;
                                case STICKER_MESSAGE:
                                case IMAGE_MESSAGE:
                                case VIEW_ONCE_IMAGE_MESSAGE:
                                case DOC_MESSAGE:
                                    if (thumb != null) {
                                        if (getTitle(message).equals("video")) //$NON-NLS-1$
                                            out.println(Messages.getString("WhatsAppReport.Video") + ":<br>"); //$NON-NLS-1$ //$NON-NLS-2$
                                        out.print("<img class=\"thumb\" src=\""); //$NON-NLS-1$
                                        out.print("data:image/jpg;base64," + Util.encodeBase64(thumb) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                                        out.println(" title=\"" + getTitle(message) + "\">"); //$NON-NLS-1$ //$NON-NLS-2$

                                    } else if (message.getMessageType() == MessageType.DOC_MESSAGE) {
                                        out.print(Messages.getString("WhatsAppReport.Attachment"));
                                        out.println("<br><div class=\"attachImg\" title=\"Doc\"></div>"); //$NON-NLS-1$
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
                        if (thumb != null) {
                            out.print("<br>");
                        }
                        if (notNullNorBlank(message.getMediaCaption())) {
                            out.print(format(message.getMediaCaption()) + "<br>");
                        }
                        out.println();
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

                    String reactionName = getBestContactName(a.isFromMe(), a.getRemoteResource(), contactsDirectory,
                            account);
                    aoDetails.append(' ');
                    aoDetails.append(reactionName);

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

        out.print("<span class=\"time\">"); //$NON-NLS-1$

        if (message.getMessageType() == MessageType.VIEW_ONCE_IMAGE_MESSAGE
                || message.getMessageType() == MessageType.VIEW_ONCE_VIDEO_MESSAGE
                || message.getMessageType() == MessageType.VIEW_ONCE_AUDIO_MESSAGE) {
            out.print(viewOnceIcon);
        }

        out.print(timeFormat.format(message.getTimeStamp()));
        boolean hasStatus = false;
        if (message.isFromMe() && message.getMessageStatus() != null) {
            switch (message.getMessageStatus()) {
                case MESSAGE_UNSENT:
                    out.print("<div class=\"unsent\"></div>"); //$NON-NLS-1$
                    hasStatus = true;
                    break;
                case MESSAGE_SENT:
                    out.print("<div class=\"sent\"></div>"); //$NON-NLS-1$
                    hasStatus = true;
                    break;
                case MESSAGE_DELIVERED:
                    out.print("<div class=\"delivered\"></div>"); //$NON-NLS-1$
                    hasStatus = true;
                    break;
                case MESSAGE_VIEWED:
                    out.print("<div class=\"viewed\"></div>"); //$NON-NLS-1$
                    hasStatus = true;
                    break;
            }
        }
        Date edit = message.getEditTimeStamp();
        if (edit != null) {
            out.print("<br>" + Messages.getString("WhatsAppReport.EditedOn") + " " + timeFormat.format(edit));
            if (hasStatus) {
                out.print("<div class=\"edit\"></div>");
            }
        }
        out.println("</span>"); //$NON-NLS-1$
        if (message.isDeleted()) {
            out.println("<br><span class=\"recovered\">"); //$NON-NLS-1$
            out.println("<i>" + Messages.getString("WhatsAppReport.MessageDeletedRecovered") + "</i>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            out.println("<div class=\"deletedIcon\"></div>"); //$NON-NLS-1$
            out.println("</span>"); //$NON-NLS-1$
        }

        if (message.getRecoveredFrom() != null) {
            out.println("<br><span class=\"recovered\">"); //$NON-NLS-1$
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

    private void printThumb(PrintWriter out, Message message) {
        byte[] thumb = message.getThumbData();
        IItemReader mediaItem = message.getMediaItem();
        if (mediaItem != null) {
            byte[] generatedThumb = mediaItem.getThumb();
            if (generatedThumb != null) {
                thumb = generatedThumb;
            }
        }
        if (thumb != null) {
            out.print("<img class=\"thumb\" src=\"");
            out.print("data:image/jpg;base64," + Util.encodeBase64(thumb) + "\"><br>");
        }
    }

    private String formatURL(Message message) {
        StringBuilder sb = new StringBuilder();
        if (notNullNorBlank(message.getUrl())) {
            sb.append("<b>").append(format(message.getUrl())).append("</b>");
        }
        if (notNullNorBlank(message.getMediaCaption()) && !message.getMediaCaption().equals(message.getData())
                && !message.getMediaCaption().equals(message.getUrl())) {
            if (sb.length() > 0) {
                sb.append("<br>");
            }
            sb.append("[").append(format(message.getMediaCaption())).append("]");
        }
        if (notNullNorBlank(message.getData()) && !message.getData().equals(message.getUrl())) {
            if (sb.length() > 0) {
                sb.append("<br>");
            }
            sb.append(format(message.getData()));
        }
        return sb.toString();
    }

    private String formatLocation(Message message) {
        StringBuilder sb = new StringBuilder();

        String key = message.getMessageType() == MessageType.LOCATION_MESSAGE ? "LocationMessage"
                : "SharedLocationMessage";
        sb.append("<b>").append(locationIcon).append(Messages.getString("WhatsAppReport." + key)).append("</b><br>");

        sb.append(Messages.getString("WhatsAppReport.Latitude")).append(": ");
        sb.append(message.getLatitude()).append("<br>");

        sb.append(Messages.getString("WhatsAppReport.Longitude")).append(": ");
        sb.append(message.getLongitude());

        if (notNullNorBlank(message.getAddress())) {
            sb.append("<br>").append(format(message.getAddress()));
        }
        if (notNullNorBlank(message.getData())) {
            sb.append("<br>").append(format(message.getData()));
        }
        return sb.toString();
    }

    private void printQuote(PrintWriter out, Message message, WAContactsDirectory contactsDirectory,
            WAAccount account) {
        String quoteClass = message.isFromMe() ? "quoteTo" : "quoteFrom";
        Message messageQuote = message.getMessageQuote();

        if (messageQuote != null) {
            String quoteData = messageQuote.getData();
            String quoteClick = "onclick=\"goToAnchorId(" + messageQuote.getId() + ");\"";
            String quoteIcon = "";
            String quoteDuration = formatDuration(messageQuote.getDuration());
            String quoteUser = getBestContactName(messageQuote, contactsDirectory, account);
            byte[] quoteThumb = messageQuote.getThumbData();

            IItemReader quoteMediaItem = messageQuote.getMediaItem();
            if (quoteMediaItem != null) {
                byte[] quoteGeneratedThumb = quoteMediaItem.getThumb();
                if (quoteGeneratedThumb != null) {
                    quoteThumb = quoteGeneratedThumb;
                }
            }

            String quoteMsg = "";
            if (notNullNorBlank(messageQuote.getMediaCaption())) {
                quoteMsg += format(messageQuote.getMediaCaption());
            }
            if (notNullNorBlank(quoteData) && !quoteMsg.equals(quoteData)) {
                if (!quoteMsg.isEmpty()) {
                    quoteMsg += "<br>";
                }
                quoteMsg += format(quoteData);
            }

            String quoteEnd = "</span></div>";
            String privateGroupName = messageQuote.getQuotePrivateGroupName();
            switch (messageQuote.getMessageQuotedType()) {
                case QUOTE_NOT_FOUND:
                    quoteEnd = "</span><br><span style=\"float:none\" class=\"recovered\"><div class=\"deletedIcon\"></div><i>"
                            + Messages.getString("WhatsAppReport.QuoteNotFound") + "</i>" + quoteEnd;
                    break;
                case QUOTE_STATUS:
                    quoteEnd = "</span><br><span style=\"float:none\" class=\"outside\"><div class=\"statusIcon\"></div><i>"
                            + Messages.getString("WhatsAppReport.QuoteStaus") + "</i>" + quoteEnd;
                    break;
                case QUOTE_CATALOG:
                    quoteEnd = "</span><br><span style=\"float:none\" class=\"outside\"><div class=\"catalogIcon\"></div><i>"
                            + Messages.getString("WhatsAppReport.QuoteCatalog") + "</i>" + quoteEnd;
                    break;
                case QUOTE_PRIVACY_GROUP:
                    quoteEnd = "</span><br><span style=\"float:none\" class=\"outside\"><div class=\"privacyIcon\"></div><i>"
                            + Messages.getString("WhatsAppReport.QuotePrivacy") + "</i>" + quoteEnd;
                    if (privateGroupName != null && !privateGroupName.isEmpty()) {
                        String ms = Messages.getString("WhatsAppReport.QuotePrivacyMessage") + ": " + privateGroupName
                                + "</br> " + Messages.getString("WhatsAppReport.ReferenceId") + " "
                                + messageQuote.getId();
                        quoteClick = "onclick=\"showMessage('" + ms + "');\"";
                    }
                    break;
                case QUOTE_PRIVACY_GROUP_NOT_FOUND:
                    quoteEnd = "</span><br><span style=\"float:none\" class=\"recovered\"><div class=\"privacyDeleteIcon\"></div><i>"
                            + Messages.getString("WhatsAppReport.QuotePrivacyNotFound") + "</i>" + quoteEnd;
                    String ms = "";
                    if (privateGroupName != null && !privateGroupName.isEmpty()) {
                        ms = Messages.getString("WhatsAppReport.QuotePrivacyMessage") + ": " + privateGroupName
                                + "</br> ";
                    }
                    ms += Messages.getString("WhatsAppReport.QuoteNotFound");
                    quoteClick = "onclick=\"showMessage('" + ms + "');\"";
                    break;
                default:
                    break;
            }

            switch (messageQuote.getMessageType()) {
                case VIEW_ONCE_AUDIO_MESSAGE:
                case AUDIO_MESSAGE:
                    if (quoteData == null || quoteData.isEmpty()) {
                        quoteData = Messages.getString("WhatsAppReport.Audio");
                    }
                    quoteIcon = "\uD83C\uDFA7";
                    out.print("<div class=\"" + quoteClass + "\" " + quoteClick
                            + "><div style=\"display:table-cell;\"><span class=\"quoteUser\">" + quoteUser
                            + "</span><br><span class=\"quoteMsg\">" + quoteIcon + " " + quoteMsg + " " + quoteDuration
                            + quoteEnd);
                    break;

                case VIEW_ONCE_VIDEO_MESSAGE:
                case VIDEO_MESSAGE:
                case GIF_MESSAGE:
                    quoteIcon = "\uD83D\uDCF9";
                    if (quoteData == null || quoteData.isEmpty()) {
                        quoteData = Messages.getString("WhatsAppReport.Video");
                    }
                    out.print("<div class=\"" + quoteClass + "\" " + quoteClick
                            + "><div class=\"quoteTop\"><span class=\"quoteUser\">" + quoteUser
                            + "</span><br><span class=\"quoteMsg\">" + quoteIcon + " " + quoteMsg + " " + quoteDuration
                            + quoteEnd);
                    if (quoteThumb != null) {
                        out.print("<div><img class=\"quoteImg\" src=\"");
                        out.print("data:image/jpg;base64," + Util.encodeBase64(quoteThumb) + "\"></div>");
                    } else {
                        out.print("<div class=\"videoImg quoteImg\" title=\"Video\"></div>");
                    }
                    break;

                case VIEW_ONCE_IMAGE_MESSAGE:
                case STICKER_MESSAGE:
                case IMAGE_MESSAGE:
                    quoteIcon = "\uD83D\uDDBC";
                    if (quoteData == null || quoteData.isEmpty()) {
                        quoteData = Messages.getString("WhatsAppReport.Photo");
                    }
                    out.print("<div class=\"" + quoteClass + "\" " + quoteClick
                            + "><div class=\"quoteTop\"><span class=\"quoteUser\">" + quoteUser
                            + "</span><br><span class=\"quoteMsg\">" + quoteIcon + " " + quoteMsg + quoteEnd);
                    if (quoteThumb != null) {
                        out.print("<div><img class=\"quoteImg\" src=\"");
                        out.print("data:image/jpg;base64," + Util.encodeBase64(quoteThumb) + "\"></div>");
                    } else {
                        out.print("<div class=\"imageImg quoteImg\" title=\"Image\"></div>");
                    }
                    break;

                case DOC_MESSAGE:
                    quoteIcon = "\uD83D\uDCC4";
                    if (quoteData == null || quoteData.isEmpty()) {
                        quoteData = Messages.getString("WhatsAppReport.Document");
                    }
                    out.print("<div class=\"" + quoteClass + "\" " + quoteClick
                            + "><div class=\"quoteTop\"><span class=\"quoteUser\">" + quoteUser
                            + "</span><br><span class=\"quoteMsg\">" + quoteIcon + " " + quoteMsg + quoteEnd);
                    if (quoteThumb != null) {
                        out.print("<div><img class=\"quoteImg\" src=\"");
                        out.print("data:image/jpg;base64," + Util.encodeBase64(quoteThumb) + "\"></div>");
                    } else {
                        out.print("<div class=\"attachImg quoteImg\" title=\"Doc\"></div>");
                    }
                    break;

                case TEMPLATE_MESSAGE:
                    out.print("<div class=\"" + quoteClass + "\" " + quoteClick
                            + "><div style=\"display:table-cell;\"><span class=\"quoteUser\">" + quoteUser
                            + "</span><br><span class=\"quoteMsg\">" + formatTemplate(messageQuote) + quoteEnd);
                    break;

                case URL_MESSAGE:
                    out.print("<div class=\"" + quoteClass + "\" " + quoteClick
                            + "><div class=\"quoteTop\"><span class=\"quoteUser\">" + quoteUser
                            + "</span><br><span class=\"quoteMsg\">" + formatURL(messageQuote) + quoteEnd);
                    if (quoteThumb != null) {
                        out.print("<div><img class=\"quoteImg\" src=\"");
                        out.print("data:image/jpg;base64," + Util.encodeBase64(quoteThumb) + "\"></div>");
                    }
                    break;

                case LOCATION_MESSAGE:
                    out.print("<div class=\"" + quoteClass + "\" " + quoteClick
                            + "><div class=\"quoteTop\"><span class=\"quoteUser\">" + quoteUser
                            + "</span><br><span class=\"quoteMsg\">" + formatLocation(messageQuote) + quoteEnd);
                    if (quoteThumb != null) {
                        out.print("<div><img class=\"quoteImg\" src=\"");
                        out.print("data:image/jpg;base64," + Util.encodeBase64(quoteThumb) + "\"></div>");
                    }
                    break;

                case CONTACT_MESSAGE:
                    out.print("<div class=\"" + quoteClass + "\" " + quoteClick
                            + "><div class=\"quoteTop\"><span class=\"quoteUser\">" + quoteUser
                            + "</span><br><span class=\"quoteMsg\">");
                    out.println("<b>" + Messages.getString("WhatsAppReport.Contact") + "</b><br>");
                    for (String c : messageQuote.getVcards()) {
                        if (notNullNorBlank(c)) {
                            VCardParser.printHtmlFromString(out, c);
                        }
                    }
                    out.print(quoteEnd);
                    break;
                case PRODUCT_MESSAGE:
                    MessageProduct product = messageQuote.getProduct();
                    String seller = null;
                    if (product != null) {
                        seller = getBestContactName(false, product.getSeller(), contactsDirectory, account);
                    }
                    out.print("<div class=\"" + quoteClass + "\" " + quoteClick
                            + "><div class=\"quoteTop\"><span class=\"quoteUser\">" + quoteUser
                            + "</span><br><span class=\"quoteMsg\">" + formatProduct(product, seller) + quoteEnd);
                    if (quoteThumb != null) {
                        out.print("<div><img class=\"quoteImg\" src=\"");
                        out.print("data:image/jpg;base64," + Util.encodeBase64(quoteThumb) + "\"></div>");
                    }
                    break;
                default:
                    out.print("<div class=\"" + quoteClass + "\" " + quoteClick
                            + "><div style=\"display:table-cell;\"><span class=\"quoteUser\">" + quoteUser
                            + "</span><br><span class=\"quoteMsg\">");
                    StringBuilder sb = new StringBuilder();
                    if (notNullNorBlank(quoteData)) {
                        sb.append(format(quoteData));
                    }
                    if (notNullNorBlank(messageQuote.getUiElements())) {
                        if (sb.length() > 0) {
                            sb.append("<br>");
                        }
                        sb.append(formatUiElements(messageQuote.getUiElements()));
                    }
                    out.print(sb);
                    out.print(quoteEnd);
                    break;
            }
            out.println("</div>");

        } else {
            // Reference not found
            out.println("<div class=\"" + quoteClass + "\"><span class=\"quoteUser\">"
                    + Messages.getString("WhatsAppReport.QuoteNotFound") + "</span><br><span class=\"quoteMsg\">"
                    + format("") + "</span></div>");
        }
    }

    private String formatProduct(MessageProduct product, String seller) {
        StringBuilder sb = new StringBuilder();
        if (notNullNorBlank(product.getTitle())) {
            sb.append("<b>").append(Messages.getString("WhatsAppReport.ProductTitle")).append(": </b>");
            sb.append(format(product.getTitle())).append("<br>");
        }
        if (notNullNorBlank(product.getDescription())) {
            sb.append(format(product.getDescription())).append("<br>");
        }
        if (notNullNorBlank(seller)) {
            sb.append("<b>").append(Messages.getString("WhatsAppReport.ProductSeller")).append(": </b>");
            sb.append(format(seller)).append("<br>");
        }
        if (product instanceof MessageOrder) {
            MessageOrder order = (MessageOrder) product;
            if (order.getCount() != 0) {
                sb.append("<b>").append(Messages.getString("WhatsAppReport.OrderCount")).append(": </b>");
                sb.append(order.getCount()).append("<br>");
            }
        }
        if (product.getAmount() > 0) {
            sb.append("<b>").append(Messages.getString("WhatsAppReport.ProductAmount")).append(": </b>");
            if (notNullNorBlank(product.getCurrency())) {
                sb.append(format(product.getCurrency())).append(' ');
            }
            DecimalFormat nf = LocalizedFormat.getDecimalInstance("#,##0.00");
            sb.append(nf.format(product.getAmount() / 1000.0)).append("<br>");
        }
        return sb.toString();
    }

    private static String formatUiElements(String s) {
        StringBuilder sb = new StringBuilder();
        String[] keys = { "title", "content", "description", "footerText", "footer" };
        for (String key : keys) {
            key = '"' + key + '"';
            int p1 = s.indexOf(key);
            if (p1 >= 0) {
                p1 += key.length();
                p1 = s.indexOf("\"", p1);
                if (p1 >= 0) {
                    p1++;
                    int p2 = s.indexOf("\"", p1);
                    if (p2 > 0 && p2 > p1 + 1) {
                        if (sb.length() > 0) {
                            sb.append("<br>\n");
                        }
                        sb.append(format(s.substring(p1, p2).replaceAll("\\\\n", "\n")));
                    }
                }
            }
        }
        keys = new String[] { "displayText", "title", "description" };
        int p0 = s.indexOf("\"id\"");
        while (p0 > 0 && p0 < s.length()) {
            StringBuilder opt = new StringBuilder();
            int p3 = p0;
            for (String key : keys) {
                key = '"' + key + '"';
                int p1 = s.indexOf(key, p0);
                if (p1 >= 0) {
                    p1 += key.length();
                    p1 = s.indexOf("\"", p1);
                    if (p1 >= 0) {
                        p1++;
                        int p2 = s.indexOf("\"", p1);
                        if (p2 > 0 && p2 > p1 + 1) {
                            if (opt.length() > 0) {
                                opt.append(" - ");
                            }
                            opt.append(format(s.substring(p1, p2).replaceAll("\\\\n", "\n")));
                            p3 = Math.max(p3, p2 + 1);
                        }
                    }
                }
            }
            if (opt.length() == 0) {
                break;
            }
            if (sb.length() > 0) {
                sb.append("<br>\n");
            }
            sb.append("[<b>").append(opt).append("</b>]");
            p0 = p3 + 1;
        }
        return sb.toString();
    }

    private String formatTemplate(Message message) {
        StringBuilder sb = new StringBuilder();
        if (notNullNorBlank(message.getData())) {
            sb.append(format(message.getData()));
        }
        MessageTemplate t = message.getMessageTemplate();
        if (t != null) {
            String content = t.getContent();
            if (notNullNorBlank(content)) {
                if (sb.length() > 0) {
                    sb.append("<br>");
                }
                sb.append(format(content));
            }
            for (MessageTemplate.Button button : t.getButtons()) {
                String text = button.getText();
                if (notNullNorBlank(text)) {
                    if (sb.length() > 0) {
                        sb.append("<br>");
                    }
                    sb.append("<b>[").append(format(text)).append("]</b>");
                }
                String extra = button.getExtra();
                if (notNullNorBlank(extra) && !extra.equals(text) && !extra.startsWith("{\"")) {
                    if (sb.length() > 0) {
                        sb.append("<br>");
                    }
                    sb.append(format(extra));
                }
            }
        }
        return sb.toString();
    }

    private String getBestContactName(Message message, WAContactsDirectory contactsDirectory, WAAccount account) {
        return getBestContactName(message.isFromMe(), message.getRemoteResource(), contactsDirectory, account);
    }

    private String getBestContactName(boolean isFromMe, String remoteResource, WAContactsDirectory contactsDirectory,
            WAAccount account) {
        String name = null;
        String number = null;
        if (isFromMe) {
            if (account != null && !account.isUnknown()) {
                name = account.getName();
            } else {
                name = "[" + Messages.getString("WhatsAppReport.Owner") + "]";
            }
        } else {
            number = remoteResource;
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
        return format(name);
    }

    public static String formatMMSS(int duration) {
        return String.format("%02d:%02d", duration / 60, duration % 60);
    }

    private static String formatDuration(int duration) {
        if (duration == 0) {
            return "";
        }
        return "(" + formatMMSS(duration) + ")";
    }

    private static String getTitle(Message message) {
        String mime = message.getMediaMime();
        if (mime != null && !mime.isEmpty()) {
            int pos = message.getMediaMime().indexOf('/');
            if (pos > 0) {
                return format(mime.substring(0, pos));
            }
        }
        return "File";
    }

    private void printMessageFile(PrintWriter out, String title, String id, byte[] avatar, boolean isDeleted,
            boolean isBroadcast, Supplier<String> messages) {
        String strAvatar;
        if (avatar == null || avatar.length == 0) {
            strAvatar = Util.getImageResourceAsEmbedded("img/avatar.png");
        } else {
            strAvatar = "data:image/jpg;base64," + Util.encodeBase64(avatar);
        }
        String deletedDiv;
        if (isDeleted) {
            deletedDiv = "<div class=\"linha\"><div class=\"recoveredChat\">"
                    + Messages.getString("WhatsAppReport.RecoveredChat") + "</div></div>";
        } else {
            deletedDiv = "";
        }
        String favicon = Util.getImageResourceAsEmbedded("img/whatsapp.png");
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
                    case "topbarclass":
                        return isBroadcast ? " class=\"status\"" : "";
                    case "messages":
                        return messages.get();
                    case "javascript":
                        return js;
                    case "css":
                        return css;
                    case "deleted":
                        return deletedDiv;
                    case "favicon":
                        return favicon;
                }
                return StringLookupFactory.INSTANCE.interpolatorStringLookup().lookup(key);
            }
        });
        out.println(interpolator.replace(template));
    }

    private static boolean notNullNorBlank(String s) {
        return s != null && !s.isBlank();
    }
}
