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
package iped.app.ui;

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
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.RowSorter.SortKey;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.table.JTableHeader;
import javax.swing.text.JTextComponent;
import javax.swing.tree.TreePath;

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
import bibliothek.gui.dock.action.ActionType;
import bibliothek.gui.dock.action.ButtonDockAction;
import bibliothek.gui.dock.action.SelectableDockAction;
import bibliothek.gui.dock.action.view.ActionViewConverter;
import bibliothek.gui.dock.action.view.ViewGenerator;
import bibliothek.gui.dock.action.view.ViewTarget;
import bibliothek.gui.dock.common.CControl;
import bibliothek.gui.dock.common.CLocation;
import bibliothek.gui.dock.common.ColorMap;
import bibliothek.gui.dock.common.DefaultSingleCDockable;
import bibliothek.gui.dock.common.action.CButton;
import bibliothek.gui.dock.common.action.CCheckBox;
import bibliothek.gui.dock.common.event.CDockableLocationEvent;
import bibliothek.gui.dock.common.event.CDockableLocationListener;
import bibliothek.gui.dock.common.intern.CDockable;
import bibliothek.gui.dock.common.theme.ThemeMap;
import bibliothek.gui.dock.station.stack.tab.layouting.TabPlacement;
import bibliothek.gui.dock.themes.basic.action.BasicButtonHandler;
import bibliothek.gui.dock.themes.basic.action.BasicSelectableHandler;
import bibliothek.gui.dock.themes.basic.action.BasicTitleViewItem;
import iped.app.config.LogConfiguration;
import iped.app.config.XMLResultSetViewerConfiguration;
import iped.app.graph.AppGraphAnalytics;
import iped.app.graph.FilterSelectedEdges;
import iped.app.ui.bookmarks.BookmarkIcon;
import iped.app.ui.bookmarks.BookmarkTreeCellRenderer;
import iped.app.ui.columns.ColumnsManager;
import iped.app.ui.columns.ColumnsManagerUI;
import iped.app.ui.controls.CSelButton;
import iped.app.ui.controls.CustomButton;
import iped.app.ui.controls.table.FilterTableHeaderController;
import iped.app.ui.controls.table.FilterTableHeaderRenderer;
import iped.app.ui.themes.ThemeManager;
import iped.app.ui.utils.PanelsLayout;
import iped.app.ui.viewers.TextViewer;
import iped.data.IIPEDSource;
import iped.data.IItem;
import iped.data.IItemId;
import iped.engine.Version;
import iped.engine.config.Configuration;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.FileSystemConfig;
import iped.engine.config.LocaleConfig;
import iped.engine.core.Manager;
import iped.engine.data.Category;
import iped.engine.data.IPEDMultiSource;
import iped.engine.data.IPEDSource;
import iped.engine.data.ItemId;
import iped.engine.search.IPEDSearcher;
import iped.engine.search.ImageSimilarityLowScoreFilter;
import iped.engine.search.ImageSimilarityScorer;
import iped.engine.search.MultiSearchResult;
import iped.engine.search.QueryBuilder;
import iped.engine.search.SimilarDocumentSearch;
import iped.engine.search.SimilarFacesSearch;
import iped.engine.search.SimilarImagesSearch;
import iped.engine.task.ImageThumbTask;
import iped.engine.util.Util;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.parsers.standard.StandardParser;
import iped.search.IIPEDSearcher;
import iped.search.IMultiSearchResult;
import iped.utils.IconUtil;
import iped.utils.UiUtil;
import iped.viewers.ATextViewer;
import iped.viewers.api.AbstractViewer;
import iped.viewers.api.ClearFilterListener;
import iped.viewers.api.GUIProvider;
import iped.viewers.api.IColumnsManager;
import iped.viewers.api.IFilter;
import iped.viewers.api.IFilterer;
import iped.viewers.api.IItemRef;
import iped.viewers.api.IMiniaturizable;
import iped.viewers.api.IMultiSearchResultProvider;
import iped.viewers.api.IMutableFilter;
import iped.viewers.api.IQuantifiableFilter;
import iped.viewers.api.IQueryFilter;
import iped.viewers.api.IQueryFilterer;
import iped.viewers.api.IResultSetFilter;
import iped.viewers.api.IResultSetFilterer;
import iped.viewers.api.ResultSetViewer;
import iped.viewers.api.ResultSetViewerConfiguration;
import iped.viewers.components.HitsTable;
import iped.viewers.components.HitsTableModel;

public class App extends JFrame implements WindowListener, IMultiSearchResultProvider, GUIProvider {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    public static final boolean triageGui = System.getProperty("enableTriageGui") != null; //$NON-NLS-1$

    private static Logger LOGGER;

    private LogConfiguration logConfiguration;

    private Manager processingManager;

    IMultiSearchResult ipedResult = new MultiSearchResult(new ItemId[0], new float[0]);

    public IPEDMultiSource appCase;

    FilterManager filterManager;

    public JDialog dialogBar;
    JProgressBar progressBar;
    JComboBox<String> queryComboBox, filterComboBox;
    JButton searchButton, optionsButton, updateCaseData, helpButton, exportToZip;
    JCheckBox checkBox, recursiveTreeList, filterDuplicates;
    JTable resultsTable;
    ResultTableListener resultTableListener;
    GalleryTable gallery;
    public HitsTable hitsTable;
    AppGraphAnalytics appGraphAnalytics;

    HitsTable subItemTable, parentItemTable, duplicatesTable, referencesTable, referencedByTable;
    JTree tree, bookmarksTree, categoryTree;
    MetadataPanel metadataPanel;
    JScrollPane categoriesPanel, bookmarksPanel;
    JPanel evidencePanel;
    TreeListener treeListener;
    CategoryTreeListener categoryListener;
    BookmarksTreeListener bookmarksListener;
    TimelineListener timelineListener;
    public CControl dockingControl;
    private DefaultSingleCDockable categoriesTabDock, metadataTabDock, bookmarksTabDock, evidenceTabDock;
    private List<DefaultSingleCDockable> rsTabDock = new ArrayList<DefaultSingleCDockable>();

    private DefaultSingleCDockable tableTabDock, galleryTabDock, graphDock;
    public DefaultSingleCDockable hitsDock, subitemDock, parentDock, duplicateDock, referencesDock, referencedByDock;

    private List<DefaultSingleCDockable> viewerDocks;
    private ViewerController viewerController;
    private CCheckBox timelineButton;
    private CButton butSimSearch, butFaceSearch;
    private CCheckBox galleryGrayButton;
    private CCheckBox galleryBlurButton;

    Color defaultColor;
    Color defaultFocusedColor;
    Color defaultSelectedColor;
    private JScrollPane hitsScroll, subItemScroll, parentItemScroll, duplicatesScroll, referencesScroll, referencedByScroll;
    JScrollPane viewerScroll, resultsScroll, galleryScroll;
    JPanel topPanel;
    ClearFilterButton clearAllFilters;
    boolean verticalLayout = false;

    public ResultTableModel resultsModel;
    List<? extends SortKey> resultSortKeys;

    SubitemTableModel subItemModel = new SubitemTableModel();
    ParentTableModel parentItemModel = new ParentTableModel();
    DuplicatesTableModel duplicatesModel = new DuplicatesTableModel();
    ReferencingTableModel referencesModel = new ReferencingTableModel();
    ReferencedByTableModel referencedByModel = new ReferencedByTableModel();

    GalleryModel galleryModel = new GalleryModel();

    Color alertColor = Color.RED;
    Color alertFocusedColor = Color.RED;
    Color alertSelectedColor = Color.RED;

    public SimilarImagesFilterPanel similarImageFilterPanel;
    private IItem similarImagesQueryRefItem;
    public List<? extends SortKey> similarImagesPrevSortKeys;

    public SimilarFacesFilterPanel similarFacesFilterPanel;
    public IItem similarFacesRefItem;
    public List<? extends SortKey> similarFacesPrevSortKeys;
    SimilarDocumentFilterer similarDocumentFilterer;

    public File casesPathFile;
    boolean isMultiCase;
    public JLabel status;

    private static final String resPath = '/' + App.class.getPackageName().replace('.', '/') + '/';

    final static String FILTRO_TODOS = Messages.getString("App.NoFilter"); //$NON-NLS-1$
    final static String FILTRO_SELECTED = Messages.getString("App.Checked"); //$NON-NLS-1$
    public final static String SEARCH_TOOL_TIP = Messages.getString("App.SearchBoxTip"); //$NON-NLS-1$

    static int MAX_HITS = 10000;

    final static int FRAG_SIZE = 100, TEXT_BREAK_SIZE = 1000000;

    private static App app;

    AppListener appletListener;

    private ResultSetViewerConfiguration resultSetViewerConfiguration;

    public boolean useVideoThumbsInGallery = false;

    private IPEDSource lastSelectedSource;

    public int lastSelectedDoc = -1;

    private StandardParser autoDetectParser;

    private String fontStartTag = null;

    private Query query = null;

    private Set<String> highlightTerms = new HashSet<>();

    private SimilarImagesQueryFilterer similarImagesFilterer;

    private DuplicatesFilterer duplicatesFilterer;

    public FiltersPanel filtersPanel;

    private DefaultSingleCDockable filtersTabDock;

    public SimilarFacesSearchFilterer similarFacesSearchFilterer;

    private App() {
    }

    public static final App get() {
        if (app == null) {
            app = new App();
        }
        return app;
    }

    public void setLastSelectedDoc(int lastSelectedDoc) {
        this.lastSelectedDoc = lastSelectedDoc;
    }

    public int getLastSelectedDoc() {
        return this.lastSelectedDoc;
    }

    public void setHighlightTerms(Set<String> terms) {
        this.highlightTerms = terms;
    }

    public Set<String> getHighlightTerms() {
        return this.highlightTerms;
    }

    public AppListener getAppListener() {
        return appletListener;
    }

    public Manager getProcessingManager() {
        return processingManager;
    }

    public MenuClass getContextMenu() {
        IItemId id = resultTableListener.getSelectedItemId();
        IItem item = id == null ? null : appCase.getItemByItemId(id); 
        return new MenuClass(item);
    }

    public LogConfiguration getLogConfiguration() {
        return this.logConfiguration;
    }

    public String getFontStartTag() {
        return this.fontStartTag;
    }

    public void init(LogConfiguration logConfiguration, boolean isMultiCase, File casesPathFile, Manager processingManager, String codePath) {

        this.logConfiguration = logConfiguration;
        this.isMultiCase = isMultiCase;
        this.casesPathFile = casesPathFile;
        this.processingManager = processingManager;
        if (isMultiCase) {
            // Currently robust Image reading does not work with multicases.
            FileSystemConfig fsConfig = ConfigurationManager.get().findObject(FileSystemConfig.class);
            fsConfig.setRobustImageReading(false);
        }

        LOGGER = LoggerFactory.getLogger(App.class);
        LOGGER.info("Starting..."); //$NON-NLS-1$

        // Force initialization of ImageThumbTask to load external conversion
        // configuration
        try {
            new ImageThumbTask().init(ConfigurationManager.get());
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (SwingUtilities.isEventDispatchThread()) {
            createGUI();

        } else {
            try {
                SwingUtilities.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            createGUI();

                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }
                });
            } catch (InvocationTargetException | InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    public Query getQuery() {
        return this.query;
    }

    public void setQuery(Query query) {
        this.query = query;
    }

    public StandardParser getAutoParser() {
        return this.autoDetectParser;
    }

    public void setAutoParser(StandardParser autoParser) {
        this.autoDetectParser = autoParser;
    }

    public ATextViewer getTextViewer() {
        return viewerController.getTextViewer();
    }

    public ViewerController getViewerController() {
        return viewerController;
    }

    private void destroy() {
        boolean processingFinished = processingManager == null || processingManager.isProcessingFinished();
        try {
            if (viewerController != null) {
                viewerController.dispose();
            }
            if (this.resultsTable != null) {
                ColumnsManagerUI.getInstance().dispose();
            }

            appCase.close();

            if (processingFinished) {
                if (processingManager != null)
                    processingManager.deleteTempDir();
                if (logConfiguration != null)
                    logConfiguration.closeConsoleLogFile();

            } else {
                processingManager.setSearchAppOpen(false);
                app = null;
            }

            FileProcessor.disposeLastItem();

        } catch (Exception e) {
            e.printStackTrace();
        }
        if (processingFinished) {
            System.exit(0);
        }
    }

    public void createGUI() {

        if (!Util.isJavaFXPresent()) {
            JOptionPane.showMessageDialog(this, Messages.get("NoJavaFX.Error"), "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String tab = "     "; //$NON-NLS-1$
        this.setTitle(Version.APP_NAME + tab + "[" + Messages.getString("App.Case") + ": " + casesPathFile + "]"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        this.setSize(new Dimension(800, 600));
        this.setExtendedState(Frame.MAXIMIZED_BOTH);
        this.addWindowListener(this);
        this.setIconImages(IconUtil.getIconImages("search", "/iped/app/icon"));
        this.setVisible(true);
        if (processingManager != null) {
            processingManager.setSearchAppOpen(true);
        }

        ToolTipManager.sharedInstance().setInitialDelay(10);

        dockingControl = new CControl(this);

        // Set the locale used for docking frames, so texts and tool tips are localized
        // (if available)
        LocaleConfig localeConfig = ConfigurationManager.get().findObject(LocaleConfig.class);
        dockingControl.setLanguage(localeConfig.getLocale());

        // Set the locale used by JFileChooser's
        JFileChooser.setDefaultLocale(localeConfig.getLocale());

        try {
            ThemeManager.getInstance().setLookAndFeel();
        } catch (Exception e) {
            e.printStackTrace();
        }

        queryComboBox = new JComboBox<String>();
        queryComboBox.setMinimumSize(new Dimension());
        queryComboBox.setToolTipText(SEARCH_TOOL_TIP);
        queryComboBox.setEditable(true);
        queryComboBox.setSelectedItem(SEARCH_TOOL_TIP);
        queryComboBox.setMaximumRowCount(30);

        searchButton = new JButton(Messages.getString("App.Search")); //$NON-NLS-1$
        optionsButton = new JButton(Messages.getString("App.Options")); //$NON-NLS-1$
        updateCaseData = new JButton(Messages.getString("App.Update")); //$NON-NLS-1$
        helpButton = new JButton(Messages.getString("App.Help")); //$NON-NLS-1$
        exportToZip = new JButton(Messages.getString("App.ExportZip")); //$NON-NLS-1$
        checkBox = new JCheckBox("0"); //$NON-NLS-1$

        filterComboBox = new JComboBox<String>();
        filterComboBox.setMaximumSize(new Dimension(100, 50));
        filterComboBox.setMaximumRowCount(30);
        filterComboBox.addItem(App.FILTRO_TODOS);
        filterComboBox.setToolTipText(Messages.getString("App.FilterTip")); //$NON-NLS-1$
        filterManager = new FilterManager(filterComboBox);

        similarDocumentFilterer = new SimilarDocumentFilterer();

        similarImagesFilterer = new SimilarImagesQueryFilterer();

        similarFacesSearchFilterer = new SimilarFacesSearchFilterer();

        filterDuplicates = new JCheckBox(Messages.getString("App.FilterDuplicates"));
        filterDuplicates.setToolTipText(Messages.getString("App.FilterDuplicatesTip"));

        topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.LINE_AXIS));
        topPanel.setAlignmentX(LEFT_ALIGNMENT);

        clearAllFilters = new ClearFilterButton();
        clearAllFilters.setMaximumSize(new Dimension(100, 100));

        similarImageFilterPanel = new SimilarImagesFilterPanel();
        similarImageFilterPanel.setVisible(false);

        similarFacesFilterPanel = new SimilarFacesFilterPanel();
        similarFacesFilterPanel.setVisible(false);

        topPanel.add(filterComboBox);
        topPanel.add(filterDuplicates);
        topPanel.add(clearAllFilters);
        topPanel.add(similarImageFilterPanel);
        topPanel.add(similarFacesFilterPanel);
        topPanel.add(new JLabel(tab + Messages.getString("App.SearchLabel"))); //$NON-NLS-1$
        topPanel.add(queryComboBox);
        topPanel.add(optionsButton);
        if (processingManager != null)
            topPanel.add(updateCaseData);
        topPanel.add(helpButton);
        topPanel.add(exportToZip);
        exportToZip.setVisible(false);
        topPanel.add(checkBox);
        topPanel.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));

        resultsModel = new ResultTableModel();
        resultsTable = new JTable(resultsModel);
        resultTableListener = new ResultTableListener();
        resultsScroll = new JScrollPane(resultsTable);
        resultsTable.setFillsViewportHeight(true);
        resultsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        resultsTable.setDefaultRenderer(String.class, new TableCellRenderer());
        resultsTable.setShowGrid(false);
        resultsTable.setAutoscrolls(false);
        FilterTableHeaderController.init(resultsTable.getTableHeader());

        InputMap inputMap = resultsTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputMap.put(KeyStroke.getKeyStroke("SPACE"), "none"); //$NON-NLS-1$ //$NON-NLS-2$
        inputMap.put(KeyStroke.getKeyStroke("ctrl SPACE"), "none"); //$NON-NLS-1$ //$NON-NLS-2$

        gallery = new GalleryTable(galleryModel);
        galleryScroll = new JScrollPane(gallery);
        gallery.setFillsViewportHeight(true);
        gallery.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        gallery.setShowGrid(false);
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
                int colWidth = (int) gallery.getVisibleRect().getWidth() / getGalleryColCount();
                if (colWidth > 0) {
                    gallery.setRowHeight(colWidth);
                }
                int selRow = App.get().gallery.getSelectedRow();
                if (selRow >= 0) {
                    App.get().gallery.scrollRectToVisible(App.get().gallery.getCellRect(selRow, 0, false));
                }
            }
        });

        appGraphAnalytics = new AppGraphAnalytics();

        viewerController = new ViewerController();

        hitsTable = new HitsTable(new HitsTableModel(viewerController.getTextViewer()));
        hitsScroll = new JScrollPane(hitsTable);
        hitsTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        hitsTable.getColumnModel().getColumn(1).setPreferredWidth(4096);

        viewerController.setHitsTableInTextViewer(hitsTable);

        subItemTable = new HitsTable(subItemModel);
        subItemScroll = new JScrollPane(subItemTable);
        setupItemTable(subItemTable);

        duplicatesTable = new HitsTable(duplicatesModel);
        duplicatesScroll = new JScrollPane(duplicatesTable);
        setupItemTable(duplicatesTable);

        parentItemTable = new HitsTable(parentItemModel);
        parentItemScroll = new JScrollPane(parentItemTable);
        setupItemTable(parentItemTable);

        referencesTable = new HitsTable(referencesModel);
        referencesScroll = new JScrollPane(referencesTable);
        setupItemTable(referencesTable);

        referencedByTable = new HitsTable(referencedByModel);
        referencedByScroll = new JScrollPane(referencedByTable);
        setupItemTable(referencedByTable);

        categoryTree = new JTree(new Object[0]);
        categoryTree.setCellRenderer(new CategoryTreeCellRenderer());
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

        recursiveTreeList = new JCheckBox(Messages.getString("App.RecursiveListing")); //$NON-NLS-1$
        recursiveTreeList.setSelected(true);

        tree = new JTree(new Object[0]);
        tree.setRootVisible(true);
        tree.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        treeListener = new TreeListener();
        tree.addTreeSelectionListener(treeListener);
        tree.addTreeExpansionListener(treeListener);
        tree.addMouseListener(treeListener);

        evidencePanel = new JPanel(new BorderLayout());
        evidencePanel.add(recursiveTreeList, BorderLayout.NORTH);
        evidencePanel.add(new JScrollPane(tree), BorderLayout.CENTER);

        filtersPanel = new FiltersPanel();

        // treeTab.insertTab(Messages.getString("TreeViewModel.RootName"), null,
        // evidencePanel, null, 2);

        dockingControl.setTheme(ThemeMap.KEY_ECLIPSE_THEME);

        // This forces Eclipse theme to use rectangular tabs instead of curved ones, to
        // save horizontal space.
        dockingControl.putProperty(EclipseTheme.TAB_PAINTER, new TabPainter() {
            public Border getFullBorder(BorderedComponent owner, DockController controller, Dockable dockable) {
                return RectGradientPainter.FACTORY.getFullBorder(owner, controller, dockable);
            }

            public TabComponent createTabComponent(EclipseTabPane pane, Dockable dockable) {
                // return RectGradientPainter.FACTORY.createTabComponent(pane, dockable);
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

        // Customize appearance of buttons and check boxes shown in docking frames title
        // bar, so focus is not painted (avoiding intersection with buttons icons) and
        // more clear indication when a CCheckBox is selected.
        dockingControl.getController().getActionViewConverter().putClient(ActionType.BUTTON, ViewTarget.TITLE, new ViewGenerator<ButtonDockAction, BasicTitleViewItem<JComponent>>() {
            public BasicTitleViewItem<JComponent> create(ActionViewConverter converter, ButtonDockAction action, Dockable dockable) {
                BasicButtonHandler handler = new BasicButtonHandler(action, dockable);
                CustomButton button = new CustomButton(handler, handler);
                handler.setModel(button.getModel());
                return handler;
            }
        });
        dockingControl.getController().getActionViewConverter().putTheme(ActionType.CHECK, ViewTarget.TITLE, new ViewGenerator<SelectableDockAction, BasicTitleViewItem<JComponent>>() {
            public BasicTitleViewItem<JComponent> create(ActionViewConverter converter, SelectableDockAction action, Dockable dockable) {
                BasicSelectableHandler.Check handler = new BasicSelectableHandler.Check(action, dockable);
                CustomButton button = new CustomButton(handler, handler);
                handler.setModel(button.getModel());
                return handler;
            }
        });

        dockingControl.putProperty(StackDockStation.TAB_PLACEMENT, TabPlacement.TOP_OF_DOCKABLE);
        this.getContentPane().add(dockingControl.getContentArea(), BorderLayout.CENTER);
        defaultColor = dockingControl.getController().getColors().get(ColorMap.COLOR_KEY_TAB_BACKGROUND);
        defaultFocusedColor = dockingControl.getController().getColors().get(ColorMap.COLOR_KEY_TAB_BACKGROUND_FOCUSED);
        defaultSelectedColor = dockingControl.getController().getColors().get(ColorMap.COLOR_KEY_TAB_BACKGROUND_SELECTED);

        timelineButton = new CCheckBox(Messages.get("App.ToggleTimelineView"), IconUtil.getToolbarIcon("time", resPath)) {
            protected void changed() {
                if (timelineListener != null)
                    timelineListener.setTimelineTableView(isSelected());
            }
        };
        timelineListener = new TimelineListener(timelineButton, IconUtil.getToolbarIcon("timeon", resPath));

        if (triageGui) {
            verticalLayout = true;
            exportToZip.setVisible(true);
        }

        progressBar = new JProgressBar(0, 1);
        progressBar.setValue(0);
        progressBar.setString(Messages.getString("App.Wait")); //$NON-NLS-1$
        progressBar.setForeground(Color.WHITE);
        progressBar.setStringPainted(true);
        progressBar.setIndeterminate(true);

        dialogBar = new JDialog(this, Dialog.ModalityType.DOCUMENT_MODAL);
        dialogBar.setBounds(0, 0, 150, 30);
        dialogBar.setUndecorated(true);
        dialogBar.getContentPane().add(progressBar);

        adjustLayout(false);
        PanelsLayout.load(dockingControl);

        status = new JLabel(" "); //$NON-NLS-1$

        this.getContentPane().add(topPanel, BorderLayout.PAGE_START);
        this.getContentPane().add(status, BorderLayout.PAGE_END);

        appletListener = new AppListener();

        recursiveTreeList.addActionListener(treeListener);
        queryComboBox.addActionListener(appletListener);
        filterComboBox.addActionListener(appletListener);
        filterDuplicates.addActionListener(appletListener);
        searchButton.addActionListener(appletListener);
        optionsButton.addActionListener(appletListener);
        exportToZip.addActionListener(appletListener);
        updateCaseData.addActionListener(appletListener);
        helpButton.addActionListener(appletListener);
        checkBox.addActionListener(appletListener);
        resultsTable.getSelectionModel().addListSelectionListener(resultTableListener);
        resultsTable.addMouseListener(resultTableListener);
        resultsTable.addKeyListener(resultTableListener);

        duplicatesFilterer = new DuplicatesFilterer();

        filterManager.addQueryFilterer(new SearchFilterer());
        filterManager.addQueryFilterer(categoryListener);
        filterManager.addQueryFilterer(treeListener);
        filterManager.addQueryFilterer(similarImagesFilterer);
        filterManager.addQueryFilterer(similarDocumentFilterer);
        filterManager.addQueryFilterer(TableHeaderFilterManager.get());
        filterManager.addResultSetFilterer(bookmarksListener);
        filterManager.addResultSetFilterer(FilterSelectedEdges.getInstance());
        filterManager.addResultSetFilterer(duplicatesFilterer);
        filterManager.addResultSetFilterer(similarImagesFilterer);
        filterManager.addResultSetFilterer(similarFacesSearchFilterer);
        filterManager.addResultSetFilterer(timelineListener);
        filterManager.addResultSetFilterer(TableHeaderFilterManager.get());
        filterManager.addResultSetFilterer(metadataPanel);

        filterManager.getFilterers().stream().forEach(new Consumer<IFilterer>() {
            @Override
            public void accept(IFilterer filterer) {
                clearAllFilters.addClearListener(filterer);
            }
        });

        filtersPanel.install(filterManager);
        filtersPanel.updateUI();

        hitsTable.getSelectionModel().addListSelectionListener(new HitsTableListener(TextViewer.font));
        subItemTable.addMouseListener(subItemModel);
        subItemTable.getSelectionModel().addListSelectionListener(subItemModel);
        parentItemTable.addMouseListener(parentItemModel);
        parentItemTable.getSelectionModel().addListSelectionListener(parentItemModel);
        duplicatesTable.addMouseListener(duplicatesModel);
        duplicatesTable.getSelectionModel().addListSelectionListener(duplicatesModel);
        referencesTable.addMouseListener(referencesModel);
        referencesTable.getSelectionModel().addListSelectionListener(referencesModel);
        referencedByTable.addMouseListener(referencedByModel);
        referencedByTable.getSelectionModel().addListSelectionListener(referencedByModel);

        hitsTable.addMouseListener(appletListener);
        updateUI(false);
        updateIconContainersUI(IconManager.getIconSize(), false);

        setupKeyboardShortcuts();

        LOGGER.info("UI created"); //$NON-NLS-1$
    }

    private void setupItemTable(HitsTable itemTable) {
        itemTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        itemTable.getColumnModel().getColumn(1).setPreferredWidth(18);
        itemTable.getColumnModel().getColumn(3).setPreferredWidth(4096);
        itemTable.setDefaultRenderer(String.class, new TableCellRenderer());
        itemTable.addKeyListener(new SpaceKeyListener());
    }

    /**
     * Setup application global keyboard shortcuts. TODO: Check if other existing
     * keyboard shortcuts may be handled globally.
     */
    private void setupKeyboardShortcuts() {
        KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        kfm.addKeyEventDispatcher(new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.isControlDown()) {
                    if (e.getKeyCode() == KeyEvent.VK_Q) {
                        if (e.getID() == KeyEvent.KEY_RELEASED) {
                            toggleGlobalBlurFilter();
                        }
                        // avoid being used as different shortcut (e.g. bookmark key)
                        return true;
                    }
                    if (e.getKeyCode() == KeyEvent.VK_W) {
                        if (e.getID() == KeyEvent.KEY_RELEASED) {
                            toggleGlobalGrayScale();
                        }
                        // avoid being used as different shortcut (e.g. bookmark key)
                        return true;
                    }
                    if (e.getKeyCode() == KeyEvent.VK_B) {
                        if (e.getID() == KeyEvent.KEY_RELEASED) {
                            // Shortcut to BookmarkManager Window
                            BookmarksManager.setVisible();
                        }
                        return true;
                    }
                }
                return false;
            }
        });
    }

    protected void toggleGlobalBlurFilter() {
        boolean enableBlur = !galleryModel.getBlurFilter();
        galleryModel.setBlurFilter(enableBlur);
        galleryBlurButton.setSelected(enableBlur);
        gallery.repaint();
        viewerController.getMultiViewer().setBlurFilter(enableBlur);
    }

    protected void toggleGlobalGrayScale() {
        boolean enableGray = !galleryModel.getGrayFilter();
        galleryModel.setGrayFilter(enableGray);
        galleryGrayButton.setSelected(enableGray);
        gallery.repaint();
        viewerController.getMultiViewer().setGrayFilter(enableGray);
    }

    public void updateUI(boolean refresh) {
        JTableHeader header = resultsTable.getTableHeader();
        FilterTableHeaderRenderer renderer = new FilterTableHeaderRenderer(header);
        header.setDefaultRenderer(renderer);
        FilterTableHeaderController.setRenderer(header, renderer);

        queryComboBox.getEditor().getEditorComponent().addMouseListener(appletListener);
        queryComboBox.getComponent(0).addMouseListener(appletListener);
        new AutoCompleteColumns((JTextComponent) queryComboBox.getEditor().getEditorComponent());

        Color foreground = UIManager.getColor("Viewer.foreground"); //$NON-NLS-1$
        if (foreground == null)
            fontStartTag = null;
        else
            fontStartTag = "<font color=" + UiUtil.getHexRGB(foreground) + ">"; //$NON-NLS-1$ //$NON-NLS-2$

        if (refresh) {
            if (gallery != null) {
                ((GalleryCellEditor) gallery.getDefaultEditor(GalleryCellRenderer.class)).updateUI();
                ((GalleryCellRenderer) gallery.getDefaultRenderer(GalleryCellRenderer.class)).updateUI();
                gallery.repaint();
            }
            if (viewerController != null)
                viewerController.reload();
        }
    }

    private void createAllDockables() {
        categoriesTabDock = createDockable("categoriestab", Messages.getString("CategoryTreeModel.RootName"), //$NON-NLS-1$ //$NON-NLS-2$
                categoriesPanel);
        filtersTabDock = createDockable("filterstab", Messages.getString("App.appliedFilters"), //$NON-NLS-1$ //$NON-NLS-2$
                filtersPanel);
        metadataTabDock = createDockable("metadatatab", Messages.getString("App.Metadata"), metadataPanel); //$NON-NLS-1$ //$NON-NLS-2$
        if (evidencePanel != null) {
            evidenceTabDock = createDockable("evidencetab", Messages.getString("TreeViewModel.RootName"), //$NON-NLS-1$ //$NON-NLS-2$
                    evidencePanel);
        }
        bookmarksTabDock = createDockable("bookmarkstab", Messages.getString("BookmarksTreeModel.RootName"), //$NON-NLS-1$ //$NON-NLS-2$
                bookmarksPanel);

        tableTabDock = createDockable("tabletab", Messages.getString("App.Table"), resultsScroll); //$NON-NLS-1$ //$NON-NLS-2$
        tableTabDock.addAction(timelineButton);
        tableTabDock.addSeparator();

        galleryTabDock = createDockable("galleryscroll", Messages.getString("App.Gallery"), galleryScroll); //$NON-NLS-1$ //$NON-NLS-2$

        graphDock = createDockable("graphtab", Messages.getString("App.Links"), appGraphAnalytics);

        CCheckBox butToggleVideoFramesMode = new CCheckBox(Messages.getString("Gallery.ToggleVideoFrames"), IconUtil.getToolbarIcon("video", resPath)) {
            protected void changed() {
                galleryModel.clearVideoThumbsInCache();
                useVideoThumbsInGallery = isSelected();
                gallery.repaint();
            }
        };
        galleryTabDock.addAction(butToggleVideoFramesMode);
        galleryTabDock.addSeparator();

        galleryGrayButton = new CCheckBox(Messages.getString("Gallery.GalleryGrayFilter"), IconUtil.getToolbarIcon("gray-scale", resPath)) {
            protected void changed() {
                galleryModel.setGrayFilter(isSelected());
                gallery.repaint();
            }
        };
        galleryTabDock.addAction(galleryGrayButton);

        galleryBlurButton = new CCheckBox(Messages.getString("Gallery.GalleryBlurFilter"), IconUtil.getToolbarIcon("blur-image", resPath)) {
            protected void changed() {
                galleryModel.setBlurFilter(isSelected());
                gallery.repaint();
            }
        };
        galleryTabDock.addAction(galleryBlurButton);
        galleryTabDock.addSeparator();

        butSimSearch = new CButton(Messages.getString("MenuClass.FindSimilarImages"), IconUtil.getToolbarIcon("find", resPath));
        galleryTabDock.addAction(butSimSearch);
        butSimSearch.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                SimilarImagesFilterActions.searchSimilarImages(false);
            }
        });
        butSimSearch.setEnabled(false);

        butFaceSearch = new CButton(Messages.getString("MenuClass.FindSimilarFaces"), IconUtil.getToolbarIcon("face", resPath));
        galleryTabDock.addAction(butFaceSearch);
        butFaceSearch.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                SimilarFacesFilterActions.searchSimilarFaces(false);
            }
        });
        butFaceSearch.setEnabled(false);
        
        galleryTabDock.addSeparator();

        // Add buttons to control the thumbnails size / number of columns in the gallery
        CButton butDec = new CButton(Messages.getString("Gallery.DecreaseThumbsSize"), IconUtil.getToolbarIcon("minus", resPath));
        galleryTabDock.addAction(butDec);
        CButton butInc = new CButton(Messages.getString("Gallery.IncreaseThumbsSize"), IconUtil.getToolbarIcon("plus", resPath));
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
            DefaultSingleCDockable tabDock = createDockable(resultSetViewer.getID(), resultSetViewer.getTitle(), resultSetViewer.getPanel());

            resultSetViewer.setDockableContainer(tabDock);

            if (resultSetViewer instanceof ClearFilterListener) {
                clearAllFilters.addClearListener((ClearFilterListener) resultSetViewer);
            }

            if (resultSetViewer instanceof IQueryFilterer) {
                filterManager.addQueryFilterer((IQueryFilterer) resultSetViewer);
            }

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
        referencesDock = createDockable("referencestab", Messages.getString("ReferencesTab.Title"), referencesScroll);
        referencedByDock = createDockable("referencedbytab", Messages.getString("ReferencedByTab.Title"), referencedByScroll);

        dockingControl.addDockable(categoriesTabDock);
        dockingControl.addDockable(filtersTabDock);
        dockingControl.addDockable(metadataTabDock);
        if (evidenceTabDock != null) {
            dockingControl.addDockable(evidenceTabDock);
        }
        dockingControl.addDockable(bookmarksTabDock);
        dockingControl.addDockable(tableTabDock);
        dockingControl.addDockable(galleryTabDock);
        if (graphDock != null) {
            dockingControl.addDockable(graphDock);
        }

        for (Iterator<DefaultSingleCDockable> iterator = rsTabDock.iterator(); iterator.hasNext();) {
            DefaultSingleCDockable tabDock = iterator.next();
            dockingControl.addDockable(tabDock);
        }

        dockingControl.addDockable(hitsDock);
        dockingControl.addDockable(subitemDock);
        dockingControl.addDockable(duplicateDock);
        dockingControl.addDockable(parentDock);
        dockingControl.addDockable(referencesDock);
        dockingControl.addDockable(referencedByDock);

        List<AbstractViewer> viewers = viewerController.getViewers();
        viewerDocks = new ArrayList<DefaultSingleCDockable>();
        for (AbstractViewer viewer : viewers) {
            DefaultSingleCDockable viewerDock = createDockable(viewer.getClass().getName(), viewer.getName(), viewer.getPanel());
            viewerDocks.add(viewerDock);
            dockingControl.addDockable(viewerDock);
            viewerController.put(viewer, viewerDock);
        }

        setDockablesColors();
    }

    private void setupViewerDocks() {
        CCheckBox chkFixed = new CCheckBox(Messages.getString("ViewerController.FixViewer"), IconUtil.getToolbarIcon("pin", resPath)) {
            protected void changed() {
                viewerController.setFixed(isSelected());
            }
        };
        List<AbstractViewer> viewers = viewerController.getViewers();
        for (int i = 0; i < viewers.size(); i++) {
            AbstractViewer viewer = viewers.get(i);
            DefaultSingleCDockable viewerDock = viewerDocks.get(i);
            viewerDock.addCDockableLocationListener(new CDockableLocationListener() {
                public void changed(CDockableLocationEvent event) {
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
                            viewerController.updateViewer(viewer, false, true);
                        }
                    }
                }
            });

            if (viewer == App.get().getViewerController().getMultiViewer()) {
                CButton copyViewerImage = new CButton(Messages.getString("MenuClass.CopyViewerImage"),
                        IconUtil.getToolbarIcon("copy", resPath));
                copyViewerImage.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        AbstractViewer viewer = App.get().getViewerController().getMultiViewer().getCurrentViewer();
                        viewer.copyScreen();
                    }
                });
                viewerDock.addAction(copyViewerImage);
            }

            CButton prevHit = new CButton(Messages.getString("ViewerController.PrevHit"), IconUtil.getToolbarIcon("prev", resPath));
            CButton nextHit = new CButton(Messages.getString("ViewerController.NextHit"), IconUtil.getToolbarIcon("next", resPath));
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
                Icon downIcon = IconUtil.getToolbarIcon("down", resPath);
                Icon upIcon = IconUtil.getToolbarIcon("up", resPath);
                CSelButton butToolbar = new CSelButton(Messages.getString("ViewerController.ShowToolBar"), upIcon, downIcon);
                butToolbar.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        butToolbar.toggle();
                        viewer.setToolbarVisible(butToolbar.isSelected());
                    }
                });
                viewerDock.addAction(butToolbar);
                viewerDock.putAction("toolbar", butToolbar);
            }

            viewerDock.addSeparator();
        }
    }

    public int getGalleryColCount() {
        return galleryModel.getColumnCount();
    }

    public void setGalleryColCount(int cnt) {
        if (cnt > 0 && cnt <= 40) {
            galleryModel.setColumnCount(cnt);
            int colWidth = gallery.getWidth() / cnt;
            if (colWidth > 0)
                gallery.setRowHeight(colWidth);
            int selRow = resultsTable.getSelectedRow();
            galleryModel.fireTableStructureChanged();
            if (selRow >= 0) {
                int galleryRow = selRow / cnt;
                int galleyCol = selRow % cnt;
                gallery.getSelectionModel().setSelectionInterval(galleryRow, galleryRow);
                gallery.getColumnModel().getSelectionModel().setSelectionInterval(galleyCol, galleyCol);
            }
        }
    }

    private void updateGalleryColCount(int inc) {
        setGalleryColCount(getGalleryColCount() + inc);
    }

    public void sendCheckAllToResultSetViewers(boolean checked) {
        for (ResultSetViewer setViewer : resultSetViewerConfiguration.getResultSetViewers()) {
            setViewer.checkAll(checked);
        }
    }

    public void notifyCaseDataChanged() {
        for (ResultSetViewer setViewer : resultSetViewerConfiguration.getResultSetViewers()) {
            setViewer.notifyCaseDataChanged();
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
        docks.addAll(Arrays.asList(hitsDock, subitemDock, duplicateDock, parentDock, tableTabDock, galleryTabDock, bookmarksTabDock, evidenceTabDock, metadataTabDock, categoriesTabDock, graphDock, referencesDock, referencedByDock,
                filtersTabDock));
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
    private boolean graphDefaultColor = true;
    private boolean tableDefaultColor = true;

    public void setTableDefaultColor(boolean defaultColor) {
        if (tableDefaultColor != defaultColor) {
            tableDefaultColor = defaultColor;
            setDockablesColors();
        }
    }

    public void setGraphDefaultColor(boolean defaultColor) {
        if (graphDefaultColor != defaultColor) {
            graphDefaultColor = defaultColor;
            setDockablesColors();
        }
    }

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

    public void setDockablesColors() {
        for (int i = 0; i < dockingControl.getCDockableCount(); i++) {
            DefaultSingleCDockable tabDock = (DefaultSingleCDockable) dockingControl.getCDockable(i);
            Component c = tabDock.getContentPane().getComponent(0);
            if (c instanceof IQueryFilterer) {
                setTabColor(tabDock, !((IQueryFilterer) c).hasFiltersApplied());
            }
        }

        setTabColor(categoriesTabDock, categoriesDefaultColor);
        setTabColor(metadataTabDock, metadataDefaultColor);
        setTabColor(evidenceTabDock, evidenceDefaultColor);
        setTabColor(bookmarksTabDock, bookmarksDefaultColor);
        setTabColor(graphDock, graphDefaultColor);
        setTabColor(tableTabDock, tableDefaultColor);
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

    private void setTabColor(DefaultSingleCDockable dock, Color colorBackground, Color colorFocused, Color colorSelected) {
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

    public void savePanelLayout() {
        PanelsLayout.save(dockingControl);
    }

    public void loadPanelLayout() {
        PanelsLayout.load(dockingControl);
    }

    public void adjustLayout(boolean isReset) {
        if (isReset)
            removeAllDockables();
        createAllDockables();

        tableTabDock.setLocation(verticalLayout ? CLocation.base().normalNorth(0.7) : CLocation.base().normalNorth(0.5));
        tableTabDock.setVisible(true);

        galleryTabDock.setLocationsAside(tableTabDock);
        galleryTabDock.setVisible(true);

        CDockable prevDock = galleryTabDock;
        for (Iterator<DefaultSingleCDockable> iterator = rsTabDock.iterator(); iterator.hasNext();) {
            DefaultSingleCDockable tabDock = iterator.next();
            tabDock.setLocationsAside(prevDock);
            tabDock.setVisible(true);
            prevDock = tabDock;
        }

        if (graphDock != null) {
            graphDock.setLocationsAside(prevDock);
            graphDock.setVisible(true);
        }

        hitsDock.setLocation(verticalLayout ? CLocation.base().normalSouth(0.3) : CLocation.base().normalSouth(0.5).west(0.4));
        hitsDock.setVisible(true);

        subitemDock.setLocationsAside(hitsDock);
        subitemDock.setVisible(true);

        parentDock.setLocationsAside(subitemDock);
        parentDock.setVisible(true);

        duplicateDock.setLocationsAside(parentDock);
        duplicateDock.setVisible(true);

        referencesDock.setLocationsAside(duplicateDock);
        referencesDock.setVisible(true);

        referencedByDock.setLocationsAside(referencesDock);
        referencedByDock.setVisible(true);

        if (!verticalLayout)
            adjustViewerLayout();

        categoriesTabDock.setLocation(CLocation.base().normalWest(0.20).north(0.5));
        categoriesTabDock.setVisible(true);

        if (evidenceTabDock != null) {
            evidenceTabDock.setLocationsAside(categoriesTabDock);
            evidenceTabDock.setVisible(true);
        }

        if (filtersTabDock != null) {
            filtersTabDock.setLocationsAside(evidenceTabDock);
            filtersTabDock.setVisible(true);
        }

        bookmarksTabDock.setLocation(CLocation.base().normalWest(0.20).south(0.5));
        bookmarksTabDock.setVisible(true);

        metadataTabDock.setLocationsAside(bookmarksTabDock);
        metadataTabDock.setVisible(true);

        if (verticalLayout)
            adjustViewerLayout();

        selectDockableTab(viewerDocks.get(viewerDocks.size() - 1));
        selectDockableTab(categoriesTabDock);
        selectDockableTab(bookmarksTabDock);
        selectDockableTab(tableTabDock);

        setupViewerDocks();
        viewerController.validateViewers();

        if (isReset)
            setGalleryColCount(GalleryModel.defaultColCount);
    }

    private void adjustViewerLayout() {
        DefaultSingleCDockable prevDock = viewerDocks.get(0);
        prevDock.setLocation(verticalLayout ? CLocation.base().normalEast(0.35) : CLocation.base().normalSouth(0.5).east(0.6));
        prevDock.setVisible(true);
        for (int i = 1; i < viewerDocks.size(); i++) {
            DefaultSingleCDockable dock = viewerDocks.get(i);
            dock.setLocationsAside(prevDock);
            dock.setVisible(true);
            prevDock = dock;
        }
    }

    public void toggleHorizontalVerticalLayout() {
        verticalLayout = !verticalLayout;
        adjustLayout(true);
    }

    @Override
    public void windowActivated(WindowEvent e) {
    }

    @Override
    public void windowClosed(WindowEvent e) {
    }

    @Override
    public void windowClosing(WindowEvent e) {
        removeAllDockables();
        this.dispose();
        destroy();
    }

    @Override
    public void windowDeactivated(WindowEvent e) {
    }

    @Override
    public void windowDeiconified(WindowEvent e) {
    }

    @Override
    public void windowIconified(WindowEvent e) {
    }

    @Override
    public void windowOpened(WindowEvent e) {
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

    public void updateIconContainersUI(int size, boolean updateUI) {
        updateIconContainerUI(tree, size, updateUI);
        updateIconContainerUI(bookmarksTree, size, updateUI);

        updateIconContainerUI(resultsTable, size, updateUI);
        updateIconContainerUI(gallery, size, updateUI);

        updateIconContainerUI(subItemTable, size, updateUI);
        updateIconContainerUI(parentItemTable, size, updateUI);
        updateIconContainerUI(duplicatesTable, size, updateUI);
        updateIconContainerUI(referencesTable, size, updateUI);
        updateIconContainerUI(referencedByTable, size, updateUI);
    }

    private void updateIconContainerUI(JComponent comp, int size, boolean updateUI) {
        JTable table = null;
        if (comp instanceof JTable && comp != gallery) {
            table = (JTable) comp;
            table.setRowHeight(size);

            // Set bookmark icons column width based on current icon size
            for (int i = 0; i < table.getColumnCount(); i++) {
                if (table.getColumnName(i).equals(BookmarkIcon.columnName)) {
                    table.getColumnModel().getColumn(i).setPreferredWidth(size + 4);
                }
            }
        }
        if (updateUI) {
            comp.updateUI();
        }
        if (table != null) {
            // Fix the background of boolean columns (with a CheckBox)
            ((JComponent) table.getDefaultRenderer(Boolean.class)).setOpaque(true);
        }
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
    public IIPEDSource getIPEDSource() {
        return appCase;
    }

    @Override
    public FileDialog createFileDialog(String title, int mode) {
        return new FileDialog(this, title, mode);
    }

    @Override
    public IColumnsManager getColumnsManager() {
        return ColumnsManager.getInstance();
    }

    public void setLastSelectedSource(IPEDSource lastSelectedSource) {
        this.lastSelectedSource = lastSelectedSource;
    }

    public IPEDSource getLastSelectedSource() {
        return this.lastSelectedSource;
    }

    public void setEnableGallerySimSearchButton(boolean enabled) {
        this.butSimSearch.setEnabled(enabled);
    }

    public void setEnableGalleryFaceSearchButton(boolean enabled) {
        this.butFaceSearch.setEnabled(enabled);
    }

    public List<ResultSetViewer> getResultSetViewers() {
        return getResultSetViewerConfiguration().getResultSetViewers();
    }

    @Override
    public Set<String> getSelectedBookmarks() {
        return bookmarksListener.getSelectedBookmarkNames();
    }

    @Override
    public Set<String> getSelectedCategories() {
        HashSet<TreePath> paths = categoryListener.getSelection();
        HashSet<String> result = new HashSet<String>();
        for (TreePath path : paths) {
            Category category = (Category) path.getLastPathComponent();
            result.add(category.getName());
        }
        return result;
    }

    @Override
    public IIPEDSearcher createNewSearch(String query) {
        CaseSearcherFilter csf = new CaseSearcherFilter(query);
        csf.applyUIQueryFilters();
        return csf.getSearcher();
    }

    @Override
    public IIPEDSearcher createNewSearch(String query, boolean applyFilters) {
        CaseSearcherFilter csf = new CaseSearcherFilter(query);
        if (applyFilters) {
            csf.applyUIQueryFilters();
        }
        return csf.getSearcher();
    }

    @Override
    public IIPEDSearcher createNewSearch(String query, String[] sortFields) {
        IIPEDSearcher searcher = null;
        if (sortFields == null) {
            searcher = new IPEDSearcher(appCase, query);
        } else {
            searcher = new IPEDSearcher(appCase, query, sortFields);
        }
        return searcher;
    }

    // TODO implement this for HTML views with checkboxes (Map and HtmlLinkViewer).
    // May need to call javascript functions to update the DOM internal checkboxes.
    public void repaintAllTableViews() {
        resultsTable.repaint();
        gallery.repaint();
        subItemTable.repaint();
        parentItemTable.repaint();
        duplicatesTable.repaint();
        referencesTable.repaint();
        referencedByTable.repaint();
    }

    private class SpaceKeyListener extends KeyAdapter {

        @Override
        public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                JTable table = (JTable) e.getComponent();
                int col = table.convertColumnIndexToView(1);
                int firstRow = table.getSelectedRow();
                boolean value = true;
                if (firstRow != -1 && (Boolean) table.getValueAt(firstRow, col)) {
                    value = false;
                }
                int[] selectedRows = table.getSelectedRows();
                BookmarksController.get().setMultiSetting(true);
                for (int i = 0; i < selectedRows.length; i++) {
                    table.setValueAt(value, selectedRows[i], col);
                }
                table.repaint();
                BookmarksController.get().setMultiSetting(false);
                BookmarksController.get().updateUISelection();
                appCase.getMultiBookmarks().saveState();
            }
        }
    }

    public FilterManager getFilterManager() {
        return filterManager;
    }

    class SimilarImageFilter implements IQueryFilter, IResultSetFilter, IMiniaturizable, IItemRef {
        IItem refItem;
        IItemId refItemId;
        private String refName;
        private BufferedImage img;
        private Query query;

        public SimilarImageFilter(IItemId itemId, IItem similarImagesQueryRefItem) {
            this.refItem = similarImagesQueryRefItem;
            this.refItemId = itemId;
            if (refItem != null) {
                this.query = new SimilarImagesSearch().getQueryForSimilarImages(refItem);

                byte[] thumb = refItem.getThumb();
                if (thumb != null) {
                    refName = refItem.getName();
                    ByteArrayInputStream bais = new ByteArrayInputStream(thumb);
                    try {
                        img = ImageIO.read(bais);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

        }

        @Override
        public IMultiSearchResult filterResult(IMultiSearchResult src) throws ParseException, QueryNodeException, IOException {
            IMultiSearchResult result = src;
            if (refItem != null) {
                LOGGER.info("Starting similar image search...");
                long t = System.currentTimeMillis();
                new ImageSimilarityScorer(App.get().appCase, (MultiSearchResult) result, refItem).score();
                result = ImageSimilarityLowScoreFilter.filter(result);
                t = System.currentTimeMillis() - t;
                LOGGER.info("Similar image search took {}ms to find {} images", t, result.getLength());
            }
            return result;
        }

        public String toString() {
            return Messages.get("FilterValue.SimilarImage") + " " + refName;
        }

        @Override
        public BufferedImage getThumb() {
            return img;
        }

        @Override
        public Query getQuery() {
            return query;
        }

        @Override
        public IItem getItemRef() {
            return refItem;
        }

        @Override
        public IItemId getItemRefId() {
            return refItemId;
        }
    }

    class SimilarImagesQueryFilterer implements IQueryFilterer, IResultSetFilterer {
        private IItem item;
        SimilarImageFilter imageFilter;

        @Override
        public List getDefinedFilters() {
            ArrayList<IQueryFilter> result = new ArrayList<>();
            if (item != null) {
                result.add(imageFilter);
            }
            return result;
        }

        @Override
        public boolean hasFiltersApplied() {
            return item != null;
        }

        @Override
        public Query getQuery() {
            if (item != null) {
                return new SimilarImagesSearch().getQueryForSimilarImages(item);
            }
            return null;
        }

        @Override
        public IFilter getFilter() {
            if (item != null) {
                return imageFilter;
            }
            return null;
        }

        @Override
        public boolean hasFilters() {
            return item != null;
        }

        @Override
        public void clearFilter() {
            item = null;
            similarImageFilterPanel.clearFilter();
        }

        public void setItem(IItemId itemId, IItem similarImagesFilterer) {
            item = similarImagesFilterer;
            imageFilter = new SimilarImageFilter(itemId, item);
        }
    };

    class DuplicateFilter implements IResultSetFilter, IMutableFilter {
        public String toString() {
            return Messages.get("FilterValue.Duplicates");
        }

        @Override
        public IMultiSearchResult filterResult(IMultiSearchResult src) throws ParseException, QueryNodeException, IOException {
            IMultiSearchResult result = src;
            DynamicDuplicateFilter duplicateFilter = new DynamicDuplicateFilter(App.get().appCase);
            result = duplicateFilter.filter(src);
            return result;
        }
    }

    IResultSetFilter duplicateFilter = new DuplicateFilter();

    class DuplicatesFilterer implements IResultSetFilterer {
        @Override
        public List getDefinedFilters() {
            ArrayList<IFilter> result = new ArrayList<IFilter>();
            if (filterDuplicates.isSelected()) {
                result.add(duplicateFilter);
            }
            return result;
        }

        @Override
        public IFilter getFilter() {
            if (filterDuplicates.isSelected()) {
                return duplicateFilter;
            } else {
                return null;
            }
        }

        @Override
        public boolean hasFilters() {
            return filterDuplicates.isSelected();
        }

        @Override
        public boolean hasFiltersApplied() {
            return filterDuplicates.isSelected();
        }

        @Override
        public void clearFilter() {
            appletListener.clearAllFilters = true;
            if (filterDuplicates.isSelected())
                filterDuplicates.doClick();
            appletListener.clearAllFilters = false;
        }
    }

    class SimilarFacesSearchFilter implements IResultSetFilter, IMiniaturizable, IItemRef, IQuantifiableFilter {
        IItem itemRef;
        IItemId itemIdRef;
        private String refName;
        private BufferedImage img;
        private SimilarFacesSearch sfs;

        public SimilarFacesSearchFilter(IItemId itemIdRef, IItem similarFacesRefItem) {
            this.itemRef = similarFacesRefItem;
            this.itemIdRef = itemIdRef;
            sfs = new SimilarFacesSearch(appCase, itemRef);
            if (itemRef != null) {
                refName = itemRef.getName();
                if (itemRef.getThumb() != null) {
                    try {
                        img = ImageIO.read(new ByteArrayInputStream(itemRef.getThumb()));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        @Override
        public IMultiSearchResult filterResult(IMultiSearchResult src) throws ParseException, QueryNodeException, IOException {
            if (itemRef != null) {
                return sfs.filter((MultiSearchResult) src);
            }
            return src;
        }

        public String toString() {
            return Messages.get("FilterValue.SimilarFace") + " " + refName;
        }

        @Override
        public BufferedImage getThumb() {
            return img;
        }

        @Override
        public IItem getItemRef() {
            return itemRef;
        }

        @Override
        public IItemId getItemRefId() {
            return itemIdRef;
        }

        @Override
        public int getQuantityValue() {
            return SimilarFacesSearch.getMinScore();
        }

        @Override
        public void setQuantityValue(int value) {
            SimilarFacesSearch.setMinScore(value);
        }
    };

    class SimilarFacesSearchFilterer implements IResultSetFilterer {
        SimilarFacesSearchFilter filter = null;
        IItem itemRef;

        @Override
        public List getDefinedFilters() {
            ArrayList<IFilter> result = new ArrayList<IFilter>();
            if (similarFacesRefItem != null) {
                result.add(filter);
            }
            return result;
        }

        public void setItem(IItemId itemId, IItem item) {
            this.itemRef = item;
            filter = new SimilarFacesSearchFilter(itemId, itemRef);
        }

        @Override
        public IFilter getFilter() {
            if (itemRef != null) {
                return filter;
            }
            return null;
        }

        @Override
        public boolean hasFilters() {
            return itemRef != null;
        }

        @Override
        public boolean hasFiltersApplied() {
            return itemRef != null;
        }

        @Override
        public void clearFilter() {
            itemRef = null;
            SimilarFacesFilterActions.clear(false);
        }

    }

    class SearchFilterer implements IQueryFilterer {
        @Override
        public List<IFilter> getDefinedFilters() {
            ArrayList<IFilter> result = new ArrayList<IFilter>();
            String filterText = (String) queryComboBox.getSelectedItem();
            Query query;
            try {
                query = new QueryBuilder(appCase).getQuery(filterText);
                result.add(new IQueryFilter() {
                    String title = filterText;

                    @Override
                    public Query getQuery() {
                        return query;
                    }

                    public String toString() {
                        return title;
                    }
                });
            } catch (ParseException | QueryNodeException e) {
                e.printStackTrace();
            }
            return result;
        }

        @Override
        public boolean hasFilters() {
            if (App.get().queryComboBox.getSelectedItem() != null) {
                String searchText = App.get().queryComboBox.getSelectedItem().toString();
                if (searchText.equals(BookmarksController.HISTORY_DIV) || searchText.equals(App.SEARCH_TOOL_TIP)) {
                    return false;
                }
                if ("".equals(searchText) || "*".equals(searchText) || "*:*".equals(searchText)) {
                    return false;
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean hasFiltersApplied() {
            return false;
        }

        @Override
        public Query getQuery() {
            return null;
        }

        @Override
        public void clearFilter() {
            appletListener.clearAllFilters = true;
            queryComboBox.setSelectedItem(""); //$NON-NLS-1$
            appletListener.clearAllFilters = false;
        }

    }

    public IItem getSimilarImagesQueryRefItem() {
        return similarImagesQueryRefItem;
    }

    public void setSimilarImagesQueryRefItem(IItemId itemId, IItem similarImagesQueryRefItem) {
        this.similarImagesQueryRefItem = similarImagesQueryRefItem;
        similarImagesFilterer.setItem(itemId, similarImagesQueryRefItem);
    }

    class SimilarDocumentFilter implements IQueryFilter, IItemRef, IQuantifiableFilter {
        private IItem itemRef;
        private IItemId itemRefId;
        String refName;
        private int filterPercent;
        Query query;

        public SimilarDocumentFilter(IItemId itemId, IItem item, int percent) {
            this.itemRef = item;
            this.itemRefId = itemId;
            this.filterPercent = percent;
        }

        @Override
        public IItem getItemRef() {
            return itemRef;
        }

        @Override
        public IItemId getItemRefId() {
            return itemRefId;
        }

        @Override
        public Query getQuery() {
            if (query == null) {
                query = new SimilarDocumentSearch().getQueryForSimilarDocs(itemRefId, filterPercent, App.get().appCase);
            }
            return query;
        }

        public IItem getItem() {
            return itemRef;
        }

        public void setItemRef(IItem item) {
            this.itemRef = item;
        }

        public void setItemRef(IItemId itemId) {
            this.itemRefId = itemId;
        }

        public int getPercent() {
            return filterPercent;
        }

        public void setPercent(int percent) {
            this.filterPercent = percent;
        }

        public String toString() {
            return Messages.get("FilterValue.SimilarDocument") + " (" + Integer.toString(filterPercent) + "%): " + itemRef.getName();
        }

        @Override
        public int getQuantityValue() {
            return filterPercent;
        }

        @Override
        public void setQuantityValue(int value) {
            filterPercent = value;
            query = null;
        }
    }

    class SimilarDocumentFilterer implements IQueryFilterer {

        private IItem item;
        private IItemId itemId;
        private int percent;
        private SimilarDocumentFilter filter;

        @Override
        public List<IFilter> getDefinedFilters() {
            if (filter != null) {
                ArrayList<IFilter> result = new ArrayList<>();
                result.add(filter);
                return result;
            } else {
                return null;
            }
        }

        @Override
        public boolean hasFilters() {
            return filter != null;
        }

        @Override
        public boolean hasFiltersApplied() {
            return false;
        }

        @Override
        public void clearFilter() {
            filter = null;
            item = null;
            itemId = null;
        }

        @Override
        public Query getQuery() {
            if (itemId != null && item != null) {
                if (filter == null) {
                    filter = new SimilarDocumentFilter(itemId, item, percent);
                }
                return filter.getQuery();
            }
            return null;
        }

        public IItem getItem() {
            return item;
        }

        public void setItem(IItemId itemId, IItem item) {
            this.item = item;
            this.itemId = itemId;
            filter = null;
        }

        public int getPercent() {
            return percent;
        }

        public void setPercent(int percent) {
            this.percent = percent;
            filter = null;
        }
    }

    public AppListener getAppletListener() {
        return appletListener;
    }
}
