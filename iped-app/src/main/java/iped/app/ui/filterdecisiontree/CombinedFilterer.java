package iped.app.ui.filterdecisiontree;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import iped.app.ui.CaseSearcherFilter;
import iped.app.ui.filterdecisiontree.OperandNode.Operand;
import iped.data.IItemId;
import iped.engine.search.MultiSearchResult;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.search.IMultiSearchResult;
import iped.viewers.api.IFilter;
import iped.viewers.api.IQueryFilter;
import iped.viewers.api.IResultSetFilter;
import iped.viewers.api.IResultSetFilterer;

public class CombinedFilterer implements IResultSetFilterer {
    OperandNode rootNode = new OperandNode(Operand.OR);
    private final LinkedList<Map<Integer,BitSet>> queue = new LinkedList<Map<Integer,BitSet>>();
    int queueCount=0;

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
                return getSearchResult(src);
            }
        };        
    }

    public IMultiSearchResult getSearchResult(IMultiSearchResult input)
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
        result.setIPEDSource(input.getIPEDSource());
        
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
                            rs.setIPEDSource(input.getIPEDSource());
                            if(op.operand==Operand.OR) {
                                union.add(rs);
                            }else {
                                rs.setIPEDSource(input.getIPEDSource());
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
                            rs.setIPEDSource(input.getIPEDSource());
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
           result.setIPEDSource(input.getIPEDSource());
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

}
