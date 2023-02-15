package iped.app.home.configurables;

import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.common.base.Predicate;

import iped.app.home.MainFrame;
import iped.app.home.configurables.ParsersTreeModel.ParserElementName;
import iped.app.home.configurables.popups.ExternalParserPopup;
import iped.app.ui.CategoryMimeTreeModel;
import iped.configuration.Configurable;
import iped.engine.config.CategoryConfig;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.ParsersConfig;
import iped.engine.data.Category;

public class ParsersConfigurablePanel extends AdvancedTextConfigurablePanel {
    protected Configurable<String> parsersConfig;
    private JScrollPane parserListPanel;
    private JTree parsersTree;
    private Document document;
    private JTree categoryTree;
    private JSplitPane splitPane;
    private CategoryConfig cc;
    private ParsersTreeModel parsersModel;
    private CheckBoxTreeCellRenderer cellRenderer;
    private ParserTreeCellEditor cellEditor;
    private JScrollPane categoryPanel;
    private CategoryMimeTreeModel categoryTreeModel;
    private Category categoryRoot;
    private ExternalParserPopup externalParserPopup;
    private MouseAdapter popupMouseAdapter;

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
    }

    @Override
    protected Component createBasicPane() {
        if(splitPane==null) {
            createParserListPanel();
        }
        return splitPane;
    }

    public void createParserListPanel() {
        parserListPanel = new JScrollPane();
        parsersModel = new ParsersTreeModel(parsersConfig, categoryRoot);
        parsersTree = new JTree(parsersModel);
        parsersTree.setRootVisible(false);
        parserListPanel.setViewportView(parsersTree);
        parserListPanel.setAutoscrolls(true);
        parsersTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);        
        cellRenderer = new CheckBoxTreeCellRenderer(parsersTree, new Predicate<Object>() {
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
        cellRenderer.checkbox.addChangeListener(new ChangeListener() {
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
        categoryTree.setRootVisible(false);
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
}
