package iped.app.metadata;

import java.util.HashSet;
import java.util.Set;

import org.apache.lucene.search.Query;

import iped.app.ui.App;
import iped.engine.search.QueryBuilder;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.viewers.api.IQueryFilter;

public class ValueCountQueryFilter implements IQueryFilter {
    StringBuffer queryStr = null;
    StringBuffer name = null;
    Set<ValueCount> values = null;
    String filterField;
    private Query query;

    public ValueCountQueryFilter(String filterField, Set<ValueCount> selectedValues) {
        this.filterField = filterField;
        values = new HashSet<ValueCount>();
        if (selectedValues != null) {
            values.addAll(selectedValues);
        }
    }

    public String toString() {
        if (name == null) {
            name = new StringBuffer();
            name.append(filterField);
            name.append(":[");
            boolean first = true;
            for (ValueCount value : values) {
                if (!first) {
                    name.append(",");
                }
                name.append(value.getVal());
                if (name.length() > 50) {
                    name.append("(...)");
                    break;
                }
                first = false;
            }
            name.append("]");
        }
        return name.toString();
    }

    @Override
    public Query getQuery() {
        if (queryStr == null) {
            queryStr = new StringBuffer();
            int i = 0;
            for (ValueCount value : values) {
                queryStr.append(filterField);
                if (value instanceof RangeCount) {
                    RangeCount range = (RangeCount) value;
                    queryStr.append(":[");
                    queryStr.append(range.getStart());
                    queryStr.append(" TO ");
                    queryStr.append(range.getEnd());
                    queryStr.append("]");
                } else {
                    queryStr.append(":\"");
                    queryStr.append(value.getVal());
                    queryStr.append("\"");
                }
                i++;
                if (i < values.size()) {
                    queryStr.append(" OR ");
                }
            }
            try {
                query = new QueryBuilder(App.get().appCase).getQuery(queryStr.toString());
            } catch (ParseException | QueryNodeException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return query;
    }

}
