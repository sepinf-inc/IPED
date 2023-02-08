package iped.app.home.configurables;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
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
    private HashMap<Integer, HashSet<CarverType>> includedCarverClassType;


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
    
    public void createCarverListPanel() {
        carverListPanel = new JScrollPane();
        carverTypeList = new JList<CarverType>(createCarverTypeSumList(config.getCarverTypes()));
        carverTypeList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        carverListPanel.setViewportView(carverTypeList);
        carverListPanel.setAutoscrolls(true);
        carverTypeList.setCellRenderer(new CarverConfigCellRenderer());
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
    
    /**
     * Sumarizes carverTypeArray. There can be carverType tags with classes that register more than
     * one carver type. This carver class must apear only once in the list.
     * 
     * @param carverTypes
     * @return
     */
    private CarverType[] createCarverTypeSumList(CarverType[] carverTypes) {
        includedCarverClassType = new HashMap<Integer, HashSet<CarverType>>();
        ArrayList<CarverType> resultTemp = new ArrayList<CarverType>();
        for (int i = 0; i < carverTypes.length; i++) {
            CarverType ct = carverTypes[i];

            HashSet<CarverType> cts = includedCarverClassType.get(ct.getId());
            if(cts==null) {
                cts = new HashSet<CarverType>();
                includedCarverClassType.put(ct.getId(), cts);
                resultTemp.add(ct);
            }
            cts.add(ct);
        }
        CarverType[] result = new CarverType[resultTemp.size()];
        for (int i = 0; i < result.length; i++) {
            result[i]=resultTemp.get(i);
        }
        return result;
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
