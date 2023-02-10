package iped.app.home.configurables;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.validation.SchemaFactory;

import org.apache.commons.io.input.BOMInputStream;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.google.common.base.Predicate;

import iped.app.home.MainFrame;
import iped.app.ui.controls.textarea.XMLXSDTokenMaker;
import iped.engine.config.ParsersConfig;
import iped.utils.XMLUtil;

public class ParsersConfigurablePanel extends AdvancedTextConfigurablePanel {
    ParsersConfig parsersConfig;
    private JScrollPane parserListPanel;
    private JList<String> parserTypeList;
    private CheckboxListCellRenderer<String> cellRenderer;
    private HashMap<String, Element> parserNames = new HashMap<String,Element>();
    private HashSet<Element> parsers = new HashSet<Element>();
    private Document doc;

    protected ParsersConfigurablePanel(ParsersConfig configurable, MainFrame mainFrame) {
        super(configurable, mainFrame);
        this.parsersConfig=configurable;
    }
    
    class ResizeListener extends ComponentAdapter {
        public void componentResized(ComponentEvent e) {
            int ncols = (int) Math.ceil((parserListPanel.getSize().getWidth()-32)/cellRenderer.getMaxStringWidth());
            int nrows = (int) Math.ceil((double)parserTypeList.getModel().getSize()/(double)ncols);
            
            parserTypeList.setVisibleRowCount(nrows);
            parserListPanel.setViewportView(parserTypeList);
        }

        @Override
        public void componentShown(ComponentEvent e) {
            componentResized(e);
        }
    }

    @Override
    public void createConfigurableGUI() {
        super.createConfigurableGUI();

        textArea.getDocument().removeDocumentListener(this);
        textArea.setSyntaxEditingStyle(XMLXSDTokenMaker.SYNTAX_STYLE_XMLXSD);
        SyntaxScheme scheme = textArea.getSyntaxScheme();
        scheme.getStyle(XMLXSDTokenMaker.RESERVED_WORD).background = Color.pink;
        scheme.getStyle(XMLXSDTokenMaker.RESERVED_WORD).underline = true;
        textArea.setText(parsersConfig.getConfiguration());
        textArea.getDocument().addDocumentListener(this);
    }

    @Override
    protected Component createBasicPane() {
        if(parserListPanel==null) {
            createParserListPanel();
        }
        return parserListPanel;
    }

    public void createParserListPanel() {
        parserListPanel = new JScrollPane();
        parserTypeList = new JList<String>(getAvailableParsers().toArray(new String[0]));
        parserTypeList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        parserListPanel.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        this.addComponentListener(new ResizeListener());
        parserListPanel.setViewportView(parserTypeList);
        parserListPanel.setAutoscrolls(true);
        cellRenderer = new CheckboxListCellRenderer<String>(new Predicate<String>() {
            @Override
            public boolean apply(String input) {
                String attr = parserNames.get(input).getAttribute(ParsersConfig.PARSER_DISABLED_ATTR);
                return !attr.equals("true");
            }
        });
        parserTypeList.setCellRenderer(cellRenderer);
        parserTypeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        parserTypeList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if(!parserTypeList.getValueIsAdjusting() && parserTypeList.getSelectedValue()!=null) {
                    Element elem = parserNames.get(parserTypeList.getSelectedValue());
                    Attr attr = (Attr)elem.getAttributes().getNamedItem(ParsersConfig.PARSER_DISABLED_ATTR);
                    
                    if(attr==null) {
                        elem.setAttribute(ParsersConfig.PARSER_DISABLED_ATTR, "true");
                    }else {
                        elem.removeAttribute(ParsersConfig.PARSER_DISABLED_ATTR);
                    }

                    textArea.setText(getXMLString());

                    changed=true;
                    parserTypeList.setValueIsAdjusting(true);
                    try {
                        parserTypeList.clearSelection();
                    }finally {
                        parserTypeList.setValueIsAdjusting(false);
                    }
                }
            }
        });
    }

    public String getXMLString() {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        try {
            Transformer transformer = transformerFactory.newTransformer();
            Element documentElement = doc.getDocumentElement();
            documentElement.setAttribute("xmlns:"+ParsersConfig.PARSER_DISABLED_ATTR.split(":")[0], XMLUtil.IPED_NAMESAPCE);
            DOMSource source = new DOMSource(doc);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            StreamResult result = new StreamResult(bos);
            transformer.transform(source, result);
            String strResult = bos.toString();
            if(strResult.endsWith("?>")) {
                return null;
            }
            return strResult;
        } catch (TransformerException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return "";
    }

    private List<String> getAvailableParsers() {        
        ArrayList<String> result = new ArrayList<String>();
        try {
            String xml = (String) configurable.getConfiguration();
            BOMInputStream bis = new BOMInputStream(new ByteArrayInputStream(xml.getBytes(Charset.forName("UTF-8"))));
            DocumentBuilder docBuilder = getDocBuilder();
            parsers.clear();
            parserNames.clear();
            doc = docBuilder.parse(new InputSource(bis));
            NodeList nl = doc.getElementsByTagName("parser");
            for(int i=0; i<nl.getLength(); i++) {
                Element e =(Element) nl.item(i);
                String parserName = nl.item(i).getAttributes().getNamedItem("class").getNodeValue();
                result.add(parserName);
                parsers.add(e);
                parserNames.put(parserName, e);
            }
        } catch (SAXException | IOException | ParserConfigurationException e) {
            e.printStackTrace();
        }
        return result;
    }

    public DocumentBuilder getDocBuilder() throws SAXException, ParserConfigurationException {
        SchemaFactory factory = 
                SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
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
        super.applyChanges();
        parserTypeList.setListData(getAvailableParsers().toArray(new String[0]));
    }
}
