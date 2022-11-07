package iped.app.ui.controls.textarea;

import javax.swing.JTextPane;

public class RegexTextPane extends JTextPane {

    public RegexTextPane() {
        // Set editor kit
        this.setEditorKitForContentType("text/xml", new XmlEditorKit());
        this.setContentType("text/xml");
    }
}
