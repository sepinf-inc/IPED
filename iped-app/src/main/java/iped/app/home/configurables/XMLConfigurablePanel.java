package iped.app.home.configurables;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import iped.app.home.MainFrame;
import iped.app.home.configurables.api.ConfigurableValidationException;
import iped.app.ui.controls.textarea.XmlEditorKit;
import iped.configuration.Configurable;

public class XMLConfigurablePanel extends TextConfigurablePanel {
    String xml;
    URL schemaUrl;

    protected XMLConfigurablePanel(Configurable<String> configurable, MainFrame mainFrame) {
        this(configurable, null, mainFrame);
    }

    protected XMLConfigurablePanel(Configurable<String> configurable, URL schemaUrl, MainFrame mainFrame) {
        super(configurable, mainFrame);
        xml = configurable.getConfiguration();
        this.schemaUrl = schemaUrl;
    }

    @Override
    public void createConfigurableGUI() {
        super.createConfigurableGUI();
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_XML);
        textArea.setCodeFoldingEnabled(true);

        XmlEditorKit xek = new XmlEditorKit();
        if (this.schemaUrl != null) {
            xek.setSchema(schemaUrl);
        }
        // textArea.setEditorKitForContentType("text/xml", xek);
        // textArea.setContentType("text/xml");
        textArea.getDocument().removeDocumentListener(this);
        textArea.setText(xml);
        textArea.getDocument().addDocumentListener(this);

    }

    public DocumentBuilder getDocBuilder() throws SAXException, ParserConfigurationException {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        if (schemaUrl != null) {
            Schema schema = factory.newSchema(schemaUrl);
            dbf.setSchema(schema);
        }
        dbf.setNamespaceAware(true);
        DocumentBuilder docBuilder = dbf.newDocumentBuilder();
        docBuilder.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException exception) throws SAXException {
            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
            }

            @Override
            public void error(SAXParseException exception) throws SAXException {
                throw exception;
            }
        });
        return docBuilder;
    }

    @Override
    public void applyChanges() throws ConfigurableValidationException {
        try {
            xml = textArea.getText();
            ByteArrayInputStream bis = new ByteArrayInputStream(xml.getBytes(Charset.forName("UTF-8")));
            Document doc = getDocBuilder().parse(bis);
            ((Configurable<String>) configurable).setConfiguration(xml);
        } catch (IOException | SAXException | ParserConfigurationException e) {
            throw new ConfigurableValidationException("Erro de sintaxe no XML", e);
        }
    }

}
