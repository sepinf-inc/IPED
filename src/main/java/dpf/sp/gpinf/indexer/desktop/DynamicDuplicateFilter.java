package dpf.sp.gpinf.indexer.desktop;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;

import org.apache.commons.lang.ArrayUtils;
import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.SortedDocValues;

import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.search.IPEDMultiSource;
import dpf.sp.gpinf.indexer.search.ItemId;
import dpf.sp.gpinf.indexer.search.MultiSearchResult;

public class DynamicDuplicateFilter {
    
    private static SortedDocValues docValues;
    
    private static IPEDMultiSource ipedCase;
    
    private BitSet ordSet = new BitSet(1 << 23);
    
    public DynamicDuplicateFilter(IPEDMultiSource ipedSource) throws IOException{
        if(ipedCase != ipedSource) {
            ipedCase = ipedSource;
            AtomicReader reader = ipedCase.getAtomicReader();
            docValues = reader.getSortedDocValues(IndexItem.HASH);
        }
    }
    
    public MultiSearchResult filter(MultiSearchResult result) {
        
        ArrayList<ItemId> filteredItems = new ArrayList<ItemId>();
        ArrayList<Float> scores = new ArrayList<Float>();
        int i = 0;
        for(ItemId item : result.getIterator()){
            int docId = ipedCase.getLuceneId(item);
            int ord = docValues.getOrd(docId);
            if(ord <= 0 || !ordSet.get(ord)) {
                filteredItems.add(item);
                scores.add(result.getScore(i));
                if(ord > 0)
                    ordSet.set(ord);
            }
            i++;
        }
        return new MultiSearchResult(filteredItems.toArray(new ItemId[0]),
                ArrayUtils.toPrimitive(scores.toArray(new Float[0])));
    }

}
