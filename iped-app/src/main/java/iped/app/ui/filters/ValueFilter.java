package iped.app.ui.filters;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.commons.lang.ArrayUtils;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NumericUtils;

import iped.app.metadata.MetadataSearchable;
import iped.app.ui.App;
import iped.data.IItemId;
import iped.engine.data.ItemId;
import iped.engine.localization.CategoryLocalization;
import iped.engine.search.MultiSearchResult;
import iped.engine.task.index.IndexItem;
import iped.properties.BasicProps;
import iped.search.IMultiSearchResult;
import iped.viewers.api.IResultSetFilter;

/**
 * Represents a filter to check which documents have a field value matching some
 * criteria based on the provided value.
 * 
 * @author Patrick Dalla Bernardina
 */
public abstract class ValueFilter extends MetadataSearchable implements IResultSetFilter {

    protected String field;
    protected String value;
    protected long refOrd;
    protected long refVal;
    protected boolean isNumeric;
    protected boolean isFloat;
    protected boolean isDouble;

    protected abstract boolean filterLuceneDoc(int doc) throws IOException;

    public ValueFilter(String field, String value) {
        this.field = field;
        this.value = value;
        isNumeric = IndexItem.isNumeric(field);
        isFloat = IndexItem.isFloat(field);
        isDouble = IndexItem.isDouble(field);
        if (isNumeric) {
            if (isFloat) {
                refVal = NumericUtils.floatToSortableInt(Float.valueOf(value));
            } else if (isDouble) {
                refVal = NumericUtils.doubleToSortableLong(Double.valueOf(value));
            } else {
                refVal = Long.valueOf(value.replace(",", "").replace(".", ""));
            }
        }
    }

    protected String normalize(String value) {
        if (BasicProps.HASH.equals(field)) {
            return value;
        } else if (BasicProps.CATEGORY.equals(field)) {
            value = CategoryLocalization.getInstance().getNonLocalizedCategory(value);
            return IndexItem.normalize(value, false);
        }
        return IndexItem.normalize(value, true);
    }

    @Override
    protected void loadDocValues(String field) throws IOException {
        super.loadDocValues(field);
        if (!isNumeric) {
            if (docValues != null) {
                refOrd = docValues.lookupTerm(new BytesRef(normalize(value)));
            } else if (docValuesSet != null) {
                refOrd = docValuesSet.lookupTerm(new BytesRef(normalize(value)));
            } else {
                throw new IOException("No String DocValues found for field " + field);
            }
        }
    }

    @Override
    public IMultiSearchResult filterResult(IMultiSearchResult src) throws IOException {

        loadDocValues(field);

        ArrayList<IItemId> selectedItems = new ArrayList<IItemId>();
        ArrayList<Float> scores = new ArrayList<Float>();

        int i = 0;
        for (IItemId item : src.getIterator()) {
            int doc = App.get().appCase.getLuceneId(item);
            if (filterLuceneDoc(doc)) {
                selectedItems.add(item);
                scores.add(src.getScore(i));
            }
            i++;
        }
        MultiSearchResult r = new MultiSearchResult(selectedItems.toArray(new ItemId[0]), ArrayUtils.toPrimitive(scores.toArray(new Float[0])));

        return r;
    }

}
