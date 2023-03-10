package iped.app.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;

import iped.app.metadata.MetadataSearch;
import iped.app.metadata.RangeCount;
import iped.app.metadata.ValueCount;
import iped.app.metadata.ValueCountQueryFilter;
import iped.engine.search.MultiSearchResult;
import iped.engine.search.QueryBuilder;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.search.IMultiSearchResult;
import iped.viewers.api.IFilter;
import iped.viewers.api.IQueryFilter;
import iped.viewers.api.IQueryFilterer;
import iped.viewers.api.IResultSetFilter;
import iped.viewers.api.IResultSetFilterer;

public class TableHeaderFilterManager implements IResultSetFilterer, IQueryFilterer{
    HashMap<String, Set<ValueCount>> selectedValues = new HashMap<String, Set<ValueCount>>();
    HashMap<String, MetadataSearch> panels = new HashMap<String, MetadataSearch>();

    HashMap<String, String> otherFilters = new HashMap<String, String>();

    MetadataPanel internalMetadataPanel;

    private boolean applyFilters;

    private static TableHeaderFilterManager singleton = new TableHeaderFilterManager();

    static public TableHeaderFilterManager get() {
        return singleton;
    }

    private TableHeaderFilterManager() {
        
    }

    public void removeFilter(String field) {
        selectedValues.remove(field);
        otherFilters.remove(field);
        panels.remove(field);
        App.get().getFilterManager().notifyFilterChange();
    }

    public void addEmptyFilter(String field) {
        String fieldStr =field.replace(":", "\\:");
        otherFilters.put(field, "-"+fieldStr+":?*");

        selectedValues.remove(field);
        App.get().getFilterManager().notifyFilterChange();
    }

    public void addNonEmptyFilter(String field) {
        String fieldStr =field.replace(":", "\\:");
        otherFilters.put(field, fieldStr+":?*");

        selectedValues.remove(field);
        App.get().getFilterManager().notifyFilterChange();
    }

    public void removeEmptyFilter(String field) {
        otherFilters.remove(field);
        App.get().getFilterManager().notifyFilterChange();
    }

    public void removeNonEmptyFilter(String field) {
        otherFilters.remove(field);
        App.get().getFilterManager().notifyFilterChange();
    }

    public void addFilter(String field, Set<ValueCount> selected) {
        selectedValues.put(field, selected);
        otherFilters.remove(field);
        App.get().getFilterManager().notifyFilterChange();
    }

    public Set<ValueCount> getFilter(String field) {
        return selectedValues.get(field);
    }

    public boolean isFieldFiltered(String field) {
        return selectedValues.get(field)!=null || otherFilters.get(field)!=null;
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
        try {
            if (true) {
                if (otherFilters.size()==0) {
                    return null;
                }

                Query result = new QueryBuilder(App.get().appCase).getQuery("");
                
                StringBuffer filterStr = new StringBuffer();

                filterStr.append("(");
                int i = 0;
                for (String filter : otherFilters.values()) {
                    filterStr.append("(");
                    filterStr.append(filter);
                    filterStr.append(")");
                    i++;
                    if(i<=otherFilters.size()-1) {
                        filterStr.append(" && ");
                    }
                }

                filterStr.append(")");

                BooleanQuery.Builder boolQuery = new BooleanQuery.Builder();
                boolQuery.add(new QueryBuilder(App.get().appCase).getQuery(filterStr.toString()), Occur.MUST);
                boolQuery.add(result, Occur.MUST);
                result = boolQuery.build();

                return result;
            } else {
                return null;
            }
        } catch (QueryNodeException | ParseException qne) {
            qne.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean hasFiltersApplied() {
        return !(otherFilters.size()==0 && selectedValues.size()==0);
    }

    public boolean getContainsEmptyFilter(String field) {
        String filter = otherFilters.get(field);
        String fieldStr =field.replace(":", "\\:");
        return filter!=null && filter.equals("-"+fieldStr+":?*");
    }

    public boolean getContainsNonEmptyFilter(String field) {
        String filter = otherFilters.get(field);
        String fieldStr =field.replace(":", "\\:");
        return filter!=null && filter.equals(fieldStr+":?*");
    }

    public void addFilter(String field, String string) {
        string = string.trim();
        int i=string.indexOf(field);
        if(i>=0) {
           String fieldStr = field.replace(":", "\\:");
           string=string.substring(0,i)+ fieldStr + string.substring(i+field.length()); 
        }
        otherFilters.put(field, string);
        selectedValues.remove(field);
        App.get().getFilterManager().notifyFilterChange();
    }
    

    
    @Override
    public List<IFilter> getDefinedFilters() {
        ArrayList<IFilter> result = new ArrayList<IFilter>();
        for (String filterField : panels.keySet()) {
            /*
            result.add(new IResultSetFilter() {
                MetadataSearch internalMetadataSearch = panels.get(filterField);
                Set<ValueCount> values = selectedValues.get(filterField);
                StringBuffer name = null;
                
                @Override
                public IMultiSearchResult filterResult(IMultiSearchResult src)
                        throws ParseException, QueryNodeException, IOException {
                    MultiSearchResult result = (MultiSearchResult) src;
                    Set<Integer> ords = new HashSet<>();
                    if(values!=null && values.size()>0) {
                        for(ValueCount value: values) {
                            ords.add(value.getOrd());
                        }
                        result = internalMetadataSearch.getIdsWithOrd(result, filterField, ords);
                    }
                    return result;
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
            });
            */
            result.add(new ValueCountQueryFilter(filterField , selectedValues.get(filterField)));
        }
        for (String filterField : otherFilters.keySet()) {
            String qFilter = otherFilters.get(filterField);
            if(qFilter!=null) {
                result.add(new IQueryFilter() {
                    @Override
                    public String getFilterExpression() {
                        return qFilter;
                    }
                    public String toString() {
                        return qFilter;
                    }
                });
            }
        }
        return result;
    }
    
    public String toString() {
        return "Main table header filterer";
    }

    @Override
    public Map<Integer, BitSet> getFilteredBitSets(IMultiSearchResult input) {
        // TODO Auto-generated method stub
        return null;
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
                    }
                }
                return result;
            }
        };
    }
}
