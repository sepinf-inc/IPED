package iped.app.ui.filters;

import java.io.IOException;

import org.apache.lucene.index.SortedSetDocValues;

/**
 * A ValueFilter that checks in which docs the specified field is equal to the
 * provided string value.
 * 
 * @author Patrick Dalla Bernardina
 * @author Luís Nassif
 */
public class EqualsFilter extends ValueFilter {

    public EqualsFilter(String field, String value) {
        super(field, value);
    }

    @Override
    protected boolean filterLuceneDoc(int doc) throws IOException {
        if (isNumeric && numValues != null) {
            if (numValues.advanceExact(doc)) {
                double currentVal = numValues.longValue();
                if (currentVal == refVal) {
                    return true;
                }
            }
        } else if (isNumeric && numValuesSet != null) {
            if (numValuesSet.advanceExact(doc)) {
                for (int i = 0; i < numValuesSet.docValueCount(); i++) {
                    if (numValuesSet.nextValue() == refVal) {
                        return true;
                    }
                }
            }
        } else if (docValues != null) {
            if (docValues.advanceExact(doc)) {
                if (docValues.ordValue() == refOrd) {
                    return true;
                }
            }
        } else if (docValuesSet != null) {
            if (docValuesSet.advanceExact(doc)) {
                long ord;
                while ((ord = docValuesSet.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
                    if (ord == refOrd) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public String toString() {
        return field + "=\"" + value + "\"";
    }
}
