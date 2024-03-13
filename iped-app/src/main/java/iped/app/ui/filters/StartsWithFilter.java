package iped.app.ui.filters;

import java.io.IOException;

import org.apache.lucene.util.BytesRef;

import iped.engine.task.index.IndexItem;

/*
 * A PreQueryValueFilter that has a predicate to check if the field value starts with the 
 * defined value.
 * 
 * @author Patrick Dalla Bernardina
 */
public class StartsWithFilter extends ValueFilter {

    private int stopOrd;

    public StartsWithFilter(String field, String value) {
        super(field, value);
        if (isNumeric) {
            throw new UnsupportedOperationException(StartsWithFilter.class.getSimpleName() + " cannot be used with numeric values");
        }
    }

    @Override
    protected void loadDocValues(String field) throws IOException {
        super.loadDocValues(field);
        String stopValue = value + "邹";
        stopValue = IndexItem.normalize(stopValue, true);
        if (docValues != null) {
            stopOrd = docValues.lookupTerm(new BytesRef(stopValue));
        } else if (docValuesSet != null) {
            // TODO handle multivalued string fields

        } else {
            throw new IOException("No String DocValues found for field " + field);
        }
        if (stopOrd < 0) {
            stopOrd = -1 - stopOrd;
        }
    }

    @Override
    protected boolean filterLuceneDoc(int doc) throws IOException {
        if (docValues != null) {
            if (docValues.advanceExact(doc)) {
                int ord = docValues.ordValue();
                if (ord >= refOrd && ord < stopOrd) {
                    return true;
                }
            }
        } else if (docValuesSet != null) {
            // TODO handle multivalued string fields
        }
        return false;
    }

    public String toString() {
        return field + "=" + value + "*";
    }

}