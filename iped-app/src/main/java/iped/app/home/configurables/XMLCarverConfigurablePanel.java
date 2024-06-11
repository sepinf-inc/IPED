package iped.app.home.configurables;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.util.List;

import javax.swing.AbstractListModel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.xml.parsers.ParserConfigurationException;

import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory;
import org.xml.sax.SAXException;

import iped.app.home.MainFrame;
import iped.app.home.configurables.api.ConfigurableValidationException;
import iped.app.ui.Messages;
import iped.app.ui.controls.textarea.XMLXSDTokenMaker;
import iped.carvers.api.CarverType;
import iped.configuration.Configurable;
import iped.engine.task.carver.XMLCarverConfiguration;

public class XMLCarverConfigurablePanel extends AdvancedTextConfigurablePanel {
    XMLCarverConfiguration config;
    private JScrollPane carverListPanel;
    private JList<CarverType> carverTypeList;
    String maxStringToComputeTheWidth;
    private CarverConfigCellRenderer cellRenderer;

    static {
        AbstractTokenMakerFactory atmf = (AbstractTokenMakerFactory)TokenMakerFactory.getDefaultInstance();
        atmf.putMapping(XMLXSDTokenMaker.SYNTAX_STYLE_XMLXSD, "iped.app.ui.controls.textarea.XMLXSDTokenMaker");
    }

    protected XMLCarverConfigurablePanel(Configurable<XMLCarverConfiguration> configurable, MainFrame mainFrame) {
        super(configurable, mainFrame);
        config=configurable.getConfiguration();
    }

    @Override
    public void createConfigurableGUI() {
        super.createConfigurableGUI();

        textArea.getDocument().removeDocumentListener(this);
        textArea.setSyntaxEditingStyle(XMLXSDTokenMaker.SYNTAX_STYLE_XMLXSD);
        SyntaxScheme scheme = textArea.getSyntaxScheme();
        scheme.getStyle(XMLXSDTokenMaker.RESERVED_WORD).background = Color.pink;
        scheme.getStyle(XMLXSDTokenMaker.RESERVED_WORD).underline = true;
        textArea.setText(config.getXMLString());
        textArea.getDocument().addDocumentListener(this);
    }
    
    class ResizeListener extends ComponentAdapter {
        public void componentResized(ComponentEvent e) {
            int ncols = (int) Math
                    .floor((carverListPanel.getSize().getWidth()) / cellRenderer.getMaxStringWidth());
            int nrows = (int) Math.ceil((double)carverTypeList.getModel().getSize()/(double)ncols);
            
            carverTypeList.setVisibleRowCount(nrows);
            carverListPanel.setViewportView(carverTypeList);
        }

        @Override
        public void componentShown(ComponentEvent e) {
            componentResized(e);
        }
    }
    
    public void createCarverListPanel() {
        carverListPanel = new JScrollPane();
        carverTypeList = new JList<CarverType>(config.getAvailableCarverTypes());
        carverTypeList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        carverListPanel.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        this.addComponentListener(new ResizeListener());
        carverListPanel.setViewportView(carverTypeList);
        carverListPanel.setAutoscrolls(true);
        cellRenderer = new CarverConfigCellRenderer();
        carverTypeList.setCellRenderer(cellRenderer);
        carverTypeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        carverTypeList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if(!carverTypeList.getValueIsAdjusting()) {
                    CarverType ct = (CarverType) carverTypeList.getSelectedValue();
                    if(ct!=null) {
                        config.setEnableCarverType(ct, !ct.isEnabled());
                        textArea.setText(config.getXMLString());
                        changed=true;
                        carverTypeList.setValueIsAdjusting(true);
                        try {
                            carverTypeList.clearSelection();
                        }finally {
                            carverTypeList.setValueIsAdjusting(false);
                        }
                    }
                }
            }
        });
    }

    class CarverTypeListModel extends AbstractListModel<CarverType>{
        List<CarverType> source;

        public CarverTypeListModel(List<CarverType> source) {
            this.source = source;
        }

        @Override
        public int getSize() {
            return source.size();
        }

        @Override
        public CarverType getElementAt(int index) {
            return source.get(index);
        }
    }

    @Override
    public void applyChanges() throws ConfigurableValidationException{
        try {
            config.loadXMLConfigFile(textArea.getText());
            carverTypeList.setModel(new CarverTypeListModel(config.getAvailableCarverTypesList()));
        } catch (IOException | SAXException | ParserConfigurationException e) {
            throw new ConfigurableValidationException("Erro de sintaxe no XML", e);
        }        
    }

    protected String getBasicPaneTitle() {
        return Messages.get("Home.configurables.BasicCarverPanelLabel");
    }

    @Override
    protected Component getBasicPane() {
        if(carverListPanel==null) {
            createCarverListPanel();
        }
        return carverListPanel;
    }

}
