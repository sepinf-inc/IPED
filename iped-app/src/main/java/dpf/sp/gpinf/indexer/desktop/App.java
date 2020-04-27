/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de EvidÃªncias Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.sp.gpinf.indexer.desktop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter.SortKey;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.border.Border;
import javax.swing.text.JTextComponent;

import org.apache.lucene.search.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bibliothek.extension.gui.dock.theme.EclipseTheme;
import bibliothek.extension.gui.dock.theme.eclipse.stack.EclipseTabPane;
import bibliothek.extension.gui.dock.theme.eclipse.stack.EclipseTabPaneContent;
import bibliothek.extension.gui.dock.theme.eclipse.stack.tab.BorderedComponent;
import bibliothek.extension.gui.dock.theme.eclipse.stack.tab.InvisibleTab;
import bibliothek.extension.gui.dock.theme.eclipse.stack.tab.InvisibleTabPane;
import bibliothek.extension.gui.dock.theme.eclipse.stack.tab.RectGradientPainter;
import bibliothek.extension.gui.dock.theme.eclipse.stack.tab.TabComponent;
import bibliothek.extension.gui.dock.theme.eclipse.stack.tab.TabPainter;
import bibliothek.extension.gui.dock.theme.eclipse.stack.tab.TabPanePainter;
import bibliothek.gui.DockController;
import bibliothek.gui.Dockable;
import bibliothek.gui.dock.StackDockStation;
import bibliothek.gui.dock.common.CControl;
import bibliothek.gui.dock.common.CLocation;
import bibliothek.gui.dock.common.ColorMap;
import bibliothek.gui.dock.common.DefaultSingleCDockable;
import bibliothek.gui.dock.common.action.CButton;
import bibliothek.gui.dock.common.action.CCheckBox;
import bibliothek.gui.dock.common.event.CDockableLocationEvent;
import bibliothek.gui.dock.common.event.CDockableLocationListener;
import bibliothek.gui.dock.common.event.CDockableStateListener;
import bibliothek.gui.dock.common.intern.CDockable;
import bibliothek.gui.dock.common.mode.ExtendedMode;
import bibliothek.gui.dock.common.theme.ThemeMap;
import bibliothek.gui.dock.station.stack.tab.layouting.TabPlacement;
import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.LogConfiguration;
import dpf.sp.gpinf.indexer.Versao;
import dpf.sp.gpinf.indexer.desktop.api.XMLResultSetViewerConfiguration;
import dpf.sp.gpinf.indexer.process.Manager;
import dpf.sp.gpinf.indexer.search.IPEDMultiSource;
import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.ItemId;
import dpf.sp.gpinf.indexer.search.MultiSearchResult;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.ATextViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.TextViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.Viewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.util.AppSearchParams;
import dpf.sp.gpinf.indexer.ui.hitsViewer.HitsTable;
import dpf.sp.gpinf.indexer.ui.hitsViewer.HitsTableModel;
import dpf.sp.gpinf.indexer.util.IconUtil;
import iped3.IIPEDSource;
import iped3.desktop.CancelableWorker;
import iped3.desktop.IColumnsManager;
import iped3.desktop.GUIProvider;
import iped3.desktop.ProgressDialog;
import iped3.desktop.ResultSetViewer;
import iped3.desktop.ResultSetViewerConfiguration;
import iped3.search.IIPEDSearcher;
import iped3.search.IMultiSearchResult;
import iped3.search.IMultiSearchResultProvider;

public class App extends JFrame implements WindowListener, IMultiSearchResultProvider, GUIProvider {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    public static final boolean triageGui = System.getProperty("enableTriageGui") != null; //$NON-NLS-1$

    private static Logger LOGGER;

    private LogConfiguration logConfiguration;

    private AppSearchParams appSearchParams = null;

    private Manager processingManager;

    IMultiSearchResult ipedResult = new MultiSearchResult(new ItemId[0], new float[0]);

    public IPEDMultiSource appCase;

    FilterManager filterManager;
    public JDialog dialogBar;
    JProgressBar progressBar;
    JComboBox<String> termo, filtro;
    JButton pesquisar, opcoes, atualizar, ajuda, exportToZip;
    JCheckBox checkBox, recursiveTreeList, filterDuplicates;
    JTable resultsTable;
    GalleryTable gallery;
    public HitsTable hitsTable;

    HitsTable subItemTable, duplicatesTable;
    JTree tree, bookmarksTree, categoryTree;
    MetadataPanel metadataPanel;
    JScrollPane categoriesPanel, bookmarksPanel;
    JPanel evidencePanel;
    TreeListener treeListener;
    CategoryTreeListener categoryListener;
    BookmarksTreeListener bookmarksListener;
    HitsTable parentItemTable;
    CControl dockingControl;
    DefaultSingleCDockable categoriesTabDock, metadataTabDock, bookmarksTabDock, evidenceTabDock;
    List<DefaultSingleCDockable> rsTabDock = new ArrayList<DefaultSingleCDockable>();

    DefaultSingleCDockable tableTabDock, galleryTabDock;
    public DefaultSingleCDockable hitsDock, subitemDock, parentDock, duplicateDock;

    private List<DefaultSingleCDockable> viewerDocks;
    private ViewerController viewerController;

    Color defaultColor;
    Color defaultFocusedColor;
    Color defaultSelectedColor;
    private JScrollPane hitsScroll, subItemScroll, parentItemScroll, duplicatesScroll;
    JScrollPane viewerScroll, resultsScroll, galleryScroll;
    MenuClass menu;
    JPanel topPanel;
    JPanel multiFilterAlert;
    boolean disposicaoVertical = false;

    public ResultTableModel resultsModel;
    List resultSortKeys;
    SubitemTableModel subItemModel = new SubitemTableModel();
    ParentTableModel parentItemModel = new ParentTableModel();
    DuplicatesTableModel duplicatesModel = new DuplicatesTableModel();
    GalleryModel galleryModel = new GalleryModel();

    Color alertColor = Color.RED;
    Color alertFocusedColor = Color.RED;
    Color alertSelectedColor = Color.RED;

    private int zoomLevel;

    File casesPathFile;
    boolean isMultiCase;
    public JLabel status;

    private static final String resPath = "/dpf/sp/gpinf/indexer/desktop/";

    final static String FILTRO_TODOS = Messages.getString("App.NoFilter"); //$NON-NLS-1$
    final static String FILTRO_SELECTED = Messages.getString("App.Checked"); //$NON-NLS-1$
    public final static String SEARCH_TOOL_TIP = Messages.getString("App.SearchBoxTip"); //$NON-NLS-1$

    public static int MAX_LINE_SIZE = 100; // tamanho de quebra do texto para highlight

    static int MAX_HITS = 10000;

    public static int MAX_LINES = 100000;

    final static int FRAG_SIZE = 100, TEXT_BREAK_SIZE = 1000000;

    private static App app;

    AppListener appletListener;

    private ResultSetViewerConfiguration resultSetViewerConfiguration;

    private App() {
        this.appSearchParams = new AppSearchParams();
        this.appSearchParams.mainFrame = (JFrame) this;
        //TODO: Remove before final commit. //this.appSearchParams.viewerControl = ViewerControl.getInstance();
        this.appSearchParams.HIGHLIGHT_START_TAG = "<font color=\"black\" bgcolor=\"yellow\">"; //$NON-NLS-1$
        this.appSearchParams.HIGHLIGHT_END_TAG = "</font>"; //$NON-NLS-1$
        this.appSearchParams.TEXT_BREAK_SIZE = TEXT_BREAK_SIZE;
        this.appSearchParams.FRAG_SIZE = FRAG_SIZE;
        this.appSearchParams.MAX_LINES = MAX_LINES;
        this.appSearchParams.MAX_HITS = MAX_HITS;
        this.appSearchParams.MAX_LINE_SIZE = MAX_LINE_SIZE;
        this.appSearchParams.highlightTerms = new HashSet<String>();
    }

    public static final App get() {
        if (app == null) {
            app = new App();
        }
        return app;
    }

    public AppSearchParams getSearchParams() {
        return this.appSearchParams;
    }

    public Manager getProcessingManager() {
        return processingManager;
    }

    public void init(LogConfiguration logConfiguration, boolean isMultiCase, File casesPathFile,
            Manager processingManager) {

        this.logConfiguration = logConfiguration;
        this.isMultiCase = isMultiCase;
        this.casesPathFile = casesPathFile;
        this.processingManager = processingManager;
        if (processingManager != null)
            processingManager.setSearchAppOpen(true);

        LOGGER = LoggerFactory.getLogger(App.class);
        LOGGER.info("Starting..."); //$NON-NLS-1$

        if (SwingUtilities.isEventDispatchThread()) {
            createGUI();
            LOGGER.info("GUI created"); //$NON-NLS-1$
        } else {
            try {
                SwingUtilities.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            createGUI();
                            LOGGER.info("GUI created"); //$NON-NLS-1$

                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }
                });
            } catch (InvocationTargetException | InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

    }

    public AppSearchParams getParams() {
        return this.appSearchParams;
    }

    public Query getQuery() {
        return this.appSearchParams.query;
    }

    public void setQuery(Query query) {
        this.appSearchParams.query = query;
    }

    public Object getAutoParser() {
        return this.appSearchParams.autoParser;
    }

    public void setAutoParser(Object autoParser) {
        this.appSearchParams.autoParser = autoParser;
    }

    public ATextViewer getTextViewer() {
        return viewerController.getTextViewer();
    }

    public ViewerController getViewerController() {
        return viewerController;
    }

    private void destroy() {
        try {

            if (this.resultsTable != null) {
                ColumnsManager.getInstance().dispose();
            }
            if (viewerController != null) {
                viewerController.dispose();
            }
            appCase.close();

            if (processingManager == null || processingManager.isProcessingFinished()) {
                if (processingManager != null)
                    processingManager.deleteTempDir();
                if (logConfiguration != null)
                    logConfiguration.closeConsoleLogFile();

                System.exit(0);

            } else {
                processingManager.setSearchAppOpen(false);
                app = null;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void createGUI() {

        String tab = "     "; //$NON-NLS-1$
        this.setTitle(Versao.APP_NAME + tab + "[" + Messages.getString("App.Case") + ": " + casesPathFile + "]"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        this.setSize(new Dimension(800, 600));
        this.setExtendedState(Frame.MAXIMIZED_BOTH);
        this.addWindowListener(this);
        URL image = getClass().getResource("search.png"); //$NON-NLS-1$
        this.setIconImage(new ImageIcon(image).getImage());
        this.setVisible(true);
        ToolTipManager.sharedInstance().setInitialDelay(10);

        try {
            boolean nimbusFound = false;
            for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) { //$NON-NLS-1$
                    UIManager.put("nimbusOrange", new Color(47, 92, 180)); //$NON-NLS-1$
                    UIManager.put("nimbusRed", Color.BLUE); //$NON-NLS-1$
                    UIManager.setLookAndFeel(info.getClassName());
                    UIDefaults defaults = UIManager.getLookAndFeel().getDefaults();
                    defaults.put("ScrollBar.thumbHeight", 12); //$NON-NLS-1$
                    // Workaround JDK-8134828
                    defaults.put("ScrollBar.minimumThumbSize", new Dimension(30, 30)); //$NON-NLS-1$
                    nimbusFound = true;
                    break;
                }
            }
            if (!nimbusFound) {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        termo = new JComboBox<String>();
        termo.setMinimumSize(new Dimension());
        termo.setToolTipText(SEARCH_TOOL_TIP);
        termo.setEditable(true);
        termo.setSelectedItem(SEARCH_TOOL_TIP);
        termo.setMaximumRowCount(30);

        pesquisar = new JButton(Messages.getString("App.Search")); //$NON-NLS-1$
        opcoes = new JButton(Messages.getString("App.Options")); //$NON-NLS-1$
        atualizar = new JButton(Messages.getString("App.Update")); //$NON-NLS-1$
        ajuda = new JButton(Messages.getString("App.Help")); //$NON-NLS-1$
        exportToZip = new JButton(Messages.getString("App.ExportZip")); //$NON-NLS-1$
        checkBox = new JCheckBox("0"); //$NON-NLS-1$

        filtro = new JComboBox<String>();
        filtro.setMaximumSize(new Dimension(100, 50));
        filtro.setMaximumRowCount(30);
        filtro.addItem(App.FILTRO_TODOS);
        filtro.setToolTipText(Messages.getString("App.FilterTip")); //$NON-NLS-1$
        filterManager = new FilterManager(filtro);

        filterDuplicates = new JCheckBox(Messages.getString("App.FilterDuplicates"));
        filterDuplicates.setToolTipText(Messages.getString("App.FilterDuplicatesTip"));

        topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.LINE_AXIS));
        topPanel.setAlignmentX(LEFT_ALIGNMENT);

        JLabel alertLabel = new JLabel(Messages.getString("App.FilterWarn")); //$NON-NLS-1$
        alertLabel.setForeground(Color.WHITE);
        multiFilterAlert = new JPanel();
        multiFilterAlert.add(alertLabel);
        multiFilterAlert.setBackground(alertColor);
        multiFilterAlert.setMaximumSize(new Dimension(100, 100));
        // multiFilterAlert.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1,
        // true));
        multiFilterAlert.setVisible(false);

        topPanel.add(filtro);
        topPanel.add(filterDuplicates);
        topPanel.add(multiFilterAlert);
        topPanel.add(new JLabel(tab + Messages.getString("App.SearchLabel"))); //$NON-NLS-1$
        topPanel.add(termo);
        topPanel.add(opcoes);
        if (processingManager != null)
            topPanel.add(atualizar);
        topPanel.add(ajuda);
        topPanel.add(exportToZip);
        exportToZip.setVisible(false);
        topPanel.add(checkBox);
        topPanel.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));

        resultsModel = new ResultTableModel();
        resultsTable = new JTable(resultsModel);
        resultsScroll = new JScrollPane(resultsTable);
        resultsTable.setFillsViewportHeight(true);
        resultsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        resultsTable.setDefaultRenderer(String.class, new TableCellRenderer());
        resultsTable.setShowGrid(false);
        resultsTable.setAutoscrolls(false);
        InputMap inputMap = resultsTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputMap.put(KeyStroke.getKeyStroke("SPACE"), "none"); //$NON-NLS-1$ //$NON-NLS-2$
        inputMap.put(KeyStroke.getKeyStroke("ctrl SPACE"), "none"); //$NON-NLS-1$ //$NON-NLS-2$

        gallery = new GalleryTable(galleryModel);
        galleryScroll = new JScrollPane(gallery);
        gallery.setFillsViewportHeight(true);
        gallery.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        gallery.setShowGrid(false);
        gallery.setBackground(Color.WHITE);
        gallery.setTableHeader(null);
        gallery.setIntercellSpacing(new Dimension(1, 1));
        gallery.setCellSelectionEnabled(true);
        gallery.setDefaultRenderer(GalleryCellRenderer.class, new GalleryCellRenderer());
        GalleryCellEditor cellEditor = new GalleryCellEditor();
        gallery.setDefaultEditor(GalleryCellRenderer.class, cellEditor);
        gallery.getSelectionModel().addListSelectionListener(new GalleryListener());
        gallery.getColumnModel().getSelectionModel().addListSelectionListener(new GalleryListener());
        gallery.addMouseListener(new GalleryListener());
        GalleryListener keyListener = new GalleryListener();
        keyListener.setCellEditor(cellEditor);
        gallery.addKeyListener(keyListener);

        inputMap = gallery.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputMap.put(KeyStroke.getKeyStroke("SPACE"), "none"); //$NON-NLS-1$ //$NON-NLS-2$
        inputMap.put(KeyStroke.getKeyStroke("ctrl SPACE"), "none"); //$NON-NLS-1$ //$NON-NLS-2$

        gallery.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int colWidth = (int) gallery.getVisibleRect().getWidth() / galleryModel.colCount;
                if (colWidth > 0) {
                    gallery.setRowHeight(colWidth);
                }
                int selRow = App.get().gallery.getSelectedRow();
                if (selRow >= 0) {
                    App.get().gallery.scrollRectToVisible(App.get().gallery.getCellRect(selRow, 0, false));
                }
            }
        });

        viewerController = new ViewerController(appSearchParams);
        hitsTable = new HitsTable(new HitsTableModel(getTextViewer()));
        appSearchParams.hitsTable = hitsTable;
        hitsScroll = new JScrollPane(hitsTable);
        hitsTable.setFillsViewportHeight(true);
        hitsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        hitsTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        hitsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        hitsTable.getColumnModel().getColumn(1).setPreferredWidth(1500);
        hitsTable.getTableHeader().setPreferredSize(new Dimension(0, 0));
        hitsTable.setShowGrid(false);

        subItemTable = new HitsTable(subItemModel);
        subItemScroll = new JScrollPane(subItemTable);
        this.appSearchParams.subItemScroll = subItemScroll;
        subItemTable.setFillsViewportHeight(true);
        subItemTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        subItemTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        subItemTable.getColumnModel().getColumn(1).setPreferredWidth(20);
        subItemTable.getColumnModel().getColumn(2).setPreferredWidth(1500);
        subItemTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        subItemTable.setDefaultRenderer(String.class, new TableCellRenderer());
        subItemTable.getTableHeader().setPreferredSize(new Dimension(0, 0));
        subItemTable.setShowGrid(false);

        duplicatesTable = new HitsTable(duplicatesModel);
        duplicatesScroll = new JScrollPane(duplicatesTable);
        duplicatesTable.setFillsViewportHeight(true);
        duplicatesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        duplicatesTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        duplicatesTable.getColumnModel().getColumn(1).setPreferredWidth(20);
        duplicatesTable.getColumnModel().getColumn(2).setPreferredWidth(1500);
        duplicatesTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        duplicatesTable.setDefaultRenderer(String.class, new TableCellRenderer());
        duplicatesTable.getTableHeader().setPreferredSize(new Dimension(0, 0));
        duplicatesTable.setShowGrid(false);

        parentItemTable = new HitsTable(parentItemModel);
        parentItemScroll = new JScrollPane(parentItemTable);
        this.appSearchParams.parentItemScroll = parentItemScroll;
        parentItemTable.setFillsViewportHeight(true);
        parentItemTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        parentItemTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        parentItemTable.getColumnModel().getColumn(1).setPreferredWidth(20);
        parentItemTable.getColumnModel().getColumn(2).setPreferredWidth(1500);
        parentItemTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        parentItemTable.setDefaultRenderer(String.class, new TableCellRenderer());
        parentItemTable.getTableHeader().setPreferredSize(new Dimension(0, 0));
        parentItemTable.setShowGrid(false);

        categoryTree = new JTree(new Object[0]);
        categoryTree.setRootVisible(true);
        categoryTree.setExpandsSelectedPaths(false);
        categoryListener = new CategoryTreeListener();
        categoryTree.addTreeSelectionListener(categoryListener);
        categoryTree.addTreeExpansionListener(categoryListener);

        bookmarksTree = new JTree(new BookmarksTreeModel());
        bookmarksTree.setCellRenderer(new BookmarkTreeCellRenderer());
        ToolTipManager.sharedInstance().registerComponent(bookmarksTree);
        bookmarksListener = new BookmarksTreeListener();
        bookmarksTree.addTreeSelectionListener(bookmarksListener);
        bookmarksTree.addTreeExpansionListener(bookmarksListener);
        bookmarksTree.setExpandsSelectedPaths(false);

        metadataPanel = new MetadataPanel();
        categoriesPanel = new JScrollPane(categoryTree);
        bookmarksPanel = new JScrollPane(bookmarksTree);

        boolean isFTKReport = new File(casesPathFile, "indexador/data/containsFTKReport.flag").exists(); //$NON-NLS-1$

        if (!isFTKReport) {
            recursiveTreeList = new JCheckBox(Messages.getString("App.RecursiveListing")); //$NON-NLS-1$
            recursiveTreeList.setSelected(true);

            tree = new JTree(new Object[0]);
            tree.setRootVisible(true);
            tree.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
            treeListener = new TreeListener();
            tree.addTreeSelectionListener(treeListener);
            tree.addTreeExpansionListener(treeListener);

            evidencePanel = new JPanel(new BorderLayout());
            evidencePanel.add(recursiveTreeList, BorderLayout.NORTH);
            evidencePanel.add(new JScrollPane(tree), BorderLayout.CENTER);

            // treeTab.insertTab(Messages.getString("TreeViewModel.RootName"), null,
            // evidencePanel, null, 2); //$NON-NLS-1$
        }

        dockingControl = new CControl(this);
        dockingControl.setTheme(ThemeMap.KEY_ECLIPSE_THEME);

        // This forces Eclipse theme to use rectangular tabs instead of curved ones, to save horizontal space. 
        dockingControl.putProperty(EclipseTheme.TAB_PAINTER, new TabPainter() {
            public Border getFullBorder(BorderedComponent owner, DockController controller, Dockable dockable) {
                return RectGradientPainter.FACTORY.getFullBorder(owner, controller, dockable);
            }

            public TabComponent createTabComponent(EclipseTabPane pane, Dockable dockable) {
                //return RectGradientPainter.FACTORY.createTabComponent(pane, dockable);
                return new RectGradientPainter(pane, dockable) {
                    private static final long serialVersionUID = -9020339124009415001L;

                    public void setLabelInsets(Insets labelInsets) {
                        labelInsets = new Insets(labelInsets.top - 1, labelInsets.left - 3, labelInsets.bottom - 1, labelInsets.right - 3);
                        super.setLabelInsets(labelInsets);
                    }
                };
            }

            public InvisibleTab createInvisibleTab(InvisibleTabPane pane, Dockable dockable) {
                return RectGradientPainter.FACTORY.createInvisibleTab(pane, dockable);
            }

            public TabPanePainter createDecorationPainter(EclipseTabPane pane) {
                return RectGradientPainter.FACTORY.createDecorationPainter(pane);
            }
        });

        dockingControl.putProperty(StackDockStation.TAB_PLACEMENT, TabPlacement.TOP_OF_DOCKABLE);
        this.getContentPane().add(dockingControl.getContentArea(), BorderLayout.CENTER);
        defaultColor = dockingControl.getController().getColors().get(ColorMap.COLOR_KEY_TAB_BACKGROUND);
        defaultFocusedColor = dockingControl.getController().getColors().get(ColorMap.COLOR_KEY_TAB_BACKGROUND_FOCUSED);
        defaultSelectedColor = dockingControl.getController().getColors()
                .get(ColorMap.COLOR_KEY_TAB_BACKGROUND_SELECTED);

        if (triageGui) {
            disposicaoVertical = true;
            exportToZip.setVisible(true);
        }

        refazLayout(false);

        if (triageGui) {
            zoomFont(this, -1);
        }

        status = new JLabel(" "); //$NON-NLS-1$
        this.appSearchParams.status = status;

        this.getContentPane().add(topPanel, BorderLayout.PAGE_START);
        // this.getContentPane().add(treeSplitPane, BorderLayout.CENTER);
        this.getContentPane().add(status, BorderLayout.PAGE_END);

        progressBar = new JProgressBar(0, 1);
        progressBar.setValue(0);
        progressBar.setString(Messages.getString("App.Wait")); //$NON-NLS-1$
        progressBar.setForeground(Color.WHITE);
        progressBar.setStringPainted(true);
        progressBar.setIndeterminate(true);

        dialogBar = new JDialog(SwingUtilities.windowForComponent(resultsTable), Dialog.ModalityType.MODELESS);
        dialogBar.setBounds(0, 0, 150, 30);
        dialogBar.setUndecorated(true);
        dialogBar.getContentPane().add(progressBar);
        appSearchParams.dialogBar = dialogBar;

        appletListener = new AppListener();
        if (!isFTKReport) {
            recursiveTreeList.addActionListener(treeListener);
        }
        termo.addActionListener(appletListener);
        filtro.addActionListener(appletListener);
        filterDuplicates.addActionListener(appletListener);
        pesquisar.addActionListener(appletListener);
        opcoes.addActionListener(appletListener);
        exportToZip.addActionListener(appletListener);
        atualizar.addActionListener(appletListener);
        ajuda.addActionListener(appletListener);
        checkBox.addActionListener(appletListener);
        resultsTable.getSelectionModel().addListSelectionListener(new ResultTableListener());
        resultsTable.addMouseListener(new ResultTableListener());
        resultsTable.addKeyListener(new ResultTableListener());

        hitsTable.getSelectionModel().addListSelectionListener(new HitsTableListener(TextViewer.font));
        subItemTable.addMouseListener(subItemModel);
        subItemTable.getSelectionModel().addListSelectionListener(subItemModel);
        parentItemTable.addMouseListener(parentItemModel);
        parentItemTable.getSelectionModel().addListSelectionListener(parentItemModel);
        duplicatesTable.addMouseListener(duplicatesModel);
        duplicatesTable.getSelectionModel().addListSelectionListener(duplicatesModel);

        hitsTable.addMouseListener(appletListener);
        // filtro.addMouseListener(appletListener);
        // filtro.getComponent(0).addMouseListener(appletListener);
        termo.getEditor().getEditorComponent().addMouseListener(appletListener);
        termo.getComponent(0).addMouseListener(appletListener);

        // Permite zoom das fontes da interface com CTRL+"-" e CTRL+"="
        gallery.repaint();
        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        manager.addKeyEventDispatcher(new KeyEventDispatcher() {
            public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getID() == KeyEvent.KEY_RELEASED) {
                    if ((e.getModifiers() & InputEvent.CTRL_MASK) == InputEvent.CTRL_MASK) {
                        if (e.getKeyCode() == KeyEvent.VK_EQUALS) {
                            synchronized (App.this) {
                                if (zoomLevel < 8) {
                                    zoomLevel++;
                                    zoomFont(App.this, 1);
                                }
                            }
                            return true;
                        } else if (e.getKeyCode() == KeyEvent.VK_MINUS) {
                            synchronized (App.this) {
                                if (zoomLevel > -4) {
                                    zoomLevel--;
                                    zoomFont(App.this, -1);
                                }
                            }
                            return true;
                        }
                    }
                }
                return false;
            }
        });

        new AutoCompletarColunas((JTextComponent) termo.getEditor().getEditorComponent());
    }

    private void createAllDockables() {
        categoriesTabDock = createDockable("categoriestab", Messages.getString("CategoryTreeModel.RootName"), //$NON-NLS-1$ //$NON-NLS-2$
                categoriesPanel);
        metadataTabDock = createDockable("metadatatab", Messages.getString("App.Metadata"), metadataPanel); //$NON-NLS-1$ //$NON-NLS-2$
        if (evidencePanel != null) {
            evidenceTabDock = createDockable("evidencetab", Messages.getString("TreeViewModel.RootName"), //$NON-NLS-1$ //$NON-NLS-2$
                    evidencePanel);
        }
        bookmarksTabDock = createDockable("bookmarkstab", Messages.getString("BookmarksTreeModel.RootName"), //$NON-NLS-1$ //$NON-NLS-2$
                bookmarksPanel);

        tableTabDock = createDockable("tabletab", Messages.getString("App.Table"), resultsScroll); //$NON-NLS-1$ //$NON-NLS-2$
        galleryTabDock = createDockable("galleryscroll", Messages.getString("App.Gallery"), galleryScroll); //$NON-NLS-1$ //$NON-NLS-2$

        // Add buttons to control the thumbnails size / number of columns in the gallery
        CButton butDec = new CButton(Messages.getString("Gallery.DecreaseThumbsSize"), IconUtil.getIcon("minus", resPath));
        galleryTabDock.addAction(butDec);
        CButton butInc = new CButton(Messages.getString("Gallery.IncreaseThumbsSize"), IconUtil.getIcon("plus", resPath));
        galleryTabDock.addAction(butInc);
        galleryTabDock.addSeparator();
        butDec.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateGalleryColCount(1);
            }
        });
        butInc.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateGalleryColCount(-1);
            }
        });

        List<ResultSetViewer> rsViewers = getResultSetViewerConfiguration().getResultSetViewers();
        for (Iterator<ResultSetViewer> iterator = rsViewers.iterator(); iterator.hasNext();) {
            final ResultSetViewer resultSetViewer = iterator.next();

            resultSetViewer.init(resultsTable, this, this);
            DefaultSingleCDockable tabDock = createDockable(resultSetViewer.getID(), resultSetViewer.getTitle(),
                    resultSetViewer.getPanel());

            resultSetViewer.setDockableContainer(tabDock);

            rsTabDock.add(tabDock);
            tabDock.addCDockableLocationListener(new CDockableLocationListener() {
                @Override
                public void changed(CDockableLocationEvent event) {
                    if (event.isShowingChanged() && event.getNewShowing()) {
                        resultSetViewer.redraw();
                    }
                }
            });
        }

        hitsDock = createDockable("tabbedhits", Messages.getString("App.Hits"), hitsScroll); //$NON-NLS-1$ //$NON-NLS-2$
        subitemDock = createDockable("subitemstab", Messages.getString("SubitemTableModel.Subitens"), subItemScroll); //$NON-NLS-1$ //$NON-NLS-2$
        parentDock = createDockable("parentitemtab", Messages.getString("ParentTableModel.ParentCount"), //$NON-NLS-1$ //$NON-NLS-2$
                parentItemScroll);
        duplicateDock = createDockable("duplicatestab", Messages.getString("DuplicatesTableModel.Duplicates"), //$NON-NLS-1$ //$NON-NLS-2$
                duplicatesScroll);

        dockingControl.addDockable(categoriesTabDock);
        dockingControl.addDockable(metadataTabDock);
        if (evidenceTabDock != null) {
            dockingControl.addDockable(evidenceTabDock);
        }
        dockingControl.addDockable(bookmarksTabDock);
        dockingControl.addDockable(tableTabDock);
        dockingControl.addDockable(galleryTabDock);

        for (Iterator<DefaultSingleCDockable> iterator = rsTabDock.iterator(); iterator.hasNext();) {
            DefaultSingleCDockable tabDock = iterator.next();
            dockingControl.addDockable(tabDock);
        }

        dockingControl.addDockable(hitsDock);
        dockingControl.addDockable(subitemDock);
        dockingControl.addDockable(duplicateDock);
        dockingControl.addDockable(parentDock);

        List<Viewer> viewers = viewerController.getViewers();
        viewerDocks = new ArrayList<DefaultSingleCDockable>();
        for (Viewer viewer : viewers) {
            DefaultSingleCDockable viewerDock = createDockable(viewer.getClass().getName(), viewer.getName(), viewer.getPanel());
            viewerDocks.add(viewerDock);
            dockingControl.addDockable(viewerDock);
            viewerController.put(viewer, viewerDock);
        }

        //TODO: Remove before final commit. 
        /*
        compositeViewerDock = createDockable("compositeviewer", Messages.getString("CompositeViewer.Title"), //$NON-NLS-1$ //$NON-NLS-2$
                compositeViewer);
        compositeViewerDock.setTitleShown(false);
        compositeViewerDock.addCDockableStateListener(new CDockableStateListener() {
            @Override
            public void extendedModeChanged(CDockable arg0, ExtendedMode mode) {
                if (mode == ExtendedMode.EXTERNALIZED || mode == ExtendedMode.NORMALIZED)
                    viewerControl.restartLibreOfficeFrame();
            }
        
            @Override
            public void visibilityChanged(CDockable arg0) {
                // TODO Auto-generated method stub
            }
        });
        */

        setDockablesColors();
    }
    
    private void setupViewerDocks() {
        CCheckBox chkFixed = new CCheckBox(Messages.getString("ViewerController.FixViewer"), IconUtil.getIcon("pin", resPath)) {
            protected void changed() {
                viewerController.setFixed(isSelected());
            }
        };
        List<Viewer> viewers = viewerController.getViewers();
        for (int i = 0; i < viewers.size(); i++) {
            Viewer viewer = viewers.get(i);
            DefaultSingleCDockable viewerDock = viewerDocks.get(i);
            viewerDock.addCDockableLocationListener(new CDockableLocationListener() {
                public void changed(CDockableLocationEvent event) {
                    //TODO: Remove before final commit
                    /*
                    System.err.println();
                    System.err.println("VIEWER:" + viewer.getName());
                    System.err.println(event.getOldLocation());
                    System.err.println(event.getOldShowing());
                    System.err.println(event.getNewShowing());
                    System.err.println(event.getNewLocation());
                    System.err.println(event.isShowingChanged());
                    System.err.println(event.isLocationChanged());
                    */
                    if (viewerController != null && event.getNewShowing()) {
                        boolean validated = false;
                        if (event.isLocationChanged() && event.getOldLocation() != null) {
                            CLocation oldLocation = event.getOldLocation().getParent();
                            CLocation newLocation = event.getNewLocation();
                            if (newLocation != null) {
                                newLocation = newLocation.getParent();
                            }
                            if ((oldLocation == null && newLocation != null) || (oldLocation != null && !oldLocation.equals(newLocation))) {
                                validated = viewerController.validateViewer(viewer);
                            }
                        }
                        if (!validated && event.isShowingChanged()) {
                            viewerController.updateViewer(viewer, false);
                        } 
                    }
                }
            });
            //TODO: Remove before final commit
            /*
            viewerDock.addCDockableStateListener(new CDockableStateListener() {
                public void visibilityChanged(CDockable dockable) {
                }
                
                public void extendedModeChanged(CDockable dockable, ExtendedMode mode) {
                    if (viewerController != null) {
                        viewerController.validateViewer(viewer);
                    }
                }
            });
            */
            
            CButton prevHit = new CButton(Messages.getString("ViewerController.PrevHit"), IconUtil.getIcon("prev", resPath));
            CButton nextHit = new CButton(Messages.getString("ViewerController.NextHit"), IconUtil.getIcon("next", resPath));
            prevHit.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    viewer.scrollToNextHit(false);
                }
            });
            nextHit.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    viewer.scrollToNextHit(true);
                }
            });
            viewerDock.addAction(prevHit);
            viewerDock.addAction(nextHit);
            viewerDock.addAction(chkFixed);
            viewerDock.putAction("prevHit", prevHit);
            viewerDock.putAction("nextHit", nextHit);

            int toolbarSupport = viewer.getToolbarSupported();
            if (toolbarSupport >= 0) {
                CCheckBox chkToolbar = new CCheckBox(Messages.getString("ViewerController.ShowToolBar"), IconUtil.getIcon("down", resPath)) {
                    protected void changed() {
                        viewer.setToolbarVisible(isSelected());
                    }
                };
                chkToolbar.setSelectedIcon(IconUtil.getIcon("up", resPath));
                chkToolbar.setSelected(true);
                viewerDock.addAction(chkToolbar);
                viewerDock.putAction("toolbar", chkToolbar);
            }

            viewerDock.addSeparator();
        }
    }

    private void updateGalleryColCount(int inc) {
        int cnt = App.get().galleryModel.colCount + inc;
        if (cnt > 0 && cnt <= 40) {
            App.get().galleryModel.colCount = cnt;
            int colWidth = App.get().gallery.getWidth() / App.get().galleryModel.colCount;
            App.get().gallery.setRowHeight(colWidth);
            int selRow = App.get().resultsTable.getSelectedRow();
            App.get().galleryModel.fireTableStructureChanged();
            if (selRow >= 0) {
                int galleryRow = selRow / App.get().galleryModel.colCount;
                int galleyCol = selRow % App.get().galleryModel.colCount;
                App.get().gallery.getSelectionModel().setSelectionInterval(galleryRow, galleryRow);
                App.get().gallery.getColumnModel().getSelectionModel().setSelectionInterval(galleyCol, galleyCol);
            }
        }
    }

    private ResultSetViewerConfiguration getResultSetViewerConfiguration() {
        try {
            if (resultSetViewerConfiguration == null) {
                File xml = new File(Configuration.getInstance().appRoot + "/conf/ResultSetViewersConf.xml"); //$NON-NLS-1$
                resultSetViewerConfiguration = new XMLResultSetViewerConfiguration(xml);
            }
            return resultSetViewerConfiguration;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void removeAllDockables() {

        List<DefaultSingleCDockable> docks = new ArrayList<>();
        docks.addAll(Arrays.asList(hitsDock, subitemDock, duplicateDock, parentDock, tableTabDock, 
                galleryTabDock, bookmarksTabDock, evidenceTabDock, metadataTabDock, categoriesTabDock));
        docks.addAll(viewerDocks);
        docks.addAll(rsTabDock);
        rsTabDock.clear();

        for (DefaultSingleCDockable dockable : docks) {
            if (dockable != null) {
                Component c = dockable.getContentPane().getComponent(0);
                dockable.remove(c);
                dockingControl.removeDockable(dockable);
            }
        }
    }

    private DefaultSingleCDockable createDockable(String id, String title, JComponent component) {
        DefaultSingleCDockable dockable = new DefaultSingleCDockable(id, title);
        dockable.setLayout(new BorderLayout());
        dockable.add(component, BorderLayout.CENTER);
        return dockable;
    }

    private boolean categoriesDefaultColor = true;
    private boolean metadataDefaultColor = true;
    private boolean evidenceDefaultColor = true;
    private boolean bookmarksDefaultColor = true;

    public void setCategoriesDefaultColor(boolean defaultColor) {
        if (categoriesDefaultColor != defaultColor) {
            categoriesDefaultColor = defaultColor;
            setDockablesColors();
        }
    }

    public void setMetadataDefaultColor(boolean defaultColor) {
        if (metadataDefaultColor != defaultColor) {
            metadataDefaultColor = defaultColor;
            setDockablesColors();
        }
    }

    public void setEvidenceDefaultColor(boolean defaultColor) {
        if (evidenceDefaultColor != defaultColor) {
            evidenceDefaultColor = defaultColor;
            setDockablesColors();
        }
    }

    public void setBookmarksDefaultColor(boolean defaultColor) {
        if (bookmarksDefaultColor != defaultColor) {
            bookmarksDefaultColor = defaultColor;
            setDockablesColors();
        }
    }

    private void setDockablesColors() {
        setTabColor(categoriesTabDock, categoriesDefaultColor);
        setTabColor(metadataTabDock, metadataDefaultColor);
        setTabColor(evidenceTabDock, evidenceDefaultColor);
        setTabColor(bookmarksTabDock, bookmarksDefaultColor);
    }

    private void setTabColor(DefaultSingleCDockable dock, boolean isDefault) {
        if (dock == null)
            return;
        if (isDefault) {
            setTabColor(dock, defaultColor, defaultFocusedColor, defaultSelectedColor);
        } else {
            setTabColor(dock, alertColor, alertFocusedColor, alertSelectedColor);
        }
    }

    private void setTabColor(DefaultSingleCDockable dock, Color colorBackground, Color colorFocused,
            Color colorSelected) {
        dock.getColors().setColor(ColorMap.COLOR_KEY_TAB_BACKGROUND, colorBackground);
        dock.getColors().setColor(ColorMap.COLOR_KEY_TAB_BACKGROUND_FOCUSED, colorFocused);
        dock.getColors().setColor(ColorMap.COLOR_KEY_TAB_BACKGROUND_SELECTED, colorSelected);
        dock.getColors().setColor(ColorMap.COLOR_KEY_MINIMIZED_BUTTON_BACKGROUND, colorBackground);
        dock.getColors().setColor(ColorMap.COLOR_KEY_MINIMIZED_BUTTON_BACKGROUND_FOCUSED, colorFocused);
        dock.getColors().setColor(ColorMap.COLOR_KEY_MINIMIZED_BUTTON_BACKGROUND_SELECTED, colorSelected);
        dock.getColors().setColor(ColorMap.COLOR_KEY_TITLE_BACKGROUND, colorBackground);
        dock.getColors().setColor(ColorMap.COLOR_KEY_TITLE_BACKGROUND_FOCUSED, colorFocused);
    }

    public void moveEvidenveTabToFront() {
        selectDockableTab(evidenceTabDock);
    }

    public void selectDockableTab(DefaultSingleCDockable dock) {
        Container cont = dock.getContentPane();
        if (cont != null) {
            Container parent;
            while ((parent = cont.getParent()) != null) {
                if (parent instanceof JTabbedPane) {
                    JTabbedPane tabbedPane = (JTabbedPane) parent;
                    Component selectedTab = tabbedPane.getSelectedComponent();
                    if (selectedTab != cont) {
                        tabbedPane.setSelectedComponent(cont);
                    }
                    break;
                } else if (parent instanceof EclipseTabPaneContent) {
                    EclipseTabPaneContent eclipseTPC = (EclipseTabPaneContent) parent;
                    EclipseTabPane eclipseTab = eclipseTPC.getPane();
                    eclipseTab.setSelectedDockable(dock.intern());
                }
                cont = parent;
            }
        }
    }

    private void zoomFont(Component c, int inc) {
        if (c instanceof Container) {
            Component[] childs = ((Container) c).getComponents();
            for (Component child : childs) {
                zoomFont(child, inc);
            }
        }
        int currSize = c.getFont().getSize();
        int newSize = currSize + inc;
        c.setFont(c.getFont().deriveFont((float) newSize));
        if (c instanceof JTable) {
            ((JTable) c).setRowHeight(newSize + 4);
        }
        revalidate();
        repaint();
    }

    public void refazLayout(boolean remove) {
        if (!disposicaoVertical) {
            if (remove)
                removeAllDockables();
            createAllDockables();

            tableTabDock.setLocation(CLocation.base().normalNorth(0.5));
            tableTabDock.setVisible(true);
            CLocation nextLocation = tableTabDock.getBaseLocation().aside();

            galleryTabDock.setLocation(nextLocation);
            galleryTabDock.setVisible(true);
            nextLocation = galleryTabDock.getBaseLocation().aside();

            for (Iterator<DefaultSingleCDockable> iterator = rsTabDock.iterator(); iterator.hasNext();) {
                DefaultSingleCDockable tabDock = iterator.next();
                tabDock.setLocation(nextLocation);
                tabDock.setVisible(true);
                nextLocation = tabDock.getBaseLocation().aside();
            }

            hitsDock.setLocation(CLocation.base().normalSouth(0.5).west(0.4));
            hitsDock.setVisible(true);
            nextLocation = hitsDock.getBaseLocation().aside();

            subitemDock.setLocation(nextLocation);
            subitemDock.setVisible(true);
            nextLocation = subitemDock.getBaseLocation().aside();

            parentDock.setLocation(nextLocation);
            parentDock.setVisible(true);
            nextLocation = parentDock.getBaseLocation().aside();

            duplicateDock.setLocation(nextLocation);
            duplicateDock.setVisible(true);

            for (int i = 0; i < viewerDocks.size(); i++) {
                DefaultSingleCDockable dock = viewerDocks.get(i);
                if (i == 0) {
                    dock.setLocation(CLocation.base().normalSouth(0.5).east(0.6));
                } else {
                    dock.setLocation(nextLocation);
                }
                nextLocation = dock.getBaseLocation().aside();
                dock.setVisible(true);
            }

            categoriesTabDock.setLocation(CLocation.base().normalWest(0.20).north(0.5));
            categoriesTabDock.setVisible(true);

            if (evidenceTabDock != null) {
                nextLocation = categoriesTabDock.getBaseLocation().aside();
                evidenceTabDock.setLocation(nextLocation);
                evidenceTabDock.setVisible(true);
            }

            bookmarksTabDock.setLocation(CLocation.base().normalWest(0.20).south(0.5));
            bookmarksTabDock.setVisible(true);

            nextLocation = bookmarksTabDock.getBaseLocation().aside();
            metadataTabDock.setLocation(nextLocation);
            metadataTabDock.setVisible(true);

            selectDockableTab(viewerDocks.get(viewerDocks.size() - 1));
            selectDockableTab(categoriesTabDock);
            selectDockableTab(bookmarksTabDock);
            selectDockableTab(tableTabDock);

        } else {
            if (remove)
                removeAllDockables();
            createAllDockables();

            tableTabDock.setLocation(CLocation.base().normalNorth(0.7));
            tableTabDock.setVisible(true);

            CLocation nextLocation = tableTabDock.getBaseLocation().aside();

            galleryTabDock.setLocation(nextLocation);
            galleryTabDock.setVisible(true);
            nextLocation = galleryTabDock.getBaseLocation().aside();

            for (Iterator<DefaultSingleCDockable> iterator = rsTabDock.iterator(); iterator.hasNext();) {
                DefaultSingleCDockable tabDock = iterator.next();
                tabDock.setLocation(nextLocation);
                tabDock.setVisible(true);
                nextLocation = tabDock.getBaseLocation().aside();
            }

            hitsDock.setLocation(CLocation.base().normalSouth(0.3));
            hitsDock.setVisible(true);
            nextLocation = hitsDock.getBaseLocation().aside();

            subitemDock.setLocation(nextLocation);
            subitemDock.setVisible(true);
            nextLocation = subitemDock.getBaseLocation().aside();

            parentDock.setLocation(nextLocation);
            parentDock.setVisible(true);
            nextLocation = parentDock.getBaseLocation().aside();

            duplicateDock.setLocation(nextLocation);
            duplicateDock.setVisible(true);

            categoriesTabDock.setLocation(CLocation.base().normalWest(0.20).north(0.5));
            categoriesTabDock.setVisible(true);

            if (evidenceTabDock != null) {
                nextLocation = categoriesTabDock.getBaseLocation().aside();
                evidenceTabDock.setLocation(nextLocation);
                evidenceTabDock.setVisible(true);
            }

            bookmarksTabDock.setLocation(CLocation.base().normalWest(0.20).south(0.5));
            bookmarksTabDock.setVisible(true);

            nextLocation = bookmarksTabDock.getBaseLocation().aside();
            metadataTabDock.setLocation(nextLocation);
            metadataTabDock.setVisible(true);

            for (int i = 0; i < viewerDocks.size(); i++) {
                DefaultSingleCDockable dock = viewerDocks.get(i);
                if (i == 0) {
                    dock.setLocation(CLocation.base().normalEast(0.35));
                } else {
                    dock.setLocation(nextLocation);
                }
                nextLocation = dock.getBaseLocation().aside();
                dock.setVisible(true);
            }

            selectDockableTab(viewerDocks.get(viewerDocks.size() - 1));
            selectDockableTab(categoriesTabDock);
            selectDockableTab(bookmarksTabDock);
            selectDockableTab(tableTabDock);
        }
        
        setupViewerDocks();
        viewerController.validateViewers();
        //TODO: Remove before final commit
        //viewerController.refreshOfficeViewer();
    }

    public void alterarDisposicao() {
        disposicaoVertical = !disposicaoVertical;
        refazLayout(true);
    }

    @Override
    public void windowActivated(WindowEvent e) {
        // TODO Auto-generated method stub

    }

    @Override
    public void windowClosed(WindowEvent e) {
        // TODO Auto-generated method stub

    }

    @Override
    public void windowClosing(WindowEvent e) {
        removeAllDockables();
        this.dispose();
        destroy();
    }

    @Override
    public void windowDeactivated(WindowEvent e) {
        // TODO Auto-generated method stub

    }

    @Override
    public void windowDeiconified(WindowEvent e) {
        // TODO Auto-generated method stub

    }

    @Override
    public void windowIconified(WindowEvent e) {
        // TODO Auto-generated method stub

    }

    @Override
    public void windowOpened(WindowEvent e) {
        // TODO Auto-generated method stub

    }

    public IMultiSearchResult getResults() {
        return ipedResult;
    }

    public JTable getResultsTable() {
        return resultsTable;
    }

    public TreeListener getTreeListener() {
        return treeListener;
    }

    @Override
    public String getSortColumn() {
        SortKey ordem = resultsTable.getRowSorter().getSortKeys().get(0);
        String coluna = ColumnsManager.getInstance().getLoadedCols()[ordem.getColumn() - 2];
        return coluna;
    }

    @Override
    public SortOrder getSortOrder() {
        SortKey ordem = resultsTable.getRowSorter().getSortKeys().get(0);
        return ordem.getSortOrder();
    }

    @Override
    public IIPEDSearcher createNewSearch(String query) {
        IIPEDSearcher searcher = new IPEDSearcher(appCase, query);
        return searcher;
    }

    @Override
    public IIPEDSource getIPEDSource() {
        return appCase;
    }

    @Override
    public FileDialog createFileDialog(String title, int mode) {
        return new FileDialog(this, title, mode);
    }

    @Override
    public ProgressDialog createProgressDialog(CancelableWorker task, boolean indeterminate, long millisToPopup,
            Dialog.ModalityType modal) {
        return new ProgressDialog(this, task, indeterminate, millisToPopup, modal);
    }

    @Override
    public IColumnsManager getColumnsManager() {
        return ColumnsManager.getInstance();
    }
}
