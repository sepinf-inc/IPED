package iped.app.ui.filters;

import java.io.IOException;

/*
 * A PreQueryValueFilter that has a predicate to check if the field value is equal to the 
 * defined value.
 * 
 * @author Patrick Dalla Bernardina
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
            // TODO handle multivalued numeric fields

        } else if (docValues != null) {
            if (docValues.advanceExact(doc)) {
                if (docValues.ordValue() == refOrd) {
                    return true;
                }
            }
        } else if (docValuesSet != null) {
            // TODO handle multivalued string fields
        }
        return false;
    }

    public String toString() {
        return field + "=\"" + value + "\"";
    }
}
