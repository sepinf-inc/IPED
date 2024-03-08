package iped.app.ui.filters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Predicate;

import org.apache.commons.lang.ArrayUtils;
import org.apache.lucene.index.SortedSetDocValues;

import iped.app.metadata.MetadataSearchable;
import iped.app.ui.App;
import iped.data.IItemId;
import iped.engine.data.ItemId;
import iped.engine.search.MultiSearchResult;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.search.IMultiSearchResult;
import iped.viewers.api.IResultSetFilter;

public class ValueFilter extends MetadataSearchable implements IResultSetFilter {
    protected String field;
    protected String value;
    Predicate<String> predicate = null;

    public ValueFilter(String field, String value, Predicate<String> predicate) {
        this.field = field;
        this.value = value;
        this.predicate = predicate;
        reader = App.get().appCase.getLeafReader();

    }

    public boolean checkinDocValues(int doc) {
        if (docValues == null) {
            return false;
        }
        try {
            boolean adv = docValues.advanceExact(doc);
            String val = docValues.lookupOrd(docValues.ordValue()).utf8ToString();

            if (val != null && predicate.test(val)) {
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    public boolean checkinDocValuesSet(int doc) {
        if (docValuesSet == null) {
            return false;
        }
        try {
            boolean adv = docValuesSet.advanceExact(doc);

            long ord = -1;
            while (adv && (ord = docValuesSet.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
                String val = docValuesSet.lookupOrd(ord).utf8ToString();

                if (val != null && predicate.test(val)) {
                    return true;
                }
            }
        } catch (Exception e) {
        }
        return false;
    }

    @Override
    public IMultiSearchResult filterResult(IMultiSearchResult src) throws ParseException, QueryNodeException, IOException {

        if (predicate == null) {
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

            if (checkinDocValues(doc) || checkinDocValuesSet(doc)) {
                selectedItems.add(item);
                scores.add(src.getScore(i));
            }

            i++;
        }
        MultiSearchResult r = new MultiSearchResult(selectedItems.toArray(new ItemId[0]), ArrayUtils.toPrimitive(scores.toArray(new Float[0])));

        return r;
    }

}
