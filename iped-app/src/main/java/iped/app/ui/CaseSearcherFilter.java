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
package iped.app.ui;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;

import javax.swing.JOptionPane;

import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.roaringbitmap.RoaringBitmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.engine.data.IPEDMultiSource;
import iped.engine.data.IPEDSource;
import iped.engine.data.ItemId;
import iped.engine.search.IPEDSearcher;
import iped.engine.search.MultiSearchResult;
import iped.engine.search.QueryBuilder;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.viewers.api.CancelableWorker;
import iped.viewers.api.IBitmapFilter;
import iped.viewers.api.IFilter;
import iped.viewers.api.IQueryFilterer;
import iped.viewers.api.IResultSetFilter;
import iped.viewers.api.IResultSetFilterer;

public class CaseSearcherFilter extends CancelableWorker<MultiSearchResult, Object> {
    private static Logger LOGGER = LoggerFactory.getLogger(CaseSearcherFilter.class);
    ArrayList<CaseSearchFilterListener> listeners = new ArrayList<CaseSearchFilterListener>();
    RoaringBitmap[] unionsArray;
    RoaringBitmap[] excludeUnionsArray;

    public static SoftReference<MultiSearchResult> allItemsCache;
    private static IPEDSource ipedCase;

    private ExecutorService threadPool;

    volatile int numFilters = 0;

    String queryText;
    Query query;
    IPEDSearcher searcher;
    FilterManager filterManager;

    public CaseSearcherFilter(String queryText) {
        this.queryText = queryText;
        searcher = new IPEDSearcher(App.get().appCase, queryText);
        filterManager = App.get().getFilterManager();
    }

    public CaseSearcherFilter(Query query) {
        this.query = query;
        searcher = new IPEDSearcher(App.get().appCase, query);
        filterManager = App.get().getFilterManager();
    }

    public void applyUIQueryFilters() {
        applyUIQueryFilters(null);// no exceptions
    }

    public void applyUIQueryFilters(Set<IQueryFilterer> exceptions) {
        try {
            searcher.setQuery(getQueryWithUIFilter(exceptions));

        } catch (ParseException | QueryNodeException e) {
            JOptionPane.showMessageDialog(App.get(), Messages.getString("UISearcher.Error.Msg") + e.getMessage(), //$NON-NLS-1$
                    Messages.getString("UISearcher.Error.Title"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
            // e.printStackTrace();
        }
    }

    private Query getQueryWithUIFilter(Set<IQueryFilterer> exceptions) throws ParseException, QueryNodeException {
        Query result;
        numFilters = 0;
        if (queryText != null) {
            result = new QueryBuilder(App.get().appCase).getQuery(queryText);
            if (!queryText.trim().isEmpty())
                numFilters++;
        } else {
            result = query;
            if (!(query instanceof MatchAllDocsQuery))
                numFilters++;
        }

        if (applyUIFilters) {
            List<IQueryFilterer> queryFilterers = filterManager.queryFilterers;
            for (Iterator iterator = queryFilterers.iterator(); iterator.hasNext();) {
                IQueryFilterer iQueryFilterer = (IQueryFilterer) iterator.next();

                if (filterManager.isFiltererEnabled(iQueryFilterer)) {
                    if (exceptions == null || !exceptions.contains(iQueryFilterer)) {
                        Query fquery = iQueryFilterer.getQuery();
                        if (fquery != null) {
                            BooleanQuery.Builder boolQuery = new BooleanQuery.Builder();
                            boolQuery.add(fquery, Occur.MUST);
                            boolQuery.add(result, Occur.MUST);
                            result = boolQuery.build();
                            numFilters++;
                        }
                    }
                }
            }
        }

        return result;
    }

    MultiSearchResult result = null;
    private boolean applyUIFilters = true;

    public MultiSearchResult getDoneResult() {
        return this.result;
    }

    @Override
    public MultiSearchResult doInBackground() {

        synchronized (this.getClass()) {

            Date d1 = new Date();

            if (this.isCancelled())
                throw new CancellationException();

            try {
                for (CaseSearchFilterListener caseSearchFilterListener : listeners) {
                    caseSearchFilterListener.onStart();
                }

                if (this.isCancelled()) {
                    throw new CancellationException();
                }

                if (ipedCase == null || ipedCase != App.get().appCase) {
                    allItemsCache = null;
                    ipedCase = App.get().appCase;
                }

                Query q = searcher.getQuery();
                // LOGGER.info("Searching for query " + (q != null ? q.toString() : queryText));
                // //$NON-NLS-1$

                if (q instanceof MatchAllDocsQuery && allItemsCache != null)
                    result = allItemsCache.get();

                if (result == null) {
                    result = searcher.multiSearch();

                    if (this.isCancelled()) {
                        throw new CancellationException();
                    }

                    if (q instanceof MatchAllDocsQuery && (allItemsCache == null || allItemsCache.get() == null))
                        allItemsCache = new SoftReference(result.clone());
                }

                result.setIPEDSource(ipedCase);

                if (applyUIFilters && filterManager != null) {
                    List<IResultSetFilterer> rsFilterers = filterManager.getResultSetFilterers();

                    for (Iterator iterator = rsFilterers.iterator(); iterator.hasNext() && result.getLength() > 0;) {
                        if (this.isCancelled()) {
                            throw new CancellationException();
                        }

                        IResultSetFilterer iRSFilterer = (IResultSetFilterer) iterator.next();
                        applyFilterer(iRSFilterer, result);
                    }
                }

                if (this.isCancelled()) {
                    throw new CancellationException();
                }
                if (unionsArray != null) {
                    MultiSearchResult newresult = filterManager.applyFilter(unionsArray, result);
                    if (newresult != result) {
                        numFilters++;
                        result = newresult;
                        result.setIPEDSource(ipedCase);
                    }
                }
                if (excludeUnionsArray != null) {
                    MultiSearchResult newresult = filterManager.applyExcludeFilter(excludeUnionsArray, result);
                    if (newresult != result) {
                        numFilters++;
                        result = newresult;
                        result.setIPEDSource(ipedCase);
                    }
                }

            } catch (Throwable e) {
                if (!(e instanceof CancellationException)) {
                    e.printStackTrace();
                }
                return new MultiSearchResult(new ItemId[0], new float[0]);
            }

            result.setIpedSearcher(searcher);
            result.setIPEDSource(ipedCase);

            Date d2 = new Date();
            LOGGER.info("Search and filtering took {}ms", d2.getTime() - d1.getTime());

            return result;
        }
    }

    private void applyFilterer(IResultSetFilterer iRSFilterer, MultiSearchResult result2) {
        if (filterManager.isFiltererEnabled(iRSFilterer)) {
            IFilter rsFilter = iRSFilterer.getFilter();

            if (rsFilter != null) {
                if (rsFilter instanceof IBitmapFilter) {// if the filter exposes a internal bitmap
                    applyBitmapFilter((IBitmapFilter) rsFilter);
                } else {
                    RoaringBitmap[] cachedBitmaps = filterManager.getCachedBitmaps((IResultSetFilter) rsFilter);
                    if (cachedBitmaps != null) { // if filtermanager returned a cached bitmap
                        applyBitmapFilter(cachedBitmaps);
                    } else {
                        MultiSearchResult newresult = filterManager.applyFilter((IResultSetFilter) rsFilter, result);
                        if (newresult != result) {
                            numFilters++;
                            result = newresult;
                            result.setIPEDSource(ipedCase);
                        }
                    }
                }
            }
        }
    }

    private void applyBitmapFilter(RoaringBitmap[] cachedBitmaps) {
        RoaringBitmap[] lunionsArray = result.getCasesBitSets((IPEDMultiSource) ipedCase);
        for (int i = 0; i < unionsArray.length; i++) {
            lunionsArray[i].and(cachedBitmaps[i]);
        }

        MultiSearchResult newresult = filterManager.applyFilter(lunionsArray, result);
        if (newresult != result) {
            numFilters++;
            result = newresult;
            result.setIPEDSource(ipedCase);
        }
    }

    private void applyBitmapFilter(IBitmapFilter rsFilter) {
        RoaringBitmap[] lunionsArray = result.getCasesBitSets((IPEDMultiSource) ipedCase);
        for (int i = 0; i < lunionsArray.length; i++) {
            if (rsFilter.isToFilterOut()) {
                lunionsArray[i].andNot(((IBitmapFilter) rsFilter).getBitmap()[i]);
            } else {
                lunionsArray[i].and(((IBitmapFilter) rsFilter).getBitmap()[i]);
            }
        }

        MultiSearchResult newresult = filterManager.applyFilter(lunionsArray, result);
        if (newresult != result) {
            numFilters++;
            result = newresult;
            result.setIPEDSource(ipedCase);
        }
    }

    @Override
    public void done() {
        for (CaseSearchFilterListener caseSearchFilterListener : listeners) {
            if (isCancelled()) {
                break;
            }
            caseSearchFilterListener.onDone();
        }
    }

    @Override
    public boolean doCancel(boolean mayInterruptIfRunning) {
        searcher.cancel();

        for (CaseSearchFilterListener caseSearchFilterListener : listeners) {
            caseSearchFilterListener.onCancel(mayInterruptIfRunning);
        }

        return cancel(mayInterruptIfRunning);
    }

    public int getNumFilters() {
        return numFilters;
    }

    public void setNumFilters(int numFilters) {
        this.numFilters = numFilters;
    }

    public IPEDSearcher getSearcher() {
        return searcher;
    }

    public void setSearcher(IPEDSearcher searcher) {
        this.searcher = searcher;
    }

    public String getQueryText() {
        return queryText;
    }

    public void setQueryText(String queryText) {
        this.queryText = queryText;
    }

    public void addCaseSearchFilterListener(CaseSearchFilterListener csfl) {
        listeners.add(csfl);
    }

    public ExecutorService getThreadPool() {
        return threadPool;
    }

    public void setThreadPool(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }

    public void setAppyUIFilters(boolean applyUIFilters) {
        this.applyUIFilters = applyUIFilters;
    }

    public void addBitmapExcludeFilter(RoaringBitmap[] lunionsArray) {
        if (excludeUnionsArray == null) {
            excludeUnionsArray = new RoaringBitmap[lunionsArray.length];
        }
        for (int i = 0; i < excludeUnionsArray.length; i++) {
            if (excludeUnionsArray[i] == null) {
                excludeUnionsArray[i] = new RoaringBitmap();
                excludeUnionsArray[i].or(lunionsArray[i]);
            } else {
                excludeUnionsArray[i].and(lunionsArray[i]);
            }
        }
    }

    public void addBitmapFilter(RoaringBitmap[] lunionsArray) {
        if (unionsArray == null) {
            unionsArray = new RoaringBitmap[lunionsArray.length];
        }
        for (int i = 0; i < unionsArray.length; i++) {
            if (unionsArray[i] == null) {
                unionsArray[i] = new RoaringBitmap();
                unionsArray[i].or(lunionsArray[i]);
            } else {
                unionsArray[i].and(lunionsArray[i]);
            }
        }
    }
}
