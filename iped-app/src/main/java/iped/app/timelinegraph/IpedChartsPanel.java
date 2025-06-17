package iped.app.timelinegraph;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.LegendItem;
import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.axis.DateTickMarkPosition;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.block.Block;
import org.jfree.chart.labels.XYToolTipGenerator;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYBarPainter;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.LegendGraphic;
import org.jfree.chart.title.LegendItemBlockContainer;
import org.jfree.chart.ui.VerticalAlignment;
import org.jfree.data.Range;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimePeriod;
import org.jfree.data.xy.AbstractIntervalXYDataset;
import org.jfree.data.xy.DefaultXYDataset;
import org.jfree.data.xy.XYDataset;

import bibliothek.gui.dock.common.DefaultSingleCDockable;
import bibliothek.gui.dock.common.event.CDockableLocationEvent;
import bibliothek.gui.dock.common.event.CDockableLocationListener;
import iped.app.timelinegraph.datasets.AsynchronousDataset;
import iped.app.timelinegraph.datasets.IpedTimelineDatasetManager;
import iped.app.timelinegraph.popups.LegendItemPopupMenu;
import iped.app.timelinegraph.swingworkers.CheckWorker;
import iped.app.timelinegraph.swingworkers.HighlightWorker;
import iped.app.ui.App;
import iped.app.ui.columns.ColumnsManager;
import iped.app.ui.themes.ThemeManager;
import iped.data.IItemId;
import iped.engine.search.QueryBuilder;
import iped.engine.task.index.IndexItem;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.properties.BasicProps;
import iped.utils.IconUtil;
import iped.viewers.api.GUIProvider;
import iped.viewers.api.IFilter;
import iped.viewers.api.IMultiSearchResultProvider;
import iped.viewers.api.IQueryFilter;
import iped.viewers.api.IQueryFilterer;
import iped.viewers.api.ResultSetViewer;
import iped.viewers.api.events.RowSorterTableDataChange;

public class IpedChartsPanel extends JPanel implements ResultSetViewer, TableModelListener, ListSelectionListener, IQueryFilterer, ComponentListener {
    JTable resultsTable;
    IMultiSearchResultProvider resultsProvider;
    GUIProvider guiProvider;
    private DefaultSingleCDockable dockable;
    CDockableLocationListener dockableLocationListener;
    IpedTimelineDatasetManager ipedTimelineDatasetManager;

    static ThreadPoolExecutor swExecutor = new ThreadPoolExecutor(1, 1, 20000, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());

    private static final Logger logger = LogManager.getLogger(IpedChartsPanel.class);

    boolean syncViewWithTableSelection = false;

    LegendItemCollection legendItems = null;

    Class<? extends TimePeriod> timePeriodClass = Day.class;
    String timePeriodString = "Day";

    static TreeMap<String, String> timeEventColumnNamesList = new TreeMap<String, String>();
    static String ordToEventName[];
    static private HashMap<String, Integer> eventNameToOrd = new HashMap<>();
    AtomicBoolean dataSetUpdated = new AtomicBoolean();
    AtomicBoolean loadingCacheStarted = new AtomicBoolean();
    volatile boolean isUpdated = true;

    String[] timeFields = { BasicProps.TIMESTAMP, BasicProps.TIME_EVENT };
    LegendItemPopupMenu legendItemPopupMenu = null;

    /* chart fields */
    IpedDateAxis domainAxis = new IpedDateAxis("Date (" + timePeriodString + ")", this);
    IpedCombinedDomainXYPlot combinedPlot = new IpedCombinedDomainXYPlot(this);
    IpedChart chart = new IpedChart(combinedPlot);
    IpedChartPanel chartPanel = null;
    JList legendList = new JList();
    JScrollPane listScroller = new JScrollPane(legendList);

    IpedStackedXYBarRenderer renderer = null;
    XYLineAndShapeRenderer highlightsRenderer = new XYLineAndShapeRenderer();
    XYToolTipGenerator toolTipGenerator = null;

    String metadataToBreakChart = null;
    ImageIcon loading = null;
    JLabel loadingLabel;

    IpedSplitPane splitPane;

    boolean applyFilters = false;
    private XYBarPainter barPainter;
    private boolean internalUpdate;
    private TimeZone timeZone = TimeZone.getDefault();
    private Locale locale = Locale.getDefault();
    private Range firstRange;

    SortedSetDocValues timeStampValues = null;
    private RunnableFuture<Void> swRefresh;
    private HashMap<String, AbstractIntervalXYDataset> result;
    private DefaultListModel<LegendItemBlockContainer> legendListModel;

    Color fgColor;
    Color bgColor;

    private static final String resPath = '/' + App.class.getPackageName().replace('.', '/') + '/';

    public IpedChartsPanel() {
        this(true);
        if (chart.getTitle() != null) {
            chart.getTitle().setVerticalAlignment(VerticalAlignment.CENTER);
        }
        combinedPlot.setDomainPannable(true);

        toolTipGenerator = new XYToolTipGenerator() {
            @Override
            public String generateToolTip(XYDataset dataset, int series, int item) {
                String html;

                return "<html>" + dataset.getSeriesKey(series) + ":" + dataset.getYValue(series, item) + "</html>";
            }
        };
    }

    public IpedChartsPanel(boolean b) {
        super(b);

        this.setLayout(new GridLayout());
    }

    class LegendCellRenderer extends JLabel implements ListCellRenderer<LegendItemBlockContainer> {

        public LegendCellRenderer() {
            setOpaque(true);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends LegendItemBlockContainer> list, LegendItemBlockContainer value, int index, boolean isSelected, boolean cellHasFocus) {

            JLabel result = this;

            result.setInheritsPopupMenu(true);
            result.setText((String) value.getSeriesKey());
            Color background;
            Color foreground;
            if (isSelected || cellHasFocus) {
                background = Color.BLUE;
                foreground = Color.WHITE;
            } else {
                background = UIManager.getLookAndFeelDefaults().getColor("Viewer.background");
                if (background == null) {
                    background = Color.WHITE;
                }
                foreground = UIManager.getLookAndFeelDefaults().getColor("Viewer.foreground");
                if (foreground == null) {
                    foreground = Color.BLACK;
                }
            }
            if (chartPanel.getHiddenEvents().contains((String) value.getSeriesKey())) {
                foreground = Color.RED;
            }
            result.setForeground(foreground);
            result.setBackground(background);

            IpedCombinedDomainXYPlot rootPlot = ((IpedCombinedDomainXYPlot) getChartPanel().getChart().getPlot());
            if (rootPlot != null && rootPlot.getSubplots().size() > 0) {
                XYPlot xyPlot = (XYPlot) rootPlot.getSubplots().get(0);

                Iterator<Block> iterator = value.getBlocks().iterator();
                while (iterator.hasNext()) {
                    Block b = iterator.next();
                    if (b instanceof LegendGraphic) {
                        LegendGraphic i = (LegendGraphic) b;
                        Rectangle r = i.getShape().getBounds();
                        Image image = new BufferedImage(r.width, r.height, BufferedImage.TYPE_INT_RGB);
                        Graphics2D gr = (Graphics2D) image.getGraphics();
                        gr.translate(-r.x, -r.y);
                        gr.setBackground((Color) i.getFillPaint());
                        gr.setPaint(i.getFillPaint());
                        gr.draw(i.getShape());
                        gr.fill(i.getShape());
                        gr.dispose();
                        result.setIcon(new ImageIcon(image));
                    }
                }

                if (chartPanel.getExcludedEvents().contains((String) value.getSeriesKey())) {
                    Font f = getFont();
                    Map attributes = f.getAttributes();
                    attributes.put(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON);
                    JLabel clone;
                    clone = new JLabel();
                    clone.setOpaque(true);
                    clone.setText(result.getText());
                    clone.setIcon(result.getIcon());
                    clone.setFont(f.deriveFont(attributes));
                    clone.setForeground(foreground);
                    clone.setBackground(background);
                    return clone;
                }
            }

            return result;
        }
    }

    @Override
    public void init(JTable resultsTable, IMultiSearchResultProvider resultsProvider, GUIProvider guiProvider) {
        this.resultsTable = resultsTable;
        this.resultsProvider = resultsProvider;
        this.guiProvider = guiProvider;
        this.isUpdated = false;

        fgColor = UIManager.getLookAndFeelDefaults().getColor("Viewer.foreground");
        bgColor = UIManager.getLookAndFeelDefaults().getColor("Viewer.background");

        this.removeAll();
        splitPane = new IpedSplitPane();
        splitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);

        chartPanel = new IpedChartPanel(chart, this);
        legendListModel = new DefaultListModel<LegendItemBlockContainer>();
        legendList.setModel(legendListModel);
        legendList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        legendList.setVisibleRowCount(-1);
        legendList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        legendList.setCellRenderer(new LegendCellRenderer());
        chartPanel.setPreferredSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        listScroller.setPreferredSize(new Dimension(Integer.MAX_VALUE, 80));
        legendList.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                check(e);
            }

            public void mouseReleased(MouseEvent e) {
                check(e);
            }

            public void check(MouseEvent e) {
                if (e.isPopupTrigger()) { // if the event shows the menu
                    int selIndex = legendList.locationToIndex(e.getPoint());
                    legendList.addSelectionInterval(selIndex, selIndex); // select the item
                    legendItemPopupMenu.show(legendList, e.getX(), e.getY()); // and show the menu
                }
            }
        });
        chart.setIpedChartPanel(chartPanel);

        if (renderer == null) {
            renderer = new IpedStackedXYBarRenderer(this);
        }
        barPainter = new IpedXYBarPainter((XYBarRenderer) renderer);
        ((IpedStackedXYBarRenderer) renderer).setBarPainter(barPainter);
        ((IpedStackedXYBarRenderer) renderer).setMargin(0);
        renderer.setDefaultToolTipGenerator(toolTipGenerator);
        renderer.setDefaultItemLabelsVisible(true);

        resultsTable.getModel().addTableModelListener(this);
        resultsTable.getSelectionModel().addListSelectionListener(this);

        legendItemPopupMenu = new LegendItemPopupMenu(chartPanel);

        chartPanel.setDomainZoomable(true);
        chartPanel.setRangeZoomable(true);
        chartPanel.setDisplayToolTips(true);
        ToolTipManager ttm = ToolTipManager.sharedInstance();
        ttm.setInitialDelay(0);
        ttm.setReshowDelay(0);
        ttm.setEnabled(true);

        splitPane.setTopComponent(chartPanel);
        splitPane.setBottomComponent(listScroller);
        splitPane.setVisible(false);

        chartPanel.setPopupMenu(null);
        this.addComponentListener(this);

        loading = (ImageIcon) new ImageIcon(IconUtil.class.getResource(resPath + "loading.gif"));
        loadingLabel = new JLabel("", loading, JLabel.CENTER);
        this.add(loadingLabel);

        domainAxis.setTickMarkPosition(DateTickMarkPosition.START);
        domainAxis.setLowerMargin(0.01);
        domainAxis.setUpperMargin(0.01);
        combinedPlot.setDomainAxis(domainAxis);

        if (ipedTimelineDatasetManager == null) {
            ipedTimelineDatasetManager = new IpedTimelineDatasetManager(this);
        }
    }

    public String getTimeEventColumnName(String timeEvent) {
        synchronized (timeEventColumnNamesList) {
            return timeEventColumnNamesList.get(timeEvent);
        }
    }

    public IpedDateAxis getDomainAxis() {
        return domainAxis;
    }

    public HashMap<String, XYDataset> createAlertDataSets() {
        HashMap<String, XYDataset> result = new HashMap<String, XYDataset>();

        DefaultXYDataset highlights = new DefaultXYDataset();

        double[][] data = new double[2][2];

        Calendar cal = Calendar.getInstance(timeZone);

        cal.set(2019, 01, 23, 11, 30);
        data[0][0] = cal.getTimeInMillis();
        data[1][0] = 11;
        cal.set(2021, 02, 23, 11, 30);
        data[0][1] = cal.getTimeInMillis();
        data[1][1] = 21;

        highlights.addSeries("highlights", data);

        result.put("highlights", (XYDataset) highlights);

        return result;
    }

    public HashMap<String, AbstractIntervalXYDataset> createDataSets() {
        HashMap<String, AbstractIntervalXYDataset> result = new HashMap<String, AbstractIntervalXYDataset>();
        try {
            Set<String> selectedBookmarks = guiProvider.getSelectedBookmarks();
            Set<String> selectedCategories = guiProvider.getSelectedCategories();
            SortedSetDocValues categoriesValues = null;

            if (selectedBookmarks.size() > 0 && chartPanel.getSplitByBookmark()) {
                for (String bookmark : selectedBookmarks) {
                    result.put(bookmark, ipedTimelineDatasetManager.getBestDataset(timePeriodClass, bookmark));
                }
            } else if (selectedCategories.size() > 0 && chartPanel.getSplitByCategory()) {
                for (String category : selectedCategories) {
                    result.put(category, ipedTimelineDatasetManager.getBestDataset(timePeriodClass, category));
                }
            } else {
                result.put("Items", ipedTimelineDatasetManager.getBestDataset(timePeriodClass, null));
            }
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    private static final void loadOrdsFromString(String string, int[][] ret) {
        int len = string.length();
        int i = 0, j = 0, k = 0;
        do {
            j = string.indexOf(IndexItem.EVENT_IDX_SEPARATOR, i);
            if (j == -1) {
                j = len;
            }
            loadOrdsFromStringInner(string.substring(i, j), j - i, ret[k++]);
            i = j + 1;
        } while (j < len);
    }

    private static final void loadOrdsFromStringInner(String string, int len, int[] ret) {
        int i = 0, j = 0, k = 0;
        do {
            j = string.indexOf(IndexItem.EVENT_IDX_SEPARATOR2, i);
            if (j == -1) {
                j = len;
            }
            ret[k++] = Integer.parseInt(string.substring(i, j));
            i = j + 1;
        } while (j < len);
        ret[k] = -1;
    }

    public Future<?> refreshChart() {
        return refreshChart(false);
    }

    public Future<?> refreshChart(boolean resetDomainRange) {
        try {
            IpedChartsPanel self = this;

            self.remove(splitPane);
            self.remove(loadingLabel);
            self.add(loadingLabel);
            self.repaint();

            if (swRefresh != null) {
                synchronized (swRefresh) {
                    swRefresh.cancel(true);
                }
            }

            swRefresh = new RunnableFuture<Void>() {
                boolean cancelled = false;
                boolean isDone = false;

                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    return cancelled = true;
                }

                @Override
                public boolean isCancelled() {
                    return cancelled;
                }

                @Override
                public boolean isDone() {
                    return isDone;
                }

                @Override
                public Void get() throws InterruptedException, ExecutionException {
                    return null;
                }

                @Override
                public Void get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                    return null;
                }

                @Override
                public void run() {
                    try {
                        if (result != null) {
                            synchronized (result) {
                                for (AbstractIntervalXYDataset ds : result.values()) {
                                    if (ds instanceof AsynchronousDataset) {
                                        ((AsynchronousDataset) ds).cancel();
                                    }
                                }
                            }
                        }
                        if (isCancelled()) {
                            return;
                        }
                        HashMap<String, AbstractIntervalXYDataset> lresult = createDataSets();
                        result = lresult;

                        if (!isCancelled()) {
                            JFreeChart chart = null;
                            if (lresult != null && lresult.size() > 0) {
                                chart = createChart(lresult, resetDomainRange);

                                isUpdated = true;
                            }

                            if (chart != null && !isCancelled()) {
                                self.remove(loadingLabel);
                                self.remove(splitPane);
                                self.add(splitPane);
                                splitPane.setTopComponent(chartPanel);
                                splitPane.setBottomComponent(listScroller);
                                splitPane.setVisible(true);

                                // hide hidden events
                                IpedCombinedDomainXYPlot rootPlot = ((IpedCombinedDomainXYPlot) getChartPanel().getChart().getPlot());

                                if (rootPlot != null && rootPlot.getSubplots().size() > 0) {
                                    List<XYPlot> xyPlots = rootPlot.getSubplots();

                                    for (XYPlot xyPlot : xyPlots) {
                                        for (int i = 0; i < xyPlot.getDataset(0).getSeriesCount(); i++) {
                                            String currSeries = (String) xyPlot.getDataset(0).getSeriesKey(i);
                                            if (chartPanel.getHiddenEvents().contains(currSeries)) {
                                                xyPlot.getRenderer().setPlot(xyPlot);
                                                xyPlot.getRenderer().setSeriesVisible(i, false);
                                            }
                                        }
                                    }
                                }
                                combinedPlot.fireChangeEvent();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };

            return swExecutor.submit(swRefresh);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void setTimePeriodClass(Class<? extends TimePeriod> timePeriodClass) {
        this.timePeriodClass = timePeriodClass;
    }

    public void addEventLegent(String event) {
        Iterator<LegendItem> it = legendItems.iterator();
        for (Iterator iterator = legendItems.iterator(); iterator.hasNext();) {
            LegendItem legendItem = (LegendItem) iterator.next();
            if (event.equals(legendItem.getLabel())) {
                return;
            }
        }
        LegendItem item1 = new LegendItem(event, new Color(0x22, 0x22, 0xFF));

        legendItems.add(item1);
    }

    @Override
    public void setDockableContainer(DefaultSingleCDockable dockable) {
        if (this.dockable != null) {
            this.dockable.removeCDockableLocationListener(dockableLocationListener);
        }

        this.dockable = dockable;
        final IpedChartsPanel self = this;

        dockableLocationListener = new CDockableLocationListener() {
            @Override
            public void changed(CDockableLocationEvent dockableEvent) {
                if (!isUpdated && dockableEvent.isShowingChanged()) {
                    refreshChart();
                    if (!loadingCacheStarted.getAndSet(true)) {
                        Runnable r = new Runnable() {
                            @Override
                            public void run() {
                                ipedTimelineDatasetManager.startCacheCreation();
                            }
                        };
                        new Thread(r).start();
                    }
                }
            }
        };

        dockable.addCDockableLocationListener(dockableLocationListener);
    }

    public void cancel() {
        if (swRefresh != null) {
            swRefresh.cancel(true);
        }
    }

    @Override
    public String getTitle() {
        return "Timeline";
    }

    @Override
    public String getID() {
        // TODO Auto-generated method stub
        return "timelinetab";
    }

    @Override
    public JPanel getPanel() {
        return this;
    }

    @Override
    public void redraw() {
        // TODO Auto-generated method stub
    }

    @Override
    public void updateSelection() {
        // TODO Auto-generated method stub
    }

    @Override
    public GUIProvider getGUIProvider() {
        // TODO Auto-generated method stub
        return this.guiProvider;
    }

    public void showSelection() {
        try {
            Date min = null;
            Date max = null;

            LeafReader reader = resultsProvider.getIPEDSource().getLeafReader();
            timeStampValues = reader.getSortedSetDocValues(BasicProps.TIMESTAMP);
            TreeSet<Integer> luceneIds = new TreeSet<Integer>();

            int[] selected = resultsTable.getSelectedRows();
            for (int i = 0; i < selected.length; i++) {
                int rowModel = resultsTable.convertRowIndexToModel(selected[i]);
                IItemId item = resultsProvider.getResults().getItem(rowModel);

                int luceneId = resultsProvider.getIPEDSource().getLuceneId(item);
                luceneIds.add(luceneId);
            }

            SortedSetDocValues timeStampValues = reader.getSortedSetDocValues(BasicProps.TIMESTAMP);

            for (Iterator iterator = luceneIds.iterator(); iterator.hasNext();) {
                Integer docId = (Integer) iterator.next();
                boolean adv = false;
                try {
                    adv = timeStampValues.advanceExact(docId);
                } catch (IllegalArgumentException e) {
                    adv = timeStampValues.advanceExact(docId);
                }

                long ord, prevOrd = -1;
                while (adv && (ord = timeStampValues.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
                    if (prevOrd != ord) {
                        Date d = domainAxis.ISO8601DateParse(timeStampValues.lookupOrd(ord).utf8ToString());
                        if (min == null || d.before(min)) {
                            min = d;
                        }
                        if (max == null || d.after(max)) {
                            max = d;
                        }
                    }
                    prevOrd = ord;
                }

            }

            domainAxis.garanteeShowRange(min, max);
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
            return;
        } else {
            if (syncViewWithTableSelection) {
                showSelection();
            }
        }
    }

    Runnable populateEventNames = new Runnable() {
        // populates list with timeevent column names with uppercase letters
        @Override
        public void run() {
            ColumnsManager cm = (ColumnsManager) ((App) resultsProvider).getColumnsManager();
            String[] columnsArray = cm.fieldGroups[cm.fieldGroups.length - 1];
            synchronized (timeEventColumnNamesList) {
                LeafReader reader = resultsProvider.getIPEDSource().getLeafReader();
                try {
                    SortedSetDocValues timeEventGroupValues = reader.getSortedSetDocValues(BasicProps.TIME_EVENT);
                    if (timeEventGroupValues != null) {
                        TermsEnum te = timeEventGroupValues.termsEnum();
                        ordToEventName = new String[(int) timeEventGroupValues.getValueCount()];
                        int j = 0;
                        while (j < timeEventGroupValues.getValueCount()) {
                            String eventType = timeEventGroupValues.lookupOrd(j).utf8ToString();
                            ordToEventName[j] = eventType;
                            eventNameToOrd.put(eventType, j);
                            for (int i = 0; i < columnsArray.length; i++) {
                                if (columnsArray[i].toLowerCase().equals(eventType)) {
                                    timeEventColumnNamesList.put(eventType, columnsArray[i]);
                                }
                            }
                            j++;
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    static public Integer getEventOrd(String event) {
        return eventNameToOrd.get(event);
    }

    static public String getEventName(int ord) {
        return ordToEventName[ord];
    }

    @Override
    public void tableChanged(TableModelEvent e) {
        if ((e instanceof RowSorterTableDataChange)) {
            return;
        }
        if (!dataSetUpdated.getAndSet(true)) {
            new Thread(populateEventNames).start();
        }

        if (internalUpdate) {
            internalUpdate = false;
            return;
        }
        // all data changed
        if (e.getFirstRow() == 0 && e.getLastRow() == Integer.MAX_VALUE) {
            /* somente chamado se o tab estiver sendo exibido */
            if (dockable != null && dockable.isShowing()) {
                refreshChart();
            } else {
                isUpdated = false;
            }
        }
        // TODO Auto-generated method stub
    }

    public IpedStackedXYBarRenderer getRenderer() {
        return renderer;
    }

    private void createNotableChart(XYPlot firstPlot) {
        XYDataset highlights = createAlertDataSets().get("highlights");
        if (firstPlot != null) {
            highlightsRenderer.setDefaultLinesVisible(false);
            highlightsRenderer.setDrawSeriesLineAsPath(false);
            highlightsRenderer.setDrawSeriesLineAsPath(false);
            highlightsRenderer.setSeriesShape(0, new Ellipse2D.Float(1, 1, 10, 10));

            IpedHourAxis hours = new IpedHourAxis("Hours");
            hours.setAutoRange(false);
            hours.setRange(new Range(0, 24));
            firstPlot.setRangeAxis(1, hours);
            firstPlot.setRenderer(1, highlightsRenderer);
            firstPlot.setDataset(1, highlights);
            List<Integer> axisList = new ArrayList<Integer>();
            axisList.add(1);
            firstPlot.mapDatasetToRangeAxes(1, axisList);
        }
    }

    private JFreeChart createChart(HashMap<String, AbstractIntervalXYDataset> datasets, boolean resetDomainRange) {
        domainAxis.setLabel("Date (" + timePeriodString + ") [" + timeZone.getDisplayName() + " " + DateUtil.getTimezoneOffsetInformation(timeZone) + "]");

        combinedPlot.setSkipFireEventChange(true);

        Object[] plots = combinedPlot.getSubplots().toArray();
        boolean firstExecution = plots != null && plots.length <= 0;

        for (Object plot : plots) {
            combinedPlot.remove((XYPlot) plot);
        }
        combinedPlot.removeAllDataSets();

        combinedPlot.setSkipFireEventChange(false);

        combinedPlot.fireChangeEvent();

        combinedPlot.setSkipFireEventChange(true);

        XYItemRenderer renderer = getRenderer();
        combinedPlot.setRenderer(renderer);

        if (resetDomainRange && firstRange != null) {
            domainAxis.forceRange(firstRange, false, false);
        }

        int ids = 0;
        XYPlot firstPlot = null;
        for (Iterator iterator = datasets.keySet().iterator(); iterator.hasNext();) {
            String marcador = (String) iterator.next();
            IpedNumberAxis rangeAxis = new IpedNumberAxis(marcador);
            rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
            rangeAxis.setUpperMargin(0.10); // leave some space for item labels

            AbstractIntervalXYDataset dataset = (AbstractIntervalXYDataset) datasets.get(marcador);

            boolean canceled = false;
            if (dataset instanceof AsynchronousDataset) {
                canceled = ((AsynchronousDataset) dataset).waitLoaded();
            }
            if (canceled) {
                combinedPlot.setSkipFireEventChange(false);
                combinedPlot.fireChangeEvent();
                return null;
            }
            IpedXYPlot plot = new IpedXYPlot(chartPanel, dataset, domainAxis, rangeAxis, renderer);
            plot.changeTheme(ThemeManager.getInstance().getCurrentTheme());
            if (firstPlot == null) {
                firstPlot = plot;
            }
            combinedPlot.add(ids, plot, dataset);
            rangeAxis.autoAdjustRange();
            ids++;
        }

        combinedPlot.setSkipFireEventChange(false);
        combinedPlot.fireChangeEvent();

        if (firstExecution) {
            domainAxis.autoAdjustRange();
            firstRange = domainAxis.getRange();
        }

        return chart;
    }

    @Override
    public Query getQuery() {
        try {
            if (applyFilters) {
                if (chartPanel != null) {
                    if (chartPanel.hasNoFilter()) {
                        return null;
                    }

                    Query result = new QueryBuilder(App.get().appCase).getQuery("");

                    if (chartPanel.definedFilters.size() > 0) {
                        String timeFilter = "(";
                        int i = 0;
                        for (Date[] dates : chartPanel.definedFilters) {
                            timeFilter += "timeStamp:[";
                            timeFilter += domainAxis.ISO8601DateFormatUTC(dates[0]);
                            timeFilter += " TO ";
                            timeFilter += domainAxis.ISO8601DateFormatUTC(dates[1]);
                            timeFilter += "]";
                            i++;
                            if (i != chartPanel.definedFilters.size()) {
                                timeFilter += " || ";
                            }
                        }
                        timeFilter += ")";

                        BooleanQuery.Builder boolQuery = new BooleanQuery.Builder();
                        boolQuery.add(new QueryBuilder(App.get().appCase).getQuery(timeFilter), Occur.MUST);
                        boolQuery.add(result, Occur.MUST);
                        result = boolQuery.build();
                    }

                    if (chartPanel.getExcludedEvents().size() > 0) {
                        String eventsFilter = "-(";
                        int i = 0;
                        for (String event : chartPanel.getExcludedEvents()) {
                            eventsFilter += "timeEvent:\"";
                            eventsFilter += event;
                            eventsFilter += "\"";
                            i++;
                            if (i != chartPanel.getExcludedEvents().size()) {
                                eventsFilter += " || ";
                            }
                        }
                        eventsFilter += ")";

                        BooleanQuery.Builder boolQuery = new BooleanQuery.Builder();
                        boolQuery.add(new QueryBuilder(App.get().appCase).getQuery(eventsFilter), Occur.MUST);
                        boolQuery.add(result, Occur.MUST);
                        result = boolQuery.build();
                    }

                    return result;
                } else {
                    return null;
                }
            } else {
                return null;
            }
        } catch (QueryNodeException | ParseException qne) {
            qne.printStackTrace();
            return null;
        }
    }

    public IMultiSearchResultProvider getResultsProvider() {
        return this.resultsProvider;
    }

    public void setTimePeriodString(String timePeriodString) {
        this.timePeriodString = timePeriodString;
    }

    public void setApplyFilters(boolean applyFilters) {
        this.applyFilters = applyFilters;
    }

    public String getMetadataToBreakChart() {
        return metadataToBreakChart;
    }

    public void setMetadataToBreakChart(String metadataToBreakChart) {
        this.metadataToBreakChart = metadataToBreakChart;
    }

    @Override
    public void clearFilter() {
        chartPanel.isClearing=true;
        chartPanel.removeAllFilters();
        chartPanel.isClearing=false;
    }

    @Override
    public boolean hasFiltersApplied() {
        return applyFilters;
    }

    public void setInternalUpdate(boolean b) {
        internalUpdate = b;
    }

    public IpedChartPanel getChartPanel() {
        return chartPanel;
    }

    public void setChartPanel(IpedChartPanel chartPanel) {
        this.chartPanel = chartPanel;
    }

    public void highlightItemsOnInterval(Date firstDate, Date endDate, boolean b) {
        HighlightWorker sw = new HighlightWorker(domainAxis, resultsProvider, firstDate, endDate, b);
        sw.execute();
    }

    public void checkItemsOnInterval(Date firstDate, Date endDate, boolean b) {
        CheckWorker sw = new CheckWorker(domainAxis, resultsProvider, firstDate, endDate, b);
        sw.execute();
    }

    public void setTimeZone(TimeZone timezone) {
        this.timeZone = timezone;
        domainAxis.setTimeZone(timezone);
        refreshChart();
    }

    public TimeZone getTimeZone() {
        return timeZone;
    }

    public double getTimePeriodLength() {
        Class[] cArg = new Class[1];
        cArg[0] = Date.class;
        try {
            TimePeriod t = timePeriodClass.getDeclaredConstructor(cArg).newInstance(new Date());
            return t.getEnd().getTime() - t.getStart().getTime();
        } catch (Exception e) {
        }

        return 0;
    }

    public Class<? extends TimePeriod> getTimePeriodClass() {
        return timePeriodClass;
    }

    public IpedTimelineDatasetManager getIpedTimelineDatasetManager() {
        return ipedTimelineDatasetManager;
    }

    public void setIpedTimelineDatasetManager(IpedTimelineDatasetManager ipedTimelineDatasetManager) {
        ipedTimelineDatasetManager = ipedTimelineDatasetManager;
    }

    public IpedCombinedDomainXYPlot getCombinedPlot() {
        return combinedPlot;
    }

    public void setCombinedPlot(IpedCombinedDomainXYPlot combinedPlot) {
        this.combinedPlot = combinedPlot;
    }

    public DefaultListModel<LegendItemBlockContainer> getLegendListModel() {
        return legendListModel;
    }

    public void setLegendListModel(DefaultListModel<LegendItemBlockContainer> legendListModel) {
        this.legendListModel = legendListModel;
    }

    public JList getLegendList() {
        return legendList;
    }

    public void setLegendList(JList legendList) {
        this.legendList = legendList;
    }

    @Override
    public void componentResized(ComponentEvent e) {
        this.remove(loadingLabel);
        this.remove(splitPane);
        this.add(splitPane);
    }

    @Override
    public void componentMoved(ComponentEvent e) {
    }

    @Override
    public void componentShown(ComponentEvent e) {
    }

    @Override
    public void componentHidden(ComponentEvent e) {
    }

    public boolean isSyncViewWithTableSelection() {
        return syncViewWithTableSelection;
    }

    public void setSyncViewWithTableSelection(boolean syncViewWithTableSelection) {
        this.syncViewWithTableSelection = syncViewWithTableSelection;
    }

    @Override
    public void checkAll(boolean value) {
        // TODO Auto-generated method stub

    }

    @Override
    public void notifyCaseDataChanged() {
        this.ipedTimelineDatasetManager = new IpedTimelineDatasetManager(this);
        this.dataSetUpdated.set(false);
    }

    public void resetZoom() {
        setTimePeriodClass(Day.class);
        setTimePeriodString("Day");
        refreshChart(true);
    }

    @Override
    public List getDefinedFilters() {
        ArrayList<IFilter> result = new ArrayList<IFilter>();
        if (chartPanel.definedFilters.size() > 0) {
            for (Date[] dates : chartPanel.definedFilters) {
                result.add(new IQueryFilter() {
                    private Query query;

                    public String toString() {
                        String timeFilter = domainAxis.ISO8601DateFormatUTC(dates[0]);
                        timeFilter += " TO ";
                        timeFilter += domainAxis.ISO8601DateFormatUTC(dates[1]);
                        return timeFilter;
                    }

                    @Override
                    public Query getQuery() {
                        if (query == null) {
                            String timeFilter = "timeStamp:[";
                            timeFilter += domainAxis.ISO8601DateFormatUTC(dates[0]);
                            timeFilter += " TO ";
                            timeFilter += domainAxis.ISO8601DateFormatUTC(dates[1]);
                            timeFilter += "]";

                            try {
                                query = new QueryBuilder(App.get().appCase).getQuery(timeFilter);
                            } catch (ParseException | QueryNodeException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                        }
                        return query;
                    }
                });
            }
        }
        for (String event : chartPanel.excludedEvents) {
            result.add(new IFilter() {
                public String toString() {
                    return "-eventType:" + event;
                }
            });
        }

        return result;
    }

    public String toString() {
        return "Timeline panel";
    }

    @Override
    public boolean hasFilters() {
        return chartPanel.definedFilters.size() > 0 || chartPanel.excludedEvents.size() > 0;
    }

    public static String[] getOrdToEventName() {
        return ordToEventName;
    }
    
}