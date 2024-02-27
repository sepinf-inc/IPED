package iped.parsers.discord;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;

import iped.data.IItemReader;
import iped.parsers.discord.json.DiscordAttachment;
import iped.parsers.discord.json.DiscordAuthor;
import iped.parsers.discord.json.DiscordMention;
import iped.parsers.discord.json.DiscordReaction;
import iped.parsers.discord.json.DiscordRoot;
import iped.parsers.discord.json.DiscordSticker;
import iped.parsers.util.Messages;
import iped.parsers.util.Util;
import iped.properties.BasicProps;
import iped.search.IItemSearcher;
import iped.utils.IOUtil;
import iped.utils.SimpleHTMLEncoder;

/***
 * 
 * @author PCF Campanini
 *
 */
public class DiscordHTMLReport {

    private static final String defaultAvatarMe = Base64.getEncoder().encodeToString(readResourceAsBytes("discordme.png"));
    private static final String defaultAvatarOther = Base64.getEncoder().encodeToString(readResourceAsBytes("discord.png"));
    private static final String lottiejs = new String(readResourceAsBytes("lottie-player.js"), Charset.forName("UTF-8"));
    private static final String CSS = new String(readResourceAsBytes("/iped/parsers/discord/discord.css"), Charset.forName("UTF-8"));

    private DiscordAuthor me;

    public DiscordHTMLReport(DiscordAuthor me) {
        this.me = me;
    }

    private static byte[] readResourceAsBytes(String resource) {
        byte[] result = null;
        try {
            result = IOUtil.loadInputStream(DiscordHTMLReport.class.getResourceAsStream(resource));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return result;
    }

    public byte[] convertToHTML(List<DiscordRoot> drl, IItemSearcher searcher) throws IOException {
        return convertToHTML(drl, searcher, true);
    }

    public byte[] convertToHTML(List<DiscordRoot> drl, IItemSearcher searcher, boolean includeHeader) throws IOException {

        ByteArrayOutputStream bout = new ByteArrayOutputStream();

        PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, StandardCharsets.UTF_8));

        SimpleDateFormat dateFormat = new SimpleDateFormat(Messages.getString("DiscordParser.DateFormat0"));

        if (includeHeader) {
            out.println("<!DOCTYPE html>");
            out.println("<HTML>");
            out.println("<HEAD>");
            out.println(" <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
            out.println("<style>" + CSS + "</style>");
            out.println("<script>"); //$NON-NLS-1$
            out.println(iped.parsers.whatsapp.Util.readResourceAsString("js/whatsapp.js"));
            out.println("</script>"); //$NON-NLS-1$
            // out.println("<script
            // src=\"https://unpkg.com/@lottiefiles/lottie-player@latest/dist/lottie-player.js\"
            // crossorigin=\"\"></script>"); //$NON-NLS-1$
            out.println("<script>\n" + lottiejs + "\n</script>");

            out.println("</HEAD>");
            out.println("<BODY>");
        }

        out.println("<TABLE>");

        // Used to know when user ID changed
        String userID = "";
        String colorClass = "black";
        for (DiscordRoot dr : drl) {

            if (dr.isPinned()) {
                // TODO rule for when message is pinned
            }

            // TODO check mentions roles
            if (!dr.getAuthor().getId().equals(userID)) {

                userID = dr.getAuthor().getId();
                colorClass = (colorClass.equals("black") ? "gray" : "black");

                out.println("<TR class='" + colorClass + "'>");
                out.println("	<TD colspan='2'>");
                out.println("		<TABLE class='title'>");
                out.println("			<TR>");
                out.println("				<TD>");

                if (dr.getAuthor().getAvatarBytes() == null) {
                    String avatar = dr.getAuthor().getAvatar();
                    if (avatar == null || avatar.trim().equals("")) {
                        String defaultAvatar;
                        if (me != null && dr.getAuthor().getId().equals(me.getId())) {
                            defaultAvatar = defaultAvatarMe;
                        } else {
                            defaultAvatar = defaultAvatarOther;
                        }
                        out.println("                   <img src='data:image/jpeg;base64, " + defaultAvatar + "' alt='' width='50' height='50'>");
                    } else {
                        out.println("                   <img src='https://cdn.discordapp.com/avatars/" + format(dr.getAuthor().getId()) + "/" + format(dr.getAuthor().getAvatar()) + ".png' alt='' width='50' height='50'>");
                    }
                } else {
                    out.println("                   <img src='data:image/jpeg;base64, " + Base64.getEncoder().encodeToString(dr.getAuthor().getAvatarBytes()) + "' alt='' width='50' height='50'>");
                }

                out.println("				</TD>");
                out.println("				<TD>");
                out.println("					<span title='Channel ID=" + format(dr.getChannel_id()) + ", UserID=" + format(dr.getAuthor().getId()) + "'>" + format(dr.getAuthor().getName()) + "<b>"
                        + format(dr.getAuthor().getFullUsername()) + "</b></span>");
                out.println("				</TD>");
                out.println("				<TD>");
                out.println((dr.getTimestamp() == null ? "" : dateFormat.format(dr.getTimestamp())));
                out.println("				</TD>");
                out.println("			</TR>");
                out.println("		</TABLE>");
                out.println("	</TD>");
                out.println("</TR>");

            }

            out.println("<TR class='" + colorClass + "' id='" + format(dr.getId()) + "'>");

            // message sending time
            out.println("	<TD class='td-timestamp'>");
            out.println("<P>" + (dr.getTimestamp() == null ? "" : formatDate(dr.getTimestamp())) + "</P>");
            out.println("<P>" + (dr.getEditedTimestamp() == null ? "" : "(" + Messages.getString("DiscordParser.EditTime") + dateFormat.format(dr.getEditedTimestamp()) + ")") + "</P>");
            out.println("	</TD>");

            // message body
            out.println("	<TD>");

            List<DiscordSticker> stickers = dr.getStickers();
            if (stickers != null) {
                for (DiscordSticker sticker : stickers) {
                    String hash = sticker.getMediaHash();
                    String query = BasicProps.HASH + ":" + hash;
                    Iterator<IItemReader> it = searcher.searchIterable(query).iterator();
                    // if hash exists, at least 1 item will be returned
                    IItemReader item = it.next();
                    byte buff[] = item.getBufferedInputStream().readAllBytes();
                    out.println("<lottie-player src=\"data:application/json;base64, " + Base64.getEncoder().encodeToString(buff)
                            + "\" background=\"transparent\"  speed=\"1\"  style=\"width: 300px; height: 300px;\" loop controls autoplay><lottie-player>");
                }
            }

            // used for debug
            // out.println("<TABLE><TR><TD>" + dr.toString() + "</TD></TR></TABLE>");

            // rule for calls
            if (dr.getCall() != null) {
                out.println("<TABLE>");
                out.println("	<TR>");
                out.println("		<TD colspan='2'>Call</TD>");
                out.println("	</TR>");
                out.println("	<TR>");
                out.println("		<TD>" + Messages.getString("DiscordParser.Start") + "</TD>");
                out.println("		<TD>" + (dr.getTimestamp() == null ? "" : dateFormat.format(dr.getTimestamp())) + "</TD>");
                out.println("	</TR>");
                out.println("	<TR>");
                out.println("		<TD>" + Messages.getString("DiscordParser.End") + "</TD>");
                out.println("		<TD>" + (dr.getCall().getEndedTimestamp() == null ? "" : dateFormat.format(dr.getCall().getEndedTimestamp())) + "</TD>");
                out.println("	</TR>");
                out.println("	<TR>");
                out.println("		<TD>" + Messages.getString("DiscordParser.Participants") + "</TD>");
                out.println("		<TD>" + format(dr.getCall().getParticipantsNames(drl)) + "</TD>");
                out.println("	</TR>");
                out.println("</TABLE>");
            }

            // rule for reactions
            if (dr.getReactions() != null) {

                List<String> reactions = new ArrayList<String>();

                for (DiscordReaction reaction : dr.getReactions()) {
                    reactions.add(format(reaction.getEmoji().getName()));
                }

                out.println("<TABLE>");
                out.println("	<TR>");
                out.println("		<TD>" + Messages.getString("DiscordParser.Reactions") + String.join(", ", reactions) + "</TD>");
                out.println("	</TR>");
                out.println("</TABLE>");
            }

            // rule for attachments
            if (dr.getAttachments() != null && dr.getAttachments().size() > 0) {

                out.println("<TABLE>");
                out.println("	<TR>");
                out.println("		<TD colspan='2'>" + Messages.getString("DiscordParser.Attachments") + "</TD>");
                out.println("	</TR>");

                for (DiscordAttachment att : dr.getAttachments()) {
                    out.println("<TR>");
                    out.println("	<TD>");
                    if (att.getMediaHash() != null) {
                        String query = BasicProps.HASH + ":" + att.getMediaHash();
                        Iterator<IItemReader> it = searcher.searchIterable(query).iterator();
                        // if hash exists, at least 1 item will be returned
                        IItemReader item = it.next();
                        printCheckbox(out, att.getMediaHash());

                        String exportPath = iped.parsers.util.Util.getExportPath(item);
                        String source = iped.parsers.util.Util.getSourceFileIfExists(item).orElse("");
                        byte[] thumb = item.getThumb();

                        out.println("       <a onclick=\"app.open('hash:" + att.getMediaHash() + "')\" href='" + Util.getExportPath(item) + "'>");
                        out.println("       " + format(att.getFilename()) + "<BR/>");

                        if (!item.getMediaType().toString().startsWith("video/") && !item.getMediaType().toString().startsWith("audio/")) {
                            if (thumb != null) {
                                out.println("       <img src=\"data:image/jpeg;base64," + Base64.getEncoder().encodeToString(thumb) + "\" title=\"" + format(att.getFilename()) + "\">");
                            } else if (item.getMediaType().toString().startsWith("image/")) {
                                out.println("       <div class=\"imageImg\" title=\"" + format(att.getFilename()) + "\">");
                            } else {
                                out.println("       <div class=\"attachImg\" title=\"" + format(att.getFilename()) + "\">");
                            }
                        }

                        if (item.getMediaType().toString().startsWith("audio/")) {
                            out.println("<div class=\"audioImg iped-audio\" " //$NON-NLS-1$
                                    + " title=\"Audio\" " + "data-src1=\"" + format(exportPath) + "\" " + "data-src2=\"" //$NON-NLS-4$
                                    + format(source) + "\"></div>");
                        }
                        if (item.getMediaType().toString().startsWith("video/")) {
                            if (thumb != null) {
                                out.println("<img class=\"thumb iped-video\" src=\"" + "data:image/jpg;base64," + iped.parsers.whatsapp.Util.encodeBase64(thumb) + "\"" + " data-src1=\"" + format(exportPath) + "\"" + " data-src2=\""
                                        + format(source) + "\"" //$NON-NLS-1$
                                        + " title=\"" + att.getFilename() //$NON-NLS-1$
                                        + "\"/>");
                            } else {
                                out.println("<div class=\"videoImg iped-video\" title=\"Video\"" + " data-src1=\"" + format(exportPath) + "\"" + " data-src2=\"" + format(source) + "\"></div>");
                            }
                        }

                        out.println("       </a>");

                    } else {
                        out.println("       <DIV>" + format(att.getFilename()) + "</DIV>");
                    }
                    if (!att.getChildPornSets().isEmpty()) {
                        out.println("       <BR/>");
                        out.print("<p><i>" + Messages.getString("WhatsAppReport.FoundInPedoHashDB") + " " + format(att.getChildPornSets().toString()) + "</i></p>");
                    }
                    out.println("	</TD>");
                    out.println("</TR>");
                }
                out.println("</TABLE>");
            }

            if (dr.getMessageContent() != null && !dr.getMessageContent().isEmpty()) {

                String escapedMessage = format(dr.getMessageContent());

                // rule for mentions
                if (dr.getMentions() != null && dr.getMentions().size() > 0) {
                    for (DiscordMention dm : dr.getMentions()) {
                        escapedMessage = escapedMessage.replace(format("<@" + dm.getId() + ">"), "<span title='UserID=" + format(dm.getId()) + "'>" + "<B style='color:#0099FF'>@" + format(dm.getFullUsername()) + "</B>" + "</span>");
                    }
                }
                out.println(escapedMessage);
            }

            out.println("	</TD>");
            out.println("</TR>");
        }

        out.println("</TABLE>");
        if (includeHeader) {
            out.println("</BODY>");
            out.println("</HTML>");
        }

        out.flush();
        out.close();

        return bout.toByteArray();
    }

    public String formatDate(Date date) {

        SimpleDateFormat df1 = new SimpleDateFormat("hh:mm:ss");
        SimpleDateFormat df2 = new SimpleDateFormat(Messages.getString("DiscordParser.DateFormat2"));

        return "<P>" + df1.format(date) + "<BR/>(" + df2.format(date) + ")</P>";

    }

    public void formatDate(Date date, XHTMLContentHandler xHandler) throws SAXException {

        SimpleDateFormat df1 = new SimpleDateFormat("hh:mm:ss");
        SimpleDateFormat df2 = new SimpleDateFormat(Messages.getString("DiscordParser.DateFormat2"));

        xHandler.startElement("p");
        xHandler.characters(df1.format(date));
        xHandler.startElement("BR");
        xHandler.endElement("BR");
        xHandler.characters("(" + df2.format(date) + ")");
        xHandler.endElement("p");
    }

    private void printCheckbox(PrintWriter out, String hash) {
        out.println("<input class=\"check\" type=\"checkbox\" onclick=\"app.check('hash:" + hash + "', this.checked)\" name=\"" + hash + "\" />");
    }

    private static final String format(String s) {
        if (s == null || s.trim().isEmpty())
            return ""; //$NON-NLS-1$
        else
            return SimpleHTMLEncoder.htmlEncode(s.trim());

    }

}
