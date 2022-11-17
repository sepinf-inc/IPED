package iped.app.home.configurables;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import iped.app.home.MainFrame;
import iped.configuration.Configurable;import iped.engine.task.carver.XMLCarverConfiguration;

public class XMLCarverConfigurablePanel extends TextConfigurablePanel {
    XMLCarverConfiguration config;

    protected XMLCarverConfigurablePanel(Configurable<XMLCarverConfiguration> configurable, MainFrame mainFrame) {
        super(configurable, mainFrame);
        config=configurable.getConfiguration();
    }

    @Override
    public void createConfigurableGUI() {
        super.createConfigurableGUI();
        textArea.getDocument().removeDocumentListener(this);
        textArea.setText(config.getXMLString());
        textArea.getDocument().addDocumentListener(this);
    }

    @Override
    public void applyChanges() {
        try {
            config.loadXMLConfigFile(textArea.getText());
        } catch (IOException | SAXException | ParserConfigurationException e) {
            e.printStackTrace();
        }        
    }

}
