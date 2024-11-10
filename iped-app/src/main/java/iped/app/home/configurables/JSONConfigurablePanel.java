package iped.app.home.configurables;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import iped.app.home.MainFrame;
import iped.app.home.configurables.api.ConfigurableValidationException;
import iped.configuration.Configurable;

public class JSONConfigurablePanel extends TextConfigurablePanel {

    private String config;

    protected JSONConfigurablePanel(Configurable<String> configurable, MainFrame mainFrame) {
        super(configurable, mainFrame);
        config = configurable.getConfiguration();
    }

    @Override
    public void createConfigurableGUI() {
        super.createConfigurableGUI();
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON_WITH_COMMENTS);
        // textArea.setCodeFoldingEnabled(true);
        // textArea.setEditorKitForContentType("text/xml", new JSONEditorKit());
        // textArea.setContentType("text/xml");
        textArea.getDocument().removeDocumentListener(this);
        textArea.setText(config);
        textArea.getDocument().addDocumentListener(this);

    }

    @Override
    public void applyChanges() throws ConfigurableValidationException {
        try {
            JSONParser parser = new JSONParser();
            parser.parse(textArea.getText());
        } catch (ParseException e) {
            throw new ConfigurableValidationException("Erro de sintaxe JSON.", e);
        }
    }

}
