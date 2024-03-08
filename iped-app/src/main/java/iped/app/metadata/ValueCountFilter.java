package iped.app.metadata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.commons.lang.ArrayUtils;

import iped.data.IItemId;
import iped.engine.data.ItemId;
import iped.engine.search.MultiSearchResult;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.search.IMultiSearchResult;
import iped.viewers.api.IResultSetFilter;

public class ValueCountFilter implements IResultSetFilter {

    private String filterField;
    private HashSet<ValueCount> values;
    private HashSet<Integer> ords = new HashSet<Integer>();

    public ValueCountFilter(String filterField, Set<ValueCount> selectedValues) {
        this.filterField = filterField;
        values = new HashSet<ValueCount>();
        if (selectedValues != null) {
            values.addAll(selectedValues);
        }
        for (Iterator iterator = selectedValues.iterator(); iterator.hasNext();) {
            ValueCount valueCount = (ValueCount) iterator.next();
            ords.add(valueCount.getOrd());
        }
    }

    @Override
    public IMultiSearchResult filterResult(IMultiSearchResult src) throws ParseException, QueryNodeException, IOException {
        ArrayList<IItemId> selectedItems = new ArrayList<IItemId>();
        ArrayList<Float> scores = new ArrayList<Float>();
        int i = 0;
        for (IItemId item : src.getIterator()) {
            if (ords.contains(item.getId())) {
                selectedItems.add(item);
                scores.add(src.getScore(i));
            }
            i++;
        }
        MultiSearchResult r = new MultiSearchResult(selectedItems.toArray(new ItemId[0]), ArrayUtils.toPrimitive(scores.toArray(new Float[0])));

        return r;
    }

}
