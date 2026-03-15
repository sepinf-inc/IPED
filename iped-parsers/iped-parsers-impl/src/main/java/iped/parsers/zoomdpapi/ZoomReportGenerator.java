package iped.parsers.zoomdpapi;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * Generates HTML report fragments for Zoom meetings, suitable for
 * embedding in IPED's viewer. Adapted from the standalone
 * HtmlReportGenerator to produce fragments instead of full pages.
 *
 * @author Calil Khalil (Hakal)
 */
public class ZoomReportGenerator {

    private final SimpleDateFormat sdf;

    public ZoomReportGenerator() {
        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public byte[] generateMeetingHtml(ZoomMeeting meeting) {
        StringBuilder w = new StringBuilder();
        w.append("<html><head><meta charset=\"UTF-8\">");
        w.append("<style>").append(getStyles()).append("</style></head><body>");

        w.append("<div class=\"meeting-card\">");

        // Header
        w.append("<div class=\"meeting-header\">");
        w.append("<h3>").append(esc(meeting.getTopic())).append("</h3>");
        w.append("</div>");

        // Identifiers
        w.append("<div class=\"ids\">");
        if (meeting.getMeetingNo() != null)
            w.append("<div><strong>Meeting Number:</strong> <code>").append(esc(meeting.getMeetingNo())).append("</code></div>");
        if (meeting.getMeetingId() != null)
            w.append("<div><strong>Meeting ID:</strong> <code>").append(esc(meeting.getMeetingId())).append("</code></div>");
        if (meeting.getHostName() != null)
            w.append("<div><strong>Host:</strong> ").append(esc(meeting.getHostName())).append("</div>");
        w.append("</div>");

        // Stats
        w.append("<div class=\"stats\">");
        if (meeting.getStartTime() > 0)
            w.append("<div><span>Start:</span> ").append(fmtTs(meeting.getStartTime())).append("</div>");
        if (meeting.getEndTime() > 0)
            w.append("<div><span>End:</span> ").append(fmtTs(meeting.getEndTime())).append("</div>");
        if (meeting.getDuration() > 0)
            w.append("<div><span>Duration:</span> ").append(fmtDuration(meeting.getDuration())).append("</div>");
        w.append("<div><span>Messages:</span> ").append(meeting.getMessages().size()).append("</div>");
        w.append("<div><span>Files:</span> ").append(meeting.getSharedFiles().size()).append("</div>");
        w.append("</div>");

        // Participants
        if (!meeting.getParticipants().isEmpty()) {
            w.append("<div class=\"section\"><h4>Participants (").append(meeting.getParticipants().size()).append(")</h4>");
            w.append("<table><thead><tr><th>Name</th><th>Role</th><th>Email</th></tr></thead><tbody>");
            for (ZoomParticipant p : meeting.getParticipants()) {
                w.append("<tr><td>").append(esc(p.getName())).append("</td>");
                w.append("<td>").append(esc(p.getRoleName())).append("</td>");
                w.append("<td>").append(esc(p.getOdEmail())).append("</td></tr>");
            }
            w.append("</tbody></table></div>");
        }

        // Shared files
        if (!meeting.getSharedFiles().isEmpty()) {
            w.append("<div class=\"section\"><h4>Shared Files (").append(meeting.getSharedFiles().size()).append(")</h4>");
            w.append("<table><thead><tr><th>Time</th><th>File</th><th>Size</th><th>Sender</th></tr></thead><tbody>");
            for (ZoomSharedFile f : meeting.getSharedFiles()) {
                w.append("<tr><td>").append(fmtTs(f.getTimestamp())).append("</td>");
                w.append("<td><strong>").append(esc(f.getFileName())).append("</strong></td>");
                w.append("<td>").append(ZoomDataExtractor.formatSize(f.getFileSize())).append("</td>");
                w.append("<td>").append(esc(f.getOwnerJid())).append("</td></tr>");
            }
            w.append("</tbody></table></div>");
        }

        // Messages
        List<ZoomMessage> messages = meeting.getMessages();
        if (!messages.isEmpty()) {
            Collections.sort(messages);
            w.append("<div class=\"section\"><h4>Chat Messages (").append(messages.size()).append(")</h4>");
            w.append("<table><thead><tr><th style=\"width:150px\">Time (UTC)</th><th style=\"width:120px\">Sender</th><th>Message</th></tr></thead><tbody>");
            for (ZoomMessage m : messages) {
                w.append("<tr><td>").append(fmtTs(m.getTimestamp())).append("</td>");
                w.append("<td>").append(esc(m.getSenderName())).append("</td>");
                w.append("<td class=\"msg\">").append(esc(m.getBody())).append("</td></tr>");
            }
            w.append("</tbody></table></div>");
        }

        w.append("</div></body></html>");
        return w.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public byte[] generateAccountHtml(ZoomUserAccount account, ZoomSystemInfo sysInfo) {
        StringBuilder w = new StringBuilder();
        w.append("<html><head><meta charset=\"UTF-8\">");
        w.append("<style>").append(getStyles()).append("</style></head><body>");

        w.append("<div class=\"card\">");
        w.append("<h3>Zoom Account</h3>");
        if (account.getEmail() != null)
            w.append("<div class=\"info\"><span>Email:</span> ").append(esc(account.getEmail())).append("</div>");
        if (account.getZoomUserId() != null)
            w.append("<div class=\"info\"><span>User ID:</span> <code>").append(esc(account.getZoomUserId())).append("</code></div>");
        if (account.getClientVersion() != null)
            w.append("<div class=\"info\"><span>Client Version:</span> ").append(esc(account.getClientVersion())).append("</div>");
        if (account.getLastLoginTime() > 0)
            w.append("<div class=\"info\"><span>Last Login:</span> ").append(fmtTs(account.getLastLoginTime())).append("</div>");
        w.append("</div>");

        if (sysInfo != null) {
            w.append("<div class=\"card\">");
            w.append("<h3>System Information</h3>");
            if (sysInfo.getProcessor() != null)
                w.append("<div class=\"info\"><span>CPU:</span> ").append(esc(sysInfo.getProcessor())).append("</div>");
            if (sysInfo.getVideoController() != null)
                w.append("<div class=\"info\"><span>GPU:</span> ").append(esc(sysInfo.getVideoController())).append("</div>");
            if (sysInfo.getComputerSystem() != null)
                w.append("<div class=\"info\"><span>Machine:</span> ").append(esc(sysInfo.getComputerSystem())).append("</div>");
            if (sysInfo.getClientGuid() != null)
                w.append("<div class=\"info\"><span>Client GUID:</span> <code>").append(esc(sysInfo.getClientGuid())).append("</code></div>");
            w.append("</div>");
        }

        w.append("</body></html>");
        return w.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String fmtTs(long ts) {
        if (ts == 0) return "-";
        if (ts > 9999999999L) ts = ts / 1000;
        return sdf.format(new Date(ts * 1000)) + " UTC";
    }

    private String fmtDuration(int seconds) {
        if (seconds <= 0) return "-";
        int h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    static String esc(String s) {
        if (s == null) return "-";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String getStyles() {
        return "body{font-family:system-ui,sans-serif;font-size:14px;color:#1a1a1a;line-height:1.5;margin:8px}"
            + ".card,.meeting-card{background:#fff;border:1px solid #ddd;border-radius:6px;padding:1rem;margin-bottom:.75rem}"
            + "h3{font-size:.9rem;font-weight:600;color:#555;margin:0 0 .5rem}"
            + "h4{font-size:.8rem;font-weight:600;color:#666;margin:.75rem 0 .5rem}"
            + ".meeting-header{margin-bottom:.75rem;padding-bottom:.5rem;border-bottom:1px solid #eee}"
            + ".ids{font-size:.75rem;color:#666;margin-bottom:.5rem;padding:.5rem;background:#f5f5f5;border-radius:4px}"
            + ".stats{display:flex;flex-wrap:wrap;gap:.5rem 1.5rem;margin-bottom:.75rem;padding:.5rem;background:#f9f9f9;border-radius:4px;font-size:.8rem}"
            + ".stats span{color:#666;margin-right:.25rem}"
            + ".section{margin-top:1rem;padding-top:.75rem;border-top:1px solid #eee}"
            + ".info{font-size:.8rem;color:#555;margin:.3rem 0}.info span{color:#333;font-weight:500;display:inline-block;min-width:100px}"
            + "code{background:#eee;padding:.1rem .3rem;border-radius:3px;font-size:.75rem;font-family:monospace}"
            + "table{width:100%;border-collapse:collapse;font-size:.8rem}"
            + "th{text-align:left;padding:.4rem;color:#666;font-weight:500;border-bottom:2px solid #ddd;background:#f9f9f9}"
            + "td{padding:.4rem;border-bottom:1px solid #eee;vertical-align:top}"
            + ".msg{max-width:450px;white-space:pre-wrap;word-break:break-word}";
    }
}
