package iped.app.ui.filters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Predicate;

import org.apache.commons.lang.ArrayUtils;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.index.SortedSetDocValues;

import iped.app.ui.App;
import iped.data.IItemId;
import iped.engine.data.ItemId;
import iped.engine.search.MultiSearchResult;
import iped.engine.task.index.IndexItem;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IMultiSearchResult;
import iped.viewers.api.IResultSetFilter;

public class ValueFilter implements IResultSetFilter {
    protected String field;
    protected String value;
    Predicate<String> predicate = null;
    
    volatile NumericDocValues numValues;
    volatile SortedNumericDocValues numValuesSet;
    volatile SortedDocValues docValues;
    volatile SortedSetDocValues docValuesSet;
    volatile SortedSetDocValues eventDocValuesSet;
    private volatile static LeafReader reader;
    
    public ValueFilter(String field, String value, Predicate<String> predicate) {
        this.field = field;
        this.value = value;
        this.predicate = predicate;
        reader = App.get().appCase.getLeafReader();
        
    }

    private void loadDocValues(String field) throws IOException {
        // System.out.println("getDocValues");
        numValues = reader.getNumericDocValues(field);
        numValuesSet = reader.getSortedNumericDocValues(field);
        docValues = reader.getSortedDocValues(field);
        String prefix = ExtraProperties.LOCATIONS.equals(field) ? IndexItem.GEO_SSDV_PREFIX : "";
        docValuesSet = reader.getSortedSetDocValues(prefix + field);
        if (BasicProps.TIME_EVENT.equals(field)) {
            eventDocValuesSet = reader.getSortedSetDocValues(ExtraProperties.TIME_EVENT_GROUPS);
        }
    }

    @Override
    public IMultiSearchResult filterResult(IMultiSearchResult src)
            throws ParseException, QueryNodeException, IOException {
        
        if(predicate==null) {
            return src;
        }

        try {
            loadDocValues(field);
        } catch (IOException e) {
            e.printStackTrace();
        }
            
        HashMap<Integer, byte[]> bookmarkBitsPerSource = new HashMap<Integer, byte[]>();
        ArrayList<IItemId> selectedItems = new ArrayList<IItemId>();
        ArrayList<Float> scores = new ArrayList<Float>();
        int i = 0;
        for (IItemId item : src.getIterator()) {
            int doc = App.get().appCase.getLuceneId(item);
            boolean adv = docValues.advanceExact(doc);
            String val = docValues.lookupOrd(docValues.ordValue()).utf8ToString();
            
            if(val!=null && predicate.test(val)) {
                selectedItems.add(item);
                scores.add(src.getScore(i));
            }
            
            i++;
        }
        MultiSearchResult r = new MultiSearchResult(selectedItems.toArray(new ItemId[0]),
                ArrayUtils.toPrimitive(scores.toArray(new Float[0])));

        return r;
    }

}
