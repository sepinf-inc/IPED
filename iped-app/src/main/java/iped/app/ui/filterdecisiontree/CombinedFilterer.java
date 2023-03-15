package iped.app.ui.filterdecisiontree;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.zaxxer.sparsebits.SparseBitSet;

import iped.app.ui.App;
import iped.app.ui.CaseSearcherFilter;
import iped.app.ui.filterdecisiontree.OperandNode.Operand;
import iped.data.IItemId;
import iped.engine.data.IPEDMultiSource;
import iped.engine.data.IPEDSource;
import iped.engine.search.MultiSearchResult;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.search.IMultiSearchResult;
import iped.viewers.api.IFilterChangeListener;
import iped.viewers.api.IFilter;
import iped.viewers.api.IMutableFilter;
import iped.viewers.api.IQueryFilter;
import iped.viewers.api.IResultSetFilter;
import iped.viewers.api.IResultSetFilterer;

public class CombinedFilterer implements IResultSetFilterer, IFilterChangeListener {
    OperandNode rootNode = new OperandNode(Operand.OR);

    private final Map<IFilter, FutureBitSetResult> cachedBitSet = new HashMap<IFilter, FutureBitSetResult>();

    int queueCount=0;

    private CombinedBitSet cbs;

    @Override
    public List getDefinedFilters() {
        ArrayList<IFilter> result = new ArrayList<IFilter>();
        result.add(getFilter());
        return result;
    }

    public IFilter getFilter() {
        return new IResultSetFilter() {
            @Override
            public IMultiSearchResult filterResult(IMultiSearchResult src)
                    throws ParseException, QueryNodeException, IOException {
                Date d1 = new Date();
                IMultiSearchResult result = getSearchResult(src);
                Date d2 = new Date();
                long tempo = d2.getTime()-d1.getTime();
                return result; 
            }
        };        
    }

    public void removePreCachedFilter(IFilter filter) {
        cachedBitSet.remove(filter);        
    }
    
    /**
     * Class that represents a future calculated bitset of a filter.
     * When the filter is added to Combined filter its bitset is calculated in
     * background, so when it is effectivelly calculated, it can
     * be used to filter results. 
     * @author patrick.pdb
     */
    class FutureBitSetResult implements Future<Map<Integer, SparseBitSet>>{
        CaseSearcherFilter csf = null;
        private IFilter filter;
        Map<Integer, SparseBitSet> bitsets = null;
        private boolean isInverting;

        FutureBitSetResult(IFilter filter){
            this.filter = filter;
            this.csf = null;
            if(filter instanceof IQueryFilter) {
                this.csf = new CaseSearcherFilter(((IQueryFilter)filter).getFilterExpression());        
            }else {
                this.csf = new CaseSearcherFilter("*:*");        
            }

            this.csf.setAppyUIFilters(false);        
            this.csf.execute();
        }
        
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return csf.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return csf.isCancelled();
        }

        @Override
        public boolean isDone() {
            return csf.isDone();
        }
        
        public void invert() {
            isInverting=!isInverting;
            if(isInverting) {                
                this.csf = new CaseSearcherFilter("*:*");
                this.csf.setAppyUIFilters(false);        
                this.csf.execute();
            }
        }

        @Override
        public Map<Integer, SparseBitSet> get() throws InterruptedException, ExecutionException {
            try {
                Map<Integer, SparseBitSet> lbitset;
                if((filter instanceof IMutableFilter)||(bitsets == null)) {//IMutableFilter aren't cached
                    MultiSearchResult rs = csf.get();
                    if(filter instanceof IResultSetFilter) {
                        rs = (MultiSearchResult) ((IResultSetFilter)filter).filterResult(rs);
                    }
                    
                    Map<Integer, SparseBitSet> tmpbitset = rs.getCasesBitSets(App.get().appCase);
                    lbitset = new HashMap<Integer, SparseBitSet>();
                    for (Iterator<Entry<Integer, SparseBitSet>> iterator = tmpbitset.entrySet().iterator(); iterator.hasNext();) {
                        Entry<Integer, SparseBitSet> entry = iterator.next();
                        lbitset.put(entry.getKey(), entry.getValue().clone());                        
                    }
                    if(!(filter instanceof IMutableFilter)) {
                        bitsets = lbitset;
                    }
                }else {
                    lbitset=bitsets;
                }
                if(isInverting) {
                    MultiSearchResult rs = csf.get();
                    List<IPEDSource> cases = App.get().appCase.getAtomicSources();
                    for (Iterator<IPEDSource> iterator = cases.iterator(); iterator.hasNext();) {
                        IPEDSource source = iterator.next();
                        SparseBitSet bs = getAllSetBitSet(source.getLastId());
                        lbitset.get(source.getSourceId()).xor(bs);
                    }
                }
                
                return lbitset;
            } catch (ParseException | QueryNodeException | IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public Map<Integer, SparseBitSet> get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            // TODO Auto-generated method stub
            return null;
        }
        
    }

    public void preCacheFilter(IFilter filter) {
        cachedBitSet.put(filter, new FutureBitSetResult(filter));
        if(filter instanceof IMutableFilter) {
            ((IMutableFilter)filter).addFilterChangeListener(this);
        }
    }
    
    static public SparseBitSet getAllSetBitSet(int length) {
        SparseBitSet bs = new SparseBitSet(length);
        for(int i=0; i<length;i++) {
            bs.set(i);
        }
        return bs;
    }

    public void startSearchResult(IMultiSearchResult input) {
        if(cbs!=null) {
            cbs.cancel(true);
        }
        cbs = new CombinedBitSet((MultiSearchResult) input, rootNode);
    }

    public IMultiSearchResult getSearchResult(IMultiSearchResult input) {
        Map<Integer,SparseBitSet> resultBitSet = null;

        if(cbs==null) {
            cbs = new CombinedBitSet((MultiSearchResult) input, rootNode);
        }

        try {
            resultBitSet = cbs.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        LinkedHashSet<IItemId> ids = new LinkedHashSet<IItemId>();
        ArrayList<Float> scores = new ArrayList<Float>();
        float[] primitiveScores;

        if(resultBitSet!=null) {
            int i=0;
            while(i<input.getLength()) {
                IItemId itemId = input.getItem(i);
                if(resultBitSet.get(itemId.getSourceId()).get(itemId.getId())){
                    ids.add(itemId);
                    scores.add(input.getScore(i));
                }
                i++;
            }

            primitiveScores = new float[scores.size()];
            i = 0;
            for (Float f : scores) {
              primitiveScores[i++] = f;
            }
        }else {
            primitiveScores = new float[0];
        }

        MultiSearchResult result = new MultiSearchResult(ids.toArray(new IItemId[0]), primitiveScores);

        return result;
    }

    public IMultiSearchResult getSearchResultQuery(IMultiSearchResult input)
            throws ParseException, QueryNodeException, IOException {
        return getSearchResult(input, rootNode);
    }

    public OperandNode getRootNode() {
        return rootNode;
    }

    public MultiSearchResult resultSetIntersection(MultiSearchResult input, MultiSearchResult rs) {
        LinkedHashSet<IItemId> ids = new LinkedHashSet<IItemId>();
        ArrayList<Float> scores = new ArrayList<Float>();

        int i=0;
        while(i<input.getLength()) {
            IItemId itemId = input.getItem(i);
            if(rs.hasDocId(input.getIPEDSource().getLuceneId(itemId))){
                if(!ids.contains(itemId)) {
                    ids.add(itemId);
                    scores.add(input.getScore(i));
                }
            }
            i++;
        }

        float[] primitiveScores = new float[scores.size()];
        i = 0;
        for (Float f : scores) {
          primitiveScores[i++] = f;
        }

        MultiSearchResult result = new MultiSearchResult(ids.toArray(new IItemId[0]), primitiveScores);

        return result;
    }

    class CombinedBitSet implements Future<Map<Integer, SparseBitSet>>{
        MultiSearchResult input; 
        OperandNode op;
        Map<Integer, SparseBitSet> result = null;
        boolean canceled = false;
        private Thread thread;
        Semaphore resultFinished = new Semaphore(1);
        
        public CombinedBitSet(MultiSearchResult input, OperandNode op){
            this.input = input;
            this.op = op;
            CombinedBitSet self = this;

            try {
                resultFinished.acquire();
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
            
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    try {
                        result = getBitSet(input, op, self);
                    }finally {
                        resultFinished.release();
                    }
                }
            };

            thread = new Thread(r);
            thread.start();
        }
        
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            canceled = true;
            return false;
        }

        @Override
        public boolean isCancelled() {
            return canceled;
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public Map<Integer, SparseBitSet> get() throws InterruptedException, ExecutionException {
            resultFinished.acquire();
            resultFinished.release();
            return result;
        }

        @Override
        public Map<Integer, SparseBitSet> get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return result;
        }
    }

    public Map<Integer, SparseBitSet> getBitSet(MultiSearchResult input, OperandNode op) {
        return getBitSet(input, op, null);
    }
    
    public Map<Integer, SparseBitSet> getBitSet(MultiSearchResult input, OperandNode op, Future cancelCheck) {
        Map<Integer, SparseBitSet> result = new HashMap<Integer, SparseBitSet>();
        IPEDMultiSource appCase = App.get().appCase;
        List<IPEDSource> cases = appCase.getAtomicSources();
        
        if(cancelCheck!=null && cancelCheck.isCancelled()) {
            return null;
        }

        if(op.operand==Operand.OR) {
            //initializes with all zero bitset
            for (Iterator iterator = cases.iterator(); iterator.hasNext();) {
                IPEDSource ipedSource = (IPEDSource) iterator.next();
                int bitsetSize=0;
                if(ipedSource.getLastId()>=0) {
                    bitsetSize=ipedSource.getLastId();
                }
                result.put(ipedSource.getSourceId(), new SparseBitSet(bitsetSize));
            }
        }else {
            //initializes with all one bitset
            for (Iterator iterator = cases.iterator(); iterator.hasNext();) {
                IPEDSource ipedSource = (IPEDSource) iterator.next();                
                int bitsetSize=0;
                if(ipedSource.getLastId()>=0) {
                    bitsetSize=ipedSource.getLastId();
                }
                result.put(ipedSource.getSourceId(), getAllSetBitSet(bitsetSize));
            }
        }

        for (Iterator iterator = op.getChildren().iterator(); iterator.hasNext();) {
            if(cancelCheck!=null && cancelCheck.isCancelled()) {
                return null;
            }
            
            DecisionNode node = (DecisionNode) iterator.next();

            Map<Integer, SparseBitSet> fbitset = null;
            if(node instanceof FilterNode) {
                try {
                    fbitset = cachedBitSet.get(((FilterNode)node).getFilter()).get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }else if(node instanceof OperandNode) {
                fbitset = getBitSet(input, ((OperandNode) node), cancelCheck);
            }
            if(result==null) {
                result=fbitset;
            }else {
                if(op.operand==Operand.AND) {
                    for (int i=0; i<cases.size();i++) {
                        IPEDSource ipedSource = (IPEDSource) cases.get(i);
                        SparseBitSet bitset = result.get(ipedSource.getSourceId());
                        if(ipedSource.getLastId()>0) {
                            bitset.and(fbitset.get(ipedSource.getSourceId()));
                        }
                    }
                }else {
                    for (int i=0; i<cases.size();i++) {
                        IPEDSource ipedSource = (IPEDSource) cases.get(i);
                        SparseBitSet bitset = result.get(ipedSource.getSourceId());
                        if(ipedSource.getLastId()>0) {
                            bitset.or(fbitset.get(ipedSource.getSourceId()));
                        }
                    }
                }
            }
        }
        return result;
    }
    
    public IMultiSearchResult getSearchResult(IMultiSearchResult input, OperandNode op) {
        if(op.getChildren().size()<=0) {
            return input;
        }

        ArrayList<MultiSearchResult> union = new ArrayList<MultiSearchResult>();

        for (Iterator iterator = op.getChildren().iterator(); iterator.hasNext();) {
            DecisionNode node = (DecisionNode) iterator.next();

            if(node instanceof FilterNode) {
                Object filter = ((FilterNode)node).getFilter();
                
                
                
                if(filter instanceof IQueryFilter) {
                    CaseSearcherFilter csf = new CaseSearcherFilter(((IQueryFilter)filter).getFilterExpression());
                    csf.setAppyUIFilters(false);
                    csf.execute();
                    try {
                        MultiSearchResult rs = csf.get();
                        if(rs!=input) {
                            if(op.operand==Operand.OR) {
                                union.add(rs);
                            }else {
                                input = resultSetIntersection((MultiSearchResult) input, rs);
                            }
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                }
                if(filter instanceof IResultSetFilter) {
                    MultiSearchResult rs;
                    try {
                        rs = (MultiSearchResult) ((IResultSetFilter) filter).filterResult(input);
                        if(rs!=input) {
                            if(op.operand==Operand.OR) {
                                union.add(rs);
                            }else {
                                input=rs;
                            }
                        }
                    } catch (ParseException | QueryNodeException | IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }else if (node instanceof OperandNode) {
                MultiSearchResult result = (MultiSearchResult) getSearchResult(input, ((OperandNode) node));
                if(result!=input) {
                    if(op.operand==Operand.OR) {
                        union.add(result);
                    }else {
                        input = result;
                    }
                }
            }
       }

       MultiSearchResult result;

       if(op.operand==Operand.OR) {
           LinkedHashSet<IItemId> ids = new LinkedHashSet<IItemId>();
           ArrayList<Float> scores = new ArrayList<Float>();

           int i=0;
           while(i<input.getLength()) {
               for (Iterator iterator = union.iterator(); iterator.hasNext();) {
                   MultiSearchResult rs = (MultiSearchResult) iterator.next();

                   IItemId itemId = input.getItem(i);
                   if(rs.hasDocId(input.getIPEDSource().getLuceneId(itemId))){
                       if(!ids.contains(itemId)) {
                           ids.add(itemId);
                           scores.add(input.getScore(i));
                       }
                   }
               }
               i++;
           }

           float[] primitiveScores = new float[scores.size()];
           i = 0;
           for (Float f : scores) {
             primitiveScores[i++] = f;
           }

           result = new MultiSearchResult(ids.toArray(new IItemId[0]), primitiveScores);
       }else {
           result = (MultiSearchResult) input;
       }

       return result;
    }

    @Override
    public Map<Integer, BitSet> getFilteredBitSets(IMultiSearchResult input) {
        return null;
    }

    public String toString() {
        return rootNode.toString();
    }

    @Override
    public boolean hasFilters() {
        return rootNode.getChildren().size()>0;
    }

    @Override
    public boolean hasFiltersApplied() {
        return false;
    }

    public void invertPreCached(IFilter op) {
        FutureBitSetResult fbitset = cachedBitSet.get(op);
        fbitset.invert();
    }

    @Override
    public void onFilterChange(IMutableFilter filter) {
        
    }

}
