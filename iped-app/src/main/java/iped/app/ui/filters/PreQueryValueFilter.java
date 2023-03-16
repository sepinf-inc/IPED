package iped.app.ui.filters;

import java.util.function.Predicate;

import iped.engine.task.index.IndexItem;
import iped.viewers.api.IQueryFilter;

public class PreQueryValueFilter extends ValueFilter implements IQueryFilter{
    protected String queryStr;

    public PreQueryValueFilter(String field, String value, Predicate predicate) {
        super(field, value, predicate);
        
        if(IndexItem.isNumeric(field)) {
            this.queryStr = field+":["+value+" TO "+value+"]";
        }else {
            this.queryStr = field+":\""+value+"\"";
        }
    }

    @Override
    public String getFilterExpression() {
        return queryStr;
    }
    
    public String toString() {
        return field+"=\""+value+"\"";
    }
}