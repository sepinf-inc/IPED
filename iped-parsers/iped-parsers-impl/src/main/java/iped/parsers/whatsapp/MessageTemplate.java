package iped.parsers.whatsapp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MessageTemplate {
    private final String content;
    private List<Button> buttons;

    public MessageTemplate(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public void addButton(Button button) {
        if (buttons == null) {
            buttons = new ArrayList<Button>(1);
        }
        buttons.add(button);
    }

    public List<Button> getButtons() {
        return buttons == null ? Collections.emptyList() : buttons;
    }

    public static class Button {
        private final String text, extra;

        public Button(String text, String extra) {
            this.text = text;
            this.extra = extra;
        }

        public String getText() {
            return text;
        }

        public String getExtra() {
            return extra;
        }
    }
}
