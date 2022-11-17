package iped.app.home.configurables;

import java.awt.BorderLayout;

import javax.swing.JScrollPane;

import iped.app.home.MainFrame;
import iped.app.ui.controls.textarea.RegexTextPane;
import iped.configuration.Configurable;

public class TextConfigurablePanel extends ConfigurablePanel{

    protected RegexTextPane textArea;

    protected TextConfigurablePanel(Configurable<?> configurable, MainFrame mainFrame) {
        super(configurable, mainFrame);
    }

    public void createConfigurableGUI() {
        textArea = new RegexTextPane();
        textArea.setAutoscrolls(true);
        textArea.setText(configurable.getConfiguration().toString());
        textArea.getDocument().addDocumentListener(this);
        JScrollPane txtAreaScroll = new JScrollPane();
        txtAreaScroll.setViewportView(textArea);
        txtAreaScroll.setAutoscrolls(true);
        this.setLayout(new BorderLayout());
        this.add(txtAreaScroll,BorderLayout.CENTER);
    }

    @Override
    public void applyChanges() {
        ((Configurable<String>)configurable).setConfiguration(textArea.getText());        
    }

}
