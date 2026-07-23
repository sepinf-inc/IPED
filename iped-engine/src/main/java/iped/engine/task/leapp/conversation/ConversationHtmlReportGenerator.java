package iped.engine.task.leapp.conversation;

import static j2html.TagCreator.attrs;
import static j2html.TagCreator.b;
import static j2html.TagCreator.br;
import static j2html.TagCreator.div;
import static j2html.TagCreator.img;
import static j2html.TagCreator.table;
import static j2html.TagCreator.td;
import static j2html.TagCreator.tr;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.metadata.XMPDM;

import iped.data.IItemReader;
import iped.parsers.util.ChildPornHashLookup;
import iped.parsers.util.Messages;
import iped.parsers.whatsapp.Util;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.utils.EmojiUtil;
import iped.utils.SimpleHTMLEncoder;
import j2html.tags.specialized.DivTag;

/**
 * Renders a LEAPP {@link Conversation} as a chat-style HTML, replicating the features of
 * iped.parsers.ufed.ReportGenerator: WhatsApp-like css/js, topbar with the conversation title, date separator lines,
 * incoming/outgoing bubbles with sender and time, media attachments with embedded base64 thumbs, audio/video player
 * hooks (iped-audio/iped-video + data-src), links/checkboxes to the original case items, audio transcriptions, child
 * porn hash set hits, location blocks and splitting of big chats into multiple HTML parts (with continuation markers
 * and a loading modal for very long parts).
 *
 * Features of the UFED report that have no data source in LEAPP rows (reply/quote references, forwarded flags,
 * message status ticks, edited/deleted states, shared contacts and contact photos) are intentionally absent.
 */
public class ConversationHtmlReportGenerator {

    private static final int MIN_MESSAGES_TO_SHOW_MODAL = 500;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");

    private final Conversation conversation;
    private final int minChatSplitSize;

    private boolean firstHtml = true;
    private int currentMsg = 0;

    public ConversationHtmlReportGenerator(Conversation conversation, int minChatSplitSize) {
        this.conversation = conversation;
        this.minChatSplitSize = minChatSplitSize;
    }

    /**
     * Index (into the conversation messages list) of the first message NOT yet rendered: after each
     * {@link #generateNextChatHtml()} call, messages [previous, getNextMsgNum()) were written to that part.
     */
    public int getNextMsgNum() {
        return currentMsg;
    }

    /**
     * Generates the next HTML part, or null when all messages were already rendered. Mirrors the iteration contract of
     * iped.parsers.ufed.ReportGenerator#generateNextChatHtml().
     */
    public byte[] generateNextChatHtml() throws UnsupportedEncodingException {

        List<ConversationMessage> messages = conversation.getMessages();

        if ((!firstHtml && currentMsg == 0) || (currentMsg > 0 && currentMsg == messages.size())) {
            return null;
        }

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, StandardCharsets.UTF_8));

        String title = conversation.getTitle();
        boolean printModal = (messages.size() - currentMsg) > MIN_MESSAGES_TO_SHOW_MODAL;
        printMessageFileHeader(out, title, printModal);

        if (currentMsg > 0) {
            printDateSeparator(out, Messages.getString("WhatsAppReport.ChatContinuation"));
        }

        String lastDate = null;
        while (currentMsg < messages.size()) {
            ConversationMessage m = messages.get(currentMsg);
            String thisDate = m.getTimestamp() != null ? dateFormat.format(m.getTimestamp())
                    : Messages.getString("ReportGenerator.UnknownDate");
            if (!thisDate.equals(lastDate)) {
                printDateSeparator(out, thisDate);
                lastDate = thisDate;
            }
            printMessage(out, m);

            if (currentMsg++ != messages.size() - 1 && bout.size() >= minChatSplitSize) {
                printDateSeparator(out, Messages.getString("WhatsAppReport.ChatContinues"));
                break;
            }
        }

        printMessageFileFooter(out);
        out.flush();

        firstHtml = false;

        return EmojiUtil.replaceByImages(bout.toByteArray());
    }

    private static void printDateSeparator(PrintWriter out, String text) {
        out.println("<div class=\"linha\"><div class=\"date\">" + text + "</div></div>");
    }

    private void printMessage(PrintWriter out, ConversationMessage message) {

        boolean isOutgoing = message.isOutgoing();

        out.println("<div id=\"" + message.getAnchorId() + "\" class=\"linha\">");
        if (isOutgoing) {
            out.println("<div class=\"bbr\"><div class=\"outgoing to\">");
        } else {
            out.println("<div class=\"bbl\"><div class=\"aw\"><div class=\"awl\"></div></div><div class=\"incoming from\">");
        }

        String name = message.getSender();
        if (StringUtils.isBlank(name)) {
            name = isOutgoing ? Messages.getString("WhatsAppReport.Owner")
                    : Messages.getString("ReportGenerator.Unknown");
        }
        out.println("<span class=\"name\">" + format(name) + "</span><br/>");

        printMessageContent(out, message);

        out.println("<span class=\"time\">");
        if (message.getTimestamp() != null) {
            out.println(timeFormat.format(message.getTimestamp()));
        } else if (isNotBlank(message.getRawTime())) {
            out.println(format(message.getRawTime()));
        }
        out.println("</span>");

        if (isOutgoing) {
            out.println("</div><div class=\"aw\"><div class=\"awr\"></div></div>");
        } else {
            out.println("</div>");
        }
        out.println("</div></div>");
    }

    private void printMessageContent(PrintWriter out, ConversationMessage message) {

        out.println(formatLocation(message));

        for (IItemReader mediaItem : message.getMediaItems()) {
            printMediaItem(out, mediaItem);
        }

        String body = message.getBody();
        if (isNotBlank(body)) {
            out.print(format(body));
            out.print("<br/>");
        }
    }

    /**
     * Renders one media attachment, with the same viewer integrations of the UFED report: hash checkbox + app.open
     * link, embedded thumb, iped-audio/iped-video hooks with export/source paths, duration, transcription and child
     * porn hash set hits.
     */
    private void printMediaItem(PrintWriter out, IItemReader mediaItem) {

        byte[] thumb = mediaItem.getThumb();
        String exportPath = iped.parsers.util.Util.getExportPath(mediaItem);
        String source = iped.parsers.util.Util.getSourceFileIfExists(mediaItem).orElse("");
        Float duration = getDuration(mediaItem);
        String hash = mediaItem.getHash();

        boolean startedLink = false;
        if (isNotBlank(hash)) {
            String query = BasicProps.HASH + ":" + hash;
            String href = iped.parsers.util.Util.getReportHref(mediaItem);
            out.println("<input class=\"check\" type=\"checkbox\" onclick=\"app.check('" + query + "',this.checked)\" name=\"" + hash + "\" />");
            out.println("<a onclick=\"app.open('" + query + "')\" href=\"" + format(href) + "\" >");
            startedLink = true;
        }

        String mediaType = mediaItem.getMediaType() != null ? mediaItem.getMediaType().toString() : "";

        if (mediaType.startsWith("audio")) {
            out.println(Messages.getString("UfedChatParser.AudioMessageTitle") + "<br>");
            out.println("<div class=\"audioImg iped-audio\" title=\"Audio\" "
                    + "data-src1=\"" + format(exportPath) + "\" "
                    + "data-src2=\"" + format(source) + "\" >");
            if (duration != null && duration > 0) {
                out.print("<span class=\"duration\"> " + formatDuration(duration) + "</span>");
            }
            out.print("</div>");

        } else if (mediaType.startsWith("video")) {
            out.println(Messages.getString("UfedChatParser.VideoMessageTitle") + "<br>");
            if (thumb != null) {
                out.print("<img class=\"thumb iped-video\" src=\"data:image/jpg;base64,");
                out.print(Base64.encodeBase64String(thumb));
                out.println("\"");
                out.print(" data-src1=\"" + format(exportPath) + "\"");
                out.print(" data-src2=\"" + format(source) + "\"");
                out.print(" /><br/>");
            } else {
                out.println("<div class=\"videoImg iped-video\" title=\"Video\"");
                out.print(" data-src1=\"" + format(exportPath) + "\"");
                out.print(" data-src2=\"" + format(source) + "\"");
                out.print("></div>");
            }
        } else if (thumb != null) {
            out.print("<img class=\"thumb\" src=\"data:image/jpg;base64,");
            out.print(Base64.encodeBase64String(thumb));
            out.println("\" /><br/>");
        } else if (mediaType.startsWith("image")) {
            out.println("<div class=\"imageImg\" title=\"Image\"></div>");
        } else {
            out.println("Attachment:<br/><div class=\"attachImg\" title=\"Doc\"></div>");
        }

        if (startedLink) {
            out.println("</a>");
        }

        // file name below the media, so the examiner can identify it
        String mediaName = mediaItem.getName();
        if (isNotBlank(mediaName)) {
            out.println("<p>" + format(mediaName) + "</p>");
        }

        // render audio transcription
        String transcription = mediaItem.getMetadata().get(ExtraProperties.TRANSCRIPT_ATTR);
        if (isNotBlank(transcription)) {
            out.print("<br/>");
            out.print(Messages.getString("ReportGenerator.TranscriptionTitle"));
            String confidence = mediaItem.getMetadata().get(ExtraProperties.CONFIDENCE_ATTR);
            if (confidence != null) {
                float score = Float.valueOf(confidence) * 100;
                out.print(" [" + (int) score + "%]");
            }
            out.println(": <i>");
            out.println(format(transcription));
            out.println("</i><br/>");
        }

        // render child porn sets
        if (isNotBlank(hash)) {
            List<String> childPornSets = ChildPornHashLookup.lookupHash(hash);
            if (childPornSets != null && !childPornSets.isEmpty()) {
                out.print("<p><i>" + Messages.getString("WhatsAppReport.FoundInPedoHashDB") + " "
                        + format(childPornSets.toString()) + "</i></p>");
            }
        }
    }

    private String formatLocation(ConversationMessage message) {

        String lat = message.getLatitude();
        String lon = message.getLongitude();

        if (StringUtils.isAllBlank(lat, lon)) {
            return StringUtils.EMPTY;
        }

        DivTag div = div(img(attrs(".location")), b(Messages.getString("UfedChatParser.Location.Title")), br());
        div.with(table(attrs(".contact-table"), //
                tr(td(Messages.getString("UfedChatParser.Location.Latitude")), td(lat)), //
                tr(td(Messages.getString("UfedChatParser.Location.Longitude")), td(lon))), //
                br());
        return div.render();
    }

    private static Float getDuration(IItemReader item) {
        String duration = StringUtils.firstNonBlank(
                item.getMetadata().get(ExtraProperties.AUDIO_META_PREFIX + XMPDM.DURATION.getName()),
                item.getMetadata().get(ExtraProperties.VIDEO_META_PREFIX + XMPDM.DURATION.getName()));
        if (duration != null) {
            try {
                return Float.parseFloat(duration);
            } catch (NumberFormatException e) {
            }
        }
        return null;
    }

    private static String formatDuration(float duration) {
        if (duration == 0) {
            return "";
        }
        return "(" + String.format("%02d:%02d", (int) duration / 60, (int) duration % 60) + ")";
    }

    private static String format(String text) {
        String ret = SimpleHTMLEncoder.htmlEncode(text);
        // Keep line breaks present in the content, converting to an HTML <br/>
        ret = ret.replaceAll("\n", "<br/>\n");
        return ret;
    }

    private void printMessageFileHeader(PrintWriter out, String title, boolean printModal) {

        out.println("<!DOCTYPE html>\n"
                + "<html>\n"
                + "<head>\n"
                + "<title>" + format(title) + "</title>\n"
                + "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n"
                + "<meta name=\"viewport\" content=\"width=device-width\" />\n"
                + "<meta charset=\"UTF-8\" />\n"
                + "<link rel=\"icon\" href=\"" + Util.getImageResourceAsEmbedded("img/message.png") + "\">\n"
                + "<style>\n" + Util.readResourceAsString("css/whatsapp.css")
                + "\n</style>\n" + "<style>.check {vertical-align: top;}</style>" + "</head>\n"
                + "<body style=\"background-image:url("
                + Util.getImageResourceAsEmbedded("img/other-chat-back.jpg") + ")\">");
        if (printModal) {
            out.println("<div id=\"loading-modal\">\n"
                    + "  <div class=\"loader\">" + Messages.getString("UfedChatParser.Loading") + "</div>\n"
                    + "</div>");
        }
        out.println("<div id=\"topbar\" class=\"other\">\n"
                + " <span class=\"left\">"
                + " &nbsp; "
                + format(title) + "</span>\n"
                + "</div>\n"
                + "<div id=\"conversation\">");
    }

    private static void printMessageFileFooter(PrintWriter out) {
        out.println("	<br /><br /><br />\n"
                + "</div>\n"
                + "<div id=\"lastmsg\">&nbsp;</div>\n"
                + "<script type=\"text/javascript\">\n" + Util.readResourceAsString("js/whatsapp.js") + "\n</script>\n"
                + "</body>\n"
                + "</html>");
    }
}
