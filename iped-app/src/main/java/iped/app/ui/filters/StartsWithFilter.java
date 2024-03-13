package iped.app.ui.filters;

import java.io.IOException;
import java.util.function.Predicate;

import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.search.IMultiSearchResult;

/*
 * A PreQueryValueFilter that has a predicate to check if the field value starts with the 
 * defined value.
 * 
 * @author Patrick Dalla Bernardina
 */
public class StartsWithFilter extends ValueFilter {
	String value;
	
    public StartsWithFilter(String field, String value) {
        super(field, new Predicate<String>() {
            @Override
            public boolean test(String t) {
                return t.startsWith(value);
            }
        });
        this.value = value;
    }

    public String toString() {
        return field + "^=\"" + value + "\"";
    }
}