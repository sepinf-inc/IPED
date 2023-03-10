package iped.app.metadata;

import java.util.HashSet;
import java.util.Set;

import iped.viewers.api.IQueryFilter;

public class ValueCountQueryFilter implements IQueryFilter{
    StringBuffer queryStr = null;
    StringBuffer name = null;
    Set<ValueCount> values = null;
    String filterField;
    
    public ValueCountQueryFilter(String filterField, Set<ValueCount> selectedValues) {
        this.filterField = filterField;
        values = new HashSet<ValueCount>();
        values.addAll(selectedValues);
    }

    public String toString() {
        if(name==null) {
            name = new StringBuffer();
            name.append(filterField);
            name.append(":[");
            for(ValueCount value: values) {
                name.append(value.getVal());
                name.append(",");
            }
            name.append("]");
        }
        return name.toString();
    }

    @Override
    public String getFilterExpression() {
        if(true) {
            queryStr = new StringBuffer();
            int i=0;
            for(ValueCount value: values) {
                queryStr.append(filterField);
                if(value instanceof RangeCount) {
                    RangeCount range = (RangeCount) value;
                    queryStr.append(":[");
                    queryStr.append(range.getStart());
                    queryStr.append(" TO ");
                    queryStr.append(range.getEnd());
                    queryStr.append("]");
                }else {
                    queryStr.append(":\"");
                    queryStr.append(value.getVal());
                    queryStr.append("\"");
                }
                i++;
                if(i<values.size()) {
                    queryStr.append(" OR ");
                }
            }
        }
        return queryStr.toString();
    }
}
