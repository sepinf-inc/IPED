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
package dpf.sp.gpinf.indexer.search;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

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
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.plaf.metal.MetalTabbedPaneUI;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.Versao;
import dpf.sp.gpinf.indexer.ui.fileViewer.control.IViewerControl;
import dpf.sp.gpinf.indexer.ui.fileViewer.control.ViewerControl;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.CompositeViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.HitsTable;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.TextViewer;
import dpf.sp.gpinf.indexer.util.Util;
import dpf.sp.gpinf.indexer.util.VersionsMap;

import dpf.sp.gpinf.indexer.ui.fileViewer.util.AppSearchParams;

public class App extends JFrame implements WindowListener {

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  private static Logger LOGGER = LoggerFactory.getLogger(App.class);

  SleuthkitCase sleuthCase;

  private AppSearchParams appSearchParams = null;

  SearchResult results = new SearchResult(0);

  int totalItens, lastSelectedDoc;
  public int lastId;
  public Marcadores marcadores;
  FilterManager filterManager;
  ArrayList<String> palavrasChave, categorias;
  HashSet<String> keywordSet = new HashSet<String>();
  Set<String> highlightTerms = new HashSet<String>();

  Set<Integer> splitedDocs;
  VersionsMap viewToRawMap;

  public volatile IndexReader reader;
  public volatile IndexSearcher searcher;
  public ExecutorService searchExecutorService;

  public JDialog dialogBar;
  JProgressBar progressBar;
  JComboBox<String> termo, filtro;
  JButton pesquisar, opcoes;
  JButton ajuda;
  JCheckBox checkBox, recursiveTreeList;
  JTable resultsTable;
  GalleryTable gallery;
  public HitsTable hitsTable;

  HitsTable subItemTable;
  JTree tree, bookmarksTree, categoryTree;
  TreeListener treeListener;
  CategoryTreeListener categoryListener;
  BookmarksTreeListener bookmarksListener;
  HitsTable parentItemTable;
  JSplitPane verticalSplitPane, horizontalSplitPane, treeSplitPane;

  IViewerControl viewerControl = ViewerControl.getInstance();
  public CompositeViewer compositeViewer;

  public JTabbedPane tabbedHits, resultTab, treeTab;
  Color defaultTabColor;
  JScrollPane subItemScroll, parentItemScroll, viewerScroll, resultsScroll, galleryScroll;
  MenuClass menu;
  JPanel topPanel;
  JPanel multiFilterAlert;
  boolean disposicaoVertical = false;
  boolean isFTKReport;

  ResultTableModel resultsModel;
  List resultSortKeys;
  SubitemTableModel subItemModel = new SubitemTableModel();
  ParentTableModel parentItemModel = new ParentTableModel();
  GalleryModel galleryModel = new GalleryModel();

  Color alertColor = Color.RED;//new Color(0xFFFF5050);

  private int zoomLevel;

  public JLabel status;

  // final String MSG_NO_PREVIEW =
  // "NÃ£o foi possÃ­vel visualizar o texto. Clique duas vezes sobre o arquivo para acessar o original!";
  // final String MSG_NO_HITS =
  // "NÃ£o foi possÃ­vel destacar as ocorrÃªncias. Clique duas vezes sobre o arquivo para acessar o original!";
  final static String FILTRO_TODOS = "[Sem Filtro]";
  final static String FILTRO_SELECTED = "[Selecionados]";
  final static String SEARCH_TOOL_TIP = "[Digite ou escolha a expressÃ£o a ser pesquisada]";

  public static int MAX_LINE_SIZE = 100; // tamanho de quebra do texto para highlight

  static int MAX_HITS = 10000;

  public static int MAX_LINES = 100000;

  static int FRAG_SIZE = 100, TEXT_BREAK_SIZE = 1000000;

  private static App applet;

  public String codePath;
  AppListener appletListener;

  private App() {
    this.appSearchParams = new AppSearchParams();
    this.appSearchParams.mainFrame = (JFrame) this;
    this.appSearchParams.viewerControl = ViewerControl.getInstance();    
    this.appSearchParams.HIGHLIGHT_START_TAG = "<font color=\"black\" bgcolor=\"yellow\">";
    this.appSearchParams.HIGHLIGHT_END_TAG = "</font>";
    this.appSearchParams.TEXT_BREAK_SIZE = TEXT_BREAK_SIZE;
    this.appSearchParams.FRAG_SIZE = FRAG_SIZE;
    this.appSearchParams.MAX_LINES = MAX_LINES;
    this.appSearchParams.MAX_HITS = MAX_HITS;
    this.appSearchParams.MAX_LINE_SIZE = MAX_LINE_SIZE;
  }

  public static final App get() {
    if (applet == null) {
      applet = new App();
    }

    return applet;
  }

  public static void main(String[] args) {
    App.get().init();
  }

  public void init() {

    LOGGER.info("init");

    applet = this;

    try {
      URL url = this.getClass().getProtectionDomain().getCodeSource().getLocation();
      codePath = new File(url.toURI()).getAbsolutePath().replace("\\", "/");

      //codePath = "E:/Imagens/18101.11/Pendrive/indexador/lib/Search.htm";
      //codePath = "E:\\Imagens\\material_3106_2012\\indexador/lib/Search.htm";
      //codePath = "E:/Casos/Teste/LAUDO 2191.11/indexador/lib/Search.htm";
      //codePath = "E:/1-1973/indexador/lib/search.jar";
      codePath = "E:/iped/index/indexador/lib/iped-utils-0.5.jar";
      codePath = codePath.substring(0, codePath.lastIndexOf('/'));
      appSearchParams.codePath = codePath;

      javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
        @Override
        public void run() {
          createGUI();
          LOGGER.info("GUI created");
        }
      });

    } catch (Exception e) {
      e.printStackTrace();
    }

    (new InicializarBusca(appSearchParams)).execute();

  }

  public AppSearchParams getParams() {
    return this.appSearchParams;
  }

  public Analyzer getAnalyzer() {
    return this.appSearchParams.analyzer;
  }

  public void setAnalyzer(Analyzer analyzer) {
    this.appSearchParams.analyzer = analyzer;
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

  public TextViewer getTextViewer() {
    return (TextViewer) this.appSearchParams.textViewer;
  }

  public void setTextViewer(TextViewer textViewer) {
    this.appSearchParams.textViewer = textViewer;
  }

  public int[] getTextSizes() {
    return this.appSearchParams.textSizes;
  }

  public void setTextSizes(int[] sizes) {
    this.appSearchParams.textSizes = sizes;
  }

  public int[] getIDs() {
    return this.appSearchParams.ids;
  }

  public void setIDs(int[] ids) {
    this.appSearchParams.ids = ids;
  }

  public int[] getDocs() {
    return this.appSearchParams.docs;
  }

  public void setDocs(int[] docs) {
    this.appSearchParams.docs = docs;
  }

  public void destroy() {
    try {
      if (this.resultsTable != null) {
        ColumnsManager.getInstance().saveColumnsState();
      }
      reader.close();
      if (compositeViewer != null) {
        compositeViewer.dispose();
      }
      if (searchExecutorService != null) {
        searchExecutorService.shutdown();
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void createGUI() {

    String tab = "     ";
    this.setTitle(Versao.APP_NAME + tab + "[Caso: " + new File(codePath).getParentFile().getParent() + "]");
    this.setSize(new Dimension(800, 600));
    this.setExtendedState(Frame.MAXIMIZED_BOTH);
    this.addWindowListener(this);
    URL image = getClass().getResource("search.png");
    this.setIconImage(new ImageIcon(image).getImage());
    this.setVisible(true);
    ToolTipManager.sharedInstance().setInitialDelay(10);

    treeTab = new JTabbedPane();
    treeTab.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
    //Possibilita pintar aba selecionada
    treeTab.setUI(new MetalTabbedPaneUI() {
      protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex,
          int x, int y, int w, int h, boolean isSelected) {
        if (tabPane.getBackgroundAt(tabIndex) == alertColor) {
          isSelected = false;
        }
        super.paintTabBackground(g, tabPlacement, tabIndex, x, y, w, h, isSelected);
      }
    });

    try {
      boolean nimbusFound = false;
      for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
        if ("Nimbus".equals(info.getName())) {
          UIManager.put("nimbusOrange", new Color(47, 92, 180));
          UIManager.put("nimbusRed", Color.BLUE);
          UIManager.setLookAndFeel(info.getClassName());
          UIDefaults defaults = UIManager.getLookAndFeel().getDefaults();
          defaults.put("ScrollBar.thumbHeight", 12);
          //Workaround JDK-8134828
          defaults.put("ScrollBar.minimumThumbSize", new Dimension(30, 30));
          nimbusFound = true;
          break;
        }
      }
      if (!nimbusFound) {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      }
    } catch (Exception e) {
    }

    try {
      palavrasChave = Util.loadKeywords(codePath + "/../palavras-chave.txt", "UTF-8");
    } catch (IOException e) {
      palavrasChave = new ArrayList<String>();
    }
    for (String keyword : palavrasChave) {
      keywordSet.add(keyword);
    }

    termo = new JComboBox<String>(palavrasChave.toArray(new String[0]));
    termo.setMinimumSize(new Dimension());
    termo.setToolTipText(SEARCH_TOOL_TIP);
    termo.setEditable(true);
    termo.setSelectedItem(SEARCH_TOOL_TIP);
    termo.setMaximumRowCount(30);

    pesquisar = new JButton("Pesquisar");
    opcoes = new JButton("Opções");
    ajuda = new JButton("Ajuda");
    checkBox = new JCheckBox("0");

    try {
      categorias = Util.loadKeywords(codePath + "/../categorias.txt", "UTF-8");
    } catch (IOException e) {
      categorias = new ArrayList<String>();
    }

    filtro = new JComboBox<String>();
    filtro.setMaximumSize(new Dimension(100, 50));
    filtro.setMaximumRowCount(30);
    filtro.setToolTipText("Filtro");
    filterManager = new FilterManager(filtro);
    filterManager.loadFilters();

    topPanel = new JPanel();
    topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.LINE_AXIS));
    topPanel.setAlignmentX(LEFT_ALIGNMENT);

    multiFilterAlert = new JPanel();
    JLabel alertLabel = new JLabel("Múltiplos Filtros Ativos");
    multiFilterAlert.add(alertLabel);
    alertLabel.setBackground(alertColor);
    alertLabel.setOpaque(true);
    multiFilterAlert.setMaximumSize(new Dimension(100, 50));
    alertLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1, true));
    multiFilterAlert.setVisible(false);

    topPanel.add(filtro);
    topPanel.add(multiFilterAlert);
    topPanel.add(new JLabel("    Pesquisar:"));
    topPanel.add(termo);
    topPanel.add(opcoes);
    topPanel.add(ajuda);
    topPanel.add(checkBox);
    topPanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

    resultsModel = new ResultTableModel();
    resultsTable = new JTable(resultsModel);
    resultsScroll = new JScrollPane(resultsTable);
    resultsTable.setFillsViewportHeight(true);
    resultsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    resultsTable.setDefaultRenderer(String.class, new TableCellRenderer());
    resultsTable.setShowGrid(false);
    resultsTable.setAutoscrolls(false);
    InputMap inputMap = resultsTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    inputMap.put(KeyStroke.getKeyStroke("SPACE"), "none");
    inputMap.put(KeyStroke.getKeyStroke("ctrl SPACE"), "none");

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
    inputMap.put(KeyStroke.getKeyStroke("SPACE"), "none");
    inputMap.put(KeyStroke.getKeyStroke("ctrl SPACE"), "none");

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

    resultTab = new JTabbedPane();
    resultTab.addTab("Tabela", resultsScroll);
    resultTab.addTab("Galeria", galleryScroll);

    hitsTable = new HitsTable(appSearchParams.hitsModel);
    appSearchParams.hitsTable = hitsTable;
    JScrollPane hitsScroll = new JScrollPane(hitsTable);
    hitsTable.setFillsViewportHeight(true);
    hitsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    hitsTable.getColumnModel().getColumn(0).setPreferredWidth(50);
    hitsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    hitsTable.getColumnModel().getColumn(1).setPreferredWidth(1500);
    hitsTable.getTableHeader().setPreferredSize(new Dimension(0, 0));
    hitsTable.setShowGrid(false);

    subItemTable = new HitsTable(subItemModel);
    subItemScroll = new JScrollPane(subItemTable);
    subItemTable.setFillsViewportHeight(true);
    subItemTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    subItemTable.getColumnModel().getColumn(0).setPreferredWidth(50);
    subItemTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    subItemTable.getColumnModel().getColumn(1).setPreferredWidth(1500);
    subItemTable.getTableHeader().setPreferredSize(new Dimension(0, 0));
    subItemTable.setShowGrid(false);

    parentItemTable = new HitsTable(parentItemModel);
    parentItemScroll = new JScrollPane(parentItemTable);
    parentItemTable.setFillsViewportHeight(true);
    parentItemTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    parentItemTable.getColumnModel().getColumn(0).setPreferredWidth(50);
    parentItemTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    parentItemTable.getColumnModel().getColumn(1).setPreferredWidth(1500);
    parentItemTable.getTableHeader().setPreferredSize(new Dimension(0, 0));
    parentItemTable.setShowGrid(false);

    tabbedHits = new JTabbedPane();
    tabbedHits.addTab("Ocorrências", hitsScroll);
    appSearchParams.tabbedHits = tabbedHits;

    compositeViewer = new CompositeViewer();
    appSearchParams.compositeViewer = compositeViewer;

    horizontalSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tabbedHits, compositeViewer);
    horizontalSplitPane.setDividerSize(5);
    horizontalSplitPane.setOneTouchExpandable(true);
    horizontalSplitPane.setContinuousLayout(true);
    horizontalSplitPane.setResizeWeight(0.4);

    verticalSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, resultTab, horizontalSplitPane);
    verticalSplitPane.setDividerSize(5);
    verticalSplitPane.setOneTouchExpandable(true);
    verticalSplitPane.setContinuousLayout(true);
    verticalSplitPane.setResizeWeight(0.5);

    treeSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeTab, verticalSplitPane);
    treeSplitPane.setOneTouchExpandable(true);
    treeSplitPane.setDividerSize(5);
    treeSplitPane.setContinuousLayout(true);
    treeSplitPane.setResizeWeight(0.1);

    categoryTree = new JTree(new CategoryTreeModel());
    categoryTree.setExpandsSelectedPaths(false);
    categoryListener = new CategoryTreeListener();
    categoryTree.addTreeSelectionListener(categoryListener);
    categoryTree.addTreeExpansionListener(categoryListener);

    bookmarksTree = new JTree(new BookmarksTreeModel());
    bookmarksListener = new BookmarksTreeListener();
    bookmarksTree.addTreeSelectionListener(bookmarksListener);
    bookmarksTree.addTreeExpansionListener(bookmarksListener);
    bookmarksTree.setExpandsSelectedPaths(false);

    treeTab.add("Categorias", new JScrollPane(categoryTree));
    treeTab.add("Marcadores", new JScrollPane(bookmarksTree));
    defaultTabColor = treeTab.getBackgroundAt(0);

    isFTKReport = new File(new File(codePath).getParent(), "data/containsFTKReport.flag").exists();

    if (!isFTKReport) {
      recursiveTreeList = new JCheckBox("Listagem recursiva de diretórios");
      recursiveTreeList.setSelected(true);

      tree = new JTree(new Object[0]);
      tree.setRootVisible(true);
      tree.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
      treeListener = new TreeListener();
      tree.addTreeSelectionListener(treeListener);
      tree.addTreeExpansionListener(treeListener);
      tree.addMouseListener(treeListener);

      JPanel evidencePanel = new JPanel(new BorderLayout());
      evidencePanel.add(recursiveTreeList, BorderLayout.NORTH);
      evidencePanel.add(new JScrollPane(tree), BorderLayout.CENTER);

      treeTab.add("Evidências", evidencePanel);

    }

    if (!isFTKReport && new File(new File(codePath).getParent(), "data/containsReport.flag").exists()) {
      treeTab.setSelectedIndex(1);
    }

    status = new JLabel(" ");

    this.getContentPane().add(topPanel, BorderLayout.PAGE_START);
    this.getContentPane().add(treeSplitPane, BorderLayout.CENTER);
    this.getContentPane().add(status, BorderLayout.PAGE_END);

    progressBar = new JProgressBar(0, 1);
    progressBar.setValue(0);
    progressBar.setString("Aguarde...");
    progressBar.setStringPainted(true);
    progressBar.setIndeterminate(true);

    dialogBar = new JDialog(SwingUtilities.windowForComponent(resultsTable), Dialog.ModalityType.MODELESS);
    dialogBar.setBounds(0, 0, 150, 30);
    dialogBar.setUndecorated(true);
    dialogBar.getContentPane().add(progressBar);
    appSearchParams.dialogBar = dialogBar;

    menu = new MenuClass();

    appletListener = new AppListener();
    if (!isFTKReport) {
      recursiveTreeList.addActionListener(treeListener);
    }
    termo.addActionListener(appletListener);
    filtro.addActionListener(appletListener);
    pesquisar.addActionListener(appletListener);
    opcoes.addActionListener(appletListener);
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

    hitsTable.addMouseListener(appletListener);
    // filtro.addMouseListener(appletListener);
    // filtro.getComponent(0).addMouseListener(appletListener);
    termo.getEditor().getEditorComponent().addMouseListener(appletListener);
    termo.getComponent(0).addMouseListener(appletListener);

    //Permite zoom das fontes da interface com CTRL+"-" e CTRL+"="
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

  public void alterarDisposicao() {

    if (disposicaoVertical) {

      horizontalSplitPane.remove(verticalSplitPane);
      horizontalSplitPane.remove(compositeViewer);
      verticalSplitPane.remove(resultTab);
      verticalSplitPane.remove(tabbedHits);
      treeSplitPane.remove(treeTab);
      treeSplitPane.remove(horizontalSplitPane);
      applet.getContentPane().removeAll();

      horizontalSplitPane.add(tabbedHits);
      horizontalSplitPane.add(compositeViewer);
      verticalSplitPane.add(resultTab);
      verticalSplitPane.add(horizontalSplitPane);

      applet.getContentPane().add(topPanel, BorderLayout.PAGE_START);
      applet.getContentPane().add(status, BorderLayout.PAGE_END);
      treeSplitPane.add(treeTab);
      treeSplitPane.add(verticalSplitPane);
      applet.getContentPane().add(treeSplitPane, BorderLayout.CENTER);

      applet.getContentPane().invalidate();
      applet.getContentPane().validate();
      applet.getContentPane().repaint();
      verticalSplitPane.setDividerLocation(0.5);
      horizontalSplitPane.setDividerLocation(0.4);

      disposicaoVertical = false;

    } else {
      horizontalSplitPane.remove(tabbedHits);
      horizontalSplitPane.remove(compositeViewer);
      verticalSplitPane.remove(resultTab);
      verticalSplitPane.remove(horizontalSplitPane);
      treeSplitPane.remove(treeTab);
      treeSplitPane.remove(horizontalSplitPane);
      applet.getContentPane().removeAll();

      verticalSplitPane.add(resultTab);
      verticalSplitPane.add(tabbedHits);
      horizontalSplitPane.add(verticalSplitPane);
      horizontalSplitPane.add(compositeViewer);

      applet.getContentPane().add(topPanel, BorderLayout.PAGE_START);
      applet.getContentPane().add(status, BorderLayout.PAGE_END);
      treeSplitPane.add(treeTab);
      treeSplitPane.add(horizontalSplitPane);
      applet.getContentPane().add(treeSplitPane, BorderLayout.CENTER);

      applet.getContentPane().invalidate();
      applet.getContentPane().validate();
      applet.getContentPane().repaint();
      verticalSplitPane.setDividerLocation(0.7);
      horizontalSplitPane.setDividerLocation(0.6);

      disposicaoVertical = true;
    }

    viewerControl.restartLibreOffice();

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
    destroy();
    this.dispose();
    System.exit(1);

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

}
