package iped.app.ui.filters;

import java.util.function.Predicate;

import org.apache.lucene.search.Query;

import iped.app.ui.App;
import iped.engine.search.QueryBuilder;
import iped.engine.task.index.IndexItem;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.viewers.api.IQueryFilter;


/*
 * Represents a filter based on a Predicate used to filter each
 * field value, but that operates on a pre fitered result set
 * based on a query string;
 * 
 * @author Patrick Dalla Bernardina
 */
public abstract class PreQueryValueFilter extends ValueFilter implements IQueryFilter{
    protected String queryStr;//query string to pre filter the result set
    
    protected String value;
    private Query query;

    public PreQueryValueFilter(String field, String value, Predicate predicate) {
        super(field, predicate);
        this.value = value;
    }

    @Override
    public Query getQuery() {
        if(query==null) {
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
        return field+"=\""+value+"\"";
    }
}