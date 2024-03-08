package iped.app.ui.filters;

import java.io.IOException;
import java.util.function.Predicate;

import iped.engine.task.index.IndexItem;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.search.IMultiSearchResult;

public class EqualsFilter extends PreQueryValueFilter {
    public EqualsFilter(String field, String value) {
        super(field, value, new Predicate<String>() {
            @Override
            public boolean test(String t) {
                return t.equals(value);
            }
        });

        if (IndexItem.isNumeric(field)) {
            this.queryStr = field + ":[" + value + " TO " + value + "]";
        }
    }

    @Override
    public IMultiSearchResult filterResult(IMultiSearchResult src) throws ParseException, QueryNodeException, IOException {
        if (IndexItem.isNumeric(field)) {
            return src;
        } else {
            return super.filterResult(src);
        }
    }

    public String toString() {
        return field + "=\"" + value + "\"";
    }
}
