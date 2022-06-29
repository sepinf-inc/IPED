package iped.viewers.timelinegraph;

import java.awt.BorderLayout;
import java.awt.Color;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ToolTipManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.search.Query;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.LegendItem;
import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.DateTickMarkPosition;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.XYToolTipGenerator;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StackedXYBarRenderer;
import org.jfree.chart.renderer.xy.XYBarPainter;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.VerticalAlignment;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimePeriod;
import org.jfree.data.xy.XYDataset;

import bibliothek.gui.dock.common.DefaultSingleCDockable;
import bibliothek.gui.dock.common.event.CDockableLocationEvent;
import bibliothek.gui.dock.common.event.CDockableLocationListener;
import iped.data.IItemId;
import iped.data.IMultiBookmarks;
import iped.engine.lucene.DocValuesUtil;
import iped.engine.search.QueryBuilder;
import iped.engine.search.TimelineResults.TimeItemId;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IMultiSearchResult;
import iped.viewers.api.GUIProvider;
import iped.viewers.api.IMultiSearchResultProvider;
import iped.viewers.api.IQueryFilterer;
import iped.viewers.api.ResultSetViewer;

public class IpedChartsPanel extends JPanel implements ResultSetViewer, TableModelListener, ListSelectionListener, IQueryFilterer {

	JTable resultsTable;
	IMultiSearchResultProvider resultsProvider;
	GUIProvider guiProvider;
	private DefaultSingleCDockable dockable;
	CDockableLocationListener dockableLocationListener;
	
	IMultiSearchResult sourceSearchResults;	
    SimpleDateFormat ISO8601DATEFORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");
    LegendItemCollection legendItems = null;
    
    Class<? extends TimePeriod> timePeriodClass = Day.class;
    String timePeriodString = "Day";

	boolean isUpdated = false;

    String[] timeFields = { BasicProps.TIMESTAMP, BasicProps.TIME_EVENT };

	/* chart fields */
    DateAxis domainAxis = new DateAxis("Date ("+timePeriodString+")");
    IpedCombinedDomainXYPlot combinedPlot = new IpedCombinedDomainXYPlot();
    JFreeChart chart = new JFreeChart(combinedPlot);
    IpedChartPanel chartPanel = new IpedChartPanel(chart, this);
	StackedXYBarRenderer stackedRenderer = new StackedXYBarRenderer(0.15);
	XYLineAndShapeRenderer lineRenderer = new XYLineAndShapeRenderer();
	XYItemRenderer renderer = null;
	XYToolTipGenerator toolTipGenerator = null;
    
    boolean applyFilters = false;
	private XYBarPainter barPainter;

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
		
		stackedRenderer.setDefaultToolTipGenerator(toolTipGenerator);
		stackedRenderer.setDefaultItemLabelsVisible(true);
		stackedRenderer.setDrawBarOutline(false);

		lineRenderer.setDefaultShapesVisible(true);
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

        resultsTable.getModel().addTableModelListener(this);
        resultsTable.getSelectionModel().addListSelectionListener(this);

        chartPanel.setMouseWheelEnabled(true);
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
	}

	public DateAxis getDomainAxis() {
		return domainAxis;
	}

	public void unselectItemsOnInterval(Date start, Date end, boolean clearPreviousSelection) {
		selectItemsOnInterval(start, end, false, clearPreviousSelection);
	}

	public void selectItemsOnInterval(Date start, Date end, boolean clearPreviousSelection) {
		selectItemsOnInterval(start, end, true, clearPreviousSelection);
	}

	public void selectItemsOnInterval(Date start, Date end, boolean select, boolean clearPreviousSelection) {
		JTable t = resultsProvider.getResultsTable();
		t.getSelectionModel().setValueIsAdjusting(true);

		if(clearPreviousSelection) {
			t.clearSelection();
		}

        LeafReader reader = resultsProvider.getIPEDSource().getLeafReader();

        SortedSetDocValues timeStampValues;
        SortedSetDocValues timeEventGroupValues;

        try {
            timeStampValues = reader.getSortedSetDocValues(BasicProps.TIMESTAMP);
    		timeEventGroupValues = reader.getSortedSetDocValues(ExtraProperties.TIME_EVENT_GROUPS);
    		BinaryDocValues eventsInDocOrdsValues = reader.getBinaryDocValues(ExtraProperties.TIME_EVENT_ORDS);
    		
    		System.out.println("selecao iniciando");

    		for (int i = 0; i < resultsProvider.getResults().getLength(); i++) {
                IItemId item = resultsProvider.getResults().getItem(i);

                int luceneId = resultsProvider.getIPEDSource().getLuceneId(item);
                
                boolean tsvAdv = timeStampValues.advanceExact(luceneId);
                
                if(tsvAdv) {
                	long ord;
    	            while ((ord = timeStampValues.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
    	                if (ord > Integer.MAX_VALUE) {
    	                    throw new RuntimeException("Integer overflow when converting timestamp ord to int");
    	                }

    	                Date d = ISO8601DATEFORMAT.parse(timeStampValues.lookupOrd(ord).utf8ToString());
    	                if(start.getTime()<=d.getTime() && end.getTime()>=d.getTime()) {
    		                int row = t.convertRowIndexToView(i);
    		                if(select) {
        		                t.addRowSelectionInterval(row, row);
    		                }else {
    		                	t.removeRowSelectionInterval(row, row);
    		                }
    	                }
    	            }            	
                }
            }

    		System.out.println("selecao finalizada");
        }catch(Exception e) {
        	e.printStackTrace();
        }finally {
    		t.getSelectionModel().setValueIsAdjusting(false);
		}
	}
	
	public HashMap<String, TimeTableCumulativeXYDataset> createDataSets(){
		HashMap<String, TimeTableCumulativeXYDataset> result = new HashMap<String, TimeTableCumulativeXYDataset>();

		try {
	        sourceSearchResults = resultsProvider.getResults();

	        LeafReader reader = resultsProvider.getIPEDSource().getLeafReader();

	        Set<String> selectedBookmarks = guiProvider.getSelectedBookmarks();

            HashSet<TimeTableCumulativeXYDataset> datasetsToIncludeItem = new HashSet<TimeTableCumulativeXYDataset>();
            if(selectedBookmarks.size()>0) {
            	for (Iterator<String> iterator = selectedBookmarks.iterator(); iterator.hasNext();) {
					String bookmark = (String) iterator.next();
					TimeTableCumulativeXYDataset dataset = new TimeTableCumulativeXYDataset();
					result.put(bookmark, dataset);
				}
            }else{
            	TimeTableCumulativeXYDataset dataset = new TimeTableCumulativeXYDataset();
            	result.put("Items", dataset);
            	datasetsToIncludeItem.add(dataset);
            }

	        IMultiBookmarks multiBookmarks = resultsProvider.getIPEDSource().getMultiBookmarks();

	        SortedSetDocValues timeStampValues;
	        SortedSetDocValues timeEventGroupValues;

	        int[] eventOrd = new int[Short.MAX_VALUE];
	        int[][] eventsInDocOrds = new int[Short.MAX_VALUE][1 << 9];

	        timeStampValues = reader.getSortedSetDocValues(BasicProps.TIMESTAMP);
			timeEventGroupValues = reader.getSortedSetDocValues(ExtraProperties.TIME_EVENT_GROUPS);
			BinaryDocValues eventsInDocOrdsValues = reader.getBinaryDocValues(ExtraProperties.TIME_EVENT_ORDS);
			
			if(sourceSearchResults.getLength()>0) {
		        for (IItemId item : sourceSearchResults.getIterator()) {
		            int luceneId = resultsProvider.getIPEDSource().getLuceneId(item);

		            /* locate all selected bookmarks corresponding datasets to include item */
		            if(selectedBookmarks.size()>0) {
			            datasetsToIncludeItem.clear();

		            	List<String> itemBookmars = multiBookmarks.getBookmarkList(item);
			            for (Iterator iterator = itemBookmars.iterator(); iterator.hasNext();) {
							String bookmark = (String) iterator.next();
							if(selectedBookmarks.contains(bookmark)) {
								TimeTableCumulativeXYDataset dataset = result.get(bookmark);
								datasetsToIncludeItem.add(dataset);
							}
						}
		            }

		            String eventsInDocStr = DocValuesUtil.getVal(eventsInDocOrdsValues, luceneId);
		            if (eventsInDocStr.isEmpty()) {
		                continue;
		            }

		            boolean tsvAdv = timeStampValues.advanceExact(luceneId);
		            boolean tegvAdv = timeEventGroupValues.advanceExact(luceneId);

		            long ord;
		            short pos = 0;
		            while (tegvAdv && (ord = timeEventGroupValues.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
		                for (int k : eventsInDocOrds[pos++]) {
		                    if (k == -1) {
		                        break;
		                    }
		                    eventOrd[k] = (int) ord;
		                }
		            }
		            pos = 0;
	           		if(item instanceof TimeItemId) {
	           			//on timeline view
	           			TimeItemId timeItemId = (TimeItemId) item;
	               		TimePeriod t = DateUtil.getDateOnConfiguredTimePeriod(timePeriodClass,ISO8601DATEFORMAT.parse(timeStampValues.lookupOrd(timeItemId.getTimeStampOrd()).utf8ToString()));
	               		
	               		if(t!=null) {
		               		String events = timeEventGroupValues.lookupOrd(timeItemId.getTimeEventOrd()).utf8ToString();
		               		StringTokenizer st = new StringTokenizer(events, "|");
		               		while(st.hasMoreTokens()) {
		               			String event = st.nextToken().trim();
		               			
		            			for (Iterator iterator = datasetsToIncludeItem.iterator(); iterator.hasNext();) {
									TimeTableCumulativeXYDataset dataset = (TimeTableCumulativeXYDataset) iterator.next();
									dataset.add(t, 1, event, item, false);
								}
		               		}	               		
	               		}
	           		}else {
			            while (tsvAdv && (ord = timeStampValues.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
			                if (ord > Integer.MAX_VALUE) {
			                    throw new RuntimeException("Integer overflow when converting timestamp ord to int");
			                }

			               	try {
			               		TimePeriod t = DateUtil.getDateOnConfiguredTimePeriod(timePeriodClass, ISO8601DATEFORMAT.parse(timeStampValues.lookupOrd(ord).utf8ToString()));

			               		if(t!=null) {
				               		String events = timeEventGroupValues.lookupOrd(eventOrd[pos++]).utf8ToString();
				               		StringTokenizer st = new StringTokenizer(events, "|");
				               		while(st.hasMoreTokens()) {
				               			String event = st.nextToken().trim();
				            			for (Iterator iterator = datasetsToIncludeItem.iterator(); iterator.hasNext();) {
											TimeTableCumulativeXYDataset dataset = (TimeTableCumulativeXYDataset) iterator.next();
											dataset.add(t, 1, event, item, false);
										}
				               		}
			               		}else {
			               			pos++;
			               		}

							} catch (ParseException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
			            }
	           		}
		        }
			}

			for (String series : result.keySet()) {
				System.out.println(series);
				int sc = ((TimeTableCumulativeXYDataset)result.get(series)).getSeriesCount();
				System.out.println(sc);
			}

			return result;
		}catch(Exception e) {
			e.printStackTrace();
			return null;			
		}
		
	}

	public void refreshChart() {
		try {
			HashMap<String, TimeTableCumulativeXYDataset> result = createDataSets();
			if(result!=null && result.size()>0) {
				createChart(result);
				chartPanel.removeAllFilters();
		        isUpdated=true;
			}
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
	    			self.repaint();
				}	
			}
		};
		
        dockable.addCDockableLocationListener(dockableLocationListener);
        
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
        if (e.getValueIsAdjusting())
            return;

    }

	@Override
	public void tableChanged(TableModelEvent e) {
		//all data changed
		if(e.getFirstRow()==0 && e.getLastRow()==Integer.MAX_VALUE) {
            /* somente chamado se o tab estiver sendo exibido */
            if (dockable != null && dockable.isShowing()) {
    			refreshChart();

    			this.repaint();
			}else {
				isUpdated = false;
			}
		}
		// TODO Auto-generated method stub
	}

    public XYItemRenderer getRenderer() {
		renderer = new IpedStackedXYBarRenderer();
		((IpedStackedXYBarRenderer)renderer).setBarPainter(new IpedXYBarPainter((XYBarRenderer)renderer));
		((IpedStackedXYBarRenderer)renderer).setMargin(0.15);
		renderer.setDefaultToolTipGenerator(toolTipGenerator);
		renderer.setDefaultItemLabelsVisible(true);

		return renderer;
    }
    
    private JFreeChart createChart(HashMap<String, TimeTableCumulativeXYDataset> datasets) {
    	domainAxis.setLabel("Date ("+timePeriodString+")");
    	
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
    	for (Iterator iterator = datasets.keySet().iterator(); iterator.hasNext();) {
			String marcador = (String) iterator.next();			
	    	NumberAxis rangeAxis = new NumberAxis(marcador);
	        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
	        rangeAxis.setUpperMargin(0.10);  // leave some space for item labels

	        TimeTableCumulativeXYDataset dataset = (TimeTableCumulativeXYDataset)datasets.get(marcador);
	        XYPlot plot = new IpedXYPlot(chartPanel, dataset, domainAxis, rangeAxis, renderer);
	        combinedPlot.add(ids,plot,dataset);
	        ids++;
		}

    	combinedPlot.setSkipFireEventChange(false);
    	combinedPlot.fireChangeEvent();

        return chart;
    }

	@Override
	public Query getQuery() {
		if(applyFilters) {
			if(chartPanel!=null) {
				String timeFilter = "(";
				for (Date[] dates : chartPanel.definedFilters) {
					timeFilter+="timeStamp:[";
					timeFilter+=ISO8601DATEFORMAT.format(dates[0]);
					timeFilter+=" TO ";
					timeFilter+=ISO8601DATEFORMAT.format(dates[1]);
					timeFilter+="]";
					timeFilter+=" || ";
				}	
				timeFilter += " false )";
				try {
					Query q = new QueryBuilder(getResultsProvider().getIPEDSource()).getQuery(timeFilter);
					applyFilters=false;
					return q;
				}catch (Exception e) {
					e.printStackTrace();
					return null;
				}
			}else {
				return null;
			}
		}else {
			return null;
		}
	}

	public IMultiSearchResultProvider getResultsProvider() {
		return this.resultsProvider;
	}
    
    public void setTimePeriodString(String timePeriodString) {
		this.timePeriodString = timePeriodString;
	}

	public boolean isApplyFilters() {
		return applyFilters;
	}

	public void setApplyFilters(boolean applyFilters) {
		this.applyFilters = applyFilters;
	}
}