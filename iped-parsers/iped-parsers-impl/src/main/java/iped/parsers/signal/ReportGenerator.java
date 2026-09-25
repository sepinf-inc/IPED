package iped.parsers.signal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.utils.IOUtil;

public class ReportGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportGenerator.class);

    private static final String TEMPLATE;
    private static final String CSS;

    // Single-pass substitution prevents user data containing ${placeholder}
    // from being re-processed by subsequent replace() calls (template injection).
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{(css|title|messages|javascript)\\}");

    static {
        TEMPLATE = readResource("/iped/parsers/signal/signal-html-template.txt");
        CSS      = readResource("/iped/parsers/signal/css/signal.css");
    }

    private static final ThreadLocal<SimpleDateFormat> DATE_FMT = ThreadLocal.withInitial(() -> {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd");
        f.setTimeZone(TimeZone.getDefault());
        return f;
    });

    private static final ThreadLocal<SimpleDateFormat> TIME_FMT = ThreadLocal.withInitial(() -> {
        // Times are shown in the timezone of the machine that processed the case, as the
        // WhatsApp and Threema reports do; the UTC offset is printed so that the instant
        // is still unambiguous for whoever reads the report
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss XXX");
        f.setTimeZone(TimeZone.getDefault());
        return f;
    });

    /** One piece of a conversation report, and where the next piece starts. */
    public static class Fragment {
        private final byte[] html;
        private final int nextMessage;

        Fragment(byte[] html, int nextMessage) {
            this.html = html;
            this.nextMessage = nextMessage;
        }

        public byte[] getHtml() { return html; }

        /** Index of the first message not included in this fragment. */
        public int getNextMessage() { return nextMessage; }
    }

    public byte[] generateChatHtml(SignalChat chat) {
        return generateChatFragment(chat, 0, Integer.MAX_VALUE).getHtml();
    }

    /**
     * Renders the conversation from {@code firstMessage} on, stopping once the messages
     * block reaches {@code minSplitSize}, so that a long conversation becomes several
     * items instead of a single huge one. Holds no state between calls: the caller walks
     * the conversation through {@link Fragment#getNextMessage()}.
     */
    public Fragment generateChatFragment(SignalChat chat, int firstMessage, int minSplitSize) {
        StringBuilder messages = new StringBuilder(1024);
        Date lastDate = null;

        List<SignalMessage> all = chat.getMessages();
        int current = firstMessage;
        // A fragment must not end on a tail of system messages, which are not indexed
        int lastIndexable = lastIndexableMessage(all);

        if (current > 0)
            messages.append(renderChatNote("Continuation of the previous conversation fragment"));

        while (current < all.size()) {
            SignalMessage m = all.get(current++);
            SignalMessage.MessageType type = m.getMessageType();

            if (type == SignalMessage.MessageType.SYSTEM) {
                messages.append(renderSystemMessage(m));
                continue;
            }

            Date msgDate = m.getDateSent() != null ? m.getDateSent() : m.getDateReceived();
            if (msgDate != null && !isSameDay(lastDate, msgDate)) {
                messages.append(renderDateSeparator(msgDate));
                lastDate = msgDate;
            }

            messages.append(renderMessage(m, chat));

            if (messages.length() >= minSplitSize && current <= lastIndexable) {
                messages.append(renderChatNote("Conversation continues on the next fragment"));
                break;
            }
        }

        final String escapedTitle  = escapeHtml(chat.getTitle());
        final String messagesBlock = messages.toString();

        String html = PLACEHOLDER.matcher(TEMPLATE).replaceAll(mr -> {
            switch (mr.group(1)) {
                case "css":        return Matcher.quoteReplacement(CSS);
                case "title":      return Matcher.quoteReplacement(escapedTitle);
                case "messages":   return Matcher.quoteReplacement(messagesBlock);
                case "javascript": return "";
                default:           return Matcher.quoteReplacement(mr.group(0));
            }
        });

        return new Fragment(html.getBytes(StandardCharsets.UTF_8), current);
    }

    /** Index of the last message that becomes an item of its own, or -1 when there is none. */
    private static int lastIndexableMessage(List<SignalMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).getMessageType() != SignalMessage.MessageType.SYSTEM)
                return i;
        }
        return -1;
    }

    private String renderChatNote(String text) {
        return "<div class=\"systemmessage\"><span>" + escapeHtml(text) + "</span></div>\n";
    }

    private String renderMessage(SignalMessage m, SignalChat chat) {
        boolean incoming = !m.isFromMe();
        String bubbleClass = incoming ? "bbl" : "bbr";
        String dirClass    = incoming ? "incoming" : "outgoing";

        String bodyHtml = buildBodyHtml(m);
        Date timeDate = m.getDateSent() != null ? m.getDateSent() : m.getDateReceived();
        String timeHtml = "<div class=\"time\">" + formatTime(timeDate) + "</div>";

        StringBuilder sb = new StringBuilder(256);
        sb.append("<div class=\"linha ").append(dirClass).append("\">");
        sb.append("<div class=\"").append(bubbleClass).append("\">");

        if (incoming && chat.isGroupChat()) {
            String senderName = resolveSenderName(m, chat);
            if (senderName != null && !senderName.isEmpty()) {
                sb.append("<div class=\"from\">").append(escapeHtml(senderName)).append("</div>");
            }
        }

        sb.append(bodyHtml);
        sb.append(timeHtml);
        sb.append("</div></div>\n");
        return sb.toString();
    }

    private String buildBodyHtml(SignalMessage m) {
        SignalMessage.MessageType type = m.getMessageType();
        if (m.isRemoteDeleted()) {
            return "<div class=\"body attachment-label\">[Message deleted by sender]</div>";
        }
        if (m.getCallDetail() != null) {
            return "<div class=\"body call-label\">&#128222; " + escapeHtml(m.getCallDetail()) + "</div>";
        }
        if (type == SignalMessage.MessageType.CALL_OUTGOING) {
            return "<div class=\"body call-label\">&#128222; Outgoing call</div>";
        }
        if (type == SignalMessage.MessageType.CALL_INCOMING) {
            return "<div class=\"body call-label\">&#128222; Incoming call</div>";
        }
        if (type == SignalMessage.MessageType.CALL_MISSED) {
            return "<div class=\"body call-label\">&#128222; Missed call</div>";
        }
        if (type == SignalMessage.MessageType.CALL_GROUP) {
            return "<div class=\"body call-label\">&#128222; Group call</div>";
        }
        String prefix = "";
        if (m.isEarlierRevision())
            prefix = "<span class=\"revision-label\">[Edited, earlier version]</span> ";
        else if (m.isScheduled())
            prefix = "<span class=\"revision-label\">[Scheduled, never sent]</span> ";
        if (m.getBody() == null) {
            return "<div class=\"body attachment-label\">" + prefix + "[Empty message]</div>";
        }
        return "<div class=\"body\">" + prefix + escapeHtml(m.getBody()) + "</div>";
    }

    private String renderSystemMessage(SignalMessage m) {
        String text = m.getBody() != null ? escapeHtml(m.getBody()) : "System message";
        return "<div class=\"systemmessage\"><span>" + text + "</span></div>\n";
    }

    private String renderDateSeparator(Date date) {
        return "<div class=\"date\"><span>" + DATE_FMT.get().format(date) + "</span></div>\n";
    }

    private String resolveSenderName(SignalMessage m, SignalChat chat) {
        // Taken from the message itself (resolved against the whole recipient table),
        // so senders who left the group still show a name
        return m.getSender() != null ? m.getSender().getDisplayName() : null;
    }

    private String formatTime(Date date) {
        return date != null ? TIME_FMT.get().format(date) : "";
    }

    private static boolean isSameDay(Date a, Date b) {
        if (a == null) return false;
        Calendar ca = Calendar.getInstance();
        Calendar cb = Calendar.getInstance();
        ca.setTime(a);
        cb.setTime(b);
        return ca.get(Calendar.YEAR)         == cb.get(Calendar.YEAR)
            && ca.get(Calendar.DAY_OF_YEAR)  == cb.get(Calendar.DAY_OF_YEAR);
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String readResource(String path) {
        try (InputStream is = ReportGenerator.class.getResourceAsStream(path)) {
            if (is == null) {
                LOGGER.warn("Signal report resource not found on classpath: {}", path);
                return "";
            }
            return new String(IOUtil.loadInputStream(is), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Failed to read Signal report resource {}: {}", path, e.getMessage());
            return "";
        }
    }
}
