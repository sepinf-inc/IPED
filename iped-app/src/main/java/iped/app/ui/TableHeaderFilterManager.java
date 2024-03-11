package iped.app.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;

import iped.app.metadata.MetadataSearch;
import iped.app.metadata.ValueCount;
import iped.app.metadata.ValueCountQueryFilter;
import iped.app.ui.filters.EqualsFilter;
import iped.app.ui.filters.StartsWithFilter;
import iped.engine.search.MultiSearchResult;
import iped.engine.search.QueryBuilder;
import iped.engine.task.index.IndexItem;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.search.IMultiSearchResult;
import iped.viewers.api.IFilter;
import iped.viewers.api.IQueryFilter;
import iped.viewers.api.IQueryFilterer;
import iped.viewers.api.IResultSetFilter;
import iped.viewers.api.IResultSetFilterer;

public class TableHeaderFilterManager implements IResultSetFilterer, IQueryFilterer{

    private static TableHeaderFilterManager singleton = new TableHeaderFilterManager();

    private HashMap<String, Set<ValueCount>> selectedValues = new HashMap<String, Set<ValueCount>>();
    HashMap<String, MetadataSearch> panels = new HashMap<String, MetadataSearch>();
    private HashMap<String, IFilter> definedFilters = new HashMap<String, IFilter>();
    private HashMap<String, String> otherFilters = new HashMap<String, String>();

    static public TableHeaderFilterManager get() {
        return singleton;
    }

    private TableHeaderFilterManager() {
    }

    private String escapeField(String field) {
        return QueryBuilder.escape(field);
    }

    public void removeFilter(String field) {
        field = escapeField(field);
        selectedValues.remove(field);
        otherFilters.remove(field);
        panels.remove(field);
        definedFilters.remove(field);
        App.get().getFilterManager().notifyFilterChange();
    }

    private void addQueryFilter(String escapedField, String filterExpression) {
        otherFilters.put(escapedField, filterExpression);
        definedFilters.put(escapedField, new IQueryFilter() {
            private Query query;
            @Override
            public Query getQuery() {
                if(query==null) {
                    try {
                        query = new QueryBuilder(App.get().appCase).getQuery(filterExpression);
                    } catch (ParseException | QueryNodeException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
                return query;
            }
            public String toString() {
                return filterExpression;
            }
        });
        selectedValues.remove(escapedField);
        App.get().getFilterManager().notifyFilterChange();
    }
    
    public void addEmptyFilter(String field) {
        field = escapeField(field);
        String filterExpression = "-" + field;
        if(IndexItem.isNumeric(field)) {
            filterExpression+=":[* TO *]";
        }else {
            filterExpression+=":?*";
        }
        addQueryFilter(field, filterExpression);
    }

    public void addNonEmptyFilter(String field) {
        field = escapeField(field);
        String filterExpression = field;
        if(IndexItem.isNumeric(field)) {
            filterExpression+=":[* TO *]";
        }else {
            filterExpression+=":?*";
        }
        addQueryFilter(field, filterExpression);
    }

    public void removeEmptyFilter(String field) {
        field = escapeField(field);
        otherFilters.remove(field);
        definedFilters.remove(field);
        App.get().getFilterManager().notifyFilterChange();
    }

    public void removeNonEmptyFilter(String field) {
        field = escapeField(field);
        otherFilters.remove(field);
        definedFilters.remove(field);
        App.get().getFilterManager().notifyFilterChange();
    }

    public void addFilter(String field, Set<ValueCount> selected) {
        field = escapeField(field);
        selectedValues.put(field, selected);
        definedFilters.put(field, new ValueCountQueryFilter(field , selected));
        otherFilters.remove(field);
        App.get().getFilterManager().notifyFilterChange();
    }

    public void addEqualsFilter(String field, String value) {
        field = escapeField(field);
        definedFilters.put(field, new EqualsFilter(field, value));
        selectedValues.remove(field);
        otherFilters.remove(field);
        App.get().getFilterManager().notifyFilterChange();
    }

    public void addStartsWithFilter(String field, String value) {
        field = escapeField(field);
        definedFilters.put(field, new StartsWithFilter(field, value));
        selectedValues.remove(field);
        otherFilters.remove(field);
        App.get().getFilterManager().notifyFilterChange();
    }

    public Set<ValueCount> getFilter(String field) {
        field = escapeField(field);
        return selectedValues.get(field);
    }

    public boolean isFieldFiltered(String field) {
        field = escapeField(field);
        return definedFilters.get(field)!=null || selectedValues.get(field)!=null || otherFilters.get(field)!=null;
    }

    public MetadataSearch getMetadataSearch(String field) {
        MetadataSearch result = panels.get(field);
        if(result==null) {
            result = new MetadataSearch();
            panels.put(field, result);
        }
        return result;
    }

    @Override
    public Query getQuery() {
        if (true) {
            if (definedFilters.size()==0) {
                return null;
            }

            Query result;
            
            BooleanQuery.Builder boolQuery = new BooleanQuery.Builder();

            int i = 0;
            for (IFilter filter : definedFilters.values()) {
                if(filter instanceof IQueryFilter) {
                    boolQuery.add(((IQueryFilter)filter).getQuery(), Occur.MUST);
                }
                i++;
            }

            result = boolQuery.build();

            return result;
        } else {
            return null;
        }
    }

    @Override
    public boolean hasFiltersApplied() {
        return !(otherFilters.size()==0 && selectedValues.size()==0);
    }

    public boolean getContainsEmptyFilter(String field) {
        field = escapeField(field);
        String filter = otherFilters.get(field);
        return filter != null && (filter.equals("-" + field + ":?*") || filter.equals("-" + field + ":[* TO *]"));
    }

    public boolean getContainsNonEmptyFilter(String field) {
        field = escapeField(field);
        String filter = otherFilters.get(field);
        return filter != null && (filter.equals(field + ":?*") || filter.equals(field + ":[* TO *]"));
    }

    public void addFilter(String field, String string) {
        field = escapeField(field);
        string = string.trim();
        int i=string.indexOf(field);
        if(i>=0) {
            string = string.substring(0, i) + field + string.substring(i + field.length());
        }
        addQueryFilter(field, string);
    }
    
    @Override
    public List<IFilter> getDefinedFilters() {
        ArrayList<IFilter> list = new ArrayList<>();
        list.addAll(definedFilters.values());
        return list;
    }
    
    public String toString() {
        return "Main table header filterer";
    }

    @Override
    public IFilter getFilter() {
        return new IResultSetFilter() {
            @Override
            public IMultiSearchResult filterResult(IMultiSearchResult src)
                    throws ParseException, QueryNodeException, IOException {
                MultiSearchResult result = (MultiSearchResult) src;
                for (String filterField : panels.keySet()) {
                    MetadataSearch internalMetadataSearch = panels.get(filterField);
                    Set<Integer> ords = new HashSet<>();
                    Set<ValueCount> values = selectedValues.get(filterField);
                    if(values!=null && values.size()>0) {
                        for(ValueCount value: values) {
                            ords.add(value.getOrd());
                        }
                        result = internalMetadataSearch.getIdsWithOrd(result, filterField, ords); 
                        if(result.getLength()<=0) {
                            return result;
                        }
                    }
                }
                for (IFilter filter : definedFilters.values()) {
                    if(filter instanceof IResultSetFilter) {
                        IResultSetFilter rsFilter = (IResultSetFilter) filter;
                        result = (MultiSearchResult) rsFilter.filterResult(result);
                    }
                    if(result.getLength()<=0) {
                        return result;
                    }
                }
                return result;
            }
        };
    }

    @Override
    public boolean hasFilters() {
        return definedFilters.size()>0;
    }

    @Override
    public void clearFilter() {
        panels.clear();        
        otherFilters.clear();
        definedFilters.clear();
        selectedValues.clear();
    }
}
