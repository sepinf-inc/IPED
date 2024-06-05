package iped.app.home.configurables;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SpringLayout;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.common.base.Predicate;

import iped.app.home.MainFrame;
import iped.app.home.configurables.ParsersTreeModel.ParserElementName;
import iped.app.home.configurables.api.ConfigurableValidationException;
import iped.app.home.configurables.autocompletion.CharsetCompletionProvider;
import iped.app.home.configurables.autocompletion.MimetypeAutoCompletionProvider;
import iped.app.home.configurables.popups.ExternalParserPopup;
import iped.app.ui.CategoryMimeTreeModel;
import iped.app.ui.Messages;
import iped.app.ui.controls.CheckBoxTreeCellRenderer;
import iped.app.ui.controls.ConfigCheckBoxTreeCellRenderer;
import iped.configuration.Configurable;
import iped.engine.config.CategoryConfig;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.ParsersConfig;
import iped.engine.data.Category;

public class ParsersConfigurablePanel extends AdvancedTextConfigurablePanel {
    protected Configurable<String> parsersConfig;
    private JScrollPane parserListPanel;
    private JTree parsersTree;
    private JTree categoryTree;
    private JSplitPane splitPane;
    private CategoryConfig cc;
    private ParsersTreeModel parsersModel;
    private ConfigCheckBoxTreeCellRenderer cellRenderer;
    private ParserTreeCellEditor cellEditor;
    private JScrollPane categoryPanel;
    private CategoryMimeTreeModel categoryTreeModel;
    private Category categoryRoot;
    private ExternalParserPopup externalParserPopup;
    private MouseAdapter popupMouseAdapter;
    private JPanel createExternalParserPanel;
    private SpringLayout panelLayout;
    private int max;
    private JLabel largestLabel;
    private ArrayList<Component> comps = new ArrayList<Component>();
    private Component lastLabel;
    private JTextField txName;
    private JTextField txCheckCommand;
    private JTextField txCheckErroCodes;
    private JTextField txCommand;
    private RSyntaxTextArea txMimetypes;
    private RSyntaxTextArea txCharset;
    private JTextField txLines;
    private DefaultCompletionProvider mimeCompletionProvider;
    private MimetypeAutoCompletionProvider mtac;
    private CharsetCompletionProvider csac;
    private VetoableChangeListener externalParserEditingVeto;

    protected ParsersConfigurablePanel(Configurable<String> configurable, MainFrame mainFrame) {
        super(configurable, mainFrame);
        this.parsersConfig=configurable;
        cc = ConfigurationManager.get().findObject(CategoryConfig.class);
        categoryRoot = cc.getRoot().clone();
    }

    @Override
    public void createConfigurableGUI() {
        super.createConfigurableGUI();

        textArea.getDocument().removeDocumentListener(this);
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_XML);
        textArea.setText(parsersConfig.getConfiguration());
        textArea.getDocument().addDocumentListener(this);
        
        if(parsersModel.isExternalParsers()) {
            createNewExternalParserPanel();
        }
    }

    @Override
    protected Component getBasicPane() {
        if(splitPane==null) {
            createParserListPanel();
        }
        return splitPane;
    }
    
    public void installField(String textLabel, Component component) {
        JLabel label = new JLabel(textLabel);
        if(lastLabel!=null) {
            panelLayout.putConstraint(SpringLayout.NORTH, label, 15, SpringLayout.SOUTH, lastLabel);
        }else {
            panelLayout.putConstraint(SpringLayout.NORTH, label, 15, SpringLayout.NORTH, createExternalParserPanel);
        }
        int width = label.getText().length();
        if(max<width) {
            max=width;
            largestLabel=label;
        }
        comps.add(label);
        Component uiField;
        if(component instanceof JTextField) {
            panelLayout.putConstraint(SpringLayout.VERTICAL_CENTER, component, 0, SpringLayout.VERTICAL_CENTER, label);
        }else {
            panelLayout.putConstraint(SpringLayout.NORTH, component, 0, SpringLayout.NORTH, label);
        }
        comps.add(component);
        lastLabel=component;
    }
    

    public void createNewExternalParserPanel() {
        createExternalParserPanel = new JPanel();
        createExternalParserPanel.setBackground(Color.white);
        JLabel lastLabel = null;
        panelLayout = new SpringLayout();
        createExternalParserPanel.setLayout(panelLayout);

        txName= new JTextField("");
        installField("Parser name:", txName);        
        txCheckCommand=new JTextField();
        installField("Check command:",txCheckCommand);
        
        txCheckErroCodes=new JTextField("1");
        installField("Check error codes:",txCheckErroCodes);
        
        txCommand=new JTextField("${INPUT}");
        installField("Command:",txCommand);
        
        txMimetypes=new RSyntaxTextArea(5,80);
        txMimetypes.setHighlightCurrentLine(false);
        mtac = new MimetypeAutoCompletionProvider();
        AutoCompletion ac = new AutoCompletion(mtac);
        ac.install(txMimetypes);
        installField("Mime-types:",new RTextScrollPane(txMimetypes));
        
        txCharset = new RSyntaxTextArea();
        txCharset.setHighlightCurrentLine(false);
        RTextScrollPane charsetPanel = new RTextScrollPane(txCharset);
        charsetPanel.setLineNumbersEnabled(false);
        charsetPanel.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        installField("Charset:",charsetPanel );
        csac = new CharsetCompletionProvider();
        ac = new AutoCompletion(csac);
        ac.install(txCharset);
        
        txLines= new JTextField("0");
        installField("First lines to ignore:",txLines);

        for (Iterator iterator = comps.iterator(); iterator.hasNext();) {
            Component component = (Component) iterator.next();
            if(component!=largestLabel) {
                if(component instanceof JLabel) {
                    panelLayout.putConstraint(SpringLayout.EAST, component, 0, SpringLayout.EAST, largestLabel);
                }else {
                    panelLayout.putConstraint(SpringLayout.WEST, component, 5,SpringLayout.EAST, largestLabel);
                    panelLayout.putConstraint(SpringLayout.EAST, component, -15,SpringLayout.EAST, createExternalParserPanel);
                }
            }
            createExternalParserPanel.add(component);
        }
        ParsersConfigurablePanel self = this;
        
        JButton cancelExternalParserBtn = new JButton(Messages.get("Home.Cancel"));
        cancelExternalParserBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ((VetoableSingleSelectionModel)tabbedPane.getModel()).removeVetoableChangeListener(externalParserEditingVeto);
                tabbedPane.setSelectedIndex(0);
                tabbedPane.removeTabAt(2);
            }
        });
        JButton createExternalParserBtn = new JButton(Messages.get("iped.app.home.configurables.ParsersConfigurablePanel.CreateExternalParser"));
        createExternalParserBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(txName.getText().trim().equals("")) {
                    JOptionPane.showMessageDialog(createExternalParserPanel, Messages.get("iped.app.home.configurables.ParsersConfigurablePanel.EmptyNameError"));
                    return;
                }
                if(txCommand.getText().trim().equals("${INPUT}")) {
                    JOptionPane.showMessageDialog(createExternalParserPanel, Messages.get("iped.app.home.configurables.ParsersConfigurablePanel.EmptyCommandError"));
                    return;
                }
                if(txMimetypes.getText().trim().equals("")) {
                    JOptionPane.showMessageDialog(createExternalParserPanel, Messages.get("iped.app.home.configurables.ParsersConfigurablePanel.EmptyMimeTypeError"));
                }
                SortedSet<MediaType> mts = MediaTypeRegistry.getDefaultRegistry().getTypes();
                String[] mimetypes= txMimetypes.getText().split("\n");
                for (int i = 0; i < mimetypes.length; i++) {
                    String[] mimeparts=mimetypes[i].trim().split("/");
                    if(mimeparts.length<2) {
                        JOptionPane.showMessageDialog(createExternalParserPanel, Messages.get("iped.app.home.configurables.ParsersConfigurablePanel.InvalidMimeTypeError")+": "+mimetypes[i]+".");
                    }
                    MediaType mt = new MediaType(mimeparts[0], mimeparts[1]);
                    if(!mtac.containsKeyword(mt.toString())) {
                        JOptionPane.showMessageDialog(createExternalParserPanel,  Messages.get("iped.app.home.configurables.ParsersConfigurablePanel.UnregisteredMimeTypeError")+": "+mimetypes[i]+".");
                        return;
                    }
                }
                if(txCharset.getText().trim().equals("")) {
                    JOptionPane.showMessageDialog(createExternalParserPanel, Messages.get("iped.app.home.configurables.ParsersConfigurablePanel.EmptyCharsetError"));
                    return;
                }
                
                try{
                    Integer.parseInt(txLines.getText().trim());
                }catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(createExternalParserPanel, Messages.get("iped.app.home.configurables.ParsersConfigurablePanel.InvalidFirstLinesToSkipError"));
                }
                
                List<Completion> cs = csac.getCompletionByInputText(txCharset.getText().trim());
                if(cs==null || cs.size()!=1) {
                    JOptionPane.showMessageDialog(createExternalParserPanel,  Messages.get("iped.app.home.configurables.ParsersConfigurablePanel.InvalidCharsetError"));
                    return;
                }
                createExternalParserElement();
                ((VetoableSingleSelectionModel)tabbedPane.getModel()).removeVetoableChangeListener(externalParserEditingVeto);
                tabbedPane.setSelectedIndex(0);
                tabbedPane.remove(createExternalParserPanel);
                self.changed = true;
                                
            }
        });
        JPanel btnPanel = new JPanel();
        btnPanel.setBackground(this.getBackground());
        btnPanel.add(createExternalParserBtn);
        btnPanel.add(cancelExternalParserBtn);
        panelLayout.putConstraint(SpringLayout.SOUTH, btnPanel, -30,SpringLayout.SOUTH, createExternalParserPanel);
        panelLayout.putConstraint(SpringLayout.HORIZONTAL_CENTER, btnPanel, 0,SpringLayout.HORIZONTAL_CENTER, createExternalParserPanel);
        createExternalParserPanel.add(btnPanel);
    }
    
    public void createExternalParserElement() {
        Document doc = parsersModel.getDocument();
        doc.getDocumentElement().getChildNodes().getLength();

        Element parserEl = doc.createElement("parser");
        Element parserNameEl = (Element) parserEl.appendChild(doc.createElement("name"));
        parserNameEl.setTextContent(txName.getText());
        Element checkEl = (Element) parserEl.appendChild(doc.createElement("check"));
        checkEl.appendChild(doc.createElement("command")).setTextContent(txCheckCommand.getText());;
        checkEl.appendChild(doc.createElement("error-codes")).setTextContent(txCheckErroCodes.getText());
        parserEl.appendChild(doc.createElement("command")).setTextContent(txCommand.getText());
        parserEl.appendChild(doc.createElement("output-charset")).setTextContent(txCharset.getText());
        parserEl.appendChild(doc.createElement("firstLinesToIgnore")).setTextContent(txLines.getText());
        String[] mimetypes= txMimetypes.getText().split("\n");
        Element mimeTypesEl = (Element) parserEl.appendChild(doc.createElement("mime-types"));
        for (int i = 0; i < mimetypes.length; i++) {
            mimeTypesEl.appendChild(doc.createElement("mime")).setTextContent(mimetypes[i]);
        }
        doc.getDocumentElement().appendChild(parserEl);
        
        refreshParsersModel();
    }

    public void createParserListPanel() {
        parserListPanel = new JScrollPane();
        parsersModel = new ParsersTreeModel(parsersConfig, categoryRoot);
        parsersTree = new JTree(parsersModel);
        parsersTree.setRootVisible(false);
        parsersTree.setShowsRootHandles(true);
        parserListPanel.setViewportView(parsersTree);
        parserListPanel.setAutoscrolls(true);
        parsersTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);        
        cellRenderer = new ConfigCheckBoxTreeCellRenderer(parsersTree, new Predicate<Object>() {
            @Override
            public boolean apply(Object input) {
                boolean result=false;
                if(input instanceof ParserElementName) {
                    Element elem = ((ParserElementName)input).getElement();                    
                    Attr attr = (Attr)elem.getAttributes().getNamedItem(ParsersConfig.PARSER_DISABLED_ATTR);
                    
                    if(attr==null) {
                        result=true;
                    }
                }
                return result;
            }
        }, new Predicate<Object>() {
            @Override
            public boolean apply(@Nullable Object input) {
                return input instanceof ParserElementName;
            }
        });
        cellRenderer.getCheckbox().addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                Object o = parsersTree.getLastSelectedPathComponent();                
                if(o!=null) {
                    if(o instanceof ParserElementName) {
                        Element elem = ((ParserElementName)o).getElement();
                        if(elem!=null) {
                            Attr attr = (Attr)elem.getAttributes().getNamedItem(ParsersConfig.PARSER_DISABLED_ATTR);
                            
                            if(attr==null) {
                                elem.setAttribute(ParsersConfig.PARSER_DISABLED_ATTR, "true");
                            }else {
                                elem.removeAttribute(ParsersConfig.PARSER_DISABLED_ATTR);
                            }

                            textArea.setText(parsersModel.getXMLString());

                            try {
                                //moves the caret to the line of the changed parser declaration
                                int lineNumber = Integer.parseInt((String) elem.getUserData(PositionalXMLReader.LINE_NUMBER_KEY_NAME))-1;
                                int offset = textArea.getDocument().getDefaultRootElement().getElement(lineNumber).getStartOffset();
                                textArea.setCaretPosition(offset);
                            }catch(Exception ex) {
                                ex.printStackTrace();
                            }

                            changed=true;
                        }
                    }
                }
            }
        });
        parsersTree.setCellRenderer(cellRenderer);
        parsersTree.setEditable(true);
        
        categoryTreeModel = new CategoryMimeTreeModel(categoryRoot);
        categoryTreeModel.setToShowUncategorizable(true);
        categoryTreeModel.hideCategories(new java.util.function.Predicate<Category>() {
            @Override
            public boolean test(Category cat) {
                if(cat.equals(categoryRoot)) {
                    return false;
                }else {
                    List<ParserElementName> parsers = parsersModel.getCategoryMediaTypesNames(cat);
                    return parsers!=null && parsers.size()<=0;
                }
            }
        });        
        categoryTree = new JTree(categoryTreeModel);
        categoryTree.setCellRenderer(new CheckBoxTreeCellRenderer(categoryTree, null, new Predicate<Object>() {
            @Override
            public boolean apply(@Nullable Object input) {
                return false;//never shows checkbox
            }
        }));
        categoryTree.setRootVisible(false);
        categoryTree.setShowsRootHandles(true);
        categoryTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        categoryTree.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                Object cat = e.getNewLeadSelectionPath().getLastPathComponent();
                if(cat==CategoryMimeTreeModel.uncategorizableName) {
                    parsersModel.setCategory(null);
                }
                if(cat instanceof Category) {
                    parsersModel.setCategory((Category) cat);
                }
                parsersTree.setModel(parsersModel.copy());
            }
        });
        categoryTree.expandRow(1);
        categoryPanel = new JScrollPane();
        categoryPanel.setViewportView(categoryTree);
        categoryPanel.setAutoscrolls(true);

        splitPane = new JSplitPane();
        splitPane.setRightComponent(parserListPanel);
        splitPane.setLeftComponent(categoryPanel);
        if(parsersModel.isExternalParsers()) {
            externalParserPopup = new ExternalParserPopup(this);
            popupMouseAdapter = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)) {            
                        int row = categoryTree.getClosestRowForLocation(e.getX(), e.getY());
                        categoryTree.setLeadSelectionPath(categoryTree.getPathForRow(row));
                        if(categoryTree.getLeadSelectionPath().getLastPathComponent() instanceof Category) {
                            externalParserPopup.show(e.getComponent(), e.getX(), e.getY());
                        }
                    }
                }
            };
            parsersTree.addMouseListener(popupMouseAdapter);
        }
    }
    
    public void refreshParsersModel() {
        //update tree model
        Enumeration<TreePath> exps = parsersTree.getExpandedDescendants(parsersTree.getPathForRow(0));
        parsersModel = new ParsersTreeModel(parsersConfig, categoryRoot, getDocument());
        parsersTree.setModel(parsersModel);
        if(exps!=null) {
            while(exps.hasMoreElements()) {
                TreePath curpath = exps.nextElement();
                parsersTree.expandPath(curpath);
            }
        }
        textArea.getDocument().removeDocumentListener(this);
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_XML);
        textArea.setText(parsersModel.getXMLString());
        textArea.getDocument().addDocumentListener(this);
    }

    @Override
    public void applyChanges() throws ConfigurableValidationException {
        super.applyChanges();
    }

    public JTree getParsersTree() {
        return parsersTree;
    }

    public Document getDocument() {
        return parsersModel.getDocument();
    }

    public void startExternalParserCreation() {
        txName.setText("");
        txCheckCommand.setText("");
        txMimetypes.setText("");
        txCommand.setText("");
        txCheckErroCodes.setText("1");
        txCharset.setText("");
        txLines.setText("0");

        tabbedPane.addTab(Messages.get("iped.app.home.configurables.ParsersConfigurablePanel.NewExternalParserPanel"), createExternalParserPanel);
        tabbedPane.setSelectedComponent(createExternalParserPanel);
        externalParserEditingVeto = new VetoableChangeListener() {
            @Override
            public void vetoableChange(PropertyChangeEvent evt) throws PropertyVetoException {
                PropertyVetoException pve = new PropertyVetoException(Messages.get("iped.app.home.configurables.ParsersConfigurablePanel.EditVetoError"), evt);
                throw pve;
            }
        };
        ((VetoableSingleSelectionModel)tabbedPane.getModel()).addVetoableChangeListener(externalParserEditingVeto);
    }

    protected String getBasicPaneTitle() {
        return Messages.get("Home.configurables.BasicParsersPanelLabel");
    }

}
