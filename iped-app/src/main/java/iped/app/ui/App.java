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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
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
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
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
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter.SortKey;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
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
import bibliothek.gui.dock.common.theme.ThemeMap;
import bibliothek.gui.dock.station.stack.tab.layouting.TabPlacement;
import bibliothek.gui.dock.themes.basic.action.BasicButtonHandler;
import bibliothek.gui.dock.themes.basic.action.BasicSelectableHandler;
import bibliothek.gui.dock.themes.basic.action.BasicTitleViewItem;
import iped.app.config.LogConfiguration;
import iped.app.config.XMLResultSetViewerConfiguration;
import iped.app.graph.AppGraphAnalytics;
import iped.app.ui.controls.CSelButton;
import iped.app.ui.controls.CustomButton;
import iped.app.ui.themes.ThemeManager;
import iped.app.ui.utils.PanelsLayout;
import iped.app.ui.viewers.TextViewer;
import iped.data.IIPEDSource;
import iped.data.IItem;
import iped.engine.Version;
import iped.engine.config.Configuration;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.LocaleConfig;
import iped.engine.core.Manager;
import iped.engine.data.IPEDMultiSource;
import iped.engine.data.IPEDSource;
import iped.engine.data.ItemId;
import iped.engine.search.IPEDSearcher;
import iped.engine.search.MultiSearchResult;
import iped.engine.task.ImageThumbTask;
import iped.engine.util.Util;
import iped.parsers.standard.StandardParser;
import iped.search.IIPEDSearcher;
import iped.search.IMultiSearchResult;
import iped.utils.IconUtil;
import iped.utils.UiUtil;
import iped.viewers.ATextViewer;
import iped.viewers.api.AbstractViewer;
import iped.viewers.api.GUIProvider;
import iped.viewers.api.IColumnsManager;
import iped.viewers.api.IMultiSearchResultProvider;
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
    //
    JButton blurButton;
    JSlider sliderBlur;
    JButton grayButton;
    //
    JCheckBox checkBox, recursiveTreeList, filterDuplicates;
    JTable resultsTable;
    GalleryTable gallery;
    public HitsTable hitsTable;
    AppGraphAnalytics appGraphAnalytics;

    HitsTable subItemTable, duplicatesTable, referencesTable;
    JTree tree, bookmarksTree, categoryTree;
    MetadataPanel metadataPanel;
    JScrollPane categoriesPanel, bookmarksPanel;
    JPanel evidencePanel;
    TreeListener treeListener;
    CategoryTreeListener categoryListener;
    BookmarksTreeListener bookmarksListener;
    TimelineListener timelineListener;
    HitsTable parentItemTable;
    public CControl dockingControl;
    DefaultSingleCDockable categoriesTabDock, metadataTabDock, bookmarksTabDock, evidenceTabDock;
    List<DefaultSingleCDockable> rsTabDock = new ArrayList<DefaultSingleCDockable>();

    DefaultSingleCDockable tableTabDock, galleryTabDock, graphDock;
    public DefaultSingleCDockable hitsDock, subitemDock, parentDock, duplicateDock, referencesDock;
    DefaultSingleCDockable compositeViewerDock;

    private List<DefaultSingleCDockable> viewerDocks;
    private ViewerController viewerController;
    private CButton timelineButton;
    private CButton butSimSearch;

    Color defaultColor;
    Color defaultFocusedColor;
    Color defaultSelectedColor;
    private JScrollPane hitsScroll, subItemScroll, parentItemScroll, duplicatesScroll, referencesScroll;
    JScrollPane viewerScroll, resultsScroll, galleryScroll;
    JPanel topPanel;
    ClearFilterButton clearAllFilters;
    boolean verticalLayout = false;

    public ResultTableModel resultsModel;
    List resultSortKeys;

    SubitemTableModel subItemModel = new SubitemTableModel();
    ParentTableModel parentItemModel = new ParentTableModel();
    DuplicatesTableModel duplicatesModel = new DuplicatesTableModel();
    ReferencesTableModel referencesModel = new ReferencesTableModel();

    GalleryModel galleryModel = new GalleryModel();

    Color alertColor = Color.RED;
    Color alertFocusedColor = Color.RED;
    Color alertSelectedColor = Color.RED;

    public SimilarImagesFilterPanel similarImageFilterPanel;
    public IItem similarImagesQueryRefItem;
    public List<? extends SortKey> similarImagesPrevSortKeys;

    public SimilarFacesFilterPanel similarFacesFilterPanel;
    public IItem similarFacesRefItem;
    public List<? extends SortKey> similarFacesPrevSortKeys;

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

    public boolean toggleBlurFilter = false;

    public boolean toggleGrayScaleFilter = false;

    public boolean useVideoThumbsInGallery = false;

    private IPEDSource lastSelectedSource;

    public int lastSelectedDoc = -1;

    private String codePath;

    private StandardParser autoDetectParser;

    private String fontStartTag = null;

    private Query query = null;

    private Set<String> highlightTerms = new HashSet<>();

    private App() {
    }

    public static final App get() {
        if (app == null) {
            app = new App();
        }
        return app;
    }

    public static final String getResPath(){
        return resPath;
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
        return new MenuClass();
    }

    public LogConfiguration getLogConfiguration() {
        return this.logConfiguration;
    }

    public String getFontStartTag() {
        return this.fontStartTag;
    }

    public void init(LogConfiguration logConfiguration, boolean isMultiCase, File casesPathFile,
            Manager processingManager, String codePath) {

        this.logConfiguration = logConfiguration;
        this.isMultiCase = isMultiCase;
        this.casesPathFile = casesPathFile;
        this.processingManager = processingManager;
        if (processingManager != null) {
            processingManager.setSearchAppOpen(true);
        }
        this.codePath = codePath;

        LOGGER = LoggerFactory.getLogger(App.class);
        LOGGER.info("Starting..."); //$NON-NLS-1$

        // Force initialization of ImageThumbTask to load external conversion configuration
        try {
            new ImageThumbTask().init(ConfigurationManager.get());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
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
                ColumnsManager.getInstance().dispose();
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
            System.exit(1);
        }

        String tab = "     "; //$NON-NLS-1$
        this.setTitle(Version.APP_NAME + tab + "[" + Messages.getString("App.Case") + ": " + casesPathFile + "]"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        this.setSize(new Dimension(800, 600));
        this.setExtendedState(Frame.MAXIMIZED_BOTH);
        this.addWindowListener(this);
        URL image = getClass().getResource("search.png"); //$NON-NLS-1$
        this.setIconImage(new ImageIcon(image).getImage());
        this.setVisible(true);
        ToolTipManager.sharedInstance().setInitialDelay(10);
        
        dockingControl = new CControl(this);

        // Set the locale used for docking frames, so texts and tool tips are localized (if available)
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
        //
        blurButton = new JButton(Messages.getString("App.ToggleBlurFilter"), IconUtil.getToolbarIcon("blur", resPath));
        blurButton.setMnemonic(KeyEvent.VK_B);
        sliderBlur = new JSlider(SwingConstants.HORIZONTAL, 0, 20, 14);
        sliderBlur.setMaximumSize(new Dimension(5, 16));
        sliderBlur.setMinimumSize(new Dimension(5, 16));
        sliderBlur.setOpaque(false);
        grayButton = new JButton(Messages.getString("App.ToggleGrayScaleFilter"), IconUtil.getToolbarIcon("gray", resPath));
        grayButton.setMnemonic(KeyEvent.VK_G);
        //
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
        //
        topPanel.add(blurButton);
        topPanel.add(sliderBlur);
        topPanel.add(grayButton);
        //
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

        int largeColWidth = 4096; 
        
        appGraphAnalytics = new AppGraphAnalytics();

        viewerController = new ViewerController();

        hitsTable = new HitsTable(new HitsTableModel(viewerController.getTextViewer()));
        hitsScroll = new JScrollPane(hitsTable);
        hitsTable.setFillsViewportHeight(true);
        hitsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        hitsTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        hitsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        hitsTable.getColumnModel().getColumn(1).setPreferredWidth(largeColWidth);
        hitsTable.getTableHeader().setPreferredSize(new Dimension(0, 0));
        hitsTable.setShowGrid(false);

        viewerController.setHitsTableInTextViewer(hitsTable);

        subItemTable = new HitsTable(subItemModel);
        subItemScroll = new JScrollPane(subItemTable);
        subItemTable.setFillsViewportHeight(true);
        subItemTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        subItemTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        subItemTable.getColumnModel().getColumn(1).setPreferredWidth(20);
        subItemTable.getColumnModel().getColumn(2).setPreferredWidth(largeColWidth);
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
        duplicatesTable.getColumnModel().getColumn(2).setPreferredWidth(largeColWidth);
        duplicatesTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        duplicatesTable.setDefaultRenderer(String.class, new TableCellRenderer());
        duplicatesTable.getTableHeader().setPreferredSize(new Dimension(0, 0));
        duplicatesTable.setShowGrid(false);

        parentItemTable = new HitsTable(parentItemModel);
        parentItemScroll = new JScrollPane(parentItemTable);
        parentItemTable.setFillsViewportHeight(true);
        parentItemTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        parentItemTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        parentItemTable.getColumnModel().getColumn(1).setPreferredWidth(20);
        parentItemTable.getColumnModel().getColumn(2).setPreferredWidth(largeColWidth);
        parentItemTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        parentItemTable.setDefaultRenderer(String.class, new TableCellRenderer());
        parentItemTable.getTableHeader().setPreferredSize(new Dimension(0, 0));
        parentItemTable.setShowGrid(false);

        referencesTable = new HitsTable(referencesModel);
        referencesTable.setFillsViewportHeight(true);
        referencesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        referencesTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        referencesTable.getColumnModel().getColumn(1).setPreferredWidth(20);
        referencesTable.getColumnModel().getColumn(2).setPreferredWidth(largeColWidth);
        referencesTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        referencesTable.setDefaultRenderer(String.class, new TableCellRenderer());
        referencesTable.getTableHeader().setPreferredSize(new Dimension(0, 0));
        referencesTable.setShowGrid(false);
        referencesScroll = new JScrollPane(referencesTable);

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
                        labelInsets = new Insets(labelInsets.top - 1, labelInsets.left - 3, labelInsets.bottom - 1,
                                labelInsets.right - 3);
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
        
        // Customize appearance of buttons and check boxes shown in docking frames title bar,
        // so focus is not painted (avoiding intersection with buttons icons) and more clear 
        // indication when a CCheckBox is selected. 
        dockingControl.getController().getActionViewConverter().putClient(ActionType.BUTTON, ViewTarget.TITLE,
                new ViewGenerator<ButtonDockAction, BasicTitleViewItem<JComponent>>() {
                    public BasicTitleViewItem<JComponent> create(ActionViewConverter converter, ButtonDockAction action,
                            Dockable dockable) {
                        BasicButtonHandler handler = new BasicButtonHandler(action, dockable);
                        CustomButton button = new CustomButton(handler, handler);
                        handler.setModel(button.getModel());
                        return handler;
                    }
                });
        dockingControl.getController().getActionViewConverter().putTheme(ActionType.CHECK, ViewTarget.TITLE,
                new ViewGenerator<SelectableDockAction, BasicTitleViewItem<JComponent>>() {
                    public BasicTitleViewItem<JComponent> create(ActionViewConverter converter,
                            SelectableDockAction action, Dockable dockable) {
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
        defaultSelectedColor = dockingControl.getController().getColors()
                .get(ColorMap.COLOR_KEY_TAB_BACKGROUND_SELECTED);

        timelineButton = new CButton(Messages.get("App.ToggleTimelineView"), IconUtil.getToolbarIcon("time", resPath));
        timelineListener = new TimelineListener(timelineButton, IconUtil.getToolbarIcon("timeon", resPath));
        timelineButton.addActionListener(timelineListener);

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

        dialogBar = new JDialog(SwingUtilities.windowForComponent(resultsTable), Dialog.ModalityType.MODELESS);
        dialogBar.setBounds(0, 0, 150, 30);
        dialogBar.setUndecorated(true);
        dialogBar.getContentPane().add(progressBar);

        adjustLayout(false);
        PanelsLayout.load(dockingControl);

        status = new JLabel(" "); //$NON-NLS-1$

        this.getContentPane().add(topPanel, BorderLayout.PAGE_START);
        // this.getContentPane().add(treeSplitPane, BorderLayout.CENTER);
        this.getContentPane().add(status, BorderLayout.PAGE_END);

        appletListener = new AppListener();
        recursiveTreeList.addActionListener(treeListener);
        queryComboBox.addActionListener(appletListener);
        filterComboBox.addActionListener(appletListener);
        filterDuplicates.addActionListener(appletListener);
        searchButton.addActionListener(appletListener);
        //
        blurButton.addActionListener(appletListener);
        sliderBlur.addChangeListener(appletListener);
        grayButton.addActionListener(appletListener);
        //
        optionsButton.addActionListener(appletListener);
        exportToZip.addActionListener(appletListener);
        updateCaseData.addActionListener(appletListener);
        helpButton.addActionListener(appletListener);
        checkBox.addActionListener(appletListener);
        resultsTable.getSelectionModel().addListSelectionListener(new ResultTableListener());
        resultsTable.addMouseListener(new ResultTableListener());
        resultsTable.addKeyListener(new ResultTableListener());

        clearAllFilters.addClearListener(categoryListener);
        clearAllFilters.addClearListener(bookmarksListener);
        clearAllFilters.addClearListener(treeListener);
        clearAllFilters.addClearListener(metadataPanel);
        clearAllFilters.addClearListener(appletListener);
        clearAllFilters.addClearListener(appGraphAnalytics);
        clearAllFilters.addClearListener(similarImageFilterPanel);
        clearAllFilters.addClearListener(similarFacesFilterPanel);
        clearAllFilters.addClearListener(timelineListener);

        hitsTable.getSelectionModel().addListSelectionListener(new HitsTableListener(TextViewer.font));
        subItemTable.addMouseListener(subItemModel);
        subItemTable.getSelectionModel().addListSelectionListener(subItemModel);
        parentItemTable.addMouseListener(parentItemModel);
        parentItemTable.getSelectionModel().addListSelectionListener(parentItemModel);
        duplicatesTable.addMouseListener(duplicatesModel);
        duplicatesTable.getSelectionModel().addListSelectionListener(duplicatesModel);
        referencesTable.addMouseListener(referencesModel);
        referencesTable.getSelectionModel().addListSelectionListener(referencesModel);

        hitsTable.addMouseListener(appletListener);
        // filterComboBox.addMouseListener(appletListener);
        // filterComboBox.getComponent(0).addMouseListener(appletListener);
        updateUI(false);
    }
    
    public void updateUI(boolean refresh) {
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

        /* CButton butToggleBlurFilter = new CButton(Messages.getString("App.ToggleBlurFilter"),
                IconUtil.getToolbarIcon("blur", resPath));
        galleryTabDock.addAction(butToggleBlurFilter);
        butToggleBlurFilter.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                galleryModel.clearAllThumbsInCache();
                toggleBlurFilter = !toggleBlurFilter;
                Icon activeIcon = IconUtil.getToolbarIcon("blur", resPath);
                if (toggleBlurFilter)
                    activeIcon = IconUtil.getToolbarIcon("bluron", resPath);    
                butToggleBlurFilter.setIcon(activeIcon);
                if (gallery != null)
                    gallery.repaint();
                if (viewerController != null){
                    //LOGGER.info("App INSIDE IF viewerController not null"); //$NON-NLS-1$
                    viewerController.setToggleBlurFilter(toggleBlurFilter);
                    viewerController.reload();
                }
            }
        });

        CButton butToggleGrayScaleFilter = new CButton(Messages.getString("App.ToggleGrayScaleFilter"),
                IconUtil.getToolbarIcon("gray", resPath));
        galleryTabDock.addAction(butToggleGrayScaleFilter);
        galleryTabDock.addSeparator();
        butToggleGrayScaleFilter.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                galleryModel.clearAllThumbsInCache();
                toggleGrayScaleFilter = !toggleGrayScaleFilter;
                Icon activeIcon = IconUtil.getToolbarIcon("gray", resPath);
                if (toggleGrayScaleFilter)
                    activeIcon = IconUtil.getToolbarIcon("grayon", resPath);    
                butToggleGrayScaleFilter.setIcon(activeIcon);
                if (gallery != null)
                    gallery.repaint();
                if (viewerController != null){
                    //LOGGER.info("App INSIDE IF viewerController not null"); //$NON-NLS-1$
                    viewerController.setToggleGrayScaleFilter(toggleGrayScaleFilter);
                    viewerController.reload();
                }
            }
        }); */

        CButton butToggleVideoFramesMode = new CButton(Messages.getString("Gallery.ToggleVideoFrames"),
                IconUtil.getToolbarIcon("video", resPath));
        galleryTabDock.addAction(butToggleVideoFramesMode);
        galleryTabDock.addSeparator();
        butToggleVideoFramesMode.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                galleryModel.clearVideoThumbsInCache();
                useVideoThumbsInGallery = !useVideoThumbsInGallery;
                gallery.repaint();
            }
        });

        butSimSearch = new CButton(Messages.getString("MenuClass.FindSimilarImages"),
                IconUtil.getToolbarIcon("find", resPath));
        galleryTabDock.addAction(butSimSearch);
        galleryTabDock.addSeparator();
        butSimSearch.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                SimilarImagesFilterActions.searchSimilarImages(false);
            }
        });
        butSimSearch.setEnabled(false);

        // Add buttons to control the thumbnails size / number of columns in the gallery
        CButton butDec = new CButton(Messages.getString("Gallery.DecreaseThumbsSize"),
                IconUtil.getToolbarIcon("minus", resPath));
        galleryTabDock.addAction(butDec);
        CButton butInc = new CButton(Messages.getString("Gallery.IncreaseThumbsSize"),
                IconUtil.getToolbarIcon("plus", resPath));
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
        referencesDock = createDockable("referencedbytab", Messages.getString("ReferencesTab.Title"),
                referencesScroll);

        dockingControl.addDockable(categoriesTabDock);
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

        List<AbstractViewer> viewers = viewerController.getViewers();
        viewerDocks = new ArrayList<DefaultSingleCDockable>();
        for (AbstractViewer viewer : viewers) {
            DefaultSingleCDockable viewerDock = createDockable(viewer.getClass().getName(), viewer.getName(),
                    viewer.getPanel());
            viewerDocks.add(viewerDock);
            dockingControl.addDockable(viewerDock);
            viewerController.put(viewer, viewerDock);
        }

        setDockablesColors();
    }

    private void setupViewerDocks() {
        CCheckBox chkFixed = new CCheckBox(Messages.getString("ViewerController.FixViewer"),
                IconUtil.getToolbarIcon("pin", resPath)) {
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
                            if ((oldLocation == null && newLocation != null)
                                    || (oldLocation != null && !oldLocation.equals(newLocation))) {
                                validated = viewerController.validateViewer(viewer);
                            }
                        }
                        if (!validated && event.isShowingChanged()) {
                            viewerController.updateViewer(viewer, false);
                        }
                    }
                }
            });

            CButton prevHit = new CButton(Messages.getString("ViewerController.PrevHit"),
                    IconUtil.getToolbarIcon("prev", resPath));
            CButton nextHit = new CButton(Messages.getString("ViewerController.NextHit"),
                    IconUtil.getToolbarIcon("next", resPath));
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
        docks.addAll(Arrays.asList(hitsDock, subitemDock, duplicateDock, parentDock, tableTabDock, galleryTabDock,
                bookmarksTabDock, evidenceTabDock, metadataTabDock, categoriesTabDock, graphDock, referencesDock));
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

    private void setDockablesColors() {
        setTabColor(categoriesTabDock, categoriesDefaultColor);
        setTabColor(metadataTabDock, metadataDefaultColor);
        setTabColor(evidenceTabDock, evidenceDefaultColor);
        setTabColor(bookmarksTabDock, bookmarksDefaultColor);
        setTabColor(graphDock, graphDefaultColor);
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

    public void savePanelLayout() {
        PanelsLayout.save(dockingControl);
    }

    public void loadPanelLayout() {
        PanelsLayout.load(dockingControl);
    }
    
    public void adjustLayout(boolean isReset) {
        if (!verticalLayout) {
            if (isReset)
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

            if (graphDock != null) {
                graphDock.setLocation(nextLocation);
                graphDock.setVisible(true);
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
            nextLocation = duplicateDock.getBaseLocation().aside();

            referencesDock.setLocation(nextLocation);
            referencesDock.setVisible(true);

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
            if (isReset)
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

            if (graphDock != null) {
                graphDock.setLocation(nextLocation);
                graphDock.setVisible(true);
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
            nextLocation = duplicateDock.getBaseLocation().aside();

            referencesDock.setLocation(nextLocation);
            referencesDock.setVisible(true);

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
        
        if (isReset)
            setGalleryColCount(GalleryModel.defaultColCount);
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
}
