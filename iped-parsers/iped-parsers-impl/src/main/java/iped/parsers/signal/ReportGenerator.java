package iped.parsers.signal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import iped.utils.IOUtil;

public class ReportGenerator {

    private static final String TEMPLATE;
    private static final String CSS;

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
        SimpleDateFormat f = new SimpleDateFormat("HH:mm");
        f.setTimeZone(TimeZone.getDefault());
        return f;
    });

    public byte[] generateChatHtml(SignalChat chat) {
        StringBuilder messages = new StringBuilder(1024);
        Date lastDate = null;

        for (SignalMessage m : chat.getMessages()) {
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
        }

        String html = TEMPLATE
                .replace("${css}", CSS)
                .replace("${title}", escapeHtml(chat.getTitle()))
                .replace("${messages}", messages.toString())
                .replace("${javascript}", "");

        return html.getBytes(StandardCharsets.UTF_8);
    }

    private String renderMessage(SignalMessage m, SignalChat chat) {
        boolean incoming = !m.isFromMe();
        String bubbleClass = incoming ? "bbl" : "bbr";
        String dirClass    = incoming ? "incoming" : "outgoing";

        String bodyHtml = buildBodyHtml(m);
        String timeHtml = "<div class=\"time\">" + formatTime(m.getDateSent()) + "</div>";

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
        if (type == SignalMessage.MessageType.CALL_OUTGOING) {
            return "<div class=\"body call-label\">&#128222; Outgoing call</div>";
        }
        if (type == SignalMessage.MessageType.CALL_INCOMING) {
            return "<div class=\"body call-label\">&#128222; Incoming call</div>";
        }
        if (type == SignalMessage.MessageType.CALL_MISSED) {
            return "<div class=\"body call-label\">&#128222; Missed call</div>";
        }
        if (m.getBody() == null) {
            return "<div class=\"body attachment-label\">[Attachment]</div>";
        }
        return "<div class=\"body\">" + escapeHtml(m.getBody()) + "</div>";
    }

    private String renderSystemMessage(SignalMessage m) {
        String text = m.getBody() != null ? escapeHtml(m.getBody()) : "System message";
        return "<div class=\"systemmessage\"><span>" + text + "</span></div>\n";
    }

    private String renderDateSeparator(Date date) {
        return "<div class=\"date\"><span>" + DATE_FMT.get().format(date) + "</span></div>\n";
    }

    private String resolveSenderName(SignalMessage m, SignalChat chat) {
        return chat.getParticipants().stream()
                .filter(p -> p.getId() == m.getFromRecipientId())
                .map(SignalContact::getDisplayName)
                .findFirst()
                .orElse(null);
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
        InputStream is = ReportGenerator.class.getResourceAsStream(path);
        if (is == null)
            return "";
        try {
            byte[] bytes = IOUtil.loadInputStream(is);
            return bytes != null ? new String(bytes, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "";
        }
    }
}
