package iped.viewers.timelinegraph;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.geom.Ellipse2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.ToolTipManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.util.BytesRef;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.LegendItem;
import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.axis.DateTickMarkPosition;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.XYToolTipGenerator;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYBarPainter;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
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
import iped.app.ui.App;
import iped.app.ui.ClearFilterListener;
import iped.app.ui.ColumnsManager;
import iped.app.ui.events.RowSorterTableDataChange;
import iped.data.IItemId;
import iped.data.IMultiBookmarks;
import iped.engine.search.QueryBuilder;
import iped.engine.task.index.IndexItem;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.utils.IconUtil;
import iped.viewers.api.GUIProvider;
import iped.viewers.api.IMultiSearchResultProvider;
import iped.viewers.api.IQueryFilterer;
import iped.viewers.api.ResultSetViewer;
import iped.viewers.timelinegraph.datasets.AsynchronousDataset;
import iped.viewers.timelinegraph.datasets.IpedTimelineDatasetManager;
import iped.viewers.timelinegraph.swingworkers.CheckWorker;
import iped.viewers.timelinegraph.swingworkers.HighlightWorker;

public class IpedChartsPanel extends JPanel implements ResultSetViewer, TableModelListener, ListSelectionListener, IQueryFilterer, ClearFilterListener {
	JTable resultsTable;
	IMultiSearchResultProvider resultsProvider;
	GUIProvider guiProvider;
	private DefaultSingleCDockable dockable;
	CDockableLocationListener dockableLocationListener;
	IpedTimelineDatasetManager ipedTimelineDatasetManager;
	
	static ThreadPoolExecutor swExecutor = new ThreadPoolExecutor(1, 1,
            20000, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>());
	
	boolean syncWithTableSelection = false;
	
    LegendItemCollection legendItems = null;
    
    Class<? extends TimePeriod> timePeriodClass = Day.class;
    String timePeriodString = "Day";
    
    TreeMap<String, String> timeEventColumnNamesList = new TreeMap<String, String>();
	private Boolean timeEventColumnNamesListDone=false;

	boolean isUpdated = false;

    String[] timeFields = { BasicProps.TIMESTAMP, BasicProps.TIME_EVENT };

	/* chart fields */
    IpedDateAxis domainAxis = new IpedDateAxis("Date ("+timePeriodString+")", this);
    IpedCombinedDomainXYPlot combinedPlot = new IpedCombinedDomainXYPlot(this);
    JFreeChart chart = new JFreeChart(combinedPlot);
    IpedChartPanel chartPanel = null;
	IpedStackedXYBarRenderer renderer = null;
	XYLineAndShapeRenderer highlightsRenderer = new XYLineAndShapeRenderer();
	XYToolTipGenerator toolTipGenerator = null;
	
	String metadataToBreakChart = null;
	ImageIcon loading = null;
	JLabel loadingLabel;

    boolean applyFilters = false;
	private XYBarPainter barPainter;
	private boolean internalUpdate;
	private TimeZone timeZone = TimeZone.getDefault();
	private Locale locale = Locale.getDefault();
	
	SortedSetDocValues timeStampValues = null;
	private RunnableFuture<Void> swRefresh;
	private HashMap<String, AbstractIntervalXYDataset> result;
	
	private static final String resPath = '/' + App.class.getPackageName().replace('.', '/') + '/';
	
	
	public IpedChartsPanel() {
		this(true);
		if(chart.getTitle()!=null) {
    		chart.getTitle().setVerticalAlignment(VerticalAlignment.CENTER);
		}
		combinedPlot.setDomainPannable(true);

		toolTipGenerator=new  XYToolTipGenerator() {
			@Override
			public String generateToolTip(XYDataset dataset, int series, int item) {
				String html;

				return "<html>"+dataset.getSeriesKey(series)+":"+dataset.getYValue(series, item)+"</html>";
			}
		};
	}
	
	public IpedChartsPanel(boolean b) {
		super(b);

		this.setLayout(new BorderLayout());
	}

	@Override
	public void init(JTable resultsTable, IMultiSearchResultProvider resultsProvider, GUIProvider guiProvider) {
		this.resultsTable = resultsTable;
		this.resultsProvider = resultsProvider;
		this.guiProvider = guiProvider;

		chartPanel = new IpedChartPanel(chart, this);
		
		renderer = new IpedStackedXYBarRenderer(this);
		((IpedStackedXYBarRenderer)renderer).setBarPainter(new IpedXYBarPainter((XYBarRenderer)renderer));
		((IpedStackedXYBarRenderer)renderer).setMargin(0);
		renderer.setDefaultToolTipGenerator(toolTipGenerator);
		renderer.setDefaultItemLabelsVisible(true);
		
        resultsTable.getModel().addTableModelListener(this);
        resultsTable.getSelectionModel().addListSelectionListener(this);

        chartPanel.setDomainZoomable(true);
        chartPanel.setRangeZoomable(true);
        chartPanel.setDisplayToolTips(true);
        ToolTipManager ttm = ToolTipManager.sharedInstance();
        ttm.setInitialDelay(0);
        ttm.setReshowDelay(0);
        ttm.setEnabled(true);

        this.add(chartPanel, BorderLayout.CENTER);
        chartPanel.setPopupMenu(null);
		
        domainAxis.setTickMarkPosition(DateTickMarkPosition.START);
        domainAxis.setLowerMargin(0.01);
        domainAxis.setUpperMargin(0.01);
        combinedPlot.setDomainAxis(domainAxis);
        
        loading = (ImageIcon) new ImageIcon(IconUtil.class.getResource(resPath + "loading.gif"));
        loadingLabel = new JLabel("", loading, JLabel.CENTER);
        
        
        ipedTimelineDatasetManager = new IpedTimelineDatasetManager(this);
	}
	
	public String getTimeEventColumnName(String timeEvent) {
		synchronized (timeEventColumnNamesList) {
			return timeEventColumnNamesList.get(timeEvent);
		}
	}

	public IpedDateAxis getDomainAxis() {
		return domainAxis;
	}

	public HashMap<String, XYDataset> createAlertDataSets(){
		HashMap<String, XYDataset> result = new HashMap<String, XYDataset>();
		
		DefaultXYDataset highlights = new DefaultXYDataset();

		double[][] data = new double[2][2];
		
		Calendar cal = Calendar.getInstance(timeZone);
		
		cal.set(2019, 01, 23, 11, 30);
		data[0][0]=cal.getTimeInMillis();
		data[1][0]=11;
		cal.set(2021, 02, 23, 11, 30);
		data[0][1]=cal.getTimeInMillis();
		data[1][1]=21;

		highlights.addSeries("highlights", data);

		result.put("highlights", (XYDataset) highlights);

		return result;
	}

	public HashMap<String, AbstractIntervalXYDataset> createDataSets(){
		result = new HashMap<String, AbstractIntervalXYDataset>();
		try {
	        IMultiBookmarks multiBookmarks = App.get().getIPEDSource().getMultiBookmarks();

	        Set<String> selectedBookmarks = guiProvider.getSelectedBookmarks();
	        Set<String> selectedCategories = guiProvider.getSelectedCategories();
			SortedSetDocValues categoriesValues = null;

	        if(selectedBookmarks.size()>0 && chartPanel.getSplitByBookmark()) {
	        	for(String bookmark:selectedBookmarks) {
					result.put(bookmark, ipedTimelineDatasetManager.getBestDataset(timePeriodClass, bookmark));
	        	}
	        }else if(selectedCategories.size()>0 && chartPanel.getSplitByCategory()) {
	        	for(String category:selectedCategories) {
					result.put(category, ipedTimelineDatasetManager.getBestDataset(timePeriodClass, category));
	        	}
	        }else {
				result.put("Items", ipedTimelineDatasetManager.getBestDataset(timePeriodClass, null));
	        }
			return result;
		}catch(Exception e) {
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
    
    Semaphore resultSemaphore = new Semaphore(1);
    
	public void refreshChart() {
		try {
			IpedChartsPanel self = this;
			
			Runnable drawTicker = new Runnable() {
				@Override
				public void run() {
					chartPanel.setVisible(false);
		            self.add(loadingLabel);
				}
			};
			
			drawTicker.run();

			if(swRefresh!=null) {
				synchronized (swRefresh) {
					swRefresh.cancel(true);
				}
			}

			resultSemaphore.acquire();
			try {
				if(result!=null) {
					synchronized (result) {
						for (AbstractIntervalXYDataset ds: result.values()) {
							if(ds instanceof AsynchronousDataset) {
								((AsynchronousDataset)ds).cancel();
							}
						}
					}
				}
			}finally {
				resultSemaphore.release();
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
				public Void get(long timeout, TimeUnit unit)
						throws InterruptedException, ExecutionException, TimeoutException {
					return null;
				}

				@Override
				public void run() {
					try {
						createDataSets();
						resultSemaphore.release();
						if(!isCancelled()) {
							JFreeChart chart = null;
							if(result!=null && result.size()>0) {
								chart = createChart(result);
						        isUpdated=true;
							}
							
							if(chart!=null) {
								SwingUtilities.invokeLater(new Runnable() {
									public void run() {
						                self.remove(loadingLabel);
						            	self.add(chartPanel);
										chartPanel.setVisible(true);
									}
								});
							}
						}
					}catch(Exception e) {
						e.printStackTrace();
					}finally {
					}
				}
			};
			
			resultSemaphore.acquire();
			swExecutor.execute(swRefresh);
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public void setTimePeriodClass(Class<? extends TimePeriod> timePeriodClass) {
		this.timePeriodClass = timePeriodClass;
	}

	
	public void addEventLegent(String event) {
		Iterator<LegendItem> it = legendItems.iterator();
		for (Iterator iterator = legendItems.iterator(); iterator.hasNext();) {
			LegendItem legendItem = (LegendItem) iterator.next();
			if(event.equals(legendItem.getLabel())) {
				return;
			}
		}
        LegendItem item1 = new LegendItem(event, new Color(0x22, 0x22, 0xFF));

        legendItems.add(item1);
	}

	@Override
	public void setDockableContainer(DefaultSingleCDockable dockable) {
		if(this.dockable!=null) {
			this.dockable.removeCDockableLocationListener(dockableLocationListener);
		}

        this.dockable = dockable;
        final IpedChartsPanel self = this;

        dockableLocationListener = new CDockableLocationListener() {
			@Override
			public void changed(CDockableLocationEvent dockableEvent) {
				if(!isUpdated && dockableEvent.isShowingChanged()) {
	    			self.refreshChart();
				}	
			}
		};
		
        dockable.addCDockableLocationListener(dockableLocationListener);
	}
	
	public void cancel() {
		if(swRefresh!=null) {
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

	@Override
    public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
            return;
        }else {
        	if(syncWithTableSelection){
            	try {
            		Date min=null;
            		Date max=null;

                    LeafReader reader = resultsProvider.getIPEDSource().getLeafReader();
            		timeStampValues = reader.getSortedSetDocValues(BasicProps.TIMESTAMP);
                	
                    ListSelectionModel lsm = (ListSelectionModel) e.getSource();
                    for (int i = e.getFirstIndex(); i <= e.getLastIndex(); i++) {
                        boolean selected = lsm.isSelectedIndex(i);

                        int rowModel = resultsTable.convertRowIndexToModel(i);
                        IItemId item = resultsProvider.getResults().getItem(rowModel);

                        int luceneId = resultsProvider.getIPEDSource().getLuceneId(item);
                        boolean adv = timeStampValues.advanceExact(luceneId);

                        long ord, prevOrd = -1;
                        while (adv && (ord = timeStampValues.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
                            if (prevOrd != ord) {
                        		Date d = domainAxis.ISO8601DateParse(timeStampValues.lookupOrd(ord).utf8ToString());
                        		if(min==null || d.before(min)) {
                        			min=d;
                        		}
                        		if(max==null || d.after(max)) {
                        			max=d;
                        		}
                            }
                            prevOrd = ord;
                        }
                    }
                    
                    domainAxis.guaranteeShowRange(min,max);
            	}catch(Exception e1) {
            		e1.printStackTrace();
            	}
        	}
        }
    }

    Thread t = new Thread(new Runnable() {
		//populates list with timeevent column names with uppercase letters
		@Override
		public void run() {
			ColumnsManager cm = (ColumnsManager)((App)resultsProvider).getColumnsManager();
			String[] columnsArray=cm.fieldGroups[cm.fieldGroups.length - 1];
			synchronized (timeEventColumnNamesList) {
				LeafReader reader = resultsProvider.getIPEDSource().getLeafReader();
				try {
					SortedSetDocValues timeEventGroupValues = reader.getSortedSetDocValues(ExtraProperties.TIME_EVENT_GROUPS);
					TermsEnum te = timeEventGroupValues.termsEnum();
					BytesRef br = te.next();
					while(br!=null) {
						String eventTypes = br.utf8ToString();
						StringTokenizer st = new StringTokenizer(eventTypes, "|");
						while(st.hasMoreTokens()) {
							String eventType = st.nextToken().trim();
							for(int i=0; i<columnsArray.length; i++) {
								if(columnsArray[i].toLowerCase().equals(eventType)) {
									timeEventColumnNamesList.put(eventType, columnsArray[i]);
								}
							}							
						}
						br = te.next();
					}
					timeEventColumnNamesListDone=true;
					timeEventColumnNamesList.notify();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	});
    
	@Override
	public void tableChanged(TableModelEvent e) {
		if((e instanceof RowSorterTableDataChange)) {
			return;
		}
		if(timeEventColumnNamesList.size()==0 && !t.isAlive()) {
			t.start();
			ipedTimelineDatasetManager.startBackgroundCacheCreation();
		}
		
		if(internalUpdate) {
			internalUpdate=false;
			return;
		}
		//all data changed
		if(e.getFirstRow()==0 && e.getLastRow()==Integer.MAX_VALUE) {
            /* somente chamado se o tab estiver sendo exibido */
            if (dockable != null && dockable.isShowing()) {
    			refreshChart();
			}else {
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
    	if(firstPlot!=null) {
    		highlightsRenderer.setDefaultLinesVisible(false);
    		highlightsRenderer.setDrawSeriesLineAsPath(false);
    		highlightsRenderer.setDrawSeriesLineAsPath(false);
    		highlightsRenderer.setSeriesShape(0,new Ellipse2D.Float(1, 1, 10, 10));

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

    private JFreeChart createChart(HashMap<String, AbstractIntervalXYDataset> datasets) {
    	domainAxis.setLabel("Date ("+timePeriodString+") ["+timeZone.getDisplayName()+" "+DateUtil.getTimezoneOffsetInformation(timeZone)+"]");
    	
    	combinedPlot.setSkipFireEventChange(true);

    	Object[] plots = combinedPlot.getSubplots().toArray();
    	for (Object plot : plots) {
	    	combinedPlot.remove((XYPlot) plot);
		}
    	combinedPlot.removeAllDataSets();

    	combinedPlot.setSkipFireEventChange(false);

    	combinedPlot.fireChangeEvent();

    	combinedPlot.setSkipFireEventChange(true);

        XYItemRenderer renderer = getRenderer();
        combinedPlot.setRenderer(renderer);

        int ids = 0;
        XYPlot firstPlot = null;
    	for (Iterator iterator = datasets.keySet().iterator(); iterator.hasNext();) {
			String marcador = (String) iterator.next();			
	    	NumberAxis rangeAxis = new NumberAxis(marcador);
	        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
	        rangeAxis.setUpperMargin(0.10);  // leave some space for item labels

	        AbstractIntervalXYDataset dataset = (AbstractIntervalXYDataset)datasets.get(marcador);
	        
	        boolean canceled = false;
	        if(dataset instanceof AsynchronousDataset) {
	        	canceled = ((AsynchronousDataset) dataset).waitLoaded();	        	
	        }
	        if(canceled) {
	        	combinedPlot.setSkipFireEventChange(false);
		    	combinedPlot.fireChangeEvent();
	        	return null;
	        }
	        XYPlot plot = new IpedXYPlot(chartPanel, dataset, domainAxis, rangeAxis, renderer);
	        if(firstPlot==null) {
	        	firstPlot=plot;
	        }
	        combinedPlot.add(ids,plot,dataset);
	        ids++;
		}

    	combinedPlot.setSkipFireEventChange(false);
    	combinedPlot.fireChangeEvent();
    	
        return chart;
    }

	@Override
	public Query getQuery() {
		try {
			if(applyFilters) {
				if(chartPanel!=null) {
					if(chartPanel.hasNoFilter()) {
						return null;
					}

					Query result = new QueryBuilder(App.get().appCase).getQuery("");

		            if(chartPanel.definedFilters.size()>0) {
						String timeFilter = "(";
						int i=0;
						for (Date[] dates : chartPanel.definedFilters) {
							timeFilter+="timeStamp:[";
							timeFilter+=domainAxis.ISO8601DateFormat(dates[0]);
							timeFilter+=" TO ";
							timeFilter+=domainAxis.ISO8601DateFormat(dates[1]);
							timeFilter+="]";
							i++;
							if(i!=chartPanel.definedFilters.size()) {
								timeFilter+=" || ";
							}
						}
						timeFilter += ")";

			            BooleanQuery.Builder boolQuery = new BooleanQuery.Builder();
			            boolQuery.add(new QueryBuilder(App.get().appCase).getQuery(timeFilter), Occur.MUST);
			            boolQuery.add(result, Occur.MUST);
			            result = boolQuery.build();
					}

					if(chartPanel.getExcludedEvents().size()>0) {
						String eventsFilter = "-(";
						int i=0;
						for (String event: chartPanel.getExcludedEvents()) {
							eventsFilter+="timeEvent:\"";
							eventsFilter+=event;
							eventsFilter+="\"";
							i++;
							if(i!=chartPanel.getExcludedEvents().size()) {
								eventsFilter+=" || ";
							}
						}
						eventsFilter += ")";

			            BooleanQuery.Builder boolQuery = new BooleanQuery.Builder();
			            boolQuery.add(new QueryBuilder(App.get().appCase).getQuery(eventsFilter), Occur.MUST);
			            boolQuery.add(result, Occur.MUST);
			            result = boolQuery.build();
					}

					return result;
				}else {
					return null;
				}
			}else {
				return null;
			}
		}catch(QueryNodeException | ParseException qne) {
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
		chartPanel.removeAllFilters();
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
		HighlightWorker sw = new HighlightWorker(domainAxis,resultsProvider, firstDate, endDate, b);
		sw.execute();
	}
	
	public void checkItemsOnInterval(Date firstDate, Date endDate, boolean b) {
		CheckWorker sw = new CheckWorker(domainAxis,resultsProvider, firstDate, endDate, b);
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
			return t.getEnd().getTime()-t.getStart().getTime();
		}catch (Exception e) {
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
}