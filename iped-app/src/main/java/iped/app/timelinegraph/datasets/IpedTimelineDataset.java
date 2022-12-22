package iped.app.timelinegraph.datasets;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortedSetDocValues;
import org.jfree.chart.util.Args;
import org.jfree.chart.util.PublicCloneable;
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
import org.jfree.data.xy.XYDomainInfo;

import iped.app.timelinegraph.IpedChartPanel;
import iped.app.timelinegraph.IpedChartsPanel;
import iped.app.timelinegraph.cache.CacheEventEntry;
import iped.app.timelinegraph.cache.CacheTimePeriodEntry;
import iped.app.timelinegraph.cache.TimeIndexedMap;
import iped.app.timelinegraph.cache.TimeStampCache;
import iped.app.ui.App;
import iped.app.ui.CaseSearcherFilter;
import iped.app.ui.Messages;
import iped.app.ui.MetadataPanel.ValueCount;
import iped.data.IIPEDSource;
import iped.data.IItemId;
import iped.data.IMultiBookmarks;
import iped.engine.data.IPEDMultiSource;
import iped.engine.data.ItemId;
import iped.search.IMultiSearchResult;
import iped.viewers.api.IMultiSearchResultProvider;
import iped.viewers.api.IQueryFilterer;

public class IpedTimelineDataset extends AbstractIntervalXYDataset implements Cloneable, PublicCloneable, IntervalXYDataset, DomainInfo, TimelineDataset, TableXYDataset, XYDomainInfo, AsynchronousDataset {
    private static final int CTITEMS_PER_THREAD = 500;
    IMultiSearchResultProvider resultsProvider;
    private Set<IQueryFilterer> exceptThis = new HashSet<IQueryFilterer>();

    /**
     * A flag that indicates that the domain is 'points in time'. If this flag is
     * true, only the x-value (and not the x-interval) is used to determine the
     * range of values in the domain.
     */
    private boolean domainIsPointsInTime;

    /**
     * The point within each time period that is used for the X value when this
     * collection is used as an {@link org.jfree.data.xy.XYDataset}. This can be the
     * start, middle or end of the time period.
     */
    private TimePeriodAnchor xPosition;

    /** A working calendar (to recycle) */
    private Calendar workingCalendar;

    Accumulator accumulator = new Accumulator();

    SortedSetDocValues timeEventGroupValues;
    IpedChartsPanel ipedChartsPanel;

    SortedSet<String> eventTypes = new TreeSet<String>();
    String[] eventTypesArray;
    LeafReader reader;

    static ThreadPoolExecutor queriesThreadPool = new ThreadPoolExecutor(5, 10, 20000, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());//pool of concurrent group of itens in a dataset beeing populated
    static ThreadPoolExecutor datasetsThreadPool = new ThreadPoolExecutor(3, 10, 20000, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());//pool of concurrent datasets beeing populated

    class Count extends Number {
        int value = 0;

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

    int itemCount = 0;

    volatile int running = 0;
    volatile int seriesCount = 0;
    Object monitor = new Object();
    private String splitValue = null;
    private ArrayList<CaseSearcherFilter> csfs;
    private boolean cancelled = false;

    Semaphore visiblePopulSem;// semaphore that controls start and end of load of visible items

    /*
     * This semaphore is used to avoid multiple thread of dataset loading, as some
     * events like mouse wheel zooming or chart panning can call many times. As the
     * cache already has a windows of items larger than the visible range, this
     * window prevents some possible unloaded info from not being ploted.
     */
    Semaphore memoryCacheReloadSem = new Semaphore(1); //semaphore that controls start and end of load of cache window contourning the visible items

    public IpedTimelineDataset(IpedTimelineDatasetManager ipedTimelineDatasetManager, IMultiSearchResultProvider resultsProvider, String splitValue) throws Exception {
        this.ipedChartsPanel = ipedTimelineDatasetManager.ipedChartsPanel;
        exceptThis.add(ipedChartsPanel);
        Args.nullNotPermitted(ipedChartsPanel.getTimeZone(), "zone");
        Args.nullNotPermitted(ipedChartsPanel.getLocale(), "locale");
        this.workingCalendar = Calendar.getInstance(ipedChartsPanel.getTimeZone(), ipedChartsPanel.getLocale());
        this.xPosition = TimePeriodAnchor.START;
        this.resultsProvider = resultsProvider;
        this.splitValue = splitValue;

        startCaseSearchFilterLoad();
    }

    /*
     * Start threads to count events by timePeriod/event. The time period
     * granularity is gotten from the configured in ipedCharts.
     */
    public void startCaseSearchFilterLoad() throws Exception {
        running = 1;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    caseSearchFilterLoad();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    visiblePopulSem.release(running);
                }
            }
        });
        visiblePopulSem = new Semaphore(running);
        visiblePopulSem.acquire(running);
        datasetsThreadPool.execute(t);

    }

    class PopulateDatasetTask implements Runnable {
        int threadCtsEnd;
        CacheTimePeriodEntry[] threadLocalCts;
        Semaphore timeSem;
        IMultiSearchResult result;
        Semaphore addValueSem;// semaphore to control block for adding value to the dataset

        public PopulateDatasetTask(IMultiSearchResult result, CacheTimePeriodEntry[] threadLocalCts, int threadCtsEnd, Semaphore timeSem, Semaphore addValueSem) {
            this.result = result;
            this.threadLocalCts = threadLocalCts;
            this.threadCtsEnd = threadCtsEnd;
            this.timeSem = timeSem;
            this.addValueSem = addValueSem;
        }

        @Override
        public void run() {
            try {
                App app = App.get();
                Set<String> selectedBookmarks = app.getSelectedBookmarks();
                String noBookmarksStr = Messages.get("BookmarksTreeModel.NoBookmarks");
                IMultiBookmarks multiBookmarks = App.get().getIPEDSource().getMultiBookmarks();
                IPEDMultiSource appcase = (IPEDMultiSource) app.getIPEDSource();

                for (int i = 0; i < threadCtsEnd; i++) {
                    CacheTimePeriodEntry ct = threadLocalCts[i];
                    for (CacheEventEntry ce : ct.events) {
                        if (cancelled) {
                            return;
                        }
                        ArrayList<Integer> docs = ce.docIds;
                        if (docs != null) {
                            ArrayList<IItemId> includedItems = new ArrayList<IItemId>();
                            ArrayList<Integer> includedDocs = new ArrayList<Integer>();
                            Count count = new Count();
                            for (Integer docId : docs) {
                                if (cancelled) {
                                    throw new InterruptedException();
                                }

                                if (result.hasDocId(docId)) {
                                    IIPEDSource atomicSource = appcase.getAtomicSource(docId);
                                    int sourceId = atomicSource.getSourceId();
                                    int baseDoc = appcase.getBaseLuceneId(atomicSource);
                                    ItemId ii = new ItemId(sourceId, atomicSource.getId(docId - baseDoc));

                                    boolean include = false;
                                    if (splitValue != null && !splitValue.equals("Bookmarks") && ipedChartsPanel.getChartPanel().getSplitByBookmark()) {
                                        if (splitValue.equals(noBookmarksStr)) {
                                            if (!multiBookmarks.hasBookmark(ii)) {
                                                include = true;
                                            }
                                        } else {
                                            if (multiBookmarks.hasBookmark(ii, splitValue)) {
                                                include = true;
                                            }
                                        }
                                    } else {
                                        // not split by bookmark, so filter by selected bookmark
                                        if (selectedBookmarks.size() > 0) {
                                            if (selectedBookmarks.contains("Bookmarks")) {
                                                include = true;
                                            } else if (multiBookmarks.hasBookmark(ii, selectedBookmarks)) {
                                                include = true;
                                            } else if (selectedBookmarks.contains(noBookmarksStr)) {
                                                if (!multiBookmarks.hasBookmark(ii)) {
                                                    include = true;
                                                }
                                            }
                                        } else {
                                            include = true;// if no bookmark selected
                                        }
                                    }
                                    if (include) {
                                        count.value++;
                                        includedDocs.add(docId);
                                        includedItems.add(ii);
                                    }
                                }
                            }
                            if (count.value > 0) {
                                addValueSem.acquire();
                                try {
                                    TimePeriod t = ipedChartsPanel.getDomainAxis().getDateOnConfiguredTimePeriod(ipedChartsPanel.getTimePeriodClass(), ct.date);
                                    addValue(count, t, ce.event, includedDocs, includedItems);
                                }catch (Exception e) {
                                    e.printStackTrace();
                                }finally {
                                    addValueSem.release();
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                timeSem.release();
            }
        }
    }

    public String getWindowQuery() {
    	IpedChartPanel chartPanel = ipedChartsPanel.getChartPanel();
        Range dateRange = ipedChartsPanel.getDomainAxis().getRange();
        Date startDate = new Date((long) dateRange.getLowerBound());
        Date endDate = new Date((long) dateRange.getUpperBound());

        if (startDate.getTime() == 0 && endDate.getTime() == 1) {
        	return "";
        }
        
        long visibleRangeLength = endDate.getTime() - startDate.getTime();
        Date tmpDate = new Date(endDate.getTime() - visibleRangeLength * MEMORY_WINDOW_CACHE_PROPORTION);
        endDate = new Date(startDate.getTime() + visibleRangeLength * MEMORY_WINDOW_CACHE_PROPORTION);
        startDate = tmpDate;
        
        
        String timeFilter = "(";
        timeFilter += "timeStamp:[";
        timeFilter += ipedChartsPanel.getDomainAxis().ISO8601DateFormatUTC(startDate);
        timeFilter += " TO ";
        timeFilter += ipedChartsPanel.getDomainAxis().ISO8601DateFormatUTC(endDate);
        timeFilter += "]";
        timeFilter += ")";

        return timeFilter;
    }
    
    int MEMORY_WINDOW_CACHE_PROPORTION = 2;// how many times the visible range the memory window will load, forward and
                                           // backward.
    List<CacheTimePeriodEntry> memoryWindowCache = new ArrayList<CacheTimePeriodEntry>();

    public void caseSearchFilterLoad() throws Exception {
        memoryCacheReloadSem.acquire();
        
        try {
            IMultiSearchResult result;

            String queryText = "";

            if (ipedChartsPanel.getChartPanel().getSplitByCategory() && splitValue != null && !splitValue.equals("Categories")) {
                queryText += "category=\"" + splitValue + "\"";
            }
            
            CaseSearcherFilter csf = new CaseSearcherFilter(queryText);
            csf.getSearcher().setNoScoring(true);
            csf.applyUIQueryFilters(exceptThis);// apply all filters from others UI objects except the chart defined interval
                                                // filters

            csf.execute();
            result = csf.get();

            App app = App.get();
            IMultiBookmarks multiBookmarks = App.get().getIPEDSource().getMultiBookmarks();
            IPEDMultiSource appcase = (IPEDMultiSource) app.getIPEDSource();

            if (result.getLength() > 0) {
                TimeStampCache cache = ipedChartsPanel.getIpedTimelineDatasetManager().getCache();
                TimeIndexedMap a = (TimeIndexedMap) cache.getNewCache();

                String className = ipedChartsPanel.getTimePeriodClass().getSimpleName();
                List<CacheTimePeriodEntry> newcache = a.get(className);
                if (newcache != null) {

                    Semaphore addValueSem = new Semaphore(1);

                    List<CacheTimePeriodEntry> visibleIntervalCache;
                    LinkedList<CacheTimePeriodEntry> beforecache = new LinkedList<CacheTimePeriodEntry>();
                    List<CacheTimePeriodEntry> aftercache = new ArrayList<CacheTimePeriodEntry>();

                    Range dateRange = ipedChartsPanel.getDomainAxis().getRange();
                    Date startDate = new Date((long) dateRange.getLowerBound());
                    Date endDate = new Date((long) dateRange.getUpperBound());
                    
                    long visibleRangeLength = endDate.getTime() - startDate.getTime();
                    if ( (startDate.getTime()==0 && endDate.getTime()==1)) {
                        visibleIntervalCache = newcache;
                        memoryWindowCache.addAll(newcache);
                    } else {
                        visibleIntervalCache = new ArrayList<CacheTimePeriodEntry>();
                        startDate = ipedChartsPanel.getChartPanel().removeFromDatePart(startDate);
                        endDate = new Date(ipedChartsPanel.getChartPanel().removeNextFromDatePart(endDate).getTime() - 1);

                        Iterator<CacheTimePeriodEntry> it = a.iterator(className, new Date(endDate.getTime() - visibleRangeLength * MEMORY_WINDOW_CACHE_PROPORTION), new Date(startDate.getTime() + visibleRangeLength * MEMORY_WINDOW_CACHE_PROPORTION));
                        CacheTimePeriodEntry ctpe = null;
                        while (it.hasNext()) {
                            ctpe = it.next();
                            boolean remove = false;
                            if (ctpe.date.before(startDate)) {
                                if (memoryWindowCache != newcache) {// if window cache were not the complete cache itself
                                    if (ctpe.date.getTime() > endDate.getTime() - visibleRangeLength * MEMORY_WINDOW_CACHE_PROPORTION) {
                                        if (!memoryWindowCache.contains(ctpe)) {
                                            beforecache.addFirst(ctpe);
                                            memoryWindowCache.add(ctpe);
                                        }
                                        remove = false;
                                    } else {
                                        // remove from memoryCacheWindow
                                        remove = true;
                                    }
                                }
                            } else if (ctpe.date.after(endDate)) {
                                if (memoryWindowCache != newcache) {// if window cache were not the complete cache itself
                                    if (ctpe.date.getTime() < startDate.getTime() + visibleRangeLength * MEMORY_WINDOW_CACHE_PROPORTION) {
                                        if (!memoryWindowCache.contains(ctpe)) {
                                            aftercache.add(ctpe);
                                            memoryWindowCache.add(ctpe);
                                        }
                                        remove = false;
                                    } else {
                                        // remove from memoryCacheWindow
                                        remove = true;
                                    }
                                }
                            } else {//inside visible window
                                if (!memoryWindowCache.contains(ctpe)) {
                                    visibleIntervalCache.add(ctpe);
                                    memoryWindowCache.add(ctpe);
                                }
                                remove = false;
                            }
                            if (remove) {
                                TimePeriod t = ipedChartsPanel.getDomainAxis().getDateOnConfiguredTimePeriod(ipedChartsPanel.getTimePeriodClass(), ctpe.date);
                                accumulator.remove(t);
                                if (memoryWindowCache != newcache) {// if window cache were not the complete cache itself
                                    memoryWindowCache.remove(ctpe);
                                }
                            }
                        }
                    }

                    populatesWithList(result, visibleIntervalCache, addValueSem);// creates first the visible interval itens to be plotted

                    if (beforecache.size() > 0 || aftercache.size() > 0) {
                        Runnable r = new Runnable() {
                            @Override
                            public void run() {
                            	try {
                                    populatesWithBeforeAndAfterList(result, beforecache, aftercache, addValueSem);
                            	}finally {
                                    memoryCacheReloadSem.release();//releases after thread end
                        		}
                            }
                        };
                        Thread t = new Thread(r);
                        t.start();// runs out of visibility asynchronously so to release GUI thread to the user.
                    }else {
                        memoryCacheReloadSem.release();//releases imediatelly as all cache was loaded
                    }
                }

            }else {
                memoryCacheReloadSem.release();//releases imediatelly as all cache was loaded
            	
            }
        }catch(Exception e) {
            memoryCacheReloadSem.release();//garantee release of semaphore on untreated exception
            throw e;
		}

    }

    public void populatesWithBeforeAndAfterList(IMultiSearchResult result, List<CacheTimePeriodEntry> beforecache, List<CacheTimePeriodEntry> aftercache, Semaphore addValueSem) {
        try {
            int threadCount = (int) Math.ceil((double) (beforecache.size() + aftercache.size()) / (double) CTITEMS_PER_THREAD);
            Semaphore timeSem = new Semaphore(threadCount);
            timeSem.acquire(threadCount);
            CacheTimePeriodEntry[] threadCts = new CacheTimePeriodEntry[CTITEMS_PER_THREAD];
            int threadCtsCount = 0;
            int totalInCount = beforecache.size() + aftercache.size();

            // populates dataset for inside visible range items
            int beforeindex = 0;
            int afterindex = 0;
            CacheTimePeriodEntry ctpeBefore = beforecache.size() > 0 ? beforecache.get(beforeindex) : null;
            CacheTimePeriodEntry ctpeAfter = aftercache.size() > 0 ? aftercache.get(afterindex) : null;
            while (ctpeBefore != null || ctpeAfter != null) {
                CacheTimePeriodEntry ctpe = null;
                if (totalInCount % 2 == 0) {// alternate between lists
                    ctpe = ctpeBefore;
                    if (ctpe == null) {
                        ctpe = ctpeAfter;
                        afterindex++;
                        if (afterindex < aftercache.size()) {
                            ctpeAfter = aftercache.get(afterindex);
                        } else {
                            ctpeAfter = null;
                        }
                    } else {
                        beforeindex++;
                        if (beforeindex < beforecache.size()) {
                            ctpeBefore = beforecache.get(beforeindex);
                        } else {
                            ctpeBefore = null;
                        }
                    }
                } else {
                    ctpe = ctpeAfter;
                    if (ctpe == null) {
                        ctpe = ctpeBefore;
                        beforeindex++;
                        if (beforeindex < beforecache.size()) {
                            ctpeBefore = beforecache.get(beforeindex);
                        } else {
                            ctpeBefore = null;
                        }
                    } else {
                        afterindex++;
                        if (afterindex < aftercache.size()) {
                            ctpeAfter = aftercache.get(afterindex);
                        } else {
                            ctpeAfter = null;
                        }
                    }

                }

                threadCts[threadCtsCount++] = ctpe;
                totalInCount--;
                if (threadCtsCount >= CTITEMS_PER_THREAD || totalInCount <= 0) {
                    int threadCtsEnd = threadCtsCount;
                    Runnable r = new PopulateDatasetTask(result, threadCts, threadCtsEnd, timeSem, addValueSem);
                    Thread t = new Thread(r);
                    queriesThreadPool.execute(t);
                    threadCts = new CacheTimePeriodEntry[CTITEMS_PER_THREAD];
                    threadCtsCount = 0;
                }
            }
            timeSem.acquire(threadCount);
            timeSem.release(threadCount);
            
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void populatesWithList(IMultiSearchResult result, List<CacheTimePeriodEntry> cache, Semaphore addValueSem) {
        try {
            int threadCount = (int) Math.ceil((double) cache.size() / (double) CTITEMS_PER_THREAD);
            Semaphore timeSem = new Semaphore(threadCount);
            timeSem.acquire(threadCount);
            CacheTimePeriodEntry[] threadCts = new CacheTimePeriodEntry[CTITEMS_PER_THREAD];
            int threadCtsCount = 0;
            int totalInCount = cache.size();

            // populates dataset for inside visible range items
            for (CacheTimePeriodEntry ctpe : cache) {
                threadCts[threadCtsCount++] = ctpe;
                totalInCount--;
                if (threadCtsCount >= CTITEMS_PER_THREAD || totalInCount <= 0) {
                    int threadCtsEnd = threadCtsCount;
                    CacheTimePeriodEntry[] threadLocalCts = threadCts.clone();
                    threadCtsCount = 0;
                    Runnable r = new PopulateDatasetTask(result, threadLocalCts, threadCtsEnd, timeSem, addValueSem);
                    Thread t = new Thread(r);
                    queriesThreadPool.execute(t);
                }
            }
            timeSem.acquire(threadCount);
            timeSem.release(threadCount);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
        }
    }

    @Override
    public boolean waitLoaded() {
        try {
            if (visiblePopulSem != null) {
                visiblePopulSem.acquire(running);
                visiblePopulSem.release(running);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            cancelled = true;
        }
        return cancelled;
    }

    public void cancel() {
        if (csfs != null && csfs.size() > 0) {
            for (CaseSearcherFilter csf : csfs) {
                csf.getSearcher().cancel();
                csf.doCancel(true);
            }
        }
        cancelled = true;
        visiblePopulSem.release(running);
        memoryCacheReloadSem.release();
        synchronized (monitor) {
            monitor.notifyAll();
        }
    }

    /**
     * Returns a flag that controls whether the domain is treated as 'points in
     * time'.
     * <P>
     * This flag is used when determining the max and min values for the domain. If
     * true, then only the x-values are considered for the max and min values. If
     * false, then the start and end x-values will also be taken into consideration.
     *
     * @return The flag.
     *
     * @see #setDomainIsPointsInTime(boolean)
     */
    public boolean getDomainIsPointsInTime() {
        return this.domainIsPointsInTime;
    }

    /**
     * Sets a flag that controls whether the domain is treated as 'points in time',
     * or time periods. A {@link DatasetChangeEvent} is sent to all registered
     * listeners.
     *
     * @param flag
     *            the new value of the flag.
     *
     * @see #getDomainIsPointsInTime()
     */
    public void setDomainIsPointsInTime(boolean flag) {
        this.domainIsPointsInTime = flag;
        notifyListeners(new DatasetChangeEvent(this, this));
    }

    /**
     * Returns the position within each time period that is used for the X value.
     *
     * @return The anchor position (never {@code null}).
     *
     * @see #setXPosition(TimePeriodAnchor)
     */
    public TimePeriodAnchor getXPosition() {
        return this.xPosition;
    }

    /**
     * Sets the position within each time period that is used for the X values, then
     * sends a {@link DatasetChangeEvent} to all registered listeners.
     *
     * @param anchor
     *            the anchor position ({@code null} not permitted).
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
        return accumulator.rowTimestamps.size();
    }

    @Override
    public DomainOrder getDomainOrder() {
        return DomainOrder.ASCENDING;
    }

    /**
     * Returns the number of items in a series. This is the same value that is
     * returned by {@link #getItemCount()} since all series share the same x-values
     * (time periods).
     *
     * @param series
     *            the series (zero-based index, ignored).
     *
     * @return The number of items within the series.
     */
    @Override
    public int getItemCount(int series) {
        return accumulator.rowTimestamps.size();
    }

    /**
     * Returns the number of series in the dataset.
     *
     * @return The series count.
     */
    @Override
    public int getSeriesCount() {
        return accumulator.colEvents.size();
    }

    /**
     * Returns the key for a series.
     *
     * @param series
     *            the series (zero-based index).
     *
     * @return The key for the series.
     */
    @Override
    public Comparable getSeriesKey(int series) {
        return accumulator.colEvents.get(series);
    }

    /**
     * Returns the x-value for an item within a series. The x-values may or may not
     * be returned in ascending order, that is up to the class implementing the
     * interface.
     *
     * @param series
     *            the series (zero-based index).
     * @param item
     *            the item (zero-based index).
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
     * @param series
     *            the series index (zero-based).
     * @param item
     *            the item index (zero-based).
     *
     * @return The value.
     */
    @Override
    public double getXValue(int series, int item) {
        TimePeriod period = (TimePeriod) accumulator.rowTimestamps.get(item);
        return getXValue(period);
    }

    /**
     * Returns the starting X value for the specified series and item.
     *
     * @param series
     *            the series (zero-based index).
     * @param item
     *            the item within a series (zero-based index).
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
     * Returns the start x-value (as a double primitive) for an item within a
     * series.
     *
     * @param series
     *            the series index (zero-based).
     * @param item
     *            the item index (zero-based).
     *
     * @return The value.
     */
    @Override
    public double getStartXValue(int series, int item) {
        TimePeriod period = (TimePeriod) accumulator.rowTimestamps.get(item);
        return period.getStart().getTime();
    }

    /**
     * Returns the ending X value for the specified series and item.
     *
     * @param series
     *            the series (zero-based index).
     * @param item
     *            the item within a series (zero-based index).
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
     * Returns the end x-value (as a double primitive) for an item within a series.
     *
     * @param series
     *            the series index (zero-based).
     * @param item
     *            the item index (zero-based).
     *
     * @return The value.
     */
    @Override
    public double getEndXValue(int series, int item) {
        TimePeriod period = (TimePeriod) accumulator.rowTimestamps.get(item);
        return period.getEnd().getTime();
    }

    /**
     * Returns the y-value for an item within a series.
     *
     * @param series
     *            the series (zero-based index).
     * @param item
     *            the item (zero-based index).
     *
     * @return The y-value (possibly {@code null}).
     */
    @Override
    public Number getY(int series, int item) {
        HashMap<Integer, Count> hc = accumulator.counts.get(item);
        return hc.get(series);
    }

    /**
     * Returns the starting Y value for the specified series and item.
     *
     * @param series
     *            the series (zero-based index).
     * @param item
     *            the item within a series (zero-based index).
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
     * @param series
     *            the series (zero-based index).
     * @param item
     *            the item within a series (zero-based index).
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
     * @param period
     *            the time period.
     *
     * @return The x-value.
     */
    private long getXValue(TimePeriod period) {
        long result = 0L;
        if (this.xPosition == TimePeriodAnchor.START) {
            result = period.getStart().getTime();
        } else if (this.xPosition == TimePeriodAnchor.MIDDLE) {
            long t0 = period.getStart().getTime();
            long t1 = period.getEnd().getTime();
            result = t0 + (t1 - t0) / 2L;
        } else if (this.xPosition == TimePeriodAnchor.END) {
            result = period.getEnd().getTime();
        }
        return result;
    }

    /**
     * Returns the minimum x-value in the dataset.
     *
     * @param includeInterval
     *            a flag that determines whether or not the x-interval is taken into
     *            account.
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
     * @param includeInterval
     *            a flag that determines whether or not the x-interval is taken into
     *            account.
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
     * @param includeInterval
     *            a flag that controls whether or not the x-intervals are taken into
     *            account.
     *
     * @return The range.
     */
    @Override
    public Range getDomainBounds(boolean includeInterval) {
        List keys = accumulator.rowTimestamps;
        if (keys.isEmpty()) {
            return null;
        }

        TimePeriod first = (TimePeriod) keys.get(0);
        TimePeriod last = (TimePeriod) keys.get(keys.size() - 1);

        if (!includeInterval || this.domainIsPointsInTime) {
            return new Range(getXValue(first), getXValue(last));
        } else {
            return new Range(first.getStart().getTime(), last.getEnd().getTime());
        }
    }

    /**
     * Tests this dataset for equality with an arbitrary object.
     *
     * @param obj
     *            the object ({@code null} permitted).
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
        if (!this.workingCalendar.getTimeZone().equals(that.workingCalendar.getTimeZone())) {
            return false;
        }
        if (!this.accumulator.counts.equals(that.accumulator.counts)) {
            return false;
        }
        return true;
    }

    /**
     * Returns a clone of this dataset.
     *
     * @return A clone.
     *
     * @throws CloneNotSupportedException
     *             if the dataset cannot be cloned.
     */
    @Override
    public Object clone() throws CloneNotSupportedException {
        IpedTimelineDataset clone = (IpedTimelineDataset) super.clone();
        // clone.values = (DefaultKeyedValues2D) this.values.clone();
        clone.workingCalendar = (Calendar) this.workingCalendar.clone();
        return clone;
    }

    public List<IItemId> getItems(int item, int seriesId) {
        TimePeriod t = accumulator.rowTimestamps.get(item);
        if (t != null) {
            HashMap<String, List<IItemId>> series = this.accumulator.itemIdsMap.get(t.toString());
            if (series != null) {
                return series.get(this.getSeriesKey(seriesId));
            }
        }
        return null;
    }

    public List<Integer> getDocIds(int item, int seriesId) {
        TimePeriod t = accumulator.rowTimestamps.get(item);
        if (t != null) {
            HashMap<String, List<Integer>> series = this.accumulator.docIdsMap.get(t.toString());
            if (series != null) {
                return series.get(this.getSeriesKey(seriesId));
            }
        }
        return null;
    }

    public void addValue(Count count, TimePeriod t, String eventType, ArrayList<Integer> docIds, ArrayList<IItemId> itemIds) {
        accumulator.addValue(count, t, eventType, docIds, itemIds);
    }

    public void addValue(ValueCount valueCount, ArrayList<Integer> docIds, ArrayList<IItemId> itemIds, String eventType) {
        accumulator.addValue(valueCount, docIds, itemIds, eventType);
    }

    @Override
    public Range getDomainBounds(List visibleSeriesKeys, boolean includeInterval) {
        if (accumulator.min == null) {
            return new Range(0, 0);
        }
        return new Range(accumulator.min.getStart().getTime(), accumulator.max.getEnd().getTime());
    }

    public class Accumulator {
        TimePeriod min;
        TimePeriod max;
        ArrayList<String> colEvents = new ArrayList<String>();
        private ArrayList<TimePeriod> rowTimestamps = new ArrayList<TimePeriod>();
        private ArrayList<HashMap<Integer, Count>> counts = new ArrayList<HashMap<Integer, Count>>();
        private HashMap<String, HashMap<String, List<IItemId>>> itemIdsMap = new HashMap<String, HashMap<String, List<IItemId>>>();
        private HashMap<String, HashMap<String, List<Integer>>> docIdsMap = new HashMap<String, HashMap<String, List<Integer>>>();

        public Accumulator() {
        }

        public void addDocIds(TimePeriod t, String eventField, ArrayList<Integer> docIds) {
            HashMap<String, List<Integer>> series = this.docIdsMap.get(t.toString());
            List<Integer> ids = null;
            if (series == null) {
                series = new HashMap<String, List<Integer>>();
                this.docIdsMap.put(t.toString(), series);
            } else {
                ids = series.get(eventField);
            }

            if (ids != null) {
                synchronized (ids) {
                    ids.addAll(docIds);
                }
            } else {
                ids = new ArrayList<Integer>();
                ids.addAll(docIds);
                series.put(eventField, ids);
            }
        }

        public void addItemIds(TimePeriod t, String eventField, ArrayList<IItemId> itemIds) {
            HashMap<String, List<IItemId>> series = this.itemIdsMap.get(t.toString());
            List<IItemId> ids = null;
            if (series == null) {
                series = new HashMap<String, List<IItemId>>();
                this.itemIdsMap.put(t.toString(), series);
            } else {
                ids = series.get(eventField);
            }

            if (ids != null) {
                synchronized (ids) {
                    ids.addAll(itemIds);
                }
            } else {
                ids = new ArrayList<IItemId>();
                ids.addAll(itemIds);
                series.put(eventField, itemIds);
            }
        }

        public void addValue(ValueCount valueCount, ArrayList<Integer> docIds, ArrayList<IItemId> itemIds, String eventType) {
            Date d = ipedChartsPanel.getDomainAxis().ISO8601DateParse(valueCount.getVal());
            TimePeriod t = ipedChartsPanel.getDomainAxis().getDateOnConfiguredTimePeriod(ipedChartsPanel.getTimePeriodClass(), d);

            if (t != null) {
                addDocIds(t, eventType, docIds);
                addItemIds(t, eventType, itemIds);

                if (min == null || t.getStart().before(min.getStart())) {
                    min = t;
                }

                if (max == null || t.getEnd().after(max.getEnd())) {
                    max = t;
                }

                int col = colEvents.indexOf(eventType);
                if (col == -1) {
                    colEvents.add(eventType);
                    col = colEvents.size() - 1;
                }

                int row = rowTimestamps.indexOf(t);
                if (row == -1) {
                    rowTimestamps.add(t);
                    row = rowTimestamps.size() - 1;

                    Count c = new Count();
                    c.value = valueCount.getCount();
                    HashMap<Integer, Count> values = new HashMap<Integer, Count>();
                    values.put(col, c);
                    counts.add(values);
                    return;
                }

                Count c;

                HashMap<Integer, Count> values = counts.get(row);
                c = values.get(col);
                if (c == null) {
                    c = new Count();
                    values.put(col, c);
                }
                c.value += valueCount.getCount();
            } else {
                System.out.println("Unexpected null value after string parsing:" + d + "  :  " + valueCount.getVal());
            }
        }

        public void addValue(Count count, TimePeriod t, String eventType, ArrayList<Integer> docIds, ArrayList<IItemId> itemIds) {
            if (min == null || t.getStart().before(min.getStart())) {
                min = t;
            }

            if (max == null || t.getEnd().after(max.getEnd())) {
                max = t;
            }

            addDocIds(t, eventType, docIds);
            addItemIds(t, eventType, itemIds);

            int col = colEvents.indexOf(eventType);
            if (col == -1) {
                colEvents.add(eventType);
                col = colEvents.size() - 1;
            }

            int row = rowTimestamps.indexOf(t);
            if (row == -1) {
                rowTimestamps.add(t);
                row = rowTimestamps.size() - 1;

                HashMap<Integer, Count> values = new HashMap<Integer, Count>();
                values.put(col, count);
                counts.add(values);
                return;
            }

            Count c;

            HashMap<Integer, Count> values = counts.get(row);
            c = values.get(col);
            if (c == null) {
                c = count;
                values.put(col, c);
            } else {
                c.value += count.value;
            }
        }

        void remove(TimePeriod t) {
            int row = rowTimestamps.indexOf(t);
            if (row != -1) {
                rowTimestamps.remove(row);
                counts.remove(row);
            }
        }

        synchronized void merge(Accumulator acc) {
            if (acc.colEvents.size() > 0) {
                int col = this.colEvents.indexOf(acc.colEvents.get(0));
                if (col < 0) {
                    this.colEvents.add(acc.colEvents.get(0));
                    col = this.colEvents.size() - 1;
                }
                this.itemIdsMap.putAll(acc.itemIdsMap);

                for (int i = 0; i < acc.rowTimestamps.size(); i++) {
                    TimePeriod t = acc.rowTimestamps.get(i);
                    int index = this.rowTimestamps.indexOf(t);
                    if (index < 0) {
                        this.rowTimestamps.add(t);
                        this.counts.add(acc.counts.get(i));
                    } else {
                        HashMap<Integer, Count> values = this.counts.get(index);
                        HashMap<Integer, Count> accValues = acc.counts.get(i);
                        Count c = values.get(col);
                        if (c == null) {
                            values.put(col, accValues.get(0));
                        } else {
                            c.value += accValues.get(0).value;
                        }
                    }
                }

                if (this.min == null || acc.min.getStart().before(this.min.getStart())) {
                    this.min = acc.min;
                }
                if (this.max == null || acc.max.getEnd().after(this.max.getEnd())) {
                    this.max = acc.max;
                }
            }
        }
    }

    @Override
    public void notifyVisibleRange(double lowerBound, double upperBound) {
        try {
            startCaseSearchFilterLoad();
            waitLoaded();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
