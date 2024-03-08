package iped.app.ui.filters;

import java.util.function.Predicate;

import org.apache.lucene.search.Query;

import iped.app.ui.App;
import iped.engine.search.QueryBuilder;
import iped.engine.task.index.IndexItem;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.viewers.api.IQueryFilter;

public class PreQueryValueFilter extends ValueFilter implements IQueryFilter {
    protected String queryStr;
    private Query query;

    public PreQueryValueFilter(String field, String value, Predicate predicate) {
        super(field, value, predicate);

        if (IndexItem.isNumeric(field)) {
            this.queryStr = field + ":[" + value + " TO " + value + "]";
        } else {
            this.queryStr = field + ":\"" + value + "\"";
        }
    }

    @Override
    public Query getQuery() {
        if (query == null) {
            try {
                query = new QueryBuilder(App.get().appCase).getQuery(queryStr);
            } catch (ParseException | QueryNodeException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return query;
    }

    public String toString() {
        return field + "=\"" + value + "\"";
    }
}