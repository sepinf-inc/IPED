package iped.app.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

/** Reads existing IPED chat bubbles without executing scripts or loading resources. */
class WordReportChat extends HTMLEditorKit.ParserCallback {

    private final WordReportDocument.Evidence chat;
    private final List<WordReportDocument.Evidence> messages = new ArrayList<>();
    private final StringBuilder plain = new StringBuilder();
    private StringBuilder content;
    private StringBuilder sender;
    private int divDepth;
    private int lineDepth;
    private int ignored;
    private boolean body;
    private boolean name;
    private boolean bubble;

    private WordReportChat(WordReportDocument.Evidence chat) {
        this.chat = chat;
    }

    static List<WordReportDocument.Evidence> read(InputStream input, WordReportDocument.Evidence chat) throws IOException {
        WordReportChat parser = new WordReportChat(chat);
        new ParserDelegator().parse(new InputStreamReader(input, StandardCharsets.UTF_8), parser, true);
        if (parser.messages.isEmpty() && (chat.text == null || chat.text.isEmpty())) {
            chat.text = parser.plain.toString().trim();
        }
        return parser.messages;
    }

    @Override
    public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
        if (tag == HTML.Tag.BODY) {
            body = true;
        }
        if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) {
            ignored++;
        }
        String classes = " " + String.valueOf(attributes.getAttribute(HTML.Attribute.CLASS)) + " ";
        if (tag == HTML.Tag.DIV) {
            divDepth++;
            if (classes.contains(" linha ") && content == null) {
                content = new StringBuilder();
                sender = new StringBuilder();
                lineDepth = divDepth;
                bubble = false;
            }
        }
        if (content != null) {
            if (classes.contains(" bbl ") || classes.contains(" bbr ") || classes.contains(" incoming ")
                    || classes.contains(" outgoing ") || classes.contains(" systemmessage ")) {
                bubble = true;
            }
            String style = String.valueOf(attributes.getAttribute(HTML.Attribute.STYLE))
                    .toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", "");
            // Earlier UFED reports identify the sender by this inline style.
            boolean legacyName = bubble && content.toString().trim().isEmpty() && sender.length() == 0
                    && style.contains("color:#b4c74b");
            if (tag == HTML.Tag.SPAN && (classes.contains(" name ") || legacyName)) {
                name = true;
            }
            if (tag == HTML.Tag.SPAN && classes.contains(" time ")) {
                newline();
            }
        }
    }

    @Override
    public void handleEndTag(HTML.Tag tag, int position) {
        if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) {
            ignored = Math.max(0, ignored - 1);
        }
        if (tag == HTML.Tag.SPAN) {
            name = false;
        }
        if (tag == HTML.Tag.DIV) {
            if (content != null && divDepth == lineDepth) {
                if (bubble && content.toString().trim().length() > 0) {
                    WordReportDocument.Evidence message = new WordReportDocument.Evidence();
                    message.message = true;
                    message.title = chat.title;
                    message.metadata.putAll(chat.metadata);
                    message.participants = sender.length() == 0 ? chat.participants : sender.toString().trim();
                    message.text = content.toString().trim();
                    messages.add(message);
                }
                content = null;
                sender = null;
                name = false;
            }
            divDepth--;
        }
        if (tag == HTML.Tag.DIV || tag == HTML.Tag.P || tag == HTML.Tag.LI || tag == HTML.Tag.TR) {
            newline();
        }
        if (tag == HTML.Tag.BODY) {
            body = false;
        }
    }

    @Override
    public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
        if (tag == HTML.Tag.BR || tag == HTML.Tag.HR) {
            newline();
        }
    }

    @Override
    public void handleText(char[] text, int position) {
        if (body && ignored == 0) {
            plain.append(text);
            if (content != null) {
                (name ? sender : content).append(text);
            }
        }
    }

    private void newline() {
        if (body && ignored == 0) {
            plain.append('\n');
            if (content != null) {
                content.append('\n');
            }
        }
    }
}
