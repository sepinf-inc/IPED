package iped.app.ui.filters;

import java.io.IOException;
import java.util.function.Predicate;

import iped.engine.task.index.IndexItem;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.search.IMultiSearchResult;

/*
 * A PreQueryValueFilter that has a predicate to check if the field value is equal to the 
 * defined value.
 * 
 * @author Patrick Dalla Bernardina
 */
public class EqualsFilter extends PreQueryValueFilter {
    public EqualsFilter(String field, String value) {
        super(field, value, new Predicate<String>() {
            @Override
            public boolean test(String t) {
                return t.equals(value);
            }
        });
        if(IndexItem.isNumeric(field)) {
            this.queryStr = field+":["+value+" TO "+value+"]";
        }else {
            this.queryStr = field+":\""+value+"\"";
        }
    }

    @Override
    public IMultiSearchResult filterResult(IMultiSearchResult src) throws ParseException, QueryNodeException, IOException {
        if (IndexItem.isNumeric(field)) {
        	//if is numeric, the query filter is enough to filter the result set even without the predicate test
            return src;
        } else {
        	//filters the result set with the predicate
            return super.filterResult(src);
        }
    }

    public String toString() {
        return field + "=\"" + value + "\"";
    }
}
