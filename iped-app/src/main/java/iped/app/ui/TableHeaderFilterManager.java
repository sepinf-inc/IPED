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
import iped.app.ui.controls.table.MetadataValueSearchList;
import iped.app.ui.filters.EqualsFilter;
import iped.app.ui.filters.StartsWithFilter;
import iped.app.ui.filters.ValueFilter;
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

public class TableHeaderFilterManager implements IResultSetFilterer, IQueryFilterer {

    private static TableHeaderFilterManager singleton = new TableHeaderFilterManager();

    private HashMap<String, Set<ValueCount>> selectedValues = new HashMap<String, Set<ValueCount>>();
    private HashMap<String, MetadataSearch> panels = new HashMap<String, MetadataSearch>();
    private HashMap<String, IFilter> definedFilters = new HashMap<String, IFilter>();

    // store the filters defined by the FieldValuePopupMenu
    private HashMap<String, String> otherFilters = new HashMap<String, String>();
    private HashMap<String, String> escapedFields = new HashMap<String, String>();

    static public TableHeaderFilterManager get() {
        return singleton;
    }

    private TableHeaderFilterManager() {
    }

    private String escape(String string) {
        return QueryBuilder.escape(string);
    }

    public void removeFilter(String field) {
        field = escape(field);
        selectedValues.remove(field);
        otherFilters.remove(field);
        panels.remove(field);
        definedFilters.remove(field);
        App.get().getFilterManager().notifyFilterChange();
    }

    class TableHeaderFilter implements IQueryFilter {
        private Query query;
        private String filterExpression;
        private String escapedField;
        MetadataSearch metadataSearch;

        public TableHeaderFilter(MetadataSearch metadataSearch, String escapedField, String filterExpression) {
            this.filterExpression = filterExpression;
            this.escapedField = escapedField;
            this.metadataSearch = metadataSearch;
        }

        @Override
        public Query getQuery() {
            if (query == null) {
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

        public MetadataSearch getMetadataSearch() {
            return metadataSearch;
        }
        // TODO Auto-generated method stub

    }

    private void addQueryFilter(String escapedField, String filterExpression) {
        otherFilters.put(escapedField, filterExpression);
        definedFilters.put(escapedField,
                new TableHeaderFilter(panels.get(escapedField), escapedField, filterExpression));
        selectedValues.remove(escapedField);
        App.get().getFilterManager().notifyFilterChange();
    }

    public void addEmptyFilter(String field) {
        field = escape(field);
        String filterExpression = "-" + field;
        if (IndexItem.isNumeric(field)) {
            filterExpression += ":[* TO *]";
        } else {
            filterExpression += ":?*";
        }
        addQueryFilter(field, filterExpression);
    }

    public void addNonEmptyFilter(String field) {
        field = escape(field);
        String filterExpression = field;
        if (IndexItem.isNumeric(field)) {
            filterExpression += ":[* TO *]";
        } else {
            filterExpression += ":?*";
        }
        addQueryFilter(field, filterExpression);
    }

    public void removeEmptyFilter(String field) {
        field = escape(field);
        otherFilters.remove(field);
        definedFilters.remove(field);
        App.get().getFilterManager().notifyFilterChange();
    }

    public void removeNonEmptyFilter(String field) {
        field = escape(field);
        otherFilters.remove(field);
        definedFilters.remove(field);
        App.get().getFilterManager().notifyFilterChange();
    }

    public void addFilter(String field, Set<ValueCount> selected) {
        field = escape(field);
        selectedValues.put(field, selected);
        definedFilters.put(field, new ValueCountQueryFilter(panels.get(field), field, selected));

        // clear any other filter defined
        otherFilters.remove(field);
        App.get().getFilterManager().notifyFilterChange();
    }

    public void addEqualsFilter(String field, String value) {
        EqualsFilter filter = new EqualsFilter(field, value);
        field = escape(field);
        definedFilters.put(field, filter);
        selectedValues.remove(field);

        // clear any other filter defined
        otherFilters.remove(field);
        otherFilters.put(field, value);
        App.get().getFilterManager().notifyFilterChange();
    }

    public void addStartsWithFilter(String field, String value) {
        StartsWithFilter filter = new StartsWithFilter(field, value);
        field = escape(field);
        definedFilters.put(field, filter);
        selectedValues.remove(field);

        // clear any other filter defined
        otherFilters.remove(field);
        otherFilters.put(field, value);
        App.get().getFilterManager().notifyFilterChange();
    }

    public Set<ValueCount> getFilter(String field) {
        field = escape(field);
        return selectedValues.get(field);
    }

    public boolean isFieldFiltered(String field) {
        field = escape(field);
        return definedFilters.get(field) != null || selectedValues.get(field) != null || otherFilters.get(field) != null;
    }

    public MetadataSearch getMetadataSearch(String field) {
        MetadataSearch result = panels.get(field);
        if (result == null) {
            result = new MetadataSearch();
            String escapedField = escape(field);
            panels.put(escapedField, result);
            escapedFields.put(escapedField, field);
        }
        return result;
    }

    @Override
    public Query getQuery() {
        if (definedFilters.size() == 0) {
            return null;
        }
        int i = 0;
        BooleanQuery.Builder boolQuery = new BooleanQuery.Builder();
        for (IFilter filter : definedFilters.values()) {
            if (filter instanceof IQueryFilter && !(filter instanceof ValueCountQueryFilter)) {
                boolQuery.add(((IQueryFilter) filter).getQuery(), Occur.MUST);
                i++;
            }
        }
        return i > 0 ? boolQuery.build() : null;
    }

    @Override
    public boolean hasFiltersApplied() {
        return !(otherFilters.isEmpty() && selectedValues.isEmpty() && definedFilters.isEmpty());
    }

    public boolean getContainsEmptyFilter(String field) {
        field = escape(field);
        String filter = otherFilters.get(field);
        return filter != null && (filter.equals("-" + field + ":?*") || filter.equals("-" + field + ":[* TO *]"));
    }

    public boolean getContainsNonEmptyFilter(String field) {
        field = escape(field);
        String filter = otherFilters.get(field);
        return filter != null && (filter.equals(field + ":?*") || filter.equals(field + ":[* TO *]"));
    }

    public void addFilter(String field, String value) {
        field = escape(field);
        value = escape(value.trim());
        addQueryFilter(field, field + ":\"" + value + "\"");
    }

    public void addNegativeFilter(String field, String value) {
        field = escape(field);
        value = escape(value.trim());
        addQueryFilter(field, "-" + field + ":\"" + value + "\"");
    }

    public void addLessThanFilter(String field, String value) {
        field = escape(field);
        value = escape(value.trim());
        addQueryFilter(field, field + ":[* TO " + value + "]");
    }

    public void addGreaterThanFilter(String field, String value) {
        field = escape(field);
        value = escape(value.trim());
        addQueryFilter(field, field + ":[" + value + " TO *]");
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
            public IMultiSearchResult filterResult(IMultiSearchResult src) throws ParseException, QueryNodeException, IOException {
                MultiSearchResult result = (MultiSearchResult) src;
                for (String filterField : panels.keySet()) {
                    MetadataSearch internalMetadataSearch = panels.get(filterField);
                    Set<Integer> ords = new HashSet<>();
                    Set<ValueCount> values = selectedValues.get(filterField);
                    if (values != null && values.size() > 0) {
                        for (ValueCount value : values) {
                            ords.add(value.getOrd());
                        }
                        result = internalMetadataSearch.getIdsWithOrd(result, escapedFields.get(filterField), ords);
                        if (result.getLength() <= 0) {
                            return result;
                        }
                    }
                }
                for (IFilter filter : definedFilters.values()) {
                    if (filter instanceof IResultSetFilter) {
                        IResultSetFilter rsFilter = (IResultSetFilter) filter;
                        result = (MultiSearchResult) rsFilter.filterResult(result);
                    }
                    if (result.getLength() <= 0) {
                        return result;
                    }
                }
                return result;
            }
        };
    }

    @Override
    public boolean hasFilters() {
        return definedFilters.size() > 0;
    }

    @Override
    public void clearFilter() {
        panels.clear();
        otherFilters.clear();
        definedFilters.clear();
        selectedValues.clear();
        MetadataValueSearchList.clearSelectedValues();
        App.get().getFilterManager().notifyFilterChange();
    }

    public HashMap<String, String> getOtherFilters() {
        return otherFilters;
    }

    @Override
    public void restoreDefinedFilters(List<IFilter> filtersToRestore) {
        definedFilters.clear();
        otherFilters.clear();
        selectedValues.clear();
        panels.clear();
        for (IFilter filter : filtersToRestore) {
            String escapedField = null;
            if (filter instanceof TableHeaderFilter) {
                escapedField = ((TableHeaderFilter) filter).escapedField;
                otherFilters.put(escapedField, ((TableHeaderFilter) filter).filterExpression);
                definedFilters.put(escapedField, filter);
                selectedValues.remove(escapedField);
                panels.put(escapedField, ((TableHeaderFilter) filter).getMetadataSearch());
            }

            if (filter instanceof ValueCountQueryFilter) {
                ValueCountQueryFilter vcqFilter = (ValueCountQueryFilter) filter;
                escapedField = vcqFilter.getFilterField();
                definedFilters.put(escapedField, filter);
                selectedValues.put(escapedField, vcqFilter.getValues());
                otherFilters.remove(escapedField);
                panels.put(escapedField, vcqFilter.getMetadataSearch());
            }

            if (filter instanceof ValueFilter) {
                ValueFilter eqFilter = (ValueFilter) filter;
                escapedField = escape(eqFilter.getField());
                definedFilters.put(escapedField, filter);
                otherFilters.put(escapedField, eqFilter.getValue());
            }
        }
        App.get().getFilterManager().notifyFilterChange();
    }

}
