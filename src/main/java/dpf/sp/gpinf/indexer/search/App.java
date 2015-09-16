/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
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
import java.net.URLDecoder;
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
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.tree.TreeSelectionModel;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.Versao;
import dpf.sp.gpinf.indexer.search.viewer.CompositeTabViewer;
import dpf.sp.gpinf.indexer.search.viewer.CompositeViewerHelper;
import dpf.sp.gpinf.indexer.search.viewer.TextViewer;
import dpf.sp.gpinf.indexer.util.Util;
import dpf.sp.gpinf.indexer.util.VersionsMap;

public class App extends JFrame implements WindowListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private static Logger LOGGER = LoggerFactory.getLogger(App.class);

	SleuthkitCase sleuthCase;

	SearchResult results = new SearchResult(0);
	int[] textSizes, ids, docs;

	int totalItens, lastId, lastSelectedDoc;
	public Marcadores marcadores;
	ArrayList<String> palavrasChave, categorias;
	HashSet<String> keywordSet = new HashSet<String>();
	Set<String> highlightTerms = new HashSet<String>();

	Set<Integer> splitedDocs;
	// HashMap<Integer,Integer> viewToRawFileMap;
	// HashMap<Integer,Integer> rawToViewFileMap;
	VersionsMap viewToRawMap;
	// int[] categoryMap;

	public IndexReader reader;
	public IndexSearcher searcher;
	public ExecutorService searchExecutorService;
	Analyzer analyzer;
	Query query;
	public Object autoParser; // IndexerDefaultParser

	public JDialog dialogBar;
	JProgressBar progressBar;
	JComboBox<String> termo, filtro;
	JButton pesquisar, opcoes;
	JButton ajuda;
	JCheckBox checkBox, filtrarDuplicados, recursiveTreeList;
	JTable resultsTable;
	GalleryTable gallery;
	public HitsTable hitsTable;

	HitsTable subItemTable;
	JTree tree;
	TreeListener treeListener;
	HitsTable parentItemTable;
	JSplitPane verticalSplitPane, horizontalSplitPane, treeSplitPane;

	public TextViewer textViewer;

	public CompositeTabViewer compositeViewer;
	JTabbedPane tabbedHits, resultTab;
	JScrollPane subItemScroll, parentItemScroll, viewerScroll, resultsScroll, galleryScroll;
	MenuClass menu;
	JPanel topPanel;
	JPanel treePanel;
	JPanel multiFilterAlert;
	boolean disposicaoVertical = true;
	boolean isReport;

	ResultTableModel resultsModel;
	List resultSortKeys;
	public HitsTableModel hitsModel = new HitsTableModel();
	SubitemTableModel subItemModel = new SubitemTableModel();
	ParentTableModel parentItemModel = new ParentTableModel();
	GalleryModel galleryModel = new GalleryModel();

	private int zoomLevel;
	
	public String codePath;
	public JLabel status;

	// final String MSG_NO_PREVIEW =
	// "Não foi possível visualizar o texto. Clique duas vezes sobre o arquivo para acessar o original!";
	// final String MSG_NO_HITS =
	// "Não foi possível destacar as ocorrências. Clique duas vezes sobre o arquivo para acessar o original!";
	public final static String HIGHLIGHT_START_TAG = "<font color=\"black\" bgcolor=\"yellow\">";
	public final static String HIGHLIGHT_END_TAG = "</font>";
	final static String FILTRO_TODOS = "[Todos os Arquivos]";
	final static String FILTRO_SELECTED = "[Selecionados]";
	final static String SEARCH_TOOL_TIP = "[Digite ou escolha a expressão a ser pesquisada]";

	public static int MAX_LINE_SIZE = 100; // tamanho de quebra do texto para
											// highlight

	static int MAX_HITS = 10000;

	public static int MAX_LINES = 100000;

	static int FRAG_SIZE = 100, TEXT_BREAK_SIZE = 1000000;

	private static App applet;
	
	AppListener appletListener;

	public static final App get() {
		if (applet == null)
			applet = new App();

		return applet;
	}

	public static void main(String[] args) {
		App.get().init();
	}

	public void init() {

		LOGGER.info("init");

		applet = this;

		try {
			codePath = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath().replace("+", "/+");
			codePath = URLDecoder.decode(codePath, "utf-8");
			codePath = codePath.replace("/ ", "+");

			//codePath = "E:/Imagens/18101.11/Pendrive/indexador/lib/Search.htm";
			//codePath = "E:\\Imagens\\material_3106_2012\\indexador/lib/Search.htm";
			//codePath = "E:/Casos/Teste/LAUDO 2191.11/indexador/lib/Search.htm";
			//codePath = "L:/indexador/lib/Search.htm";
			//codePath = "E:/iso/indexador/lib/search.jar";

			codePath = codePath.substring(0, codePath.lastIndexOf('/'));
			if (codePath.charAt(0) == '/' && codePath.charAt(2) == ':')
				codePath = codePath.substring(1);

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

		(new InicializarBusca()).execute();

	}

	public void destroy() {
		try {
			reader.close();
			if (compositeViewer != null)
				compositeViewer.dispose();
			if(searchExecutorService != null)
				searchExecutorService.shutdown();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void createGUI() {

		this.setTitle(Versao.APP_NAME);
		this.setSize(new Dimension(800, 600));
		this.setExtendedState(Frame.MAXIMIZED_BOTH);
		this.addWindowListener(this);
		URL image = getClass().getResource("search.png");
		this.setIconImage(new ImageIcon(image).getImage());
		this.setVisible(true);
		ToolTipManager.sharedInstance().setInitialDelay(10);

		try {
			boolean nimbusFound = false;
			for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
				if ("Nimbus".equals(info.getName())) {
					UIManager.put("nimbusOrange", new Color(47, 92, 180));
					UIManager.put("nimbusRed", Color.BLUE);
					UIManager.setLookAndFeel(info.getClassName());
					nimbusFound = true;
					break;
				}
			}
			if (!nimbusFound)
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
		}

		try {
			palavrasChave = Util.loadKeywords(codePath + "/../palavras-chave.txt", "UTF-8");
		} catch (IOException e) {
			palavrasChave = new ArrayList<String>();
		}
		// palavrasChave.add(0, FILTRO_TODOS);
		for (String keyword : palavrasChave)
			keywordSet.add(keyword);

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
		// checkBox.setEnabled(false);

		try {
			categorias = Util.loadKeywords(codePath + "/../categorias.txt", "UTF-8");
		} catch (IOException e) {
			categorias = new ArrayList<String>();
		}

		filtro = new JComboBox<String>();
		filtro.addItem(FILTRO_TODOS);
		filtro.addItem(FILTRO_SELECTED);
		for(String categoria : categorias)
			filtro.addItem(categoria);
		filtro.setMaximumSize(new Dimension(100, 50));
		filtro.setMaximumRowCount(30);
		filtro.setToolTipText("Filtro");

		filtrarDuplicados = new JCheckBox("Ignorar Duplicados");

		topPanel = new JPanel();
		topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.LINE_AXIS));
		topPanel.setAlignmentX(LEFT_ALIGNMENT);

		multiFilterAlert = new JPanel();
		JLabel alertLabel = new JLabel("Múltiplos Filtros Ativos!");
		multiFilterAlert.setToolTipText("Combinação de categoria, marcador, palavra-chave ou caminho na árvore");
		multiFilterAlert.add(alertLabel);
		multiFilterAlert.setBackground(new Color(255, 50, 50));
		multiFilterAlert.setMaximumSize(new Dimension(100, 50));
		multiFilterAlert.setBorder(BorderFactory.createLineBorder(Color.BLACK));
		multiFilterAlert.setVisible(false);
		
		
		//topPanel.add(new JLabel("Filtro:"));
		topPanel.add(filtro);
		topPanel.add(multiFilterAlert);
		topPanel.add(filtrarDuplicados);
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
		resultsTable.getColumnModel().getColumn(0).setPreferredWidth(55);
		resultsTable.getColumnModel().getColumn(1).setPreferredWidth(20);
		resultsTable.getColumnModel().getColumn(2).setPreferredWidth(35);
		resultsTable.getColumnModel().getColumn(2).setCellRenderer(new ProgressCellRenderer());
		resultsTable.getColumnModel().getColumn(3).setPreferredWidth(100);
		resultsTable.getColumnModel().getColumn(3).setMinWidth(0);
		resultsTable.getColumnModel().getColumn(4).setPreferredWidth(200);
		for (int i = 5; i < resultsTable.getColumnModel().getColumnCount() - 1; i++)
			resultsTable.getColumnModel().getColumn(i).setPreferredWidth(150);
		isReport = new File(new File(codePath).getParent(), "data/containsReport.flag").exists();
		if(!isReport)
			resultsTable.getColumnModel().getColumn(5).setPreferredWidth(50);
		resultsTable.getColumnModel().getColumn(6).setPreferredWidth(100);
		resultsTable.getColumnModel().getColumn(7).setPreferredWidth(60);
		resultsTable.getColumnModel().getColumn(resultsTable.getColumnModel().getColumnCount() - 2).setPreferredWidth(250);
		resultsTable.getColumnModel().getColumn(resultsTable.getColumnModel().getColumnCount() - 1).setPreferredWidth(2000);
		resultsTable.setShowGrid(false);
		resultsTable.setRowSorter(new ResultTableRowSorter());
		resultsTable.setAutoscrolls(false);
		resultsTable.setDefaultRenderer(String.class, new TableCellRenderer());
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
				if (colWidth > 0) gallery.setRowHeight(colWidth);
				int selRow = App.get().gallery.getSelectedRow();
				if (selRow >= 0)
					App.get().gallery.scrollRectToVisible(App.get().gallery.getCellRect(selRow, 0, false));
			}
		});

		resultTab = new JTabbedPane();
		resultTab.addTab("Tabela", resultsScroll);
		resultTab.addTab("Galeria", galleryScroll);

		hitsTable = new HitsTable(hitsModel);
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

		compositeViewer = new CompositeTabViewer();

		verticalSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, resultTab, tabbedHits);
		verticalSplitPane.setOneTouchExpandable(true);
		verticalSplitPane.setContinuousLayout(true);
		verticalSplitPane.setResizeWeight(0.7);

		horizontalSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, verticalSplitPane, compositeViewer);
		horizontalSplitPane.setOneTouchExpandable(true);
		horizontalSplitPane.setContinuousLayout(true);
		horizontalSplitPane.setResizeWeight(0.6);
		
		if(!isReport){
			recursiveTreeList = new JCheckBox("Listagem recursiva");
			recursiveTreeList.setSelected(true);
			tree = new JTree(new Object[0]);
			tree.setRootVisible(true);
			tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
			JScrollPane treeScroll = new JScrollPane(tree);
			treePanel = new JPanel();
			treePanel.setLayout(new BorderLayout());
			treePanel.add(recursiveTreeList, BorderLayout.NORTH);
			treePanel.add(treeScroll, BorderLayout.CENTER);
			treeSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treePanel, horizontalSplitPane);
			treeSplitPane.setOneTouchExpandable(true);
			treeSplitPane.setContinuousLayout(true);
			treeSplitPane.setResizeWeight(0.15);
			treeListener = new TreeListener();
			tree.addTreeSelectionListener(treeListener);
		}

		status = new JLabel(" ");

		this.getContentPane().add(topPanel, BorderLayout.PAGE_START);
		if(!isReport)
			this.getContentPane().add(treeSplitPane, BorderLayout.CENTER);
		else
			this.getContentPane().add(horizontalSplitPane, BorderLayout.CENTER);
		
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
		
		menu = new MenuClass();

		appletListener = new AppListener();
		if(!isReport) recursiveTreeList.addActionListener(appletListener);
		termo.addActionListener(appletListener);
		filtro.addActionListener(appletListener);
		filtrarDuplicados.addActionListener(appletListener);
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
			if(!isReport){
				treeSplitPane.remove(treePanel);
				treeSplitPane.remove(horizontalSplitPane);
			}
			applet.getContentPane().removeAll();

			horizontalSplitPane.add(tabbedHits);
			horizontalSplitPane.add(compositeViewer);
			verticalSplitPane.add(resultTab);
			verticalSplitPane.add(horizontalSplitPane);
			
			applet.getContentPane().add(topPanel, BorderLayout.PAGE_START);
			applet.getContentPane().add(status, BorderLayout.PAGE_END);
			if(!isReport){
				treeSplitPane.add(treePanel);
				treeSplitPane.add(verticalSplitPane);
				applet.getContentPane().add(treeSplitPane, BorderLayout.CENTER);
			}else
				applet.getContentPane().add(verticalSplitPane, BorderLayout.CENTER);

			applet.getContentPane().invalidate();
			applet.getContentPane().validate();
			applet.getContentPane().repaint();
			verticalSplitPane.setDividerLocation(0.4);
			horizontalSplitPane.setDividerLocation(0.3);

			disposicaoVertical = false;

		} else {
			horizontalSplitPane.remove(tabbedHits);
			horizontalSplitPane.remove(compositeViewer);
			verticalSplitPane.remove(resultTab);
			verticalSplitPane.remove(horizontalSplitPane);
			if(!isReport){
				treeSplitPane.remove(treePanel);
				treeSplitPane.remove(horizontalSplitPane);
			}
			applet.getContentPane().removeAll();

			verticalSplitPane.add(resultTab);
			verticalSplitPane.add(tabbedHits);
			horizontalSplitPane.add(verticalSplitPane);
			horizontalSplitPane.add(compositeViewer);

			applet.getContentPane().add(topPanel, BorderLayout.PAGE_START);
			applet.getContentPane().add(status, BorderLayout.PAGE_END);
			if(!isReport){
				treeSplitPane.add(treePanel);
				treeSplitPane.add(horizontalSplitPane);
				applet.getContentPane().add(treeSplitPane, BorderLayout.CENTER);
			}else
				applet.getContentPane().add(horizontalSplitPane, BorderLayout.CENTER);

			applet.getContentPane().invalidate();
			applet.getContentPane().validate();
			applet.getContentPane().repaint();
			verticalSplitPane.setDividerLocation(0.7);
			horizontalSplitPane.setDividerLocation(0.6);

			disposicaoVertical = true;
		}

		CompositeViewerHelper.restartLOAfterLayoutChange();

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
