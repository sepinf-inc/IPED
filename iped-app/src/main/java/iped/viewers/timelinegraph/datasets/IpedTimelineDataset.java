package iped.viewers.timelinegraph.datasets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.SortedSet;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;
import org.jfree.chart.util.Args;
import org.jfree.chart.util.PublicCloneable;
import org.jfree.data.DefaultKeyedValues2D;
import org.jfree.data.DomainInfo;
import org.jfree.data.DomainOrder;
import org.jfree.data.Range;
import org.jfree.data.general.DatasetChangeEvent;
import org.jfree.data.time.TimePeriod;
import org.jfree.data.time.TimePeriodAnchor;
import org.jfree.data.time.TimeTableXYDataset;
import org.jfree.data.xy.AbstractIntervalXYDataset;
import org.jfree.data.xy.IntervalXYDataset;
import org.jfree.data.xy.TableXYDataset;

import iped.app.ui.CaseSearcherFilter;
import iped.data.IItemId;
import iped.properties.ExtraProperties;
import iped.search.IMultiSearchResult;
import iped.viewers.api.IMultiSearchResultProvider;
import iped.viewers.timelinegraph.IpedChartsPanel;

public class IpedTimelineDataset extends AbstractIntervalXYDataset
									implements Cloneable, PublicCloneable, IntervalXYDataset, DomainInfo, TimelineDataset,
									TableXYDataset {
    IMultiSearchResultProvider resultsProvider;    
    CaseSearchFilterListenerFactory cacheFLFactory;
    /**
     * A flag that indicates that the domain is 'points in time'.  If this flag
     * is true, only the x-value (and not the x-interval) is used to determine
     * the range of values in the domain.
     */
    private boolean domainIsPointsInTime;

    /**
     * The point within each time period that is used for the X value when this
     * collection is used as an {@link org.jfree.data.xy.XYDataset}.  This can
     * be the start, middle or end of the time period.
     */
    private TimePeriodAnchor xPosition;

    /** A working calendar (to recycle) */
    private Calendar workingCalendar;
    
    DefaultKeyedValues2D values;

    SortedSetDocValues timeEventGroupValues;
    IpedChartsPanel ipedChartsPanel;

    SortedSet<String> eventTypes = new TreeSet<String>();
    String[] eventTypesArray;
	LeafReader reader;

	class Count extends Number{
		int value=0;

		@Override
		public String toString() {
			return Integer.toString(value);
		}

		@Override
		public int intValue() {
			return value;
		}

		@Override
		public long longValue() {
			return value;
		}

		@Override
		public float floatValue() {
			return value;
		}

		@Override
		public double doubleValue() {
			return value;
		}
	}

	Date first;
	Date last;

	int itemCount=0;

	volatile int running=0;
	volatile int seriesCount=0;
	Object monitor = new Object();
	private String splitValue;

	public IpedTimelineDataset(IpedTimelineDatasetManager ipedTimelineDatasetManager, IMultiSearchResultProvider resultsProvider, CaseSearchFilterListenerFactory cacheFLFactory, String splitValue) throws IOException {
		this.ipedChartsPanel = ipedTimelineDatasetManager.ipedChartsPanel;
        Args.nullNotPermitted(ipedChartsPanel.getTimeZone(), "zone");
        Args.nullNotPermitted(ipedChartsPanel.getLocale(), "locale");
        this.workingCalendar = Calendar.getInstance(ipedChartsPanel.getTimeZone(), ipedChartsPanel.getLocale());
        this.values = new DefaultKeyedValues2D(true);
        this.xPosition = TimePeriodAnchor.START;
		this.resultsProvider = resultsProvider;
		this.cacheFLFactory = cacheFLFactory;
		this.splitValue = splitValue;

        reader = resultsProvider.getIPEDSource().getLeafReader();

        timeEventGroupValues = reader.getSortedSetDocValues(ExtraProperties.TIME_EVENT_GROUPS);

		TermsEnum te = timeEventGroupValues.termsEnum();
		BytesRef br = te.next();

		List<CaseSearcherFilter> csfs = new ArrayList<CaseSearcherFilter>();
		while(br!=null) {
       		StringTokenizer st = new StringTokenizer(br.utf8ToString(), "|");
       		while(st.hasMoreTokens()) {
       			String eventType = st.nextToken().trim();
       			if(eventTypes.add(eventType)) {
    				//escape : char
					String eventField = ipedChartsPanel.getTimeEventColumnName(eventType);
					if(eventField==null) continue;

					running++;
    				String eventTypeEsc = eventField.replaceAll(":", "\\\\:");
    				eventTypeEsc = eventTypeEsc.replaceAll("/", "\\\\/");
    				eventTypeEsc = eventTypeEsc.replaceAll(" ", "\\\\ ");
    				eventTypeEsc = eventTypeEsc.replaceAll("-", "\\\\-");
    				
    				String query = eventTypeEsc+":[\"\" TO *]";
    				
    				if(ipedChartsPanel.getChartPanel().getSplitByCategory() && splitValue!=null) {
    					query+=" && category=\""+splitValue+"\"";
    				}
    				
       				CaseSearcherFilter csf = new CaseSearcherFilter(query);
       				csf.getSearcher().setNoScoring(true);
       				csf.applyUIQueryFilters();
       				
       				IpedTimelineDataset self = this;

       				csf.addCaseSearchFilterListener(cacheFLFactory.getCaseSearchFilterListener(eventType, csf, this, splitValue));

       				IMultiSearchResult timelineSearchResults;
					csf.applyUIQueryFilters();
					csfs.add(csf);
       			}
       		}
       		br = te.next();
		}

		eventTypesArray=new String[running];
		
		ExecutorService threadPool = Executors.newFixedThreadPool(1);
		for(CaseSearcherFilter csf:csfs) {
			threadPool.execute(csf);
		}
		
		try {
			synchronized (monitor) {
				monitor.wait();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    }

	/**
     * Returns a flag that controls whether the domain is treated as 'points in
     * time'.
     * <P>
     * This flag is used when determining the max and min values for the domain.
     * If true, then only the x-values are considered for the max and min
     * values.  If false, then the start and end x-values will also be taken
     * into consideration.
     *
     * @return The flag.
     *
     * @see #setDomainIsPointsInTime(boolean)
     */
    public boolean getDomainIsPointsInTime() {
        return this.domainIsPointsInTime;
    }

    /**
     * Sets a flag that controls whether the domain is treated as 'points in
     * time', or time periods.  A {@link DatasetChangeEvent} is sent to all
     * registered listeners.
     *
     * @param flag  the new value of the flag.
     *
     * @see #getDomainIsPointsInTime()
     */
    public void setDomainIsPointsInTime(boolean flag) {
        this.domainIsPointsInTime = flag;
        notifyListeners(new DatasetChangeEvent(this, this));
    }

    /**
     * Returns the position within each time period that is used for the X
     * value.
     *
     * @return The anchor position (never {@code null}).
     *
     * @see #setXPosition(TimePeriodAnchor)
     */
    public TimePeriodAnchor getXPosition() {
        return this.xPosition;
    }

    /**
     * Sets the position within each time period that is used for the X values,
     * then sends a {@link DatasetChangeEvent} to all registered listeners.
     *
     * @param anchor  the anchor position ({@code null} not permitted).
     *
     * @see #getXPosition()
     */
    public void setXPosition(TimePeriodAnchor anchor) {
        Args.nullNotPermitted(anchor, "anchor");
        this.xPosition = anchor;
        notifyListeners(new DatasetChangeEvent(this, this));
    }

    /**
     * Returns the number of items in ALL series.
     *
     * @return The item count.
     */
    @Override
    public int getItemCount() {
        return this.values.getRowCount();
    }
    

    @Override
    public DomainOrder getDomainOrder() {
    	return DomainOrder.ASCENDING;
    }    

    /**
     * Returns the number of items in a series.  This is the same value
     * that is returned by {@link #getItemCount()} since all series
     * share the same x-values (time periods).
     *
     * @param series  the series (zero-based index, ignored).
     *
     * @return The number of items within the series.
     */
    @Override
    public int getItemCount(int series) {
        return this.values.getRowCount();
    }

    /**
     * Returns the number of series in the dataset.
     *
     * @return The series count.
     */
    @Override
    public int getSeriesCount() {
        return this.values.getColumnCount();
    }

    /**
     * Returns the key for a series.
     *
     * @param series  the series (zero-based index).
     *
     * @return The key for the series.
     */
    @Override
    public Comparable getSeriesKey(int series) {
        return this.values.getColumnKey(series);
    }

    /**
     * Returns the x-value for an item within a series.  The x-values may or
     * may not be returned in ascending order, that is up to the class
     * implementing the interface.
     *
     * @param series  the series (zero-based index).
     * @param item  the item (zero-based index).
     *
     * @return The x-value.
     */
    @Override
    public Number getX(int series, int item) {
        return getXValue(series, item);
    }

    /**
     * Returns the x-value (as a double primitive) for an item within a series.
     *
     * @param series  the series index (zero-based).
     * @param item  the item index (zero-based).
     *
     * @return The value.
     */
    @Override
    public double getXValue(int series, int item) {
        TimePeriod period = (TimePeriod) this.values.getRowKey(item);
        return getXValue(period);
    }

    /**
     * Returns the starting X value for the specified series and item.
     *
     * @param series  the series (zero-based index).
     * @param item  the item within a series (zero-based index).
     *
     * @return The starting X value for the specified series and item.
     *
     * @see #getStartXValue(int, int)
     */
    @Override
    public Number getStartX(int series, int item) {
        return getStartXValue(series, item);
    }

    /**
     * Returns the start x-value (as a double primitive) for an item within
     * a series.
     *
     * @param series  the series index (zero-based).
     * @param item  the item index (zero-based).
     *
     * @return The value.
     */
    @Override
    public double getStartXValue(int series, int item) {
        TimePeriod period = (TimePeriod) this.values.getRowKey(item);
        return period.getStart().getTime();
    }

    /**
     * Returns the ending X value for the specified series and item.
     *
     * @param series  the series (zero-based index).
     * @param item  the item within a series (zero-based index).
     *
     * @return The ending X value for the specified series and item.
     *
     * @see #getEndXValue(int, int)
     */
    @Override
    public Number getEndX(int series, int item) {
        return getEndXValue(series, item);
    }

    /**
     * Returns the end x-value (as a double primitive) for an item within
     * a series.
     *
     * @param series  the series index (zero-based).
     * @param item  the item index (zero-based).
     *
     * @return The value.
     */
    @Override
    public double getEndXValue(int series, int item) {
        TimePeriod period = (TimePeriod) this.values.getRowKey(item);
        return period.getEnd().getTime();
    }

    /**
     * Returns the y-value for an item within a series.
     *
     * @param series  the series (zero-based index).
     * @param item  the item (zero-based index).
     *
     * @return The y-value (possibly {@code null}).
     */
    @Override
    public Number getY(int series, int item) {
        return this.values.getValue(item, series);
    }


    /**
     * Returns the starting Y value for the specified series and item.
     *
     * @param series  the series (zero-based index).
     * @param item  the item within a series (zero-based index).
     *
     * @return The starting Y value for the specified series and item.
     */
    @Override
    public Number getStartY(int series, int item) {
        return getY(series, item);
    }

    /**
     * Returns the ending Y value for the specified series and item.
     *
     * @param series  the series (zero-based index).
     * @param item  the item within a series (zero-based index).
     *
     * @return The ending Y value for the specified series and item.
     */
    @Override
    public Number getEndY(int series, int item) {
        return getY(series, item);
    }

    /**
     * Returns the x-value for a time period.
     *
     * @param period  the time period.
     *
     * @return The x-value.
     */
    private long getXValue(TimePeriod period) {
        long result = 0L;
        if (this.xPosition == TimePeriodAnchor.START) {
            result = period.getStart().getTime();
        }
        else if (this.xPosition == TimePeriodAnchor.MIDDLE) {
            long t0 = period.getStart().getTime();
            long t1 = period.getEnd().getTime();
            result = t0 + (t1 - t0) / 2L;
        }
        else if (this.xPosition == TimePeriodAnchor.END) {
            result = period.getEnd().getTime();
        }
        return result;
    }


    /**
     * Returns the minimum x-value in the dataset.
     *
     * @param includeInterval  a flag that determines whether or not the
     *                         x-interval is taken into account.
     *
     * @return The minimum value.
     */
    @Override
    public double getDomainLowerBound(boolean includeInterval) {
        double result = Double.NaN;
        Range r = getDomainBounds(includeInterval);
        if (r != null) {
            result = r.getLowerBound();
        }
        return result;
    }

    /**
     * Returns the maximum x-value in the dataset.
     *
     * @param includeInterval  a flag that determines whether or not the
     *                         x-interval is taken into account.
     *
     * @return The maximum value.
     */
    @Override
    public double getDomainUpperBound(boolean includeInterval) {
        double result = Double.NaN;
        Range r = getDomainBounds(includeInterval);
        if (r != null) {
            result = r.getUpperBound();
        }
        return result;
    }


    /**
     * Returns the range of the values in this dataset's domain.
     *
     * @param includeInterval  a flag that controls whether or not the
     *                         x-intervals are taken into account.
     *
     * @return The range.
     */
    @Override
    public Range getDomainBounds(boolean includeInterval) {
        List keys = this.values.getRowKeys();
        if (keys.isEmpty()) {
            return null;
        }

        TimePeriod first = (TimePeriod) keys.get(0);
        TimePeriod last = (TimePeriod) keys.get(keys.size() - 1);

        if (!includeInterval || this.domainIsPointsInTime) {
            return new Range(getXValue(first), getXValue(last));
        }
        else {
            return new Range(first.getStart().getTime(),
                    last.getEnd().getTime());
        }
    }

    /**
     * Tests this dataset for equality with an arbitrary object.
     *
     * @param obj  the object ({@code null} permitted).
     *
     * @return A boolean.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof TimeTableXYDataset)) {
            return false;
        }
        IpedTimelineDataset that = (IpedTimelineDataset) obj;
        if (this.domainIsPointsInTime != that.domainIsPointsInTime) {
            return false;
        }
        if (this.xPosition != that.xPosition) {
            return false;
        }
        if (!this.workingCalendar.getTimeZone().equals(
            that.workingCalendar.getTimeZone())
        ) {
            return false;
        }
        if (!this.values.equals(that.values)) {
            return false;
        }
        return true;
    }

    /**
     * Returns a clone of this dataset.
     *
     * @return A clone.
     *
     * @throws CloneNotSupportedException if the dataset cannot be cloned.
     */
    @Override
    public Object clone() throws CloneNotSupportedException {
    	IpedTimelineDataset clone = (IpedTimelineDataset) super.clone();
        clone.values = (DefaultKeyedValues2D) this.values.clone();
        clone.workingCalendar = (Calendar) this.workingCalendar.clone();
        return clone;
    }
	
    HashMap<String, HashMap<String, List<IItemId>>> itemIdsMap = new HashMap<String, HashMap<String, List<IItemId>>>(); 

    public List<IItemId> getItems(int item, int seriesId) {
    	HashMap<String, List<IItemId>> series = this.itemIdsMap.get(this.values.getRowKey(item).toString());
    	if(series!=null) {
    		return series.get(this.getSeriesKey(seriesId));
    	}
    	return null;
    }

	public void addValue(Count count, TimePeriod t, String eventField, ArrayList<IItemId> docIds) {
		values.addValue(count, t, eventField);

		HashMap<String, List<IItemId>> series = this.itemIdsMap.get(t.toString());
		if(series==null) {
			series = new HashMap<String, List<IItemId>>();
			this.itemIdsMap.put(t.toString(), series);
		}

		series.put(eventField, docIds);
	}
}

