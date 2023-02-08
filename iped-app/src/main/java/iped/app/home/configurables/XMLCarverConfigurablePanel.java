package iped.app.home.configurables;

import java.awt.Color;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

import javax.swing.CellRendererPane;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.xml.parsers.ParserConfigurationException;

import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory;
import org.xml.sax.SAXException;

import iped.app.home.MainFrame;
import iped.app.ui.controls.textarea.XMLXSDTokenMaker;
import iped.carvers.api.CarverType;
import iped.configuration.Configurable;
import iped.engine.task.carver.XMLCarverConfiguration;

public class XMLCarverConfigurablePanel extends TextConfigurablePanel {
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

        JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.BOTTOM);
        tabbedPane.setUI(new BasicTabbedPaneUI() {
            @Override protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {return 25;}

        });
        
        createCarverListPanel();
        
        this.remove(txtAreaScroll);
        
        tabbedPane.addTab("Carver type list", UIManager.getIcon("FileView.fileIcon"), carverListPanel, "");
        tabbedPane.addTab("Advanced", UIManager.getIcon("FileView.fileIcon"), txtAreaScroll, "");
        this.add(tabbedPane);
        XMLCarverConfigurablePanel self = this;
        tabbedPane.addChangeListener(new ChangeListener() {            
            @Override
            public void stateChanged(ChangeEvent e) {
                if(self.hasChanged()) {
                    try {
                        self.applyChanges();
                        changed=false;
                    } catch (ConfigurableValidationException e1) {
                    }
                }
            }
        });
        
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
            int ncols = (int) Math.ceil((carverListPanel.getSize().getWidth()-32)/cellRenderer.getMaxStringWidth());
            int nrows = (int) Math.ceil((double)carverTypeList.getModel().getSize()/(double)ncols);
            
            carverTypeList.setVisibleRowCount(nrows);
            carverListPanel.setViewportView(carverTypeList);
        }
    }
    
    public void createCarverListPanel() {
        carverListPanel = new JScrollPane();
        carverTypeList = new JList<CarverType>(config.getAvailableCarverTypes());
        carverTypeList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        carverListPanel.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        carverListPanel.addComponentListener(new ResizeListener());
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

    @Override
    public void applyChanges() throws ConfigurableValidationException{
        try {
            config.loadXMLConfigFile(textArea.getText());
        } catch (IOException | SAXException | ParserConfigurationException e) {
            throw new ConfigurableValidationException("Erro de sintaxe no XML", e);
        }        
    }

}
