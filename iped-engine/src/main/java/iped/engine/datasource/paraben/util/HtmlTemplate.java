/**
 * Paraben Data Parser Integration for IPED
 *
 * Developed by: Ariel E Aburo
 * Contact: infmpfch@gmail.com
 *
 * Description:
 * Implements parsing of data extracted via Paraben tools,
 * converting XML and associated database content into
 * IPED-compatible structures.
 *
 * This module supports multiple artifacts such as:
 * - Conversations
 * - Messages
 * - Attachments
 * - Device and user data and more.
 *
 * Notes:
 * - Uses XML as primary source
 * - Designed to be extensible for additional Paraben artifacts
 */
package iped.engine.datasource.paraben.util;

import java.util.*;

public class HtmlTemplate {

    public static String buildTable(String title, Map<String, String> data) {

        StringBuilder html = new StringBuilder();

        html.append("<html><head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<style>");
        html.append("body { font-family: Arial; font-size: 12px; }");
        html.append("h1 { background:#eee; padding:10px; }");
        html.append("table { border-collapse: collapse; width: 100%; }");
        html.append("td { border:1px solid #ccc; padding:5px; }");
        html.append("td:first-child { font-weight:bold; width:30%; }");
        html.append("</style>");
        html.append("</head><body>");

        html.append("<h1>").append(title).append("</h1>");
        html.append("<table>");

        for (Map.Entry<String, String> e : data.entrySet()) {
            html.append("<tr>");
            html.append("<td>").append(e.getKey()).append("</td>");
            html.append("<td>").append(e.getValue()).append("</td>");
            html.append("</tr>");
        }

        html.append("</table>");
        html.append("</body></html>");

        return html.toString();
    }

    public static class MessageBubble {
        public String sender;
        public String senderId;
        public String text;
        public String time;
        public boolean isSystem;
    }

    public static String buildFacebookChat(String chatName,
            List<MessageBubble> messages,
            String ownerId) {

        StringBuilder html = new StringBuilder();

        html.append("<html><head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<style>");

        html.append("body { font-family: Arial, sans-serif; background:#ffffff; margin:0; padding:10px; }");
        html.append(".container { max-width:900px; margin:auto; }");

        html.append(".participants { background:#f5f5f5; padding:10px; border-radius:10px; margin-bottom:15px; }");
        html.append(".participants div { margin:3px 0; font-size:12px; }");

        html.append(".msg { max-width:65%; padding:10px; margin:6px; border-radius:18px; clear:both; }");

        html.append(".left { background:#f0f0f0; float:left; }");
        html.append(".right { background:#0084ff; color:white; float:right; }");

        html.append(
                ".system { background:#e4e6eb; color:#000; margin:10px auto; text-align:center; float:none; border-radius:12px; max-width:70%; }");

        html.append(
                ".meta-ai { background:#f3e8ff; color:#6b21a8; margin:10px auto; text-align:center; float:none; border-radius:12px; max-width:70%; }");

        html.append(".sender { font-weight:bold; font-size:12px; margin-bottom:3px; }");
        html.append(".meta { font-size:10px; color:#666; margin-top:3px; }");

        html.append("</style>");
        html.append("</head><body>");

        html.append("<div class='container'>");
        html.append("<h2>").append(chatName).append("</h2>");

        Map<String, String> participants = new LinkedHashMap<>();

        for (MessageBubble m : messages) {
            if (m.senderId == null)
                continue;

            participants.put(m.senderId,
                    (m.sender == null || m.sender.isEmpty()) ? "Unknown" : m.sender);
        }

        html.append("<div class='participants'>");
        html.append("<b>Participants:</b>");

        for (Map.Entry<String, String> p : participants.entrySet()) {

            String role = "";

            if (ownerId != null && ownerId.equals(p.getKey())) {
                role = " (Owner)";
            } else if (p.getValue() != null && p.getValue().toLowerCase().contains("meta ai")) {
                role = " (Meta AI)";
            }

            html.append("<div>")
                    .append("ID: ").append(p.getKey())
                    .append(" | Name: ").append(p.getValue())
                    .append(role)
                    .append("</div>");
        }

        html.append("</div>");

        for (MessageBubble m : messages) {

            boolean isOwner = ownerId != null && ownerId.equals(m.senderId);

            String cssClass;

            if (m.isSystem) {
                cssClass = "system";
            } else if (m.sender != null && m.sender.toLowerCase().contains("meta ai")) {
                cssClass = "meta-ai";
            } else {
                cssClass = isOwner ? "right" : "left";
            }
            html.append("<div class='msg ")
                    .append(cssClass)
                    .append("'>");

            html.append("<div class='sender'>")
                    .append((m.sender == null ? "Unknown" : m.sender))
                    .append(" [")
                    .append(m.senderId == null ? "no-id" : m.senderId)
                    .append("]")
                    .append("</div>");

            html.append("<div>")
                    .append(escapeHtml(
                            (m.text == null || m.text.trim().isEmpty())
                                    ? "[Attachment / Content]"
                                    : m.text))
                    .append("</div>");

            html.append("<div class='meta'>")
                    .append(m.time == null ? "" : m.time)
                    .append("</div>");

            html.append("</div>");
        }

        html.append("<div style='clear:both;'></div>");
        html.append("</div>");

        html.append("</body></html>");

        return html.toString();
    }

    public static String buildInstagramContacts(String title, List<Map<String, String>> contacts) {

        StringBuilder html = new StringBuilder();

        html.append("<html><head>");
        html.append("<meta charset='UTF-8'>");

        html.append("<style>");
        html.append("body { font-family: Arial, sans-serif; background:#fafafa; margin:0; padding:20px; }");
        html.append(".container { max-width:900px; margin:auto; }");

        html.append(
                ".contact { display:flex; align-items:center; background:white; padding:10px; margin-bottom:10px; border-radius:10px; box-shadow:0 1px 3px rgba(0,0,0,0.1); }");

        html.append(".avatar { width:50px; height:50px; border-radius:50%; margin-right:10px; object-fit:cover; }");

        html.append(".info { flex:1; }");
        html.append(".username { font-weight:bold; font-size:14px; }");
        html.append(".fullname { font-size:12px; color:#555; }");

        html.append(".meta { font-size:11px; color:#888; margin-top:3px; }");

        html.append(".follow { font-size:11px; padding:3px 8px; border-radius:10px; }");
        html.append(".yes { background:#d1fae5; color:#065f46; }");
        html.append(".no { background:#fee2e2; color:#7f1d1d; }");

        html.append("</style>");
        html.append("</head><body>");

        html.append("<div class='container'>");
        html.append("<h2>").append(title).append("</h2>");

        for (Map<String, String> c : contacts) {

            String username = c.get("User Name");
            String fullname = c.get("Full Name");
            String userId = c.get("User ID");
            String following = c.get("Is Following");
            String pic = c.get("Profile Picture URL");

            html.append("<div class='contact'>");

            if (pic != null && !pic.isEmpty()) {
                html.append("<img class='avatar' src='").append(pic).append("'/>");
            } else {
                html.append("<div class='avatar' style='background:#ccc;'></div>");
            }

            html.append("<div class='info'>");

            html.append("<div class='username'>")
                    .append(username == null ? "unknown" : username)
                    .append("</div>");

            html.append("<div class='fullname'>")
                    .append(fullname == null ? "" : fullname)
                    .append("</div>");

            html.append("<div class='meta'>ID: ")
                    .append(userId == null ? "n/a" : userId)
                    .append("</div>");

            html.append("</div>");

            if ("Yes".equalsIgnoreCase(following)) {
                html.append("<div class='follow yes'>Following</div>");
            } else {
                html.append("<div class='follow no'>Not Following</div>");
            }

            html.append("</div>");
        }

        html.append("</div>");
        html.append("</body></html>");

        return html.toString();
    }

    public static String buildInstagramAccount(String title, Map<String, String> data) {

        StringBuilder html = new StringBuilder();

        html.append("<html><head>");
        html.append("<meta charset='UTF-8'>");

        html.append("<style>");
        html.append("body { font-family: Arial, sans-serif; background:#fafafa; padding:20px; }");
        html.append(
                ".card { max-width:600px; margin:auto; background:white; padding:20px; border-radius:12px; box-shadow:0 1px 5px rgba(0,0,0,0.1); text-align:center; }");

        html.append(".avatar { width:100px; height:100px; border-radius:50%; object-fit:cover; margin-bottom:10px; }");

        html.append(".username { font-size:18px; font-weight:bold; }");
        html.append(".fullname { font-size:14px; color:#555; margin-bottom:10px; }");

        html.append(".meta { font-size:12px; color:#777; margin-top:5px; }");

        html.append(
                ".owner { margin-top:10px; background:#d1fae5; color:#065f46; padding:5px 10px; border-radius:10px; display:inline-block; }");

        html.append("</style>");
        html.append("</head><body>");

        html.append("<div class='card'>");

        String pic = data.get("Profile Picture URL");

        if (pic != null && !pic.isEmpty()) {
            html.append("<img class='avatar' src='").append(pic).append("'/>");
        }

        html.append("<div class='username'>")
                .append(data.getOrDefault("User Name", "unknown"))
                .append("</div>");

        html.append("<div class='fullname'>")
                .append(data.getOrDefault("Full Name", ""))
                .append("</div>");

        html.append("<div class='meta'>User ID: ")
                .append(data.getOrDefault("User ID", "n/a"))
                .append("</div>");

        if ("Yes".equalsIgnoreCase(data.get("Is Currently Logged In"))) {
            html.append("<div class='owner'>CURRENT ACCOUNT (OWNER)</div>");
        }

        html.append("</div>");
        html.append("</body></html>");

        return html.toString();
    }

    public static String buildInstagramChat(String chatName,
            List<MessageBubble> messages,
            String ownerId) {

        StringBuilder html = new StringBuilder();

        html.append("<html><head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<style>");

        html.append("body { font-family: Arial, sans-serif; background:#fafafa; margin:0; padding:10px; }");
        html.append(".container { max-width:800px; margin:auto; }");

        html.append(".participants { background:#eee; padding:10px; border-radius:10px; margin-bottom:15px; }");
        html.append(".participants div { font-size:12px; margin:2px 0; }");

        html.append(".msg { max-width:70%; padding:10px; margin:6px; border-radius:15px; clear:both; }");

        html.append(".left { background:#e5e5ea; float:left; }");
        html.append(".right { background:#0095f6; color:white; float:right; }");

        html.append(".system { background:#d1d5db; color:#000; text-align:center; margin:10px auto; float:none; }");

        html.append(".sender { font-weight:bold; font-size:12px; margin-bottom:3px; }");
        html.append(".meta { font-size:10px; color:#555; margin-top:3px; }");

        html.append("</style>");
        html.append("</head><body>");

        html.append("<div class='container'>");
        html.append("<h2>").append(chatName).append("</h2>");

        Map<String, String> participants = new LinkedHashMap<>();

        for (MessageBubble m : messages) {
            if (m.senderId == null)
                continue;

            participants.put(m.senderId,
                    (m.sender == null || m.sender.isEmpty()) ? "Unknown" : m.sender);
        }

        html.append("<div class='participants'>");
        html.append("<b>Participants:</b>");

        for (Map.Entry<String, String> p : participants.entrySet()) {

            String role = "";

            if (ownerId != null && ownerId.equals(p.getKey())) {
                role = " (Owner)";
            }

            html.append("<div>")
                    .append(p.getKey())
                    .append(" - ")
                    .append(p.getValue())
                    .append(role)
                    .append("</div>");
        }

        html.append("</div>");

        for (MessageBubble m : messages) {

            boolean isOwner = ownerId != null && ownerId.equals(m.senderId);

            String cssClass;

            if (m.isSystem) {
                cssClass = "system";
            } else {
                cssClass = isOwner ? "right" : "left";
            }

            html.append("<div class='msg ")
                    .append(cssClass)
                    .append("'>");

            html.append("<div class='sender'>")
                    .append(m.sender == null ? "Unknown" : m.sender)
                    .append(" [")
                    .append(m.senderId == null ? "no-id" : m.senderId)
                    .append("]")
                    .append("</div>");

            html.append("<div>")
                    .append((m.text == null || m.text.trim().isEmpty())
                            ? "[Attachment / Content]"
                            : m.text)
                    .append("</div>");

            html.append("<div class='meta'>")
                    .append(m.time == null ? "" : m.time)
                    .append("</div>");

            html.append("</div>");
        }

        html.append("<div style='clear:both;'></div>");
        html.append("</div>");

        html.append("</body></html>");

        return html.toString();
    }

    private static String escapeHtml(String s) {
        if (s == null)
            return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}