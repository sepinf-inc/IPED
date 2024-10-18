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
package iped.parsers.telegram;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.List;

import iped.data.IItemReader;
import iped.parsers.util.Messages;
import iped.search.IItemSearcher;
import iped.utils.EmojiUtil;
import iped.utils.SimpleHTMLEncoder;

public class ReportGenerator {

    private int minChatSplitSize = 6000000;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd"); //$NON-NLS-1$
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss XXX"); //$NON-NLS-1$
    private IItemSearcher searcher;
    private boolean firstFragment = true;
    private int currentMsg = 0;
    
    private static final String emptyMD5 = "d41d8cd98f00b204e9800998ecf8427e";

    private String creatSpanTag(String text) {
        return "<span class=\"tooltiptext\">" + SimpleHTMLEncoder.htmlEncode(text) + "</span>";
    }

    ReportGenerator(IItemSearcher s) {
        this.searcher = s;
    }

    public int getNextMsgNum() {
        return currentMsg;
    }

    public void setMinChatSplitSize(int minChatSplitSize) {
        this.minChatSplitSize = minChatSplitSize;
    }

    private String format(String s) {
        if (s == null || s.isEmpty()) {
            return "-";
        }
        String ret = SimpleHTMLEncoder.htmlEncode(s.trim());

        // Keep line breaks present in the content, converting to an HTML <br>
        ret = ret.replaceAll("\n", "<br>\n");

        return ret;
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
                    + iped.parsers.whatsapp.Util.encodeBase64(contact.getAvatar())
                    + "\" width=\"112\"/><br>"); //$NON-NLS-1$
        out.println(Messages.getString("TelegramContact.ContactID") + " " + contact.getId());
        out.println("<br>" + Messages.getString("TelegramContact.FirstName") + " " + format(contact.getName()));
        out.println("<br>" + Messages.getString("TelegramContact.LastName") + " " + format(contact.getLastName()));
        out.println("<br>" + Messages.getString("TelegramContact.Username") + " " + format(contact.getUsername()));
        out.println("<br>" + Messages.getString("TelegramContact.Phone") + " " + format(contact.getPhone()));
        if (contact.isGroup()) {
            out.println("<br>[" + Messages.getString("TelegramContact.Group") + "]");
        } else if (contact.isChannel()) {
            out.println("<br>[" + Messages.getString("TelegramContact.Channel") + "]");
        }
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
        if (c.isGroup()) {
            title = Messages.getString("TelegramContact.Group") + ": " + title;
        } else if (c.isChannel()) {
            title = Messages.getString("TelegramContact.Channel") + ": " + title;
        } else {
            if (c.getC().getPhone() != null) {
                title += " (" + Messages.getString("TelegramContact.Phone") + " " + c.getC().getPhone() + ")";
            } else if (c.getC().getUsername() != null) {
                title += " (" + Messages.getString("TelegramContact.Username") + " " + c.getC().getUsername() + ")";
            }
        }

        printMessageFileHeader(out, title, c.getC().getAvatar(), c.isGroup(), c.isChannel(), c.isDeleted());

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

            printMessage(out, m);

            if (currentMsg != c.getMessages().size() && bout.size() >= minChatSplitSize) {
                out.println("<div class=\"linha\"><div class=\"date\">" //$NON-NLS-1$
                        + Messages.getString("WhatsAppReport.ChatContinues") + "</div></div>"); //$NON-NLS-1$ //$NON-NLS-2$
                break;
            }
        }

        printMessageFileFooter(out);
        out.flush();

        return EmojiUtil.replaceByImages(bout.toByteArray());
    }

    private TagHtml getThumbTag(Message m, String classnotfound) {
        byte thumb[] = m.getThumb();

        if (searcher != null && thumb == null && m.getMediaHash() != null && !m.getMediaHash().isBlank()) {
            if (!m.getMediaHash().equalsIgnoreCase(emptyMD5)) {
                List<IItemReader> result = iped.parsers.util.Util.getItems("md5:" + m.getMediaHash(), searcher);
                if (result != null && !result.isEmpty()) {
                    thumb = result.get(0).getThumb();
                }
            }
        }

        TagHtml img;
        if (thumb != null) {
            img = new TagHtml("img");
            img.setAtribute("class", "thumb");
            img.setAtribute("src",
                    "data:image/jpg;base64," + iped.parsers.whatsapp.Util.encodeBase64(thumb));
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
                div.getInner().add(creatSpanTag(message.getMediaComment()));
            }

            TagHtml link = new TagHtml("a");
            link.setAtribute("onclick", "app.open('hash:" + message.getMediaHash() + "')");

            TagHtml img = getThumbTag(message, "videoImg");

            String reportSource = iped.parsers.util.Util.getExportPath(message.getMediaHash(),
                    message.getMediaExtension());
            String originalSource = iped.parsers.util.Util
                    .getSourceFileIfExists(message.getMediaItem()).orElse("");

            img.setAtribute("title", "Video");
            img.setAtribute("data-src1", format(reportSource));
            img.setAtribute("data-src2", format(originalSource));
            img.setAtribute("class", img.getAtribute("class") + " iped-video");

            link.getInner().add(img);

            div.getInner().add(link);


            out.println(div.toString());
            out.println("<br/>");

        } else {
            out.println(getThumbTag(message, "videoImg").toString()); //$NON-NLS-1$
        }

    }

    private void printAudio(PrintWriter out, Message message) {

        TagHtml img = new TagHtml("div");
        img.setAtribute("title", "Audio");

        if (message.getMediaHash() != null) {

            printCheckbox(out, message.getMediaHash());

            TagHtml div = new TagHtml("div");
            if (message.getMediaComment() != null) {
                div.setAtribute("class", "tooltip");
                div.getInner().add(creatSpanTag(message.getMediaComment()));
            }

            TagHtml link = new TagHtml("a");
            link.setAtribute("onclick", "app.open('hash:" + message.getMediaHash() + "')");

            String reportSource = iped.parsers.util.Util.getExportPath(message.getMediaHash(),
                    message.getMediaExtension());
            String originalSource = iped.parsers.util.Util
                    .getSourceFileIfExists(message.getMediaItem()).orElse(null);

            if (reportSource != null) {
                img.setAtribute("data-src1", format(reportSource));
            }
            if (originalSource != null) {
                img.setAtribute("data-src2", format(originalSource));
            }
            img.setAtribute("class", "audioImg iped-audio");

            link.getInner().add(img);
            div.getInner().add(link);
            out.println(div.toString());

        } else {
            img.setAtribute("class", "audioImg");
            out.println(img.toString());
        }

        out.println("<br/>");

    }

    private void printImage(PrintWriter out, Message message) {
        printImage(out, message, false);
    }

    private void printImage(PrintWriter out, Message message, boolean isLink) {
        if (isLink) {
            out.print("<b>" + Messages.getString("TelegramReport.Link") + "</b><br/>");
            if (message.getUrl() != null) {
                out.print(Messages.getString("TelegramReport.LinkURL") + ": " + format(message.getUrl()) + "<br/>");
            }
            if (message.getLinkTitle() != null) {
                out.print(Messages.getString("TelegramReport.LinkTitle") + ": " + format(message.getLinkTitle())
                        + "<br/>");
            }
        }
        if (message.getMediaHash() != null) {

            printCheckbox(out, message.getMediaHash());

            TagHtml div = new TagHtml("div");
            if (message.getMediaComment() != null) {
                div.setAtribute("class", "tooltip");
                div.getInner().add(creatSpanTag(message.getMediaComment()));
            }

            TagHtml link = new TagHtml("a");
            link.setAtribute("onclick", "app.open('hash:" + message.getMediaHash() + "')");
            String ref = iped.parsers.util.Util.getReportHref(message.getMediaItem());
            link.setAtribute("href", format(ref));

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
                div.getInner().add(creatSpanTag(message.getMediaComment()));
            }

            TagHtml link = new TagHtml("a");
            link.setAtribute("onclick", "app.open('hash:" + message.getMediaHash() + "')");
            String ref = iped.parsers.util.Util.getReportHref(message.getMediaItem());
            link.setAtribute("href", format(ref));

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
        out.println("<input class=\"check\" type=\"checkbox\" onclick=\"app.check('hash:" + hash
                + "', this.checked)\" name=\"" + hash + "\" />");
    }

    private void printLink(PrintWriter out, Message message) {
        printImage(out, message, true);
    }

    private void printMessage(PrintWriter out, Message message) {

        out.println("<div class=\"linha\" id=\"" + message.getId() + "\">"); //$NON-NLS-1$
        if (message.isFromMe()) {
            out.println("<div class=\"bbr\"><div class=\"outgoing to\">"); //$NON-NLS-1$
        } else {
            out.println(
                    "<div class=\"bbl\"><div class=\"aw\"><div class=\"awl\"></div></div><div class=\"incoming from\">"); //$NON-NLS-1$
        }
        Contact contact = message.getFrom();
        if (contact != null) {
            out.println("<span style=\"font-family: Arial; color: #b4c74b;\">" //$NON-NLS-1$
                    + format(contact.toString()) + "</span><br/>"); //$NON-NLS-1$
        }
        if (message.getType() != null && !message.getType().isEmpty()) {
            out.print(format(message.getType()) + "<br>");
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

        PoolData poolData = message.getPoolData();
        if (poolData != null) {
            out.println("<b>" + Messages.getString("TelegramReport.Pool") + "</b><br/>" + format(poolData.getTitle()));
            out.println("<ul>");
            for (String opt : poolData.getOptions()) {
                out.println("<li>" + format(opt) + "</li>");
            }
            out.println("</ul>");
        }

        if (message.getData() != null) {
            out.print(format(message.getData()));
        }

        out.println("<br/>");

        if (!message.getChildPornSets().isEmpty()) {
            out.print("<p><i>" + Messages.getString("TelegramReport.FoundInPedoHashDB") + " "
                    + message.getChildPornSets().toString() + "</i></p>");
        }

        out.println("<span class=\"time\">"); //$NON-NLS-1$
        if (message.getTimeStamp() != null) {
            out.println(timeFormat.format(message.getTimeStamp()) + " &nbsp;"); //$NON-NLS-1$
        }
        out.println("</span>"); //$NON-NLS-1$

        if (message.isFromMe()) {
            out.println("</div><div class=\"aw\"><div class=\"awr\"></div></div>");
        } else {
            out.println("</div>");
        }

        out.println("</div></div>"); //$NON-NLS-1$

    }

    private static void printMessageFileHeader(PrintWriter out, String title, byte[] avatar,
            boolean isGroup, boolean isChannel, boolean isDeleted) {
        out.println("<!DOCTYPE html>\n" //$NON-NLS-1$
                + "<html>\n" //$NON-NLS-1$
                + "<head>\n" //$NON-NLS-1$
                + "	<title>" + title + "</title>\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "	<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n" //$NON-NLS-1$
                + "	<meta name=\"viewport\" content=\"width=device-width\" />\n" //$NON-NLS-1$
                + "     <meta charset=\"UTF-8\" />\n" //$NON-NLS-1$
                + "<link rel=\"icon\" href=\""
                + iped.parsers.whatsapp.Util.getImageResourceAsEmbedded("img/telegram.png") + "\">\n"
                + "<style>\n" + iped.parsers.whatsapp.Util.readResourceAsString("css/whatsapp.css") //$NON-NLS-2$
                + Util.readResourceAsString("css/tooltip.css") + "\n</style>\n" + "<script>"
                + iped.parsers.whatsapp.Util.readResourceAsString("js/whatsapp.js") + "</script>"
                + iped.parsers.vcard.VCardParser.HTML_STYLE + "</head>\n" //$NON-NLS-1$
                + "<style>.check {vertical-align: top;}</style>" + "<body style='background-image:url(" //$NON-NLS-2$
                + iped.parsers.whatsapp.Util.getImageResourceAsEmbedded("img/telegramwallpaper.jpg")
                + ")'>\n" + "<div id=\"topbar\" class='telegram'>\n" //$NON-NLS-2$
                + "	<span class=\"left\">" //$NON-NLS-1$
                + " &nbsp; "); //$NON-NLS-1$

        if (avatar != null) {
            out.println(
                    "<img src=\"data:image/jpg;base64," + iped.parsers.whatsapp.Util.encodeBase64(avatar) //$NON-NLS-1$
                            + "\" width=\"40\" height=\"40\"/>"); //$NON-NLS-1$

        }
        out.println(title + "</span>\n" //$NON-NLS-1$
                + "</div>\n" //$NON-NLS-1$
                + "<div id=\"conversation\">\n" //$NON-NLS-1$
                + "<br/><br/><br/>"); //$NON-NLS-1$

        if (isDeleted && (isGroup || isChannel)) {
            out.print("<div class=\"linha\"><div class=\"recoveredChat\">");
            if (isGroup) {
                out.print(Messages.getString("TelegramReport.RecoveredGroup"));
            } else {
                out.print(Messages.getString("TelegramReport.RecoveredChannel"));
            }
            out.println("</div></div>");
        }
    }

    private static void printMessageFileFooter(PrintWriter out) {
        out.println("	<br /><br /><br />\n" //$NON-NLS-1$
                + "</div>\n" //$NON-NLS-1$
                + "<div id=\"lastmsg\">&nbsp;</div>\n" //$NON-NLS-1$
                + "</body>\n" //$NON-NLS-1$
                + "</html>"); //$NON-NLS-1$
    }

}
