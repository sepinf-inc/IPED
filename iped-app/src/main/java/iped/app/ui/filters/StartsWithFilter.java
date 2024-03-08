package iped.app.ui.filters;

import java.io.IOException;
import java.util.function.Predicate;

import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.search.IMultiSearchResult;

public class StartsWithFilter extends PreQueryValueFilter {
    public StartsWithFilter(String field, String value) {
        super(field, value, new Predicate<String>() {
            @Override
            public boolean test(String t) {
                return t.startsWith(value);
            }
        });
        this.queryStr = field + ":" + value + "*";
    }

    @Override
    public IMultiSearchResult filterResult(IMultiSearchResult src) throws ParseException, QueryNodeException, IOException {
        return super.filterResult(src);
    }

    public String toString() {
        return field + "^=\"" + value + "\"";
    }

}
