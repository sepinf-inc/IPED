package iped.app.home.configurables;

import java.awt.BorderLayout;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import iped.app.home.MainFrame;
import iped.app.home.configurables.api.ConfigurableValidationException;
import iped.configuration.Configurable;

public class TextConfigurablePanel extends ConfigurablePanel {

    // protected RegexTextPane textArea;
    protected RSyntaxTextArea textArea;
    protected RTextScrollPane txtAreaScroll;

    protected TextConfigurablePanel(Configurable<?> configurable, MainFrame mainFrame) {
        super(configurable, mainFrame);
    }

    public void createConfigurableGUI() {
        textArea = new RSyntaxTextArea(20, 60);

        textArea.setAutoscrolls(true);
        textArea.setText(configurable.getConfiguration().toString());
        textArea.getDocument().addDocumentListener(this);
        txtAreaScroll = new RTextScrollPane();
        txtAreaScroll.setViewportView(textArea);
        txtAreaScroll.setAutoscrolls(true);
        txtAreaScroll.setLineNumbersEnabled(true);
        this.setLayout(new BorderLayout());
        this.add(txtAreaScroll, BorderLayout.CENTER);
    }

    @Override
    public void applyChanges() throws ConfigurableValidationException {
        ((Configurable<String>) configurable).setConfiguration(textArea.getText());
    }

}
