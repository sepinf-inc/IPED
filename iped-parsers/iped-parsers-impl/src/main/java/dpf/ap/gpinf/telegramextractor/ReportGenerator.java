/*
 * Copyright 2020-2020, João Vitor de Sá Hauck
 * 
 * This file is part of Indexador e Processador de Evidencias Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.ap.gpinf.telegramextractor;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.List;

import dpf.sp.gpinf.indexer.parsers.util.Messages;
import iped3.io.IItemBase;
import iped3.search.IItemSearcher;

public class ReportGenerator {

    private static final int MIN_SIZE_TO_SPLIT_CHAT = 5000000;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd"); //$NON-NLS-1$
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss XXX"); //$NON-NLS-1$
    private IItemSearcher searcher;
    private boolean firstFragment = true;
    private int currentMsg = 0;

    ReportGenerator(IItemSearcher s) {
        this.searcher = s;
    }

    public int getNextMsgNum() {
        return currentMsg;
    }

    private String format(String s) {
        if (s == null || s.isEmpty()) {
            return "-";
        }
        return s;
    }

    public byte[] genarateContactHtml(Contact contact) {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, StandardCharsets.UTF_8)); // $NON-NLS-1$

        out.println("<!DOCTYPE html>\n" //$NON-NLS-1$
                + "<html>\n" //$NON-NLS-1$
                + "<head>\n" //$NON-NLS-1$
                + "	<title>" + contact.getId() + "</title>\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "	<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n" //$NON-NLS-1$
                + "</head>\n" //$NON-NLS-1$
                + "<body>\n"); //$NON-NLS-1$

        if (contact.getAvatar() != null)
            out.println("<img src=\"data:image/jpg;base64," //$NON-NLS-1$
                    + dpf.mg.udi.gpinf.whatsappextractor.Util.encodeBase64(contact.getAvatar())
                    + "\" width=\"112\"/><br>"); //$NON-NLS-1$
        out.println(Messages.getString("TelegramContact.ContactID") + contact.getId());
        out.println("<br>" + Messages.getString("TelegramContact.FirstName") + format(contact.getName()));
        out.println("<br>" + Messages.getString("TelegramContact.LastName") + format(contact.getLastName()));
        out.println("<br>" + Messages.getString("TelegramContact.Username") + format(contact.getUsername()));
        out.println("<br>" + Messages.getString("TelegramContact.Phone") + format(contact.getPhone()));
        out.println("</body>\n</html>"); //$NON-NLS-1$

        out.flush();
        out.close();

        return bout.toByteArray();
    }

    public byte[] generateNextChatHtml(Chat c) {

        if ((!firstFragment && currentMsg == 0) || (currentMsg > 0 && currentMsg == c.getMessages().size()))
            return null;

        firstFragment = false;

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, StandardCharsets.UTF_8));
        String title = c.getName();
        if (!c.isGroup()) {
            if (c.getC().getPhone() != null)
                title += " phone:" + c.getC().getPhone();
            else if (c.getC().getUsername() != null)
                title += " user:" + c.getC().getUsername();
        }

        printMessageFileHeader(out, title, c.getId() + "", c.getC().getAvatar());

        if (currentMsg > 0)
            out.println("<div class=\"linha\"><div class=\"date\">" //$NON-NLS-1$
                    + Messages.getString("WhatsAppReport.ChatContinuation") + "</div></div>"); //$NON-NLS-1$ //$NON-NLS-2$

        String lastDate = null;
        while (currentMsg < c.getMessages().size()) {
            Message m = c.getMessages().get(currentMsg++);
            String thisDate = null;
            if (m.getTimeStamp() != null) {
                thisDate = dateFormat.format(m.getTimeStamp());
            }
            if (thisDate != null && (lastDate == null || !lastDate.equals(thisDate))) {
                out.println("<div class=\"linha\"><div class=\"date\">" //$NON-NLS-1$
                        + thisDate + "</div></div>"); //$NON-NLS-1$
                lastDate = thisDate;
            }

            printMessage(out, m, c.isGroup());

            if (currentMsg != c.getMessages().size() && bout.size() >= MIN_SIZE_TO_SPLIT_CHAT) {
                out.println("<div class=\"linha\"><div class=\"date\">" //$NON-NLS-1$
                        + Messages.getString("WhatsAppReport.ChatContinues") + "</div></div>"); //$NON-NLS-1$ //$NON-NLS-2$
                break;
            }
        }

        printMessageFileFooter(out);
        out.flush();

        return bout.toByteArray();
    }

    private TagHtml getThumbTag(Message m, String classnotfound) {
        byte thumb[] = m.getThumb();

        if (thumb == null && m.getMediaHash() != null) {
            List<IItemBase> result = null;
            result = dpf.sp.gpinf.indexer.parsers.util.Util.getItems("hash:" + m.getMediaHash(), searcher);
            if (result != null && !result.isEmpty()) {
                thumb = result.get(0).getThumb();
            }
        }

        TagHtml img;
        if (thumb != null) {
            img = new TagHtml("img");
            img.setAtribute("class", "thumb iped-show");
            img.setAtribute("src",
                    "data:image/jpg;base64," + dpf.mg.udi.gpinf.whatsappextractor.Util.encodeBase64(thumb));
        } else {
            img = new TagHtml("div");
            img.setAtribute("class", classnotfound);
        }
        return img;
    }

    private void printVideo(PrintWriter out, Message message) {

        if (message.getMediaHash() != null) {

            printCheckbox(out, message.getMediaHash());

            TagHtml div = new TagHtml("div");
            if (message.getMediaComment() != null) {
                div.setAtribute("class", "tooltip");
                div.getInner().add("<span class=\"tooltiptext\">" + message.getMediaComment() + "</span>");
            }

            TagHtml link = new TagHtml("a");
            link.setAtribute("onclick", "app.open('hash:" + message.getMediaHash() + "')");

            TagHtml img = getThumbTag(message, "videoImg iped-show");

            img.setAtribute("title", "Video");
            link.getInner().add(img);

            div.getInner().add(link);

            TagHtml video = new TagHtml("video");
            video.setAtribute("class", "thumb iped-hide");
            video.setAtribute("controls", null);
            String reportSource = dpf.sp.gpinf.indexer.parsers.util.Util.getExportPath(message.getMediaHash(),
                    message.getMediaExtension());
            video.getInner().add("<source src=\"" + reportSource + "\"/>");
            String originalSource = dpf.sp.gpinf.indexer.parsers.util.Util
                    .getSourceFileIfExists(message.getMediaFile());
            if (originalSource != null) {
                video.getInner().add("<source src=\"" + originalSource + "\"/>");
            }
            div.getInner().add(video);

            out.println(div.toString());
            out.println("<br/>");

        } else {
            out.println(getThumbTag(message, "videoImg iped-show").toString()); //$NON-NLS-1$
        }

    }

    private void printAudio(PrintWriter out, Message message) {

        TagHtml img = new TagHtml("div");
        img.setAtribute("class", "audioImg");
        img.setAtribute("title", "Audio");

        if (message.getMediaHash() != null) {

            printCheckbox(out, message.getMediaHash());

            TagHtml div = new TagHtml("div");
            if (message.getMediaComment() != null) {
                div.setAtribute("class", "tooltip");
                div.getInner().add("<span class=\"tooltiptext\">" + message.getMediaComment() + "</span>");
            }

            TagHtml link = new TagHtml("a");
            link.setAtribute("onclick", "app.open('hash:" + message.getMediaHash() + "')");

            img.setAtribute("class", "audioImg iped-show");

            link.getInner().add(img);
            div.getInner().add(link);

            TagHtml audio = new TagHtml("audio");
            audio.setAtribute("class", "iped-hide");
            audio.setAtribute("controls", null);
            String reportSource = dpf.sp.gpinf.indexer.parsers.util.Util.getExportPath(message.getMediaHash(),
                    message.getMediaExtension());
            audio.getInner().add("<source src=\"" + reportSource + "\"/>");
            String originalSource = dpf.sp.gpinf.indexer.parsers.util.Util
                    .getSourceFileIfExists(message.getMediaFile());
            if (originalSource != null) {
                audio.getInner().add("<source src=\"" + originalSource + "\"/>");
            }
            div.getInner().add(audio);
            out.println(div.toString());

        } else {
            out.println(img.toString());
        }

        out.println("<br/>");

    }

    private void printImage(PrintWriter out, Message message) {
        printImage(out, message, false);
    }

    private void printImage(PrintWriter out, Message message, boolean isLink) {
        if (isLink) {
            out.print("Link:<br/>");
        }
        if (message.getMediaHash() != null) {

            printCheckbox(out, message.getMediaHash());

            TagHtml div = new TagHtml("div");
            if (message.getMediaComment() != null) {
                div.setAtribute("class", "tooltip");
                div.getInner().add("<span class=\"tooltiptext\">" + message.getMediaComment() + "</span>");
            }

            TagHtml link = new TagHtml("a");
            link.setAtribute("onclick", "app.open('hash:" + message.getMediaHash() + "')");
            String ref = dpf.sp.gpinf.indexer.parsers.util.Util.getReportHref(message.getMediaHash(),
                    message.getMediaExtension(), message.getMediaFile());
            link.setAtribute("href", ref);

            TagHtml img = getThumbTag(message, "imageImg");

            String title = isLink ? "Link" : "Image";
            img.setAtribute("title", title);
            link.getInner().add(img);
            div.getInner().add(link);

            out.println(div.toString());

        } else if (!isLink) {
            out.println(getThumbTag(message, "imageImg").toString()); //$NON-NLS-1$
        }
        out.println("<br/>");

    }

    private void printAttachment(PrintWriter out, Message message) {
        if (message.getMediaHash() != null) {

            printCheckbox(out, message.getMediaHash());

            TagHtml div = new TagHtml("div");
            if (message.getMediaComment() != null) {
                div.setAtribute("class", "tooltip");
                div.getInner().add("<span class=\"tooltiptext\">" + message.getMediaComment() + "</span>");
            }

            TagHtml link = new TagHtml("a");
            link.setAtribute("onclick", "app.open('hash:" + message.getMediaHash() + "')");
            String ref = dpf.sp.gpinf.indexer.parsers.util.Util.getReportHref(message.getMediaHash(),
                    message.getMediaExtension(), message.getMediaFile());
            link.setAtribute("href", ref);

            TagHtml img = getThumbTag(message, "attachImg");

            img.setAtribute("title", "Attachment");
            link.getInner().add(img);
            div.getInner().add(link);

            out.println(div.toString());

        } else {
            out.println(getThumbTag(message, "attachImg").toString()); //$NON-NLS-1$
        }
        out.println("<br/>");

    }

    private void printGeoLocation(PrintWriter out, Message message) {
        // toDo better handling Geo locations
        out.println("Latitude: " + message.getLatitude() + "<br/>");
        out.println("Longitude: " + message.getLongitude());
    }

    private void printCheckbox(PrintWriter out, String hash) {
        out.println("<input class=\"check iped-show\" type=\"checkbox\" onclick=\"app.check('hash:" + hash
                + "', this.checked)\" name=\"" + hash + "\" />");
    }

    private void printLink(PrintWriter out, Message message) {
        printImage(out, message, true);
    }

    private void printMessage(PrintWriter out, Message message, boolean group) {
        out.println("<div class=\"linha\" id=\"" + message.getId() + "\">"); //$NON-NLS-1$
        if (message.isFromMe()) {
            out.println("<div class=\"outgoing to\">"); //$NON-NLS-1$
        } else {
            out.println("<div class=\"incoming from\">"); //$NON-NLS-1$
        }
        Contact contact = message.getFrom();
        if (contact != null) {
            out.println("<span style=\"font-family: 'Roboto-Medium'; color: #b4c74b;\">" //$NON-NLS-1$
                    + contact + "</span><br/>"); //$NON-NLS-1$
        }
        if (message.getType() != null && !message.getType().isEmpty()) {
            out.print(message.getType() + "<br>");
        }
        if (message.getMediaMime() != null) {
            if (message.getMediaMime().equals("geo")) {
                printGeoLocation(out, message);
            } else if (message.getMediaMime().toLowerCase().startsWith("video")) {
                printVideo(out, message);
            } else if (message.getMediaMime().toLowerCase().startsWith("image")) {
                printImage(out, message);

            } else if (message.getMediaMime().toLowerCase().startsWith("audio")) {
                printAudio(out, message);
            } else if (message.getMediaMime().toLowerCase().startsWith("link")) {
                printLink(out, message);
            } else {
                printAttachment(out, message);
            }

        }
        if (message.getData() != null) {
            out.print(message.getData()); // $NON-NLS-1$
        }

        out.println("<br/>");

        if (!message.getChildPornSets().isEmpty()) {
            out.print("<p><i>" + Messages.getString("TelegramReport.LEDKFF") + " "
                    + message.getChildPornSets().toString() + "</i></p>");
        }

        out.println("<span class=\"time\">"); //$NON-NLS-1$
        if (message.getTimeStamp() != null)
        out.println(timeFormat.format(message.getTimeStamp()) + " &nbsp;"); //$NON-NLS-1$
        out.println("</span>"); //$NON-NLS-1$

        out.println("</div></div>"); //$NON-NLS-1$

    }

    private static void printMessageFileHeader(PrintWriter out, String title, String id, byte[] avatar) {
        out.println("<!DOCTYPE html>\n" //$NON-NLS-1$
                + "<html>\n" //$NON-NLS-1$
                + "<head>\n" //$NON-NLS-1$
                + "	<title>" + id + "</title>\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "	<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n" //$NON-NLS-1$
                + "	<meta name=\"viewport\" content=\"width=device-width\" />\n" //$NON-NLS-1$
                + "     <meta charset=\"UTF-8\" />\n" //$NON-NLS-1$
                + "	<link rel=\"shortcut icon\" href=\"" //$NON-NLS-1$
                + dpf.mg.udi.gpinf.whatsappextractor.Util.getImageResourceAsEmbedded("img/favicon.ico") + "\" />\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "<style>\n" + dpf.mg.udi.gpinf.whatsappextractor.Util.readResourceAsString("css/whatsapp.css") //$NON-NLS-2$
                + Util.readResourceAsString("css/tooltip.css") + "\n</style>\n" + "<script>\n" //$NON-NLS-3$
                + "var css = document.createElement(\"style\");\n" //$NON-NLS-1$
                + "css.type = \"text/css\";\n" //$NON-NLS-1$
                + "var inHtml = \"\";\n" //$NON-NLS-1$
                + "if (navigator.userAgent.search(\"JavaFX\") >= 0) {\n" //$NON-NLS-1$
                + "  inHtml = \".iped-hide { display: none; }\";\n" //$NON-NLS-1$
                + "  inHtml += \".iped-show { display: inline-block; }\";\n" //$NON-NLS-1$
                + "} else {\n" //$NON-NLS-1$
                + "  inHtml = \".iped-hide { display: inline-block; }\";\n" //$NON-NLS-1$
                + "  inHtml += \".iped-show { display: none; }\";\n" //$NON-NLS-1$
                + "}\n" //$NON-NLS-1$
                + "css.innerHTML = inHtml;\n" //$NON-NLS-1$
                + "document.head.appendChild(css);\n" //$NON-NLS-1$
                + "function openIfExists(url2, url1){\r\n"
                + "    if (navigator.userAgent.search(\"JavaFX\") >= 0) return;\r\n" + "    var img1 = new Image();\r\n"
                + "    img1.onload = () => window.location = url1;\r\n"
                + "    img1.onerror = () => window.location = url2;\r\n" + "    img1.src = url1;\r\n" + "}\r\n"
                + "</script>\n" //$NON-NLS-1$
                + dpf.mg.udi.gpinf.vcardparser.VCardParser.HTML_STYLE + "</head>\n" //$NON-NLS-1$
                + "<style>.check {vertical-align: top;}</style>" + "<body style='background-image:url(" //$NON-NLS-2$
                + dpf.mg.udi.gpinf.whatsappextractor.Util.getImageResourceAsEmbedded("img/telegramwallpaper.jpg")
                + ")'>\n" + "<div id=\"topbar\" class='telegram'>\n" //$NON-NLS-2$
                + "	<span class=\"left\">" //$NON-NLS-1$
                + " &nbsp; "); //$NON-NLS-1$

        if (avatar != null) {
            out.println(
                    "<img src=\"data:image/jpg;base64," + dpf.mg.udi.gpinf.whatsappextractor.Util.encodeBase64(avatar) //$NON-NLS-1$
                            + "\" width=\"40\" height=\"40\"/>"); //$NON-NLS-1$

        }
        out.println(title + "</span>\n" //$NON-NLS-1$
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
