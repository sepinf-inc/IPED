package iped.parsers.discord;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
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
import iped.parsers.discord.json.DiscordMention;
import iped.parsers.discord.json.DiscordReaction;
import iped.parsers.discord.json.DiscordRoot;
import iped.parsers.util.Messages;
import iped.parsers.util.Util;
import iped.properties.BasicProps;
import iped.search.IItemSearcher;
import iped.utils.SimpleHTMLEncoder;

/***
 * 
 * @author PCF Campanini
 *
 */
public class DiscordHTMLReport {

    private static final String CSS = "TABLE {  border-collapse: collapse; font-family: Arial, sans-serif; } "
            + ".title tr td {border: none;}" + ".td-timestamp p {font-size: 10px;text-align:center}"
            + ".black {background-color: #383838; color:#E0E0E0;}"
            + ".gray {background-color: #585858; color:#E0E0E0;}"
            + "TH { border: solid; font-weight: bold; text-align: center; background-color:#AAAAAA; foreground-color:#FFFFFF; } "
            + "TR { vertical-align: middle; } " + ".rb { background-color:#E7E7E7; vertical-align: middle; } "
            + ".rr {  background-color:#FFFFFF; vertical-align: middle; } "
            + "TD { border: solid; border-width: thin; padding: 3px; text-align: left; vertical-align: middle; word-wrap: break-word; } "
            + ".e { display: table-cell; border: solid; border-width: thin; padding: 3px; text-align: center; vertical-align: middle; word-wrap: break-word; width: 150px; font-family: monospace; } "
            + ".a { display: table-cell; border: solid; border-width: thin; padding: 3px; text-align: center; vertical-align: middle; word-wrap: break-word; width: 110px; } "
            + ".b { display: table-cell; border: solid; border-width: thin; padding: 3px; text-align: left; vertical-align: middle; word-wrap: break-word; word-break: break-all; width: 450px; } "
            + ".z { display: table-cell; border: solid; border-width: thin; padding: 3px; text-align: left; vertical-align: middle; word-wrap: break-word; word-break: break-all; width: 160px; } "
            + ".c { display: table-cell; border: solid; border-width: thin; padding: 3px; text-align: right; vertical-align: middle; word-wrap: break-word;  width: 110px; } "
            + ".h { display: table-cell; border: solid; border-width: thin; padding: 3px; text-align: center; vertical-align: middle; word-wrap: break-word; width: 110px; }"
            + ".check {vertical-align: top; }" + " TD:hover[onclick]{background-color:#F0F0F0; cursor:pointer} "
            + ".img {height: 256px}";


    public void printHTML(List<DiscordRoot> drl, XHTMLContentHandler xHandler, IItemSearcher searcher)
            throws IOException {
        SimpleDateFormat dateFormat = new SimpleDateFormat(Messages.getString("DiscordParser.DateFormat0"));

        try {
            xHandler.startDocument();
            xHandler.startElement("head"); //$NON-NLS-1$
            xHandler.startElement("style"); //$NON-NLS-1$
            xHandler.characters(CSS); // $NON-NLS-1$
            xHandler.characters(iped.parsers.whatsapp.Util.readResourceAsString("css/whatsapp.css"));
            xHandler.endElement("style"); //$NON-NLS-1$
            xHandler.startElement("script"); //$NON-NLS-1$
            xHandler.characters(iped.parsers.whatsapp.Util.readResourceAsString("js/whatsapp.js"));
            xHandler.endElement("script"); //$NON-NLS-1$
            xHandler.endElement("head"); //$NON-NLS-1$
            
            xHandler.startElement("TABLE");

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

                    xHandler.startElement("TR class='" + colorClass + "'");
                    xHandler.startElement("TD colspan='2'");
                    xHandler.startElement("TABLE class='title'");
                    xHandler.startElement("TR");
                    xHandler.startElement("TD");
                    if (dr.getAuthor().getAvatarBytes() == null) {
                        xHandler.startElement(
                                "img src='https://cdn.discordapp.com/avatars/" + format(dr.getAuthor().getId()) + "/"
                                        + format(dr.getAuthor().getAvatar()) + ".png' alt='' width='50' height='50'");
                        xHandler.endElement("img");
                    } else {
                        xHandler.startElement("img src='data:image/jpeg;base64, "
                                + Base64.getEncoder().encodeToString(dr.getAuthor().getAvatarBytes())
                                + "' alt='' width='50' height='50'");
                        xHandler.endElement("img");
                    }

                    xHandler.endElement("TD");
                    xHandler.startElement("TD");
                    xHandler.startElement("span title='Channel ID=" + format(dr.getChannel_id()) + ", UserID="
                            + format(dr.getAuthor().getId()) + "'");
                    xHandler.characters(format(dr.getAuthor().getName()));
                    xHandler.startElement("b");
                    xHandler.characters(format(dr.getAuthor().getFullUsername()));
                    xHandler.endElement("b");
                    xHandler.endElement("span");

                    xHandler.endElement("TD");
                    xHandler.startElement("TD");
                    xHandler.characters((dr.getTimestamp() == null ? "" : dateFormat.format(dr.getTimestamp())));
                    xHandler.endElement("TD");
                    xHandler.endElement("TR");
                    xHandler.endElement("TABLE");
                    xHandler.endElement("TD");
                    xHandler.endElement("TR");

                }

                xHandler.startElement("TR class='" + colorClass + "' id='" + format(dr.getId()) + "'");

                // message sending time
                xHandler.startElement("TD class='td-timestamp'");
                if (dr.getTimestamp() != null) {
                    formatDate(dr.getTimestamp(), xHandler);
                }
                xHandler.startElement("P");
                xHandler.characters(dr.getEditedTimestamp() == null ? ""
                        : "(" + Messages.getString("DiscordParser.EditTime")
                                + dateFormat.format(dr.getEditedTimestamp()) + ")");

                xHandler.endElement("P");
                xHandler.endElement("TD");

                // message body
                xHandler.startElement("TD");

                // used for debug
                // xHandler.startElement("TABLE><TR><TD>" + dr.toString() +
                // "</TD></TR></TABLE");

                // rule for calls
                if (dr.getCall() != null) {
                    xHandler.startElement("TABLE");
                    xHandler.startElement("TR");
                    xHandler.startElement("TD colspan='2'");
                    xHandler.characters("Call");
                    xHandler.endElement("TD");
                    xHandler.endElement("TR");
                    xHandler.startElement("TR");
                    xHandler.startElement("TD");
                    xHandler.characters(Messages.getString("DiscordParser.Start"));
                    xHandler.endElement("TD");
                    xHandler.startElement("TD");
                    xHandler.characters(dr.getTimestamp() == null ? "" : dateFormat.format(dr.getTimestamp()));
                    xHandler.endElement("TD");
                    xHandler.endElement("TR");
                    xHandler.startElement("TR");
                    xHandler.startElement("TD");
                    xHandler.characters(Messages.getString("DiscordParser.End"));
                    xHandler.endElement("TD");
                    xHandler.startElement("TD");
                    xHandler.characters(dr.getCall().getEndedTimestamp() == null ? ""
                            : dateFormat.format(dr.getCall().getEndedTimestamp()));
                    xHandler.endElement("TD");
                    xHandler.endElement("TR");
                    xHandler.startElement("TR");
                    xHandler.startElement("TD");
                    xHandler.characters(Messages.getString("DiscordParser.Participants"));
                    xHandler.endElement("TD");
                    xHandler.startElement("TD");
                    xHandler.characters(format(dr.getCall().getParticipantsNames(drl)));
                    xHandler.endElement("TD");
                    xHandler.endElement("TR");
                    xHandler.endElement("TABLE");
                }

                // rule for reactions
                if (dr.getReactions() != null) {

                    List<String> reactions = new ArrayList<String>();

                    for (DiscordReaction reaction : dr.getReactions()) {
                        reactions.add(format(reaction.getEmoji().getName()));
                    }

                    xHandler.startElement("TABLE");
                    xHandler.startElement("TR");
                    xHandler.startElement("TD");
                    xHandler.characters(Messages.getString("DiscordParser.Reactions")
                            + String.join(", ", reactions));
                    xHandler.endElement("TD");
                    xHandler.endElement("TR");
                    xHandler.endElement("TABLE");
                }

                // rule for attachments
                if (dr.getAttachments() != null && dr.getAttachments().size() > 0) {

                    xHandler.startElement("TABLE");
                    xHandler.startElement("TR");
                    xHandler.startElement("TD colspan='2'");
                    xHandler.characters(Messages.getString("DiscordParser.Attachments"));
                    xHandler.endElement("TD");
                    xHandler.endElement("TR");

                    for (DiscordAttachment att : dr.getAttachments()) {
                        xHandler.startElement("TR");
                        xHandler.startElement("TD");
                        if (att.getMediaHash() != null) {
                            String query = BasicProps.HASH + ":" + att.getMediaHash();
                            Iterator<IItemReader> it = searcher.searchIterable(query).iterator();
                            // if hash exists, at least 1 item will be returned
                            IItemReader item = it.next();
                            String hash = att.getMediaHash();
                            xHandler.startElement("input class=\"check\" type=\"checkbox\" onclick=\"app.check('hash:"
                                    + hash + "', this.checked)\" name=\"" + hash + "\"");
                            xHandler.endElement("input");
                            xHandler.startElement("a onclick=\"app.open('hash:" + att.getMediaHash() + "')\" href='"
                                    + Util.getExportPath(item) + "'");
                            if (item.getThumb() != null) {
                                xHandler.startElement("img src=\"data:image/jpeg;base64,"
                                        + Base64.getEncoder().encodeToString(item.getThumb()) + "\" title=\""
                                        + format(att.getFilename()) + "\"");
                                xHandler.endElement("img");
                                xHandler.startElement("BR");
                                xHandler.endElement("BR");
                            }
                            xHandler.startElement("DIV");
                            xHandler.characters(format(att.getFilename()));
                            xHandler.endElement("DIV");
                            xHandler.endElement("a");

                            String exportPath = iped.parsers.util.Util.getExportPath(item);
                            String source = iped.parsers.util.Util.getSourceFileIfExists(item).orElse("");
                            if (item.getMediaType().toString().startsWith("audio/")) {
                                xHandler.startElement("div class=\"audioImg iped-audio\" " //$NON-NLS-1$
                                        + " title=\"Audio\" " + "data-src1=\"" + format(exportPath) + "\" "
                                        + "data-src2=\"" //$NON-NLS-1$
                                        + format(source) + "\"");
                                xHandler.endElement("div");
                            }

                        } else {
                            xHandler.startElement("DIV");
                            xHandler.characters(format(att.getFilename()));
                            xHandler.endElement("DIV");
                        }
                        if (!att.getChildPornSets().isEmpty()) {
                            xHandler.startElement("BR");
                            xHandler.endElement("BR");
                            xHandler.startElement("P");
                            xHandler.startElement("i");
                            xHandler.characters(Messages.getString("WhatsAppReport.FoundInPedoHashDB") + " "
                                    + format(att.getChildPornSets().toString()));
                            xHandler.endElement("i");
                            xHandler.endElement("P");
                        }
                        xHandler.endElement("TD");
                        xHandler.endElement("TR");
                    }
                    xHandler.endElement("TABLE");
                }

                if (dr.getMessageContent() != null && !dr.getMessageContent().isEmpty()) {

                    String escapedMessage = format(dr.getMessageContent());

                    // rule for mentions
                    if (dr.getMentions() != null && dr.getMentions().size() > 0) {
                        for (DiscordMention dm : dr.getMentions()) {
                            escapedMessage = escapedMessage.replace(format("<@" + dm.getId() + ">"),
                                    "<span title='UserID=" + format(dm.getId()) + "'>" + "<B style='color:#0099FF'>@"
                                            + format(dm.getFullUsername()) + "</B>" + "</span>");
                        }
                    }
                    xHandler.characters(escapedMessage);
                }

                xHandler.endElement("TD");
                xHandler.endElement("TR");
            }

            xHandler.endElement("TABLE");

            xHandler.endDocument();
        } catch (SAXException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }finally {
        }

    }

    public byte[] convertToHTML(List<DiscordRoot> drl, IItemSearcher searcher) throws IOException {
        return convertToHTML(drl, searcher, true);
    }

    public byte[] convertToHTML(List<DiscordRoot> drl, IItemSearcher searcher,
            boolean includeHeader)
            throws IOException {

        ByteArrayOutputStream bout = new ByteArrayOutputStream();

        PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, StandardCharsets.UTF_8));

        SimpleDateFormat dateFormat = new SimpleDateFormat(Messages.getString("DiscordParser.DateFormat0"));

        if (includeHeader) {
            out.println("<!DOCTYPE html>");
            out.println("<HTML>");
            out.println("<HEAD>");
            out.println(" <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
            out.println("<style>" + CSS + "</style>");
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
                    out.println("					<img src='https://cdn.discordapp.com/avatars/" + format(dr.getAuthor().getId()) + "/" + format(dr.getAuthor().getAvatar()) + ".png' alt='' width='50' height='50'>");
                } else {
                    out.println("					<img src='data:image/jpeg;base64, " + Base64.getEncoder().encodeToString(dr.getAuthor().getAvatarBytes()) + "' alt='' width='50' height='50'>");
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
                        out.println("       <a onclick=\"app.open('hash:" + att.getMediaHash() + "')\" href='"
                                + Util.getExportPath(item) + "'>");
                        if (item.getThumb() != null) {
                            out.println("       <img src=\"data:image/jpeg;base64," + Base64.getEncoder().encodeToString(item.getThumb()) + "\" title=\"" + format(att.getFilename()) + "\">");
                        }
                        out.println("       <BR/>");
                        out.println("       <DIV>" + format(att.getFilename()) + "</DIV>");
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
