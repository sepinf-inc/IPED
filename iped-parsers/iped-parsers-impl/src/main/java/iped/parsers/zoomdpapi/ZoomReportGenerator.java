package iped.parsers.zoomdpapi;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * Generates complete HTML forensic reports for Zoom meetings,
 * matching the visual style of the standalone zoom_forensics
 * HtmlReportGenerator. Includes meeting cards with participants,
 * chat messages, shared files with encryption details, and
 * activity timeline with colored tags.
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
        w.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">");
        w.append("<title>").append(esc(meeting.getTitle())).append("</title>");
        w.append("<style>").append(getStyles()).append("</style></head><body>");
        w.append("<div class=\"container\">");

        // Meeting card
        w.append("<div class=\"meeting-card\">");

        // Header
        w.append("<div class=\"meeting-header\">");
        w.append("<h3>").append(esc(meeting.getTopic() != null ? meeting.getTopic() : "Meeting")).append("</h3>");
        w.append("</div>");

        // Identifiers
        w.append("<div style=\"font-size:.75rem;color:#666;margin-bottom:.5rem;padding:.5rem;background:#f5f5f5;border-radius:4px\">");
        if (meeting.getMeetingNo() != null)
            w.append("<div><strong>Meeting Number:</strong> <code>").append(esc(meeting.getMeetingNo())).append("</code></div>");
        if (meeting.getMeetingId() != null)
            w.append("<div><strong>SDK Meeting UID:</strong> <code>").append(esc(meeting.getMeetingId().toUpperCase())).append("</code></div>");
        if (meeting.getConfId() != null)
            w.append("<div><strong>Conference ID (B64):</strong> <code>").append(esc(meeting.getConfId())).append("</code></div>");
        if (meeting.getHostName() != null)
            w.append("<div><strong>Host:</strong> ").append(esc(meeting.getHostName())).append("</div>");
        if (meeting.getHostId() != null)
            w.append("<div><strong>Host ID:</strong> <code>").append(esc(meeting.getHostId())).append("</code></div>");
        w.append("</div>");

        // Stats
        List<ZoomMessage> messages = meeting.getMessages();
        long minTs = Long.MAX_VALUE, maxTs = 0;
        for (ZoomMessage m : messages) {
            if (m.getTimestamp() > 0 && m.getTimestamp() < minTs) minTs = m.getTimestamp();
            if (m.getTimestamp() > maxTs) maxTs = m.getTimestamp();
        }

        w.append("<div class=\"meeting-stats\">");
        if (meeting.getStartTime() > 0) {
            w.append("<div class=\"stat-item\"><span>Start:</span> ").append(fmtTs(meeting.getStartTime())).append("</div>");
        } else if (minTs != Long.MAX_VALUE) {
            w.append("<div class=\"stat-item\"><span>First Message:</span> ").append(fmtTs(minTs)).append("</div>");
        }
        if (maxTs > 0 && minTs != Long.MAX_VALUE) {
            w.append("<div class=\"stat-item\"><span>Last Message:</span> ").append(fmtTs(maxTs)).append("</div>");
            w.append("<div class=\"stat-item\"><span>Duration:</span> ").append(fmtDuration((int)(maxTs - minTs))).append("</div>");
        }
        if (meeting.getDuration() > 0) {
            w.append("<div class=\"stat-item\"><span>Scheduled Duration:</span> ").append(fmtDuration(meeting.getDuration())).append("</div>");
        }
        w.append("<div class=\"stat-item\"><span>Messages:</span> ").append(messages.size()).append("</div>");
        w.append("<div class=\"stat-item\"><span>Files:</span> ").append(meeting.getSharedFiles().size()).append("</div>");
        w.append("</div>");

        // Participants from messages
        Map<String, String> chatParticipants = new LinkedHashMap<>();
        for (ZoomMessage m : messages) {
            if (m.getSenderName() != null && m.getSenderGuid() != null) {
                chatParticipants.put(m.getSenderGuid(), m.getSenderName());
            }
        }
        if (!chatParticipants.isEmpty() || !meeting.getParticipants().isEmpty()) {
            w.append("<div class=\"subsection\">");
            if (!meeting.getParticipants().isEmpty()) {
                w.append("<h4>Participants (").append(meeting.getParticipants().size()).append(")</h4>");
                w.append("<table><thead><tr><th>Name</th><th>Role</th><th>GUID</th></tr></thead><tbody>");
                for (ZoomParticipant p : meeting.getParticipants()) {
                    w.append("<tr><td>").append(esc(p.getName())).append("</td>");
                    w.append("<td>").append(esc(p.getRoleName())).append("</td>");
                    w.append("<td><code>").append(esc(p.getOdId())).append("</code></td></tr>");
                }
                w.append("</tbody></table>");
            } else {
                w.append("<h4>Participants (").append(chatParticipants.size()).append(")</h4>");
                w.append("<table><thead><tr><th>Name</th><th>GUID</th></tr></thead><tbody>");
                for (Map.Entry<String, String> e : chatParticipants.entrySet()) {
                    w.append("<tr><td>").append(esc(e.getValue())).append("</td>");
                    w.append("<td><code>").append(esc(e.getKey().toUpperCase())).append("</code></td></tr>");
                }
                w.append("</tbody></table>");
            }
            w.append("</div>");
        }

        // Shared files with full details
        if (!meeting.getSharedFiles().isEmpty()) {
            w.append("<div class=\"subsection\">");
            w.append("<h4>Files Shared (").append(meeting.getSharedFiles().size()).append(")</h4>");
            for (ZoomSharedFile f : meeting.getSharedFiles()) {
                w.append("<div class=\"file-item\">");
                w.append("<div class=\"file-header\"><strong>").append(esc(f.getFileName()));
                w.append("</strong> <span class=\"file-size\">").append(fmtSize(f.getFileSize()));
                w.append(" (").append(f.getFileSize()).append(" bytes)</span></div>");
                w.append("<div class=\"file-details\">");

                w.append("<div class=\"info\"><span>Timestamp:</span> ").append(fmtTs(f.getTimestamp())).append("</div>");
                if (f.getOwnerJid() != null)
                    w.append("<div class=\"info\"><span>Sender:</span> ").append(esc(f.getOwnerJid())).append("</div>");
                if (f.getSenderGuid() != null)
                    w.append("<div class=\"info\"><span>Sender GUID:</span> <code>").append(esc(f.getSenderGuid().toUpperCase())).append("</code></div>");
                if (f.getSenderId() != null)
                    w.append("<div class=\"info\"><span>Sender Conf ID:</span> <code>").append(esc(f.getSenderId())).append("</code></div>");
                if (f.getSenderNodeId() != null)
                    w.append("<div class=\"info\"><span>Sender Node ID:</span> <code>").append(esc(f.getSenderNodeId())).append("</code></div>");

                // File identifiers
                w.append("<h5>File Identifiers</h5>");
                if (f.getFileId() != null)
                    w.append("<div class=\"info\"><span>File ID:</span> <code>").append(esc(f.getFileId())).append("</code></div>");
                if (f.getMsgId() != null)
                    w.append("<div class=\"info\"><span>Message ID:</span> <code>").append(esc(cleanMsgId(f.getMsgId()))).append("</code></div>");

                // Encryption details
                w.append("<h5>Encryption Details</h5>");
                if (f.getEncryptionAlg() > 0)
                    w.append("<div class=\"info\"><span>Algorithm:</span> ").append(getEncAlgName(f.getEncryptionAlg())).append("</div>");
                if (f.getEncryptionKey() != null)
                    w.append("<div class=\"info\"><span>File Key (B64):</span> <code style=\"word-break:break-all\">").append(esc(f.getEncryptionKey())).append("</code></div>");
                if (f.getDbKey() != null)
                    w.append("<div class=\"info\"><span>DB Key (B64):</span> <code style=\"word-break:break-all\">").append(esc(f.getDbKey())).append("</code></div>");
                if (f.getKAttribute() != null)
                    w.append("<div class=\"info\"><span>Full K Attr:</span> <code style=\"word-break:break-all;font-size:.65rem\">").append(esc(f.getKAttribute())).append("</code></div>");

                // File hash
                w.append("<h5>File Hashes</h5>");
                if (f.getFileHash() != null) {
                    w.append("<div class=\"info\"><span>SHA-256:</span> <code style=\"word-break:break-all\">").append(base64ToHex(f.getFileHash())).append("</code></div>");
                } else {
                    w.append("<div class=\"info\"><span>Hashes:</span> <code style=\"color:#999\">Not available</code></div>");
                }

                w.append("</div></div>");
            }
            w.append("</div>");
        }

        // Chat messages
        if (!messages.isEmpty()) {
            Collections.sort(messages);
            w.append("<div class=\"subsection\">");
            w.append("<h4>Chat Messages (").append(messages.size()).append(")</h4>");
            w.append("<table><thead><tr><th style=\"width:150px\">Time (UTC)</th><th style=\"width:120px\">Sender</th><th>Message</th><th style=\"width:280px\">GUID</th></tr></thead><tbody>");
            for (ZoomMessage m : messages) {
                w.append("<tr>");
                w.append("<td>").append(fmtTs(m.getTimestamp())).append("</td>");
                w.append("<td>").append(esc(m.getSenderName())).append("</td>");
                w.append("<td class=\"msg\">").append(esc(m.getBody())).append("</td>");
                w.append("<td style=\"font-size:.7rem\">");
                if (m.getId() != null) {
                    w.append("<code>").append(esc(cleanMsgId(m.getId()))).append("</code>");
                }
                w.append("</td></tr>");
            }
            w.append("</tbody></table></div>");
        }

        w.append("</div>"); // meeting-card
        w.append("</div></body></html>");
        return w.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public byte[] generateAccountHtml(ZoomUserAccount account, ZoomSystemInfo sysInfo) {
        StringBuilder w = new StringBuilder();
        w.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">");
        w.append("<title>Zoom Account - ").append(esc(account.getName())).append("</title>");
        w.append("<style>").append(getStyles()).append("</style></head><body>");
        w.append("<div class=\"container\">");

        // Account section
        w.append("<section>");
        w.append("<h2>Zoom Account</h2>");
        w.append("<div class=\"grid-2\">");
        w.append("<div class=\"card\">");
        w.append("<h3>Credentials</h3>");
        if (account.getEmail() != null)
            w.append("<div class=\"info\"><span>Email:</span> ").append(esc(account.getEmail())).append("</div>");
        if (account.getZoomEmail() != null)
            w.append("<div class=\"info\"><span>Zoom Email:</span> ").append(esc(account.getZoomEmail())).append("</div>");
        if (account.getFirstName() != null || account.getLastName() != null)
            w.append("<div class=\"info\"><span>Name:</span> ").append(esc(account.getName())).append("</div>");
        if (account.getZoomJid() != null)
            w.append("<div class=\"info\"><span>JID:</span> <code>").append(esc(account.getZoomJid())).append("</code></div>");
        if (account.getZoomUserId() != null)
            w.append("<div class=\"info\"><span>User ID:</span> <code>").append(esc(account.getZoomUserId())).append("</code></div>");
        if (account.getClientVersion() != null)
            w.append("<div class=\"info\"><span>Client Version:</span> ").append(esc(account.getClientVersion())).append("</div>");
        if (account.getLastLoginTime() > 0)
            w.append("<div class=\"info\"><span>Last Login:</span> ").append(fmtTs(account.getLastLoginTime())).append("</div>");
        w.append("</div>");

        if (sysInfo != null) {
            w.append("<div class=\"card\">");
            w.append("<h3>System</h3>");
            if (sysInfo.getComputerSystem() != null)
                w.append("<div class=\"info\"><span>Machine:</span> ").append(esc(parseField(sysInfo.getComputerSystem(), "Model"))).append("</div>");
            if (sysInfo.getProcessor() != null)
                w.append("<div class=\"info\"><span>CPU:</span> ").append(esc(parseField(sysInfo.getProcessor(), "Name"))).append("</div>");
            if (sysInfo.getVideoController() != null)
                w.append("<div class=\"info\"><span>GPU:</span> ").append(esc(parseField(sysInfo.getVideoController(), "Name"))).append("</div>");
            if (sysInfo.getClientGuid() != null) {
                String cleanGuid = sysInfo.getClientGuid().replaceAll("[{}]", "").toUpperCase();
                w.append("<div class=\"info\"><span>Client GUID:</span> <code>").append(esc(cleanGuid)).append("</code></div>");
            }
            if (sysInfo.getFingerprint() != null)
                w.append("<div class=\"info\"><span>Fingerprint:</span> <code>").append(esc(sysInfo.getFingerprint())).append("</code></div>");
            w.append("</div>");
        }

        w.append("</div>");
        w.append("</section>");

        w.append("</div></body></html>");
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

    private String fmtSize(long bytes) {
        if (bytes <= 0) return "-";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    static String esc(String s) {
        if (s == null) return "-";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String cleanMsgId(String id) {
        if (id == null) return "-";
        if (id.matches("\\d+-\\{.*\\}")) {
            id = id.replaceAll("^\\d+-\\{", "").replaceAll("\\}$", "");
        }
        return id.toUpperCase();
    }

    private String getEncAlgName(int alg) {
        switch (alg) {
            case 1: return "AES-128 (alg=1)";
            case 2: return "AES-256 (alg=2)";
            default: return "Unknown (alg=" + alg + ")";
        }
    }

    private String base64ToHex(String b64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(b64);
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02X", b));
            return sb.toString();
        } catch (Exception e) {
            return b64;
        }
    }

    private String parseField(String data, String fieldName) {
        if (data == null) return null;
        for (String part : data.split("\\|")) {
            part = part.trim();
            if (part.startsWith(fieldName + "=")) return part.substring(fieldName.length() + 1);
        }
        return data.length() > 50 ? data.substring(0, 50) + "..." : data;
    }

    private String getStyles() {
        return "*{margin:0;padding:0;box-sizing:border-box}"
            + "body{font-family:system-ui,-apple-system,sans-serif;background:#f5f5f5;color:#1a1a1a;line-height:1.5;font-size:14px}"
            + ".container{max-width:1000px;margin:0 auto;padding:1.5rem}"
            + "h2{font-size:1.1rem;font-weight:600;color:#444;margin:1.5rem 0 1rem;padding-bottom:.5rem;border-bottom:1px solid #ddd}"
            + "h3{font-size:.9rem;font-weight:600;color:#555;margin:0 0 .5rem}"
            + "h4{font-size:.8rem;font-weight:600;color:#666;margin:.75rem 0 .5rem}"
            + "h5{margin-top:.5rem;margin-bottom:.25rem;font-size:.75rem;color:#666}"
            + "section{margin-bottom:1.5rem}"
            + ".grid-2{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:.75rem}"
            + ".card{background:#fff;border:1px solid #ddd;border-radius:6px;padding:1rem;margin-bottom:.75rem}"
            + ".meeting-card{background:#fff;border:1px solid #ccc;border-radius:8px;padding:1rem;margin-bottom:1rem}"
            + ".meeting-header{display:flex;justify-content:space-between;align-items:center;margin-bottom:.75rem;padding-bottom:.5rem;border-bottom:1px solid #eee}"
            + ".meeting-header h3{margin:0;color:#333}"
            + ".meeting-stats{display:flex;flex-wrap:wrap;gap:.5rem 1.5rem;margin-bottom:.75rem;padding:.5rem;background:#f9f9f9;border-radius:4px;font-size:.8rem}"
            + ".stat-item span{color:#666;margin-right:.25rem}"
            + ".subsection{margin-top:1rem;padding-top:.75rem;border-top:1px solid #eee}"
            + ".info{font-size:.8rem;color:#555;margin:.3rem 0}.info span{color:#333;font-weight:500;display:inline-block;min-width:90px}"
            + "code{background:#eee;padding:.1rem .3rem;border-radius:3px;font-size:.75rem;font-family:monospace;color:#555}"
            + "table{width:100%;border-collapse:collapse;font-size:.8rem}"
            + "th{text-align:left;padding:.4rem;color:#666;font-weight:500;border-bottom:2px solid #ddd;background:#f9f9f9}"
            + "td{padding:.4rem;border-bottom:1px solid #eee;vertical-align:top}"
            + "tr:hover{background:#fafafa}"
            + ".msg{max-width:450px;white-space:pre-wrap;word-break:break-word}"
            + ".file-item{background:#f9f9f9;border:1px solid #e5e5e5;border-radius:4px;padding:.75rem;margin-bottom:.5rem}"
            + ".file-header{display:flex;justify-content:space-between;align-items:center;margin-bottom:.5rem}"
            + ".file-size{color:#666;font-size:.8rem}"
            + ".file-details{font-size:.75rem}";
    }
}
