package iped.app.ui.filters;

import java.io.IOException;

import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.util.BytesRef;

/**
 * A ValueFilter that checks in which docs the specified field starts with the
 * provided string value.
 * 
 * @author Patrick Dalla Bernardina
 * @author Luís Nassif
 */
public class StartsWithFilter extends ValueFilter {

    private long stopOrd;

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
        stopValue = normalize(stopValue);
        if (docValues != null) {
            stopOrd = docValues.lookupTerm(new BytesRef(stopValue));
        } else if (docValuesSet != null) {
            stopOrd = docValuesSet.lookupTerm(new BytesRef(stopValue));
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
            if (docValuesSet.advanceExact(doc)) {
                long ord;
                while ((ord = docValuesSet.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
                    if (ord >= refOrd && ord < stopOrd) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public String toString() {
        return field + "=" + value + "*";
    }

}