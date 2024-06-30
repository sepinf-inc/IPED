package iped.app.home.configurables;

import java.awt.BorderLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.Predicate;

import javax.swing.DropMode;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.ListModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.event.ListDataListener;
import javax.swing.tree.TreePath;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.eclipse.collections.impl.set.sorted.mutable.TreeSortedSet;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.CompletionProvider;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextAreaBase;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import iped.app.home.MainFrame;
import iped.app.home.configurables.api.ConfigurableValidationException;
import iped.app.home.configurables.autocompletion.MimetypeAutoCompletionProvider;
import iped.app.home.configurables.popups.CategoryTreePopup;
import iped.app.ui.CategoryMimeTreeModel;
import iped.app.ui.Messages;
import iped.app.ui.controls.ConfigCheckBoxTreeCellRenderer;
import iped.engine.config.CategoryConfig;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.SignatureConfig;
import iped.engine.data.Category;

public class SetCategoryConfigurablePanel extends ConfigurablePanel {
    CategoryConfig cc;
    private JTree categoryTree;
    private JScrollPane treeScrollPanel;
    private JList<String> mimeList;
    private JScrollPane mimeListScrollPanel;
    private CategoryMimeTreeModel ctm;
    private List<String> availableMimes;
    private CategoryTreePopup categoryTreePopupMenu;
    private MouseAdapter popupMa;
    private JPanel mimelistPanel;
    private RTextAreaBase txMimeFilter;
    private CompletionProvider cp;
    private JCheckBox ckShowTika;
    static final String PATHARRAY_FLAVOR_NAME = "PATHARRAY";
    static final DataFlavor PATHARRAY_FLAVOR = new DataFlavor(int[].class, PATHARRAY_FLAVOR_NAME);

    protected SetCategoryConfigurablePanel(CategoryConfig configurable, MainFrame mainFrame) {
        super(configurable, mainFrame);
        cc = configurable;
    }

    class MimeListModel implements ListModel<String> {
        @Override
        public int getSize() {
            return (int) availableMimes.stream().filter(new Predicate<String>() {
                @Override
                public boolean test(String t) {
                    return t.contains(txMimeFilter.getText());
                }
            }).count();
        }

        @Override
        public String getElementAt(int index) {
            return (String) availableMimes.stream().filter(new Predicate<String>() {
                @Override
                public boolean test(String t) {
                    return t.contains(txMimeFilter.getText());
                }
            }).toArray()[index];
        }

        @Override
        public void addListDataListener(ListDataListener l) {
        }

        @Override
        public void removeListDataListener(ListDataListener l) {
        }

        public Object getViewToModelIndex(int i) {
            // TODO Auto-generated method stub
            return null;
        }
    }

    @Override
    public void createConfigurableGUI() {
        ctm = new CategoryMimeTreeModel(cc.getRoot(), true);
        categoryTree = new JTree(ctm);
        categoryTree.setEditable(true);
        categoryTreePopupMenu = new CategoryTreePopup(this);
        popupMa = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = categoryTree.getClosestRowForLocation(e.getX(), e.getY());
                    categoryTree.setLeadSelectionPath(categoryTree.getPathForRow(row));
                    if (categoryTree.getLeadSelectionPath().getLastPathComponent() instanceof Category) {
                        categoryTreePopupMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        };
        categoryTree.addMouseListener(popupMa);

        categoryTree.setCellRenderer(new ConfigCheckBoxTreeCellRenderer(categoryTree, null, new Predicate<Object>() {
            @Override
            public boolean test(Object t) {
                return false;
            }
        }));

        treeScrollPanel = new JScrollPane();
        treeScrollPanel.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        treeScrollPanel.setViewportView(categoryTree);
        treeScrollPanel.setAutoscrolls(true);

        this.setLayout(new BorderLayout());
        this.add(treeScrollPanel, BorderLayout.CENTER);

        try {
            getAvailableMimetypes();
            mimeList = new JList<String>(new MimeListModel());
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }
        JLabel mimesLabel = new JLabel("Available mime-types:");
        txMimeFilter = new RSyntaxTextArea(1, 20);
        txMimeFilter.setHighlightCurrentLine(false);
        txMimeFilter.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
                mimeList.setModel(new MimeListModel());
            }

            @Override
            public void keyReleased(KeyEvent e) {
            }

            @Override
            public void keyPressed(KeyEvent e) {
            }
        });
        cp = new MimetypeAutoCompletionProvider();
        AutoCompletion ac = new AutoCompletion(cp);
        ac.install(txMimeFilter);

        mimelistPanel = new JPanel(new BorderLayout());
        mimelistPanel.setBackground(this.getBackground());
        // mimelistPanel.add(mimesLabel,BorderLayout.BEFORE_FIRST_LINE);
        RTextScrollPane tsMimeFilter = new RTextScrollPane(txMimeFilter);
        tsMimeFilter.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        tsMimeFilter.setLineNumbersEnabled(false);
        mimelistPanel.add(tsMimeFilter, BorderLayout.NORTH);
        mimeListScrollPanel = new JScrollPane();
        mimeListScrollPanel.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        mimeListScrollPanel.setViewportView(mimeList);
        mimeListScrollPanel.setAutoscrolls(true);
        mimeList.setDropMode(DropMode.ON);
        mimeList.setTransferHandler(new MimeListTransferHandler());
        mimelistPanel.add(mimeListScrollPanel, BorderLayout.CENTER);
        ckShowTika = new JCheckBox();
        ckShowTika.setText(Messages.get("Home.IPEDMimeSeachList.showTikaMimeTypes"));
        ckShowTika.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    getAvailableMimetypes();
                } catch (ParserConfigurationException | SAXException | IOException e1) {
                    e1.printStackTrace();
                }
                mimeList.setModel(new MimeListModel());
            }
        });
        mimelistPanel.add(ckShowTika, BorderLayout.SOUTH);
        this.add(mimelistPanel, BorderLayout.WEST);

        mimeList.setDragEnabled(true);
        categoryTree.setDropMode(DropMode.ON);
        categoryTree.setDragEnabled(true);
        categoryTree.setTransferHandler(new CategoryTreeTransferHandler());

    }

    class CategoryTreeTransferHandler extends TransferHandler {
        public boolean canImport(TransferHandler.TransferSupport info) {
            if (!info.isDataFlavorSupported(DataFlavor.stringFlavor) && !info.isDataFlavorSupported(PATHARRAY_FLAVOR)) {
                return false;
            }
            return true;
        }

        public int getSourceActions(JComponent c) {
            return TransferHandler.MOVE;
        }

        public boolean importData(TransferHandler.TransferSupport info) {
            if (!info.isDrop()) {
                return false;
            }

            boolean modelChanged = false;

            if (info.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                TreePath path = categoryTree.getPathForLocation(info.getDropLocation().getDropPoint().x, info.getDropLocation().getDropPoint().y);
                if (path.getLastPathComponent() instanceof Category) {
                    Category cat = (Category) path.getLastPathComponent();
                    int[] selind = mimeList.getSelectedIndices();
                    for (int i = 0; i < selind.length; i++) {
                        String mime = mimeList.getModel().getElementAt(selind[i]);
                        availableMimes.remove(mimeList.getModel().getElementAt(selind[i]));
                        cat.getMimes().add(mime);
                    }

                    // refreshs mimeList
                    mimeList.setModel(new MimeListModel());

                    modelChanged = true;
                }
            } else if (info.isDataFlavorSupported(PATHARRAY_FLAVOR)) {
                TreePath[] paths = categoryTree.getSelectionPaths();
                TreePath dstpath = categoryTree.getPathForLocation(info.getDropLocation().getDropPoint().x, info.getDropLocation().getDropPoint().y);
                if (dstpath.getLastPathComponent() instanceof Category) {
                    Category dstcat = (Category) dstpath.getLastPathComponent();
                    for (int i = 0; i < paths.length; i++) {
                        if (paths[i].getLastPathComponent() instanceof Category) {
                            Category cat = (Category) paths[i].getLastPathComponent();
                            Category parent = cat.getParent();
                            parent.getChildren().remove(cat);
                            dstcat.getChildren().add(cat);
                        }
                        if (paths[i].getLastPathComponent() instanceof String) {
                            String mime = (String) paths[i].getLastPathComponent();
                            dstcat.getMimes().add(mime);
                            Category cat = (Category) paths[i].getParentPath().getLastPathComponent();
                            cat.getMimes().remove(mime);
                        }
                    }
                }
                modelChanged = true;
            }

            if (modelChanged) {
                refreshModel();
            }

            changed = true;

            return modelChanged;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            return new Transferable() {
                public boolean isDataFlavorSupported(DataFlavor flavor) {
                    return flavor.equals(PATHARRAY_FLAVOR);
                }

                @Override
                public DataFlavor[] getTransferDataFlavors() {
                    DataFlavor[] dataFlavors = new DataFlavor[1];
                    dataFlavors[0] = PATHARRAY_FLAVOR;
                    return dataFlavors;
                }

                @Override
                public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
                    return categoryTree.getSelectionPaths();// attention that transfer data pass copies of the objects not references to it
                }
            };
        }

    }

    class MimeListTransferHandler extends TransferHandler {
        public boolean canImport(TransferHandler.TransferSupport info) {
            if (!info.isDataFlavorSupported(DataFlavor.stringFlavor) && !info.isDataFlavorSupported(PATHARRAY_FLAVOR)) {
                return false;
            }
            return true;
        }

        public int getSourceActions(JComponent c) {
            return TransferHandler.MOVE;
        }

        public boolean importData(TransferHandler.TransferSupport info) {
            if (!info.isDrop()) {
                return false;
            }

            boolean modelChanged = false;

            if (info.isDataFlavorSupported(PATHARRAY_FLAVOR)) {
                TreePath[] paths = categoryTree.getSelectionPaths();
                boolean thereAreCategories = false;
                // first loop to check if there is any category to be removed
                for (int i = 0; i < paths.length; i++) {
                    if (paths[i].getLastPathComponent() instanceof Category) {
                        thereAreCategories = true;
                        break;
                    }
                }
                if (thereAreCategories) {
                    int result = JOptionPane.showConfirmDialog(mainFrame, "Do you really want to remove selected categories?", "Category remoaval", JOptionPane.OK_CANCEL_OPTION);

                    if (result != JOptionPane.OK_OPTION) {
                        return false;
                    }
                }

                for (int i = 0; i < paths.length; i++) {
                    if (paths[i].getLastPathComponent() instanceof Category) {
                        Category cat = (Category) paths[i].getLastPathComponent();
                        Category parent = cat.getParent();
                        parent.getChildren().remove(cat);
                        recursiveMoveMime(cat);
                    }
                    if (paths[i].getLastPathComponent() instanceof String) {
                        String mime = (String) paths[i].getLastPathComponent();
                        Category cat = (Category) paths[i].getParentPath().getLastPathComponent();
                        cat.getMimes().remove(mime);
                        availableMimes.add(mime);
                    }
                }

                // refreshs mimeList
                Collections.sort(availableMimes);
                mimeList.setModel(new MimeListModel());
                modelChanged = true;
            }

            if (modelChanged) {
                refreshModel();
            }

            changed = true;

            return modelChanged;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            return new Transferable() {
                public boolean isDataFlavorSupported(DataFlavor flavor) {
                    return flavor.equals(DataFlavor.stringFlavor);
                }

                @Override
                public DataFlavor[] getTransferDataFlavors() {
                    DataFlavor[] dataFlavors = new DataFlavor[1];
                    dataFlavors[0] = DataFlavor.stringFlavor;
                    return dataFlavors;
                }

                @Override
                public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
                    return mimeList.getSelectedValue();
                }
            };
        }
    }

    private void recursiveMoveMime(Category cat) {
        List mimes = cat.getMimes();
        for (Iterator iterator2 = mimes.iterator(); iterator2.hasNext();) {
            String mime = (String) iterator2.next();
            availableMimes.add(mime);
        }
        Set<Category> children = cat.getChildren();
        for (Iterator iterator = children.iterator(); iterator.hasNext();) {
            Category category = (Category) iterator.next();
            recursiveMoveMime(category);
        }
    }

    public void refreshModel() {
        // update tree model
        Enumeration<TreePath> exps = categoryTree.getExpandedDescendants(categoryTree.getPathForRow(0));
        categoryTree.setModel(new CategoryMimeTreeModel(cc.getRoot(), true));
        while (exps.hasMoreElements()) {
            TreePath curpath = exps.nextElement();
            categoryTree.expandPath(curpath);
        }
    }

    private List<String> getAvailableMimetypes() throws ParserConfigurationException, SAXException, IOException {
        SignatureConfig sc = ConfigurationManager.get().findObject(SignatureConfig.class);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder docBuilder = dbf.newDocumentBuilder();
        String xml = sc.getConfiguration();
        ByteArrayInputStream bis = new ByteArrayInputStream(xml.getBytes(Charset.forName("UTF-8")));
        Document doc = docBuilder.parse(bis);

        SortedSet<String> mimes = new TreeSortedSet<String>();
        NodeList nl = doc.getElementsByTagName("mime-type");
        for (int i = 0; i < nl.getLength(); i++) {
            String mime = nl.item(i).getAttributes().getNamedItem("type").getNodeValue();
            if (!isIncluded(cc.getRoot(), mime)) {
                mimes.add(mime);
            }
        }

        if (ckShowTika != null && ckShowTika.isSelected()) {
            SortedSet<MediaType> mts = MediaTypeRegistry.getDefaultRegistry().getTypes();
            for (Iterator iterator = mts.iterator(); iterator.hasNext();) {
                MediaType mediaType = (MediaType) iterator.next();
                if (!isIncluded(cc.getRoot(), mediaType.toString())) {
                    mimes.add(mediaType.toString());
                }
            }
        }

        this.availableMimes = new ArrayList<String>();
        this.availableMimes.addAll(mimes);

        return this.availableMimes;
    }

    private boolean isIncluded(Category cat, String mime) {
        if (cat.getMimes().contains(mime)) {
            return true;
        } else {
            SortedSet<Category> cats = cat.getChildren();
            if (cats != null && cats.size() > 0) {
                for (Iterator iterator = cats.iterator(); iterator.hasNext();) {
                    Category category = (Category) iterator.next();
                    if (isIncluded(category, mime)) {
                        return true;
                    }
                }
                return false;
            } else {
                return false;
            }
        }
    }

    @Override
    public void applyChanges() throws ConfigurableValidationException {
    }

    public JTree getCategoryTree() {
        return categoryTree;
    }

}
